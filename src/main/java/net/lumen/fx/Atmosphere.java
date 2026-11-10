package net.lumen.fx;

import net.lumen.config.Effect;
import net.lumen.config.LumenConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;

/**
 * Feeds live values (time of day, weather, camera angle, biome, settings) to the screen shader
 * through a tiny 9x1 texture that the post effect samples. Updated every frame.
 */
public final class Atmosphere {
	public static final Identifier TEXTURE = Identifier.of("lumen", "textures/effect/data.png");
	public static final Identifier TEXTURE_SHORT = Identifier.of("lumen", "data");
	private static final int W = 18;

	private static NativeImageBackedTexture texture;
	private static NativeImage image;

	// smoothed values
	private static float morning, heat, mist, sky, cave;
	private static final float[] strengths = new float[Effect.values().length];

	public static void init(MinecraftClient client) {
		image = new NativeImage(W, 1, false);
		for (int i = 0; i < W; i++) image.setColorArgb(i, 0, 0xFF000000);
		texture = new NativeImageBackedTexture(() -> "lumen_atmosphere", image);
		client.getTextureManager().registerTexture(TEXTURE, texture);
		client.getTextureManager().registerTexture(TEXTURE_SHORT, texture);
	}

	public static void update(MinecraftClient client, Env env) {
		if (texture == null) return;
		ClientWorld world = client.world;
		if (world == null || client.player == null) return;
		Camera cam = client.gameRenderer.getCamera();
		BlockPos pos = cam.getBlockPos();

		morning = lerp(morning, env.morning ? 1f : 0f, 0.02f);
		RegistryEntry<Biome> b = world.getBiome(pos);
		float temp = b.value().getTemperature();
		heat = lerp(heat, (temp >= 1.5f && !b.value().hasPrecipitation()) ? 1f : 0f, 0.02f);
		mist = lerp(mist, mistFactor(b), 0.02f);
		sky = lerp(sky, world.getLightLevel(LightType.SKY, pos) / 15f, 0.05f);
		cave = lerp(cave, world.getLightLevel(LightType.SKY, pos) == 0 ? 1f : 0f, 0.05f);
		for (Effect e : Effect.values()) strengths[e.ordinal()] = lerp(strengths[e.ordinal()], LumenConfig.strength(e), 0.1f);

		boolean overworld = world.getRegistryKey() == World.OVERWORLD;
		float pitch = cam.getPitch();
		float yaw = cam.getYaw() % 360f;
		if (yaw < 0) yaw += 360f;
		float fov = client.options.getFov().getValue();
		try {
			// the real drawing FOV: sprinting/speed/spyglass change it, and a wrong FOV puts shadows in the wrong place
			fov = ((net.lumen.mixin.GameRendererAccessor) client.gameRenderer).lumen$getFov(cam, client.getRenderTickCounter().getTickProgress(true), true);
		} catch (Throwable ignored) {}
		float far = client.options.getClampedViewDistance() * 16f * 4f;

		// pixel 0: sun height, morning, rain, thunder
		put(0, env.sunElevation * 0.5f + 0.5f, morning, env.rain, env.thunder);
		// pixel 1: pitch (16 bit), yaw (16 bit)
		put16(1, (pitch + 90f) / 180f, yaw / 360f);
		// pixel 2: camera height (16 bit), fov, far plane
		float camY = (float) (cam.getBlockPos().getY() + 0.5);
		int y16 = clamp16((camY + 128f) / 512f);
		set(2, y16 >> 8, y16 & 255, b8(fov / 180f), b8(far / 4096f));
		// pixel 3: underwater, heat, mist biome, night
		put(3, env.underwater ? 1f : 0f, heat, mist, env.night);
		// pixels 4-6: effect strengths (stored as strength / 2)
		put(4, s(Effect.SUNSET), s(Effect.HAZE), s(Effect.GROUND_MIST), s(Effect.HEAT_DISTORTION));
		put(5, s(Effect.UNDERWATER_RAYS), s(Effect.UNDERWATER_DEPTH), s(Effect.STORM_FOG), s(Effect.STORM_RAIN));
		put(6, s(Effect.COLOR_GRADE), s(Effect.BLOOM), s(Effect.VIGNETTE), overworld ? 1f : 0f);
		// pixel 7: sky light at camera, snowy, cave, daylight
		put(7, sky, env.snowy, cave, env.day * (1f - env.rain * 0.4f));
		// pixel 8: bright sun & light shafts strength
		put(8, s(Effect.SUN_RAYS), s(Effect.SUN_SHADOWS), s(Effect.AMBIENT_OCCLUSION), net.lumen.ShadowMap.ready ? 1f : 0f);
		// pixels 9-10: shadow map alignment (16 bit each)
		put16(9, net.lumen.ShadowMap.offsetU, net.lumen.ShadowMap.offsetV);
		put16(10, net.lumen.ShadowMap.lightX * 0.5f + 0.5f, net.lumen.ShadowMap.lightY * 0.5f + 0.5f);

		// pixel 11: Distant Horizons installed
		// pixel 11: Distant Horizons installed, exact fov (16 bit)
		int f16 = clamp16(fov / 180f);
		set(11, net.lumen.Compat.DISTANT_HORIZONS ? 255 : 0, f16 >> 8, f16 & 255, 0);
		// pixels 12-17: exact camera (eye offset, center ray, right and up steps), 16 bit each
		net.lumen.ViewCapture.update(client);
		var e = net.lumen.ViewCapture.EYE; var c = net.lumen.ViewCapture.CENTER;
		var r = net.lumen.ViewCapture.RIGHT; var u = net.lumen.ViewCapture.UP;
		put16(12, sn(e.x, 2f), sn(e.y, 2f));
		put16(13, sn(e.z, 2f), sn(c.x, 8f));
		put16(14, sn(c.y, 8f), sn(c.z, 8f));
		put16(15, sn(r.x, 8f), sn(r.y, 8f));
		put16(16, sn(r.z, 8f), sn(u.x, 8f));
		put16(17, sn(u.y, 8f), sn(u.z, 8f));
		texture.upload();
	}

