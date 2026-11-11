package net.lumen.fx;

import net.lumen.config.Effect;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;
import net.minecraft.world.biome.BiomeKeys;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Finds the bottoms of waterfalls near the player and gives them mist, spray, foam and drips. */
public final class WaterfallFx {
	private static final class Fall {
		int height;
		int lastSeen;
		// how wide the waterfall is (a wide fall is tracked as one, but mists across its whole width)
		int minX, maxX, minZ, maxZ;

		int columns() { return (maxX - minX + 1) * (maxZ - minZ + 1); }
		double rx() { return minX + Fx.R.nextInt(maxX - minX + 1) + 0.5; }
		double rz() { return minZ + Fx.R.nextInt(maxZ - minZ + 1) + 0.5; }
	}

	private static final Map<BlockPos, Fall> FALLS = new HashMap<>();
	private static final int MAX_FALLS = 40;
	private static int tick;
	private static ClientWorld lastWorld;

	public static void tick(MinecraftClient client, ClientWorld world) {
		if (world != lastWorld) {
			FALLS.clear();
			lastWorld = world;
		}
		tick++;
		float mist = Fx.rate(Effect.WATERFALL_MIST);
		float drips = Fx.rate(Effect.DRIPS);
		BlockPos c = client.player.getBlockPos();

		if (mist > 0 || drips > 0) scan(world, c);
		else FALLS.clear();

		// expire old ones / re-check now and then
		Iterator<Map.Entry<BlockPos, Fall>> it = FALLS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<BlockPos, Fall> e = it.next();
			if (tick - e.getValue().lastSeen > 100 || e.getKey().getSquaredDistance(c) > 48 * 48) { it.remove(); continue; }
			if ((tick + e.getKey().hashCode()) % 40 == 0 && !isFalling(world.getFluidState(e.getKey()))) { it.remove(); continue; }
			if ((tick + e.getKey().hashCode()) % 40 == 0) e.getValue().lastSeen = tick;
		}

		// Spread a fixed per-tick allowance over waterfalls, closest first, so big falls can't flood the screen
		java.util.List<Map.Entry<BlockPos, Fall>> sorted = new java.util.ArrayList<>(FALLS.entrySet());
		sorted.sort(java.util.Comparator.comparingDouble(en -> en.getKey().getSquaredDistance(c)));
		int active = 0;
		for (Map.Entry<BlockPos, Fall> e : sorted) {
			if (++active > 30) break; // cap so a valley full of waterfalls stays smooth
			BlockPos base = e.getKey();
			double dist = Math.sqrt(base.getSquaredDistance(c));
			float lod = (float) Math.max(0.15, 1.0 - dist / 48.0);
			float h = Math.min(1.2f, 0.3f + e.getValue().height / 14f);
			// little spills (a lone source block dripping down a step or two) don't count as waterfalls
			boolean realFall = e.getValue().height >= 4 || e.getValue().columns() >= 3;
			if (mist > 0 && realFall) {
				Fall f = e.getValue();
				float wide = (float) Math.min(3.0, Math.sqrt(f.columns()));
				emitBase(world, base, f, h * lod * mist * wide);
				emitFoam(world, base, f, lod * mist * wide);
			}
			if (drips > 0) emitDrips(world, base, e.getValue().height, lod * drips);
		}

