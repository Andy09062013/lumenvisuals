package net.lumen.fx;

import net.lumen.config.Effect;
import net.minecraft.block.BlockState;
import net.minecraft.block.LeavesBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

/** Rain splashes on ground & water, drips under trees, denser snowstorms. All fade in/out with the rain gradient. */
public final class WeatherFx {
	public static void tick(MinecraftClient client, ClientWorld world, Env env) {
		if (env.rain < 0.02f) return;
		BlockPos c = client.player.getBlockPos();
		BlockPos.Mutable m = new BlockPos.Mutable();

		boolean snowy = env.snowy > 0.5f;

		// Rain splashes on solid ground and on water
		float splash = Fx.rate(Effect.RAIN_SPLASH) * env.rain * (1 + env.thunder * 0.6f);
		if (splash > 0 && !snowy) {
			int n = Fx.count(40 * splash);
			for (int i = 0; i < n && Fx.hasBudget(); i++) {
				int x = c.getX() + Fx.R.nextInt(25) - 12, z = c.getZ() + Fx.R.nextInt(25) - 12;
				int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
				if (Math.abs(y - c.getY()) > 14) continue;
				m.set(x, y, z);
				if (!world.hasRain(m)) continue;
				FluidState below = world.getFluidState(m.down());
				double sx = x + Fx.R.nextDouble(), sz = z + Fx.R.nextDouble();
				if (below.isIn(FluidTags.WATER)) {
					double sy = y - 1 + below.getHeight(world, m.down()) + 0.02;
					Fx.spawn(ParticleTypes.SPLASH, sx, sy, sz, 0, 0, 0);
					if (Fx.chance(0.6f)) Fx.spawn(ParticleTypes.FISHING, sx, sy, sz, Fx.rand(0.03), 0, Fx.rand(0.03));
				} else {
					Fx.spawn(ParticleTypes.SPLASH, sx, y + 0.02, sz, 0, 0, 0);
				}
			}
		}

		// Trees drip after the drops gather on the leaves
		float drips = Fx.rate(Effect.TREE_DRIPS) * env.rain;
		if (drips > 0 && !snowy) {
			int tries = Fx.count(25 * drips);
			for (int i = 0; i < tries && Fx.hasBudget(); i++) {
				m.set(c.getX() + Fx.R.nextInt(25) - 12, c.getY() + Fx.R.nextInt(17) - 3, c.getZ() + Fx.R.nextInt(25) - 12);
				BlockState s = world.getBlockState(m);
				if (!(s.getBlock() instanceof LeavesBlock) || !world.getBlockState(m.down()).isAir()) continue;
				if (!Fx.chance(0.3f)) continue;
				Fx.spawn(ParticleTypes.DRIPPING_WATER, m.getX() + 0.1 + Fx.R.nextDouble() * 0.8, m.getY() - 0.05, m.getZ() + 0.1 + Fx.R.nextDouble() * 0.8, 0, 0, 0);
			}
		}

		// Snowstorms: extra flakes, blown sideways harder when it storms
		float storm = Fx.rate(Effect.SNOWSTORM) * env.rain;
		if (storm > 0 && snowy) {
			int n = Fx.count(10 * storm * (0.5f + env.thunder + env.gust * 0.5f));
			double sp = 0.05 + env.thunder * 0.2 + env.gust * 0.1;
			double wx = Math.cos(AmbientFx.windAngle()) * sp, wz = Math.sin(AmbientFx.windAngle()) * sp;
			for (int i = 0; i < n; i++) {
				double x = client.player.getX() + Fx.rand(12) - wx * 20, z = client.player.getZ() + Fx.rand(12) - wz * 20;
				double y = client.player.getY() + 2 + Fx.R.nextDouble() * 8;
				m.set((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
				if (!world.isSkyVisible(m)) continue;
				Fx.spawn(ParticleTypes.SNOWFLAKE, x, y, z, wx, -0.08 - Fx.R.nextDouble() * 0.05, wz);
			}
		}
	}

	private WeatherFx() {}
}
