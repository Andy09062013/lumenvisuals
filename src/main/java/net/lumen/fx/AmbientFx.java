package net.lumen.fx;

import net.lumen.config.Effect;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;

/** Leaves, pollen, fireflies, desert/cave dust, snow gusts and underwater particles. */
public final class AmbientFx {
	private static final Random MC_RANDOM = Random.create();
	private static float windAngle = 0f;

	public static void tick(MinecraftClient client, ClientWorld world, Env env) {
		BlockPos c = client.player.getBlockPos();
		BlockPos.Mutable m = new BlockPos.Mutable();
		windAngle += (Fx.R.nextFloat() - 0.5f) * 0.02f;

		// Falling leaves: let vanilla leaves spawn their own (correctly colored) leaf particles more often
		float leaves = Fx.rate(Effect.LEAVES);
		int tries = Fx.count(30 * leaves);
		for (int i = 0; i < tries && Fx.hasBudget(); i++) {
			m.set(c.getX() + Fx.R.nextInt(33) - 16, c.getY() + Fx.R.nextInt(19) - 4, c.getZ() + Fx.R.nextInt(33) - 16);
			BlockState s = world.getBlockState(m);
			if (s.getBlock() instanceof LeavesBlock && world.getBlockState(m.down()).isAir() && Fx.near(m.getX(), m.getY(), m.getZ())) {
				s.getBlock().randomDisplayTick(s, world, m.toImmutable(), MC_RANDOM);
			}
		}

		RegistryEntry<Biome> biome = world.getBiome(c);
		boolean outside = world.getLightLevel(LightType.SKY, c) >= 10;
		boolean overworld = world.getRegistryKey() == World.OVERWORLD;

		// Pollen: flowery / grassy biomes in daytime
		float pollen = Fx.rate(Effect.POLLEN) * env.day * (1 - env.rain);
		if (pollen > 0 && outside && isPollenBiome(biome)) {
			int n = Fx.count(0.8f * pollen);
			for (int i = 0; i < n; i++) {
				int color = Fx.R.nextBoolean() ? 0xFFF1A8 : 0xF4FFD0;
				Fx.spawn(new DustParticleEffect(color, 0.35f + Fx.R.nextFloat() * 0.2f),
						client.player.getX() + Fx.rand(10), client.player.getY() + Fx.R.nextDouble() * 4 - 0.5, client.player.getZ() + Fx.rand(10), 0, 0, 0);
			}
		}

		// Fireflies: night, temperate grassy places & swamps
		float flies = Fx.rate(Effect.FIREFLIES) * env.night * (1 - env.rain);
		if (flies > 0 && overworld && isFireflyBiome(biome)) {
			int n = Fx.count(5 * flies);
			for (int i = 0; i < n; i++) {
				int x = c.getX() + Fx.R.nextInt(25) - 12, z = c.getZ() + Fx.R.nextInt(25) - 12;
				int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
				if (Math.abs(y - c.getY()) > 10) continue;
				m.set(x, y - 1, z);
				BlockState ground = world.getBlockState(m), above = world.getBlockState(m.up());
				boolean grassy = ground.isOf(Blocks.GRASS_BLOCK) || ground.isOf(Blocks.MOSS_BLOCK) || above.isIn(BlockTags.FLOWERS)
						|| above.isOf(Blocks.SHORT_GRASS) || above.isOf(Blocks.TALL_GRASS) || above.isOf(Blocks.FERN)
						|| world.getFluidState(m).isIn(FluidTags.WATER);
				if (!grassy || !Fx.chance(0.12f)) continue;
				Fx.spawn(ParticleTypes.FIREFLY, x + Fx.R.nextDouble(), y + 0.4 + Fx.R.nextDouble() * 1.8, z + Fx.R.nextDouble(), 0, 0, 0);
			}
		}

		// Desert dust: sand drifting in hot dry biomes
		float desert = Fx.rate(Effect.DESERT_DUST) * (1 - env.rain);
		if (desert > 0 && outside && isDesert(biome)) {
			boolean red = biome.isIn(BiomeTags.IS_BADLANDS);
			BlockState sand = red ? Blocks.RED_SAND.getDefaultState() : Blocks.SAND.getDefaultState();
			int n = Fx.count(2.5f * desert * (0.6f + env.gust));
			double wx = Math.cos(windAngle) * 0.08, wz = Math.sin(windAngle) * 0.08;
			for (int i = 0; i < n; i++) {
				double x = client.player.getX() + Fx.rand(14), z = client.player.getZ() + Fx.rand(14);
				double y = client.player.getY() + Fx.R.nextDouble() * 5 - 1;
				if (Fx.R.nextBoolean()) Fx.spawn(new BlockStateParticleEffect(ParticleTypes.FALLING_DUST, sand), x, y + 2, z, 0, 0, 0);
				else Fx.spawn(new DustParticleEffect(red ? 0xC8794A : 0xE3D3A2, 0.5f + Fx.R.nextFloat() * 0.4f), x, y, z, wx, 0, wz);
			}
		}

		// Cave dust: dark, no sky light, below the surface
		float cave = Fx.rate(Effect.CAVE_DUST);
		if (cave > 0 && world.getLightLevel(LightType.SKY, c) == 0 && (c.getY() < 60 || !overworld)) {
			int n = Fx.count(1.5f * cave);
			for (int i = 0; i < n; i++) {
				m.set(c.getX() + Fx.R.nextInt(17) - 8, c.getY() + Fx.R.nextInt(9) - 3, c.getZ() + Fx.R.nextInt(17) - 8);
				if (!world.getBlockState(m).isAir()) continue;
				Fx.spawn(new DustParticleEffect(0x8F8A82, 0.3f + Fx.R.nextFloat() * 0.25f), m.getX() + Fx.R.nextDouble(), m.getY() + Fx.R.nextDouble(), m.getZ() + Fx.R.nextDouble(), 0, 0, 0);
			}
		}

		// Snow gusts: cold biomes, bursts of blown snow
		float gusts = Fx.rate(Effect.SNOW_GUSTS);
		if (gusts > 0 && outside && env.snowy > 0.5f) {
			int n = Fx.count(6 * gusts * env.gust * env.gust);
			double sp = 0.15 + env.gust * 0.25;
			double wx = Math.cos(windAngle) * sp, wz = Math.sin(windAngle) * sp;
			for (int i = 0; i < n; i++) {
				double x = client.player.getX() - wx * 30 + Fx.rand(12), z = client.player.getZ() - wz * 30 + Fx.rand(12);
				double y = client.player.getY() + Fx.R.nextDouble() * 3;
				Fx.spawn(ParticleTypes.SNOWFLAKE, x, y, z, wx + Fx.rand(0.03), 0.02 + Fx.rand(0.02), wz + Fx.rand(0.03));
			}
		}

		// Underwater: drifting specks
		float uw = Fx.rate(Effect.UNDERWATER_PARTICLES);
		if (uw > 0 && env.underwater) {
			int n = Fx.count(5 * uw);
			for (int i = 0; i < n; i++) {
				m.set(c.getX() + Fx.R.nextInt(15) - 7, c.getY() + Fx.R.nextInt(9) - 3, c.getZ() + Fx.R.nextInt(15) - 7);
				if (!world.getFluidState(m).isIn(FluidTags.WATER)) continue;
				Fx.spawn(ParticleTypes.UNDERWATER, m.getX() + Fx.R.nextDouble(), m.getY() + Fx.R.nextDouble(), m.getZ() + Fx.R.nextDouble(), 0, 0, 0);
			}
		}
	}

