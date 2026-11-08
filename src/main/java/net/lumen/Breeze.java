package net.lumen;

import net.minecraft.block.LeavesBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

/**
 * Random breezes every 1-2 minutes: a soft rustling sound that's only loud when you're among trees.
 * /lumen breeze plays one on demand.
 */
public final class Breeze {
	private static final SoundEvent SOUND = SoundEvent.of(Identifier.of("lumen", "breeze"));
	private static final java.util.Random R = new java.util.Random();
	private static final int LENGTH = 100; // 5 seconds

	private static int untilNext = 1200 + R.nextInt(1200);
	private static int remaining;
	private static float strength = 1f;
	private static float level, lastLevel;

	public static void tick(MinecraftClient client) {
		lastLevel = level;
		if (client.world == null || client.isPaused()) return;
		if (remaining > 0) {
			remaining--;
		} else if (--untilNext <= 0) {
			start(0.7f + R.nextFloat() * 0.3f, true);
		}
		float target = 0f;
		if (remaining > 0) {
			float t = 1f - remaining / (float) LENGTH;      // 0 -> 1 over the breeze
			target = strength * (float) Math.sin(Math.PI * t); // swell up, then die down
		}
		level += (target - level) * 0.25f;
	}

	/** Starts a breeze now. */
	public static void start(float power, boolean withSound) {
		strength = power;
		remaining = LENGTH;
		untilNext = 1200 + R.nextInt(1200);
		if (withSound) playSound(MinecraftClient.getInstance());
	}

	/** 0..1, smooth across frames. */
	public static float level(float tickDelta) {
		return lastLevel + (level - lastLevel) * tickDelta;
	}

	/** Value handed to shaders in place of the menu blur radius while no screen is open. */
	public static int shaderValue(float tickDelta) {
		return 100 + Math.round(Math.max(0f, Math.min(1f, level(tickDelta))) * 100f);
	}

	private static void playSound(MinecraftClient client) {
		ClientWorld world = client.world;
		if (world == null || client.player == null) return;
		// how many leaf blocks are around you decides how loud the rustle is
		BlockPos c = client.player.getBlockPos();
		BlockPos.Mutable m = new BlockPos.Mutable();
		int leaves = 0, samples = 160;
		for (int i = 0; i < samples; i++) {
			m.set(c.getX() + R.nextInt(17) - 8, c.getY() + R.nextInt(13) - 2, c.getZ() + R.nextInt(17) - 8);
			if (world.getBlockState(m).getBlock() instanceof LeavesBlock) leaves++;
		}
		float trees = Math.min(1f, leaves / 18f);       // ~11% leaves around you = fully surrounded
		float volume = 0.12f + 0.58f * trees;          // tuned down; full only deep in a forest
		client.getSoundManager().play(new PositionedSoundInstance(SOUND, SoundCategory.AMBIENT, volume,
				0.95f + R.nextFloat() * 0.1f, Random.create(), client.player.getX(), client.player.getEyeY(), client.player.getZ()));
	}

	private Breeze() {}
}
