package net.lumen;

import net.fabricmc.loader.api.FabricLoader;
import net.lumen.config.Effect;

/** Which effects can actually run with the renderer mods that are installed. */
public final class Compat {
	public static final boolean SODIUM = FabricLoader.getInstance().isModLoaded("sodium");
	/** Distant Horizons draws far-away terrain where the normal depth buffer says "sky". */
	public static final boolean DISTANT_HORIZONS = FabricLoader.getInstance().isModLoaded("distanthorizons");

	/** Null if the effect works, otherwise a short reason shown in the settings screen. */
	public static String unavailable(Effect e) {
		if (e.kind == Effect.Kind.SCREEN && IrisCompat.shaderPackActive()) return "off: Iris shaderpack";
		if (SODIUM && (e == Effect.HELD_LIGHT)) return "blocks need no Sodium";
		if (SODIUM && e == Effect.SUN_SHADOWS) return "screen-space with Sodium";
		return null;
	}

	/** Same for the built-in shader packs (index into LumenClient.PACKS). */
	public static String packUnavailable(int pack) {
		if (IrisCompat.shaderPackActive()) return "off: Iris shaderpack";
		if (SODIUM && pack == 0) return "grass only with Sodium";
		return null;
	}

	private Compat() {}
}
