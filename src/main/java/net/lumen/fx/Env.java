package net.lumen.fx;

import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

/** Smoothed environment values shared by the particle systems and the screen shader. */
public final class Env {
	public float day, night, rain, thunder, snowy, gust;
	public boolean underwater;
	/** -1..1, sun height (1 = noon, 0 = horizon). */
	public float sunElevation;
	public boolean morning;

	private float gustTimer;

	public void update(MinecraftClient client, ClientWorld world) {
		long t = Math.floorMod(world.getTimeOfDay(), 24000L);
		double d = (t / 24000.0) - 0.25;
		d -= Math.floor(d);
		double e = 0.5 - Math.cos(d * Math.PI) / 2.0;
		double skyAngle = (d * 2.0 + e) / 3.0;
		sunElevation = (float) Math.cos(skyAngle * Math.PI * 2.0);
		morning = t >= 18000 || t < 6000;

		boolean overworld = world.getRegistryKey() == World.OVERWORLD;
		float dayTarget = overworld ? smooth(-0.15f, 0.25f, sunElevation) : 0f;
		day = lerp(day, dayTarget, 0.05f);
		night = lerp(night, overworld ? 1f - smooth(-0.25f, 0.05f, sunElevation) : 0f, 0.05f);
		rain = world.getRainGradient(1f);
		thunder = world.getThunderGradient(1f);

		BlockPos pos = client.player.getBlockPos();
		RegistryEntry<Biome> b = world.getBiome(pos);
		float temp = b.value().getTemperature();
		snowy = lerp(snowy, temp < 0.15f ? 1f : 0f, 0.05f);

		// wind gusts: slow random swells
		gustTimer -= 1f;
		if (gustTimer <= 0) gustTimer = 60 + Fx.R.nextInt(140);
		float target = gustTimer < 40 ? (float) Math.sin(gustTimer / 40.0 * Math.PI) : 0f;
		gust = lerp(gust, target * (0.5f + thunder * 0.5f + rain * 0.3f), 0.1f);

		underwater = client.gameRenderer.getCamera().getSubmersionType() == CameraSubmersionType.WATER;
	}

	static float smooth(float a, float b, float x) {
		float t = Math.max(0f, Math.min(1f, (x - a) / (b - a)));
		return t * t * (3 - 2 * t);
	}

	static float lerp(float a, float b, float k) {
		return a + (b - a) * k;
	}
}
