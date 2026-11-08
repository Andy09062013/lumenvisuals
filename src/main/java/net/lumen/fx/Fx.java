package net.lumen.fx;

import net.lumen.config.Effect;
import net.lumen.config.LumenConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.util.math.BlockPos;

import java.util.Random;

/**
 * Shared particle helper: a per-tick spawn budget, distance culling and density scaling.
 * Only uses vanilla particle types so it works on any server and keeps the blocky look.
 */
public final class Fx {
	public static final Random R = new Random();

	private static int budget;
	private static double px, py, pz;
	private static double cullSq = 48 * 48;
	private static float particlesSetting = 1f;
	private static boolean customBroken;

	/** Called once at the start of each client tick. */
	public static void beginTick(MinecraftClient client) {
		var mode = client.options.getParticles().getValue();
		int o = ((Enum<?>) mode).ordinal(); // ALL, DECREASED, MINIMAL
		particlesSetting = o == 0 ? 1f : o == 1 ? 0.5f : 0.15f;
		budget = Math.max(4, Math.round(LumenConfig.preset.budget * particlesSetting));
		if (client.player != null) {
			px = client.player.getX();
			py = client.player.getEyeY();
			pz = client.player.getZ();
		}
		int view = client.options.getClampedViewDistance();
		double cull = Math.min(64, Math.max(24, view * 4));
		cullSq = cull * cull;
	}

	/** Density for an effect: 0 when off. Combines preset, effect slider and vanilla Particles setting. */
	public static float rate(Effect e) {
		return LumenConfig.strength(e) * LumenConfig.preset.density * particlesSetting;
	}

	/** Rolls a number of spawns for a base amount: e.g. count(2.4) gives 2 or 3. */
	public static int count(float amount) {
		if (amount <= 0) return 0;
		int n = (int) amount;
		if (R.nextFloat() < amount - n) n++;
		return n;
	}

	public static boolean chance(float p) {
		return p > 0 && R.nextFloat() < p;
	}

	public static boolean hasBudget() {
		return budget > 0;
	}

	public static boolean near(double x, double y, double z) {
		double dx = x - px, dy = y - py, dz = z - pz;
		return dx * dx + dy * dy + dz * dz <= cullSq;
	}

	public static boolean spawn(ParticleEffect effect, double x, double y, double z, double vx, double vy, double vz) {
		if (budget <= 0 || !near(x, y, z)) return false;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.particleManager == null) return false;
		budget--;
		client.particleManager.addParticle(effect, x, y, z, vx, vy, vz);
		return true;
	}

	/** Spawns one of Lumen's own water particles (budgeted and culled like the rest). */
	public static boolean custom(LumenParticle.Kind kind, double x, double y, double z, double vx, double vy, double vz, float size, int life) {
		if (budget <= 0 || !near(x, y, z)) return false;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.particleManager == null || client.world == null) return false;
		if (customBroken) return false;
		budget--;
		try {
			client.particleManager.addParticle(new LumenParticle(client.world, x, y, z, vx, vy, vz, kind, size, life));
		} catch (Throwable t) {
			// never crash over a particle: switch the textured ones off and keep the vanilla-based effects
			customBroken = true;
			System.err.println("[Lumen] textured water particles disabled: " + t);
			return false;
		}
		return true;
	}

	/** Splash = crown + flat ripple + droplets + a little foam. Strength ~0.3 (pebble) to ~4 (big fall). */
	public static void splash(ClientWorld world, double x, double surfaceY, double z, double strength, float rate) {
		double s = Math.max(0.3, Math.min(4.0, strength));
		float crown = (float) (0.3 + 0.32 * s);
		custom(LumenParticle.Kind.SPLASH, x, surfaceY + crown * 0.85, z, 0, 0, 0, crown, 12 + (int) (s * 3));
		custom(LumenParticle.Kind.RIPPLE, x, surfaceY + 0.03, z, 0, 0, 0, (float) (0.35 + 0.3 * s), 18 + (int) (s * 4));
		if (s > 1.6) {
			// big hits: extra crowns around the impact
			for (int i = 0; i < 3; i++) {
				double a = R.nextDouble() * Math.PI * 2, d = 0.3 + s * 0.12;
				float cs = crown * 0.65f;
				custom(LumenParticle.Kind.SPLASH, x + Math.cos(a) * d, surfaceY + cs * 0.85, z + Math.sin(a) * d, 0, 0, 0, cs, 10 + (int) s * 2);
			}
		}
		int drops = count((float) (5 + 9 * s) * rate);
		for (int i = 0; i < drops; i++) {
			double a = R.nextDouble() * Math.PI * 2, out = 0.06 + R.nextDouble() * 0.1 * Math.sqrt(s);
			custom(LumenParticle.Kind.DROPLET, x + Math.cos(a) * 0.2, surfaceY + 0.15, z + Math.sin(a) * 0.2,
					Math.cos(a) * out, 0.18 + R.nextDouble() * 0.12 * Math.sqrt(s), Math.sin(a) * out, 0.06f + R.nextFloat() * 0.05f, 25);
		}
		int foam = count((float) (1 + 2 * s) * rate);
		for (int i = 0; i < foam; i++) {
			custom(LumenParticle.Kind.FOAM, x + rand(0.4 * s), surfaceY + 0.2, z + rand(0.4 * s), rand(0.02), 0.01, rand(0.02), (float) (0.2 + 0.1 * s), 20 + R.nextInt(15));
		}
	}

	/** Small pixel droplet tinted to the local water color (lighter so it reads as spray). */
	public static DustParticleEffect waterDroplet(ClientWorld world, BlockPos pos, float whiten, float scale) {
		int c = BiomeColors.getWaterColor(world, pos);
		int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
		r = (int) (r + (255 - r) * whiten);
		g = (int) (g + (255 - g) * whiten);
		b = (int) (b + (255 - b) * whiten);
		return new DustParticleEffect((r << 16) | (g << 8) | b, scale);
	}

	public static double rand(double spread) {
		return (R.nextDouble() * 2 - 1) * spread;
	}

	private Fx() {}
}