	/** True if anything on the screen pass is switched on. */
	public static boolean anyScreenEffect() {
		for (Effect e : Effect.values()) if (e.kind == Effect.Kind.SCREEN && LumenConfig.on(e)) return true;
		return false;
	}

	private static float mistFactor(RegistryEntry<Biome> b) {
		if (b.matchesKey(BiomeKeys.SWAMP) || b.matchesKey(BiomeKeys.MANGROVE_SWAMP)) return 1f;
		if (b.matchesKey(BiomeKeys.DARK_FOREST) || b.matchesKey(BiomeKeys.PALE_GARDEN)) return 0.85f;
		if (b.isIn(BiomeTags.IS_FOREST) || b.isIn(BiomeTags.IS_TAIGA) || b.isIn(BiomeTags.IS_JUNGLE)) return 0.6f;
		if (b.isIn(BiomeTags.IS_RIVER)) return 0.55f;
		return 0.2f;
	}

	private static float s(Effect e) { return strengths[e.ordinal()] / 2f; }

	private static float lerp(float a, float b, float k) { return a + (b - a) * k; }

	private static int b8(float v) { return Math.max(0, Math.min(255, Math.round(v * 255f))); }

	/** signed value in [-range, range] -> 0..1 */
	private static float sn(float v, float range) { return Math.max(0f, Math.min(1f, v / range * 0.5f + 0.5f)); }

	private static int clamp16(float v) { return Math.max(0, Math.min(65535, Math.round(v * 65535f))); }

	private static void put(int x, float r, float g, float b, float a) { set(x, b8(r), b8(g), b8(b), b8(a)); }

	private static void put16(int x, float v1, float v2) {
		int a = clamp16(v1), c = clamp16(v2);
		set(x, a >> 8, a & 255, c >> 8, c & 255);
	}

	private static void set(int x, int r, int g, int b, int a) {
		image.setColorArgb(x, 0, (a << 24) | (r << 16) | (g << 8) | b);
	}

	private Atmosphere() {}
}
