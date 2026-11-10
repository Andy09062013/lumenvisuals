package net.lumen.fx;

import net.lumen.config.Effect;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.CampfireBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

/** Fires and lit campfires now and then pop a burst of sparks, with a crackle sound right when it happens. */
public final class FireFx {
	private static final SoundEvent CRACKLE = SoundEvent.of(Identifier.of("lumen", "fire_crackle"));
	private static int soundCooldown;

	public static void tick(MinecraftClient client, ClientWorld world) {
		if (soundCooldown > 0) soundCooldown--;
		float rate = Fx.rate(Effect.FIRE_SPARKS);
		if (rate <= 0) return;
		BlockPos c = client.player.getBlockPos();
		BlockPos.Mutable m = new BlockPos.Mutable();
		int tries = 60;
		for (int i = 0; i < tries; i++) {
			m.set(c.getX() + Fx.R.nextInt(33) - 16, c.getY() + Fx.R.nextInt(17) - 6, c.getZ() + Fx.R.nextInt(33) - 16);
			BlockState s = world.getBlockState(m);
			boolean fire = s.getBlock() instanceof AbstractFireBlock;
			boolean campfire = s.getBlock() instanceof CampfireBlock && s.contains(CampfireBlock.LIT) && s.get(CampfireBlock.LIT);
			if (!fire && !campfire) continue;
			// each fire block gets a burst roughly every 10-20 seconds (we only look at some blocks each tick)
			if (!Fx.chance(0.04f * rate)) continue;
			burst(client, m.toImmutable(), campfire);
		}
	}

	private static void burst(MinecraftClient client, BlockPos pos, boolean campfire) {
		double x = pos.getX() + 0.5, y = pos.getY() + (campfire ? 0.5 : 0.3), z = pos.getZ() + 0.5;
		int n = 4 + Fx.R.nextInt(6);
		boolean any = false;
		for (int i = 0; i < n; i++) {
			double a = Fx.R.nextDouble() * Math.PI * 2, out = 0.03 + Fx.R.nextDouble() * 0.08;
			// LAVA is vanilla's glowing ember: it flies up, arcs and falls
			any |= Fx.spawn(ParticleTypes.LAVA, x + Fx.rand(0.25), y, z + Fx.rand(0.25), Math.cos(a) * out, 0.1 + Fx.R.nextDouble() * 0.15, Math.sin(a) * out);
			if (Fx.chance(0.5f)) Fx.spawn(ParticleTypes.SMALL_FLAME, x + Fx.rand(0.2), y + 0.2, z + Fx.rand(0.2), Math.cos(a) * out * 0.5, 0.06, Math.sin(a) * out * 0.5);
		}
		// play the crackle only when sparks actually came out, and not over and over
		if (any && soundCooldown == 0 && Fx.near(x, y, z)) {
			soundCooldown = 6;
			client.getSoundManager().play(new PositionedSoundInstance(CRACKLE, SoundCategory.BLOCKS, 0.55f + Fx.R.nextFloat() * 0.25f,
					0.9f + Fx.R.nextFloat() * 0.2f, Random.create(), x, y, z));
		}
	}

	private FireFx() {}
}
