package net.lumen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Sodium reads its terrain shaders straight from the mod jars (not resource packs), so Lumen hands it
 * patched versions here: foliage sway, sky colours on water, clearer water and soft fog.
 * The built-in Lumen shader packs still decide which of those are on.
 */
public final class SodiumShaders {
	/** Returns replacement source for a Sodium shader, or null to let Sodium load its own. */
	public static String override(Identifier id) {
		try {
			String key = id.toString();
			switch (key) {
				case "sodium:blocks/block_layer_opaque.vsh":
					return read("/lumen_sodium/block_layer_opaque.vsh");
				case "sodium:blocks/block_layer_opaque.fsh":
					return read("/lumen_sodium/block_layer_opaque.fsh");
				case "lumen:sodium/sway.glsl":
					return packOn("lumen_foliage")
							? read("/resourcepacks/lumen_foliage/assets/lumen/shaders/sodium/sway.glsl")
							: read("/assets/lumen/shaders/sodium/sway.glsl");
				case "lumen:sodium/water.glsl":
					return packOn("lumen_water")
							? read("/resourcepacks/lumen_water/assets/lumen/shaders/sodium/water.glsl")
							: read("/assets/lumen/shaders/sodium/water.glsl");
				case "sodium:include/fog.glsl":
					return packOn("lumen_fog") ? read("/resourcepacks/lumen_fog/assets/sodium/shaders/include/fog.glsl") : null;
				default:
					return null;
			}
		} catch (Throwable t) {
			System.err.println("[Lumen] couldn't patch Sodium shader " + id + ": " + t);
			return null;
		}
	}

	private static boolean packOn(String name) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.getResourcePackManager() == null) return true;
		for (ResourcePackProfile p : mc.getResourcePackManager().getEnabledProfiles()) {
			if (p.getId().contains(name)) return true;
		}
		return false;
	}

	private static String read(String path) throws Exception {
		try (InputStream in = SodiumShaders.class.getResourceAsStream(path)) {
			if (in == null) throw new IllegalStateException("missing " + path);
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private SodiumShaders() {}
}