		if (drips > 0) caveDrips(world, c, drips);
	}

	private static boolean isFalling(FluidState fs) {
		return fs.isIn(FluidTags.WATER) && !fs.isStill() && fs.contains(FlowableFluid.FALLING) && fs.get(FlowableFluid.FALLING);
	}

	private static void scan(ClientWorld world, BlockPos c) {
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int i = 0; i < 24; i++) {
			m.set(c.getX() + Fx.R.nextInt(65) - 32, c.getY() + Fx.R.nextInt(41) - 20, c.getZ() + Fx.R.nextInt(65) - 32);
			if (!isFalling(world.getFluidState(m))) continue;
			// walk down to the bottom of this falling column
			int guard = 0;
			while (guard++ < 64 && isFalling(world.getFluidState(m.down()))) m.move(0, -1, 0);
			BlockPos base = m.toImmutable();
			Fall f = FALLS.get(base);
			if (f == null) {
				// wide waterfalls: if another bottom of the same fall is already tracked nearby, use that one
				Fall near = nearbyFall(base);
				if (near != null) {
					near.lastSeen = tick;
					near.minX = Math.min(near.minX, base.getX()); near.maxX = Math.max(near.maxX, base.getX());
					near.minZ = Math.min(near.minZ, base.getZ()); near.maxZ = Math.max(near.maxZ, base.getZ());
					continue;
				}
				if (FALLS.size() >= MAX_FALLS) continue;
				f = new Fall();
				int h = 0;
				BlockPos.Mutable up = base.mutableCopy();
				while (h < 24 && isFalling(world.getFluidState(up.move(0, 1, 0)))) h++;
				f.height = h + 1;
				f.minX = f.maxX = base.getX();
				f.minZ = f.maxZ = base.getZ();
				FALLS.put(base, f);
			}
			f.lastSeen = tick;
		}
	}

	/** A tracked waterfall bottom within a few blocks (same waterfall, just wide), or null. */
	private static Fall nearbyFall(BlockPos p) {
		for (Map.Entry<BlockPos, Fall> e : FALLS.entrySet()) {
			Fall f = e.getValue();
			BlockPos o = e.getKey();
			if (p.getX() >= f.minX - 2 && p.getX() <= f.maxX + 2 && p.getZ() >= f.minZ - 2 && p.getZ() <= f.maxZ + 2
					&& Math.abs(o.getY() - p.getY()) <= 2) return f;
		}
		return null;
	}

	private static void emitBase(ClientWorld world, BlockPos base, Fall fall, float amount) {
		double y = base.getY() + 0.1; // where the falling water meets the pool / ground
		// rising mist: big soft puffs in the local water colour, mostly white, across the whole width
		int mists = Fx.count(5f * amount);
		for (int i = 0; i < mists; i++) {
			Fx.spawn(Fx.waterDroplet(world, base, 0.85f + Fx.R.nextFloat() * 0.1f, 2.4f + Fx.R.nextFloat() * 1.6f),
					fall.rx() + Fx.rand(1.4), y + 0.2 + Fx.R.nextDouble() * 2.2, fall.rz() + Fx.rand(1.4), 0, 0, 0);
		}
		double x = fall.rx(), z = fall.rz();
		// spray hopping out of the impact
		int spray = Fx.count(4.0f * amount);
		for (int i = 0; i < spray; i++) {
			double a = Fx.R.nextDouble() * Math.PI * 2, sp = 0.06 + Fx.R.nextDouble() * 0.12;
			Fx.spawn(ParticleTypes.SPLASH, x + Math.cos(a) * 0.5, y + 0.1, z + Math.sin(a) * 0.5, Math.cos(a) * sp, 0, Math.sin(a) * sp);
		}
		// white foam spreading over the surface
		int foam = Fx.count(3.5f * amount);
		for (int i = 0; i < foam; i++) {
			double a = Fx.R.nextDouble() * Math.PI * 2;
			Fx.spawn(ParticleTypes.FISHING, x + Math.cos(a) * 0.7, y - 0.05, z + Math.sin(a) * 0.7, Math.cos(a) * 0.05, 0, Math.sin(a) * 0.05);
		}
		// small droplets
		for (int k = 0; k < 2 && Fx.chance(Math.min(1f, amount)); k++)
			Fx.spawn(Fx.waterDroplet(world, base, 0.4f, 0.5f), x + Fx.rand(0.8), y + 0.4 + Fx.R.nextDouble(), z + Fx.rand(0.8), 0, 0, 0);
	}

	/** Textured foam puffs and splash crowns where the water lands; taller falls churn harder. */
	private static void emitFoam(ClientWorld world, BlockPos base, Fall fall, float amount) {
		int height = fall.height;
		double x = fall.rx(), z = fall.rz(), y = base.getY() + 0.05;
		float size = Math.min(1.4f, 0.5f + height * 0.06f);
		int puffs = Fx.count(3f * amount * (0.6f + size));
		for (int i = 0; i < puffs; i++) {
			x = fall.rx();
			z = fall.rz();
			Fx.custom(LumenParticle.Kind.FOAM, x + Fx.rand(0.7), y + size * 0.6 + Fx.R.nextDouble() * 0.3, z + Fx.rand(0.7),
					Fx.rand(0.03), 0.025 + Fx.R.nextDouble() * 0.03, Fx.rand(0.03), size * (0.8f + Fx.R.nextFloat() * 0.6f), 35 + Fx.R.nextInt(25));
		}
		if (Fx.chance(0.35f * amount)) {
			float crown = size * 0.9f;
			Fx.custom(LumenParticle.Kind.SPLASH, x + Fx.rand(0.5), y + crown * 0.85, z + Fx.rand(0.5), 0, 0, 0, crown, 12);
		}
		if (Fx.chance(0.25f * amount)) {
			Fx.custom(LumenParticle.Kind.RIPPLE, x + Fx.rand(0.4), y + 0.02, z + Fx.rand(0.4), 0, 0, 0, size * 1.2f, 22);
		}
		if (Fx.chance(0.5f * amount)) {
			double a = Fx.R.nextDouble() * Math.PI * 2;
			Fx.custom(LumenParticle.Kind.DROPLET, x, y + 0.3, z, Math.cos(a) * 0.12, 0.2 + Fx.R.nextDouble() * 0.15, Math.sin(a) * 0.12, 0.07f, 25);
		}
	}

	/** Drips from the undersides of solid blocks next to a waterfall. */
	private static void emitDrips(ClientWorld world, BlockPos base, int height, float amount) {
		if (!Fx.chance(0.35f * amount)) return;
		BlockPos.Mutable m = new BlockPos.Mutable(base.getX() + Fx.R.nextInt(5) - 2, base.getY() + 1 + Fx.R.nextInt(Math.max(1, height)), base.getZ() + Fx.R.nextInt(5) - 2);
		BlockState s = world.getBlockState(m);
		if (!s.isSolidBlock(world, m) || !world.getBlockState(m.down()).isAir()) return;
		Fx.spawn(ParticleTypes.DRIPPING_WATER, m.getX() + 0.15 + Fx.R.nextDouble() * 0.7, m.getY() - 0.05, m.getZ() + 0.15 + Fx.R.nextDouble() * 0.7, 0, 0, 0);
	}

	/** Wet cave ceilings: water above, or lush/dripstone caves. */
	private static void caveDrips(ClientWorld world, BlockPos c, float amount) {
		int tries = Fx.count(6 * amount);
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int i = 0; i < tries; i++) {
			m.set(c.getX() + Fx.R.nextInt(25) - 12, c.getY() + Fx.R.nextInt(13) - 2, c.getZ() + Fx.R.nextInt(25) - 12);
			BlockState s = world.getBlockState(m);
			if (!s.isSolidBlock(world, m) || !world.getBlockState(m.down()).isAir()) continue;
			if (world.getLightLevel(LightType.SKY, m.down()) > 0) continue;
			boolean wet = false;
			for (int up = 1; up <= 3 && !wet; up++) wet = world.getFluidState(m.up(up)).isIn(FluidTags.WATER);
			if (!wet) {
				var biome = world.getBiome(m);
				wet = (biome.matchesKey(BiomeKeys.LUSH_CAVES) || biome.matchesKey(BiomeKeys.DRIPSTONE_CAVES)) && Fx.chance(0.35f);
			}
			if (!wet) continue;
			Fx.spawn(ParticleTypes.DRIPPING_WATER, m.getX() + 0.15 + Fx.R.nextDouble() * 0.7, m.getY() - 0.05, m.getZ() + 0.15 + Fx.R.nextDouble() * 0.7, 0, 0, 0);
		}
	}

	private WaterfallFx() {}
}
