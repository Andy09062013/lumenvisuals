package net.lumen.config;

public enum Preset {
	LOW("Low", 0.45f, 70),
	MEDIUM("Medium", 0.8f, 150),
	HIGH("High", 1.2f, 280),
	ULTRA("Ultra", 1.8f, 450),
	CUSTOM("Custom", 1.2f, 280);

	public final String label;
	/** Particle density multiplier. */
	public final float density;
	/** Max particles Lumen may spawn per tick (before the vanilla Particles setting scales it). */
	public final int budget;

	Preset(String label, float density, int budget) {
		this.label = label;
		this.density = density;
		this.budget = budget;
	}

	/** Which effects are on by default for this preset. */
	public boolean enables(Effect e) {
		if (e == Effect.COZY_BRIGHT) return false; // only on when you turn it on
		return switch (this) {
			case LOW -> switch (e) {
				case HEAT_DISTORTION, STORM_RAIN, UNDERWATER_RAYS, GROUND_MIST, POLLEN, CAVE_DUST, BLOOM, BOAT_WAKE, SUN_RAYS, SUN_SHADOWS, AMBIENT_OCCLUSION -> false;
				default -> true;
			};
			case MEDIUM -> switch (e) {
				case HEAT_DISTORTION, STORM_RAIN, SUN_SHADOWS -> false;
				default -> true;
			};
			case HIGH -> e != Effect.STORM_RAIN;
			case ULTRA, CUSTOM -> true;
		};
	}
}
