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
	public boolean requireClientMod = true;
	public String downloadUrl = "https://github.com/Andy09062013/lumenvisuals/releases/latest/download/lumen.jar";
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
