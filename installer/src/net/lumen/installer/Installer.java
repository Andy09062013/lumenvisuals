package net.lumen.installer;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/** Lumen installer: pick a launcher + profile, it puts Lumen (+ Fabric API, optional Distant Horizons) in that profile's mods folder. */
public class Installer {
    static final String MC = "1.21.11";
    static final String LUMEN_URL = "https://github.com/Andy09062013/lumenvisuals/raw/main/download/lumen.jar";
    static final String UA = "Andy09062013/LumenInstaller/1.1";

    static class Profile {
        final String name; final Path mods; final boolean fabric;
        Profile(String name, Path mods, boolean fabric) { this.name = name; this.mods = mods; this.fabric = fabric; }
        public String toString() { return fabric ? name + "   [Fabric " + MC + "]" : name; }
    }

    // ---------------- profile detection ----------------
    static Path appdata() {
        String a = System.getenv("APPDATA");
        if (a != null) return Paths.get(a);
        String home = System.getProperty("user.home");
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) return Paths.get(home, "Library", "Application Support");
        return Paths.get(home);
    }

    static Path mcDir() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) return appdata().resolve(".minecraft");
        if (os.contains("mac")) return appdata().resolve("minecraft");
        return Paths.get(System.getProperty("user.home"), ".minecraft");
    }

    static String read(Path p) throws IOException { return new String(Files.readAllBytes(p), StandardCharsets.UTF_8); }

    static List<Profile> official() {
        List<Profile> out = new ArrayList<>();
        Path root = mcDir();
        Path f = root.resolve("launcher_profiles.json");
        if (!Files.exists(f)) return out;
        try {
            Map<?, ?> json = (Map<?, ?>) Json.parse(read(f));
            Map<?, ?> profiles = (Map<?, ?>) json.get("profiles");
            if (profiles == null) return out;
            for (Object v : profiles.values()) {
                if (!(v instanceof Map)) continue;
                Map<?, ?> p = (Map<?, ?>) v;
                String name = str(p.get("name"));
                if (name.isEmpty()) name = str(p.get("type"));
                if (name.isEmpty()) name = "Unnamed profile";
                String ver = str(p.get("lastVersionId"));
                boolean fabric = ver.startsWith("fabric-loader") && ver.endsWith(MC);
                String gd = str(p.get("gameDir"));
                Path dir = gd.isEmpty() ? root : Paths.get(gd);
                if (!ver.isEmpty() && !fabric) name = name + "  (" + ver + ")";
                out.add(new Profile(name, dir.resolve("mods"), fabric));
            }
        } catch (Exception ignored) {}
        return out;
    }

    static List<Profile> prism() {
        List<Profile> out = new ArrayList<>();
        Path a = appdata();
        for (Path base : new Path[]{a.resolve("PrismLauncher/instances"), a.resolve("MultiMC/instances"),
                Paths.get(System.getProperty("user.home"), ".local/share/PrismLauncher/instances")}) {
            for (Path d : dirs(base)) {
                Path cfg = d.resolve("instance.cfg");
                if (!Files.exists(cfg)) continue;
                String name = d.getFileName().toString();
                try {
                    for (String line : Files.readAllLines(cfg, StandardCharsets.UTF_8))
                        if (line.startsWith("name=")) { name = line.substring(5); break; }
                } catch (IOException ignored) {}
                Path game = d.resolve("minecraft");
                if (!Files.exists(game) && Files.exists(d.resolve(".minecraft"))) game = d.resolve(".minecraft");
                boolean fabric = false;
                try {
                    String t = read(d.resolve("mmc-pack.json"));
                    fabric = t.contains("net.fabricmc.fabric-loader") && t.contains("\"" + MC + "\"");
                } catch (IOException ignored) {}
                out.add(new Profile(name, game.resolve("mods"), fabric));
            }
        }
        return out;
    }

    static List<Profile> modrinth() {
        List<Profile> out = new ArrayList<>();
        Path a = appdata();
        for (Path base : new Path[]{a.resolve("ModrinthApp/profiles"), a.resolve("com.modrinth.theseus/profiles")}) {
            for (Path d : dirs(base)) {
                String n = d.getFileName().toString();
                boolean fabric = n.toLowerCase().contains("fabric") && n.contains(MC);
                out.add(new Profile(n, d.resolve("mods"), fabric));
            }
        }
        return out;
    }

    static List<Profile> curseforge() {
        List<Profile> out = new ArrayList<>();
        Path base = Paths.get(System.getProperty("user.home"), "curseforge", "minecraft", "Instances");
        for (Path d : dirs(base)) {
            boolean fabric = false;
            try {
                Map<?, ?> j = (Map<?, ?>) Json.parse(read(d.resolve("minecraftinstance.json")));
                Object bml = j.get("baseModLoader");
                String loader = bml instanceof Map ? str(((Map<?, ?>) bml).get("name")) : "";
                fabric = loader.toLowerCase().startsWith("fabric") && MC.equals(str(j.get("gameVersion")));
            } catch (Exception ignored) {}
            out.add(new Profile(d.getFileName().toString(), d.resolve("mods"), fabric));
        }
        return out;
    }

    static List<Path> dirs(Path base) {
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(base)) return out;
        try (DirectoryStream<Path> s = Files.newDirectoryStream(base)) {
            for (Path p : s) if (Files.isDirectory(p)) out.add(p);
        } catch (IOException ignored) {}
        return out;
    }

    static String str(Object o) { return o == null ? "" : o.toString(); }

    // ---------------- downloads ----------------
    static HttpURLConnection open(String url) throws IOException {
        for (int i = 0; i < 6; i++) {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestProperty("User-Agent", UA);
            c.setConnectTimeout(15000);
            c.setReadTimeout(60000);
            c.setInstanceFollowRedirects(false);
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) { url = new URL(new URL(url), c.getHeaderField("Location")).toString(); continue; }
            if (code != 200) throw new IOException("HTTP " + code + " from " + new URL(url).getHost());
            return c;
        }
        throw new IOException("Too many redirects");
    }

    static void download(String url, Path to) throws IOException {
        Path tmp = to.resolveSibling(to.getFileName() + ".part");
        try (InputStream in = open(url).getInputStream()) {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.move(tmp, to, StandardCopyOption.REPLACE_EXISTING);
    }

    /** returns {url, filename} of the newest Fabric MC build of a Modrinth project */
    static String[] modrinthFile(String project) throws IOException {
        String gv = URLEncoder.encode("[\"" + MC + "\"]", "UTF-8");
        String ld = URLEncoder.encode("[\"fabric\"]", "UTF-8");
        String body;
        try (InputStream in = open("https://api.modrinth.com/v2/project/" + project + "/version?game_versions=" + gv + "&loaders=" + ld).getInputStream()) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            body = new String(b.toByteArray(), StandardCharsets.UTF_8);
        }
        List<?> versions = (List<?>) Json.parse(body);
        if (versions.isEmpty()) throw new IOException("No " + project + " build for " + MC + " yet");
        List<?> files = (List<?>) ((Map<?, ?>) versions.get(0)).get("files");
        Map<?, ?> pick = (Map<?, ?>) files.get(0);
        for (Object f : files) if (Boolean.TRUE.equals(((Map<?, ?>) f).get("primary"))) { pick = (Map<?, ?>) f; break; }
        return new String[]{str(pick.get("url")), str(pick.get("filename"))};
    }

    static boolean has(Path mods, String regex) {
        File[] fs = mods.toFile().listFiles();
        if (fs == null) return false;
        for (File f : fs) if (f.getName().toLowerCase().matches(regex)) return true;
        return false;
    }

    // ---------------- window ----------------
    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        SwingUtilities.invokeLater(Installer::show);
    }

    static void show() {
        JFrame f = new JFrame("Lumen Installer");
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));

        JLabel title = new JLabel("Install Lumen for Minecraft " + MC);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 17f));
        p.add(left(title)); p.add(Box.createVerticalStrut(12));

        p.add(left(new JLabel("1. Which launcher do you use?")));
        JComboBox<String> launcher = new JComboBox<>(new String[]{"Pick one...", "Minecraft Launcher (official)", "Prism Launcher / MultiMC", "Modrinth App", "CurseForge"});
        launcher.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        p.add(left(launcher)); p.add(Box.createVerticalStrut(10));

        p.add(left(new JLabel("2. Which profile? (Fabric " + MC + " ones are marked and listed first)")));
        DefaultListModel<Profile> model = new DefaultListModel<>();
        JList<Profile> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane sp = new JScrollPane(list);
        sp.setPreferredSize(new Dimension(460, 160));
        p.add(left(sp)); p.add(Box.createVerticalStrut(8));

        JCheckBox api = new JCheckBox("Fabric API (required, skipped if you already have it)", true);
        JCheckBox dh = new JCheckBox("Distant Horizons (recommended, see much further)", true);
        p.add(left(api)); p.add(left(dh)); p.add(Box.createVerticalStrut(10));

        JButton install = new JButton("Install");
        JButton openBtn = new JButton("Open mods folder");
        openBtn.setEnabled(false);
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.add(install); row.add(Box.createHorizontalStrut(8)); row.add(openBtn);
        p.add(left(row)); p.add(Box.createVerticalStrut(10));

        JLabel status = new JLabel("Pick your launcher to begin.");
        p.add(left(status));

        final Path[] last = new Path[1];

        launcher.addActionListener(e -> {
            model.clear();
            List<Profile> found;
            switch (launcher.getSelectedIndex()) {
                case 1: found = official(); break;
                case 2: found = prism(); break;
                case 3: found = modrinth(); break;
                case 4: found = curseforge(); break;
                default: status.setText("Pick your launcher to begin."); return;
            }
            found.sort(Comparator.comparing((Profile q) -> !q.fabric).thenComparing(q -> q.name.toLowerCase()));
            for (Profile q : found) model.addElement(q);
            if (found.isEmpty()) status.setText("<html>No profiles found there. Make a Fabric " + MC + " profile first, then pick the launcher again.</html>");
            else { list.setSelectedIndex(0); status.setText("Pick a profile, then press Install."); }
        });

        install.addActionListener(e -> {
            Profile q = list.getSelectedValue();
            if (q == null) { status.setText("Pick a profile first."); return; }
            if (!q.fabric) {
                int a = JOptionPane.showConfirmDialog(f, "'" + q.name + "' doesn't look like a Fabric " + MC + " profile.\nLumen only runs on Fabric " + MC + ".\n\nInstall into it anyway?", "Lumen Installer", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (a != JOptionPane.YES_OPTION) return;
            }
            install.setEnabled(false);
            boolean wantApi = api.isSelected(), wantDh = dh.isSelected();
            new SwingWorker<Void, String>() {
                protected Void doInBackground() throws Exception {
                    Files.createDirectories(q.mods);
                    publish("Removing old Lumen versions...");
                    File[] fs = q.mods.toFile().listFiles();
                    if (fs != null) for (File x : fs) if (x.getName().toLowerCase().matches("lumen.*\\.jar")) x.delete();
                    publish("Downloading Lumen...");
                    download(LUMEN_URL, q.mods.resolve("lumen.jar"));
                    if (wantApi && !has(q.mods, "fabric-api.*\\.jar")) {
                        publish("Downloading Fabric API...");
                        String[] m = modrinthFile("fabric-api");
                        download(m[0], q.mods.resolve(m[1]));
                    }
                    if (wantDh && !has(q.mods, ".*distant.?horizons.*\\.jar")) {
                        publish("Downloading Distant Horizons...");
                        String[] m = modrinthFile("distanthorizons");
                        download(m[0], q.mods.resolve(m[1]));
                    }
                    return null;
                }
                protected void process(List<String> chunks) { status.setText(chunks.get(chunks.size() - 1)); }
                protected void done() {
                    install.setEnabled(true);
                    try {
                        get();
                        last[0] = q.mods;
                        openBtn.setEnabled(true);
                        status.setText("<html>Done! Installed in '" + q.name + "'. Press F6 in game for settings.</html>");
                        JOptionPane.showMessageDialog(f, "Lumen is installed in '" + q.name + "'.\n\nStart Minecraft with that profile and you're good.", "Lumen Installer", JOptionPane.INFORMATION_MESSAGE);
                    } catch (Exception ex) {
                        Throwable c = ex.getCause() != null ? ex.getCause() : ex;
                        status.setText("<html>Something went wrong: " + c.getMessage() + "</html>");
                    }
                }
            }.execute();
        });

        openBtn.addActionListener(e -> {
            try { if (last[0] != null) Desktop.getDesktop().open(last[0].toFile()); } catch (Exception ignored) {}
        });

        f.setContentPane(p);
        f.pack();
        f.setMinimumSize(new Dimension(520, f.getHeight()));
        f.setLocationRelativeTo(null);
        f.setVisible(true);
    }

    static JComponent left(JComponent c) { c.setAlignmentX(Component.LEFT_ALIGNMENT); return c; }
}
