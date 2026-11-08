package net.lumen;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.registry.tag.BlockTags;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Marks swaying blocks while chunk meshes are built by setting their vertex alpha to a tag value,
 * which the terrain shader reads: 253 = leaves (whole block sways), 252 = crops/dead bushes/dry grass (tops sway).
 * Looks identical otherwise.
 */
public final class LeafTag {
	public static final int ALPHA = 253;
	public static final int PLANT_ALPHA = 252;
	private static final ThreadLocal<boolean[]> ACTIVE = ThreadLocal.withInitial(() -> new boolean[1]);
	private static final ThreadLocal<int[]> TAG = ThreadLocal.withInitial(() -> new int[1]);
	private static final AtomicInteger ANY = new AtomicInteger();

	public static void begin(BlockState state) {
		int tag = 0;
		if (state != null) {
			if (state.getBlock() instanceof LeavesBlock) tag = ALPHA;
			else if (state.isIn(BlockTags.CROPS) || state.isOf(Blocks.DEAD_BUSH)
					|| state.isOf(Blocks.SHORT_DRY_GRASS) || state.isOf(Blocks.TALL_DRY_GRASS)) tag = PLANT_ALPHA;
		}
		if (tag != 0) {
			TAG.get()[0] = tag;
			boolean[] f = ACTIVE.get();
			if (!f[0]) { f[0] = true; ANY.incrementAndGet(); }
		}
	}

	public static void end() {
		if (ANY.get() == 0) return;
		boolean[] f = ACTIVE.get();
		if (f[0]) { f[0] = false; ANY.decrementAndGet(); }
	}

	public static int apply(int argb) {
		if (ANY.get() == 0 || !ACTIVE.get()[0]) return argb;
		return (argb & 0x00FFFFFF) | (TAG.get()[0] << 24);
	}

	private LeafTag() {}
}
