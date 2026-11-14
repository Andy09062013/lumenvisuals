package net.lumen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Saved to config/lumen-server.json on the server. */
public final class ServerConfig {
	public static final String DEFAULT_URL = "https://andy09062013.github.io/lumenvisuals/";
	/** Old default links that get upgraded automatically. */
	private static final String[] OLD_URLS = {
			"https://modrinth.com/mod/lumen",
			"https://github.com/Andy09062013/lumenvisuals/releases/latest/download/lumen.jar",
			"https://github.com/Andy09062013/lumenvisuals/raw/main/download/lumen.jar"
	};

	/** false: anyone can join, players without Lumen just get a chat tip. true: players without Lumen are kicked. */
	public boolean requireClientMod = false;
	/** Tell players without Lumen where to get it when they join (only used when it's optional). */
	public boolean suggestToPlayersWithout = true;
	public int configVersion = 2;
	public String downloadUrl = DEFAULT_URL;
	public String kickMessage = "This server requires the Lumen visual mod (client side, Fabric 1.21.11).";

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static ServerConfig load() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("lumen-server.json");
		ServerConfig cfg = new ServerConfig();
		try {
			if (Files.exists(file)) {
				try (Reader r = Files.newBufferedReader(file)) {
					ServerConfig read = GSON.fromJson(r, ServerConfig.class);
					if (read != null) cfg = read;
					if (cfg.configVersion < 2) {
						// Lumen is optional now: older configs that required it are switched over once
						cfg.requireClientMod = false;
						cfg.suggestToPlayersWithout = true;
						cfg.configVersion = 2;
					}
					for (String o : OLD_URLS) if (o.equals(cfg.downloadUrl)) cfg.downloadUrl = DEFAULT_URL;
				}
			}
			Files.createDirectories(file.getParent());
			try (Writer w = Files.newBufferedWriter(file)) { GSON.toJson(cfg, w); }
		} catch (Exception e) {
			System.err.println("[Lumen] couldn't read server config: " + e);
		}
		return cfg;
	}
}
