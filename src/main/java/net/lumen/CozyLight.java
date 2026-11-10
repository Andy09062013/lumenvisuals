package net.lumen;

import net.lumen.config.Effect;
import net.lumen.config.LumenConfig;
import net.minecraft.client.MinecraftClient;

/**
 * Cozy fullbright: like fullbright, but dark places are lit the way torches light them (warm),
 * by raising the minimum block light used for drawing. Purely visual: real light levels and mob spawning don't change.
 */
public final class CozyLight {
	private static volatile int level;
	private static boolean last;

	/** Minimum block light to draw with, 0 when off. Read from chunk building threads. */
	public static int level() {
		return level;
	}

	public static void tick(MinecraftClient client) {
		boolean on = LumenConfig.on(Effect.COZY_BRIGHT);
		float strength = LumenConfig.strength(Effect.COZY_BRIGHT);
		level = on ? Math.max(1, Math.min(15, Math.round(12f * Math.min(strength, 1.25f)))) : 0;
		if (on != last) {
			last = on;
			// rebuild chunks so the new lighting shows up everywhere
			if (client.worldRenderer != null && client.world != null) client.worldRenderer.reload();
		}
	}

	private CozyLight() {}
}
