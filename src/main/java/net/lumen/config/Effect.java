package net.lumen.config;

/** Every effect Lumen has. Each one can be turned on/off and scaled in /lumen. */
public enum Effect {
	// Comfort
	COZY_BRIGHT("Cozy fullbright (warm light everywhere)", Kind.OTHER),
	// Water
	WATER_SPLASH("Water splashes", Kind.PARTICLES),
	WATER_RIPPLES("Water ripples", Kind.PARTICLES),
	SWIM_FOAM("Swimming foam & droplets", Kind.PARTICLES),
	BOAT_WAKE("Boat wakes", Kind.PARTICLES),
	WATERFALL_MIST("Waterfall mist, spray & foam", Kind.PARTICLES),
	DRIPS("Drips (waterfalls & wet caves)", Kind.PARTICLES),
	// Sky
	SUNSET("Sunrise & sunset colors", Kind.SCREEN),
	SUN_RAYS("Bright sun & light shafts", Kind.SCREEN),
	HAZE("Dawn & dusk haze", Kind.SCREEN),
	// Ambience
	LEAVES("Falling leaves", Kind.PARTICLES),
	POLLEN("Pollen", Kind.PARTICLES),
	FIREFLIES("Fireflies", Kind.PARTICLES),
	GROUND_MIST("Ground mist", Kind.SCREEN),
	DESERT_DUST("Desert dust", Kind.PARTICLES),
	CAVE_DUST("Cave dust", Kind.PARTICLES),
	HEAT_DISTORTION("Heat distortion (optional)", Kind.SCREEN),
	SNOW_GUSTS("Snow gusts", Kind.PARTICLES),
	FIRE_SPARKS("Fire sparks & crackle", Kind.PARTICLES),
	// Weather
	RAIN_SPLASH("Rain splashes", Kind.PARTICLES),
	TREE_DRIPS("Tree drips in rain", Kind.PARTICLES),
	STORM_FOG("Storm fog", Kind.SCREEN),
	STORM_RAIN("Wind-driven storm rain", Kind.SCREEN),
	SNOWSTORM("Denser snowstorms", Kind.PARTICLES),
	// Underwater
	UNDERWATER_PARTICLES("Underwater particles", Kind.PARTICLES),
	UNDERWATER_RAYS("Underwater light rays", Kind.SCREEN),
	UNDERWATER_DEPTH("Underwater depth color", Kind.SCREEN),
	BUBBLES("Bubbles around swimmers", Kind.PARTICLES),
	// Lighting & look
	SUN_SHADOWS("Sun lighting & shadows (shader style)", Kind.SCREEN),
	AMBIENT_OCCLUSION("Ambient occlusion (shader style)", Kind.SCREEN),
	HELD_LIGHT("Held torch/lantern light", Kind.OTHER),
	COLOR_GRADE("Warm color grading", Kind.SCREEN),
	BLOOM("Bloom", Kind.SCREEN),
	VIGNETTE("Vignette", Kind.SCREEN);

	public enum Kind { PARTICLES, SCREEN, OTHER }

	public final String label;
	public final Kind kind;

	Effect(String label, Kind kind) {
		this.label = label;
		this.kind = kind;
	}
}
