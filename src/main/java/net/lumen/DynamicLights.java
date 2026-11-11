package net.lumen;

import net.lumen.config.Effect;
import net.lumen.config.LumenConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Client side only light from torches/lanterns held by players.
 * It only changes how blocks and entities are drawn; real light levels (mob spawning etc.) are untouched.
 */
public final class DynamicLights {
	/** A light source at a block position. */
	public record Source(int x, int y, int z, int level) {}

	private static volatile List<Source> sources = List.of();
	private static final Map<UUID, Source> LAST = new HashMap<>();
	private static final double RANGE = 64.0;
	private static ClientWorld lastWorld;

	/** Light level our held lights add at this block, 0 if none. Called from chunk building threads. */
	public static int lightAt(BlockPos pos) {
		List<Source> list = sources;
		if (list.isEmpty()) return 0;
		int best = 0;
		for (Source s : list) {
			double dx = pos.getX() - s.x();
			double dy = pos.getY() - s.y();
			double dz = pos.getZ() - s.z();
			double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
			int l = (int) Math.round(s.level() - dist);
			if (l > best) best = l;
		}
		return Math.min(best, 15);
	}

	public static int luminance(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return 0;
		Item item = stack.getItem();
		if (item == Items.LAVA_BUCKET) return 15;
		if (item == Items.BLAZE_ROD || item == Items.BLAZE_POWDER) return 10;
		if (item == Items.GLOWSTONE_DUST) return 8;
		if (item == Items.GLOW_BERRIES) return 10;
		if (item == Items.NETHER_STAR) return 12;
		if (item == Items.MAGMA_CREAM) return 6;
		if (item instanceof BlockItem bi) return bi.getBlock().getDefaultState().getLuminance();
		return 0;
	}

	public static void tick(MinecraftClient client) {
		ClientWorld world = client.world;
		if (world != lastWorld) {
			sources = List.of();
			LAST.clear();
			lastWorld = world;
		}
		if (world == null || client.player == null) {
			if (!sources.isEmpty()) sources = List.of();
			LAST.clear();
			return;
		}

		List<Source> next = new ArrayList<>();
		Map<UUID, Source> seen = new HashMap<>();
		boolean enabled = LumenConfig.on(Effect.HELD_LIGHT);
		List<? extends PlayerEntity> players = enabled ? world.getPlayers() : List.of();
		for (PlayerEntity p : players) {
			if (p.isSpectator() || (p.isInvisible() && p != client.player)) continue;
			if (p.squaredDistanceTo(client.player) > RANGE * RANGE) continue;
			int level = Math.max(luminance(p.getMainHandStack()), luminance(p.getOffHandStack()));
			if (level <= 0) continue;
			BlockPos at = BlockPos.ofFloored(p.getX(), p.getEyeY() - 0.4, p.getZ());
			Source s = new Source(at.getX(), at.getY(), at.getZ(), level);
			next.add(s);
			seen.put(p.getUuid(), s);
		}

		// Figure out which areas need re-meshing: where a light appeared, moved or disappeared
		List<Source> dirty = new ArrayList<>();
		for (Map.Entry<UUID, Source> e : seen.entrySet()) {
			Source old = LAST.get(e.getKey());
			if (!e.getValue().equals(old)) {
				dirty.add(e.getValue());
				if (old != null) dirty.add(old);
			}
		}
		for (Map.Entry<UUID, Source> e : LAST.entrySet()) {
			if (!seen.containsKey(e.getKey())) dirty.add(e.getValue());
		}

		sources = List.copyOf(next);
		LAST.clear();
		LAST.putAll(seen);

		if (client.worldRenderer != null) {
			for (Source s : dirty) {
				int r = s.level();
				client.worldRenderer.scheduleBlockRenders(s.x() - r, s.y() - r, s.z() - r, s.x() + r, s.y() + r, s.z() + r);
			}
		}
	}

	private DynamicLights() {}
}
