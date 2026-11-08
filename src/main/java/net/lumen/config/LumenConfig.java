package net.lumen.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Saved to config/lumen.json */
public final class LumenConfig {
	public static final class Setting {
		public boolean enabled = true;
		/** 0.0 to 2.0, 1.0 = normal */
		public float intensity = 1.0f;
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("lumen.json");

	public static Preset preset = Preset.HIGH;
	private static final EnumMap<Effect, Setting> SETTINGS = new EnumMap<>(Effect.class);

	static { applyPreset(Preset.HIGH); }

	public static Setting get(Effect e) {
		return SETTINGS.computeIfAbsent(e, k -> new Setting());
	}

	public static boolean on(Effect e) {
		Setting s = get(e);
		return s.enabled && s.intensity > 0.001f;
	}

	/** Effect strength: 0 when disabled. */
	public static float strength(Effect e) {
		Setting s = get(e);
		return s.enabled ? s.intensity : 0f;
	}

	public static void applyPreset(Preset p) {
		preset = p;
		if (p == Preset.CUSTOM) return;
		for (Effect e : Effect.values()) {
			Setting s = get(e);
			s.enabled = p.enables(e);
			s.intensity = 1.0f;
		}
	}

	private static final class Saved {
		String preset;
		Map<String, Setting> effects = new LinkedHashMap<>();
	}

	public static void load() {
		try {
			if (!Files.exists(FILE)) { save(); return; }
			try (Reader r = Files.newBufferedReader(FILE)) {
				Saved s = GSON.fromJson(r, Saved.class);
				if (s == null) return;
				try { preset = Preset.valueOf(s.preset); } catch (Exception ignored) { preset = Preset.HIGH; }
				applyPreset(preset);
				if (s.effects != null) {
					for (Map.Entry<String, Setting> en : s.effects.entrySet()) {
						try {
							Effect e = Effect.valueOf(en.getKey());
							Setting v = en.getValue();
							Setting cur = get(e);
							cur.enabled = v.enabled;
							cur.intensity = Math.max(0f, Math.min(2f, v.intensity));
						} catch (Exception ignored) {}
					}
				}
			}
		} catch (Exception e) {
			System.err.println("[Lumen] couldn't read config: " + e);
		}
	}

	public static void save() {
		try {
			Saved s = new Saved();
			s.preset = preset.name();
			for (Effect e : Effect.values()) s.effects.put(e.name(), get(e));
			Files.createDirectories(FILE.getParent());
			try (Writer w = Files.newBufferedWriter(FILE)) { GSON.toJson(s, w); }
		} catch (Exception e) {
			System.err.println("[Lumen] couldn't save config: " + e);
		}
	}

	private LumenConfig() {}
}