	public static float windAngle() { return windAngle; }

	static boolean isPollenBiome(RegistryEntry<Biome> b) {
		return b.matchesKey(BiomeKeys.PLAINS) || b.matchesKey(BiomeKeys.SUNFLOWER_PLAINS) || b.matchesKey(BiomeKeys.FLOWER_FOREST)
				|| b.matchesKey(BiomeKeys.MEADOW) || b.matchesKey(BiomeKeys.CHERRY_GROVE) || b.matchesKey(BiomeKeys.FOREST)
				|| b.matchesKey(BiomeKeys.BIRCH_FOREST) || b.matchesKey(BiomeKeys.OLD_GROWTH_BIRCH_FOREST) || b.matchesKey(BiomeKeys.SAVANNA);
	}

	static boolean isFireflyBiome(RegistryEntry<Biome> b) {
		float t = b.value().getTemperature();
		return b.matchesKey(BiomeKeys.SWAMP) || b.matchesKey(BiomeKeys.MANGROVE_SWAMP) || (t >= 0.5f && t <= 1.0f);
	}

	static boolean isDesert(RegistryEntry<Biome> b) {
		return b.matchesKey(BiomeKeys.DESERT) || b.isIn(BiomeTags.IS_BADLANDS);
	}

	private AmbientFx() {}
}
