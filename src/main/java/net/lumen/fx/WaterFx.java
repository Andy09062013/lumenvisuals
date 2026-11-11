package net.lumen.fx;

import net.lumen.config.Effect;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.AbstractBoatEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Splashes, ripples, swim foam, boat wakes and bubbles for entities near the player. */
public final class WaterFx {
	private static final class Track {
		double x, y, z;
		double vx, vy, vz;
		boolean inWater;
		int seenTick;
	}

	private static final Map<Integer, Track> TRACKS = new HashMap<>();
	private static int tick;
	private static ClientWorld lastWorld;
	private static final double RANGE = 40;

	public static void tick(MinecraftClient client, ClientWorld world) {
		if (world != lastWorld) {
			TRACKS.clear();
			lastWorld = world;
		}
		tick++;
		boolean any = Fx.rate(Effect.WATER_SPLASH) > 0 || Fx.rate(Effect.WATER_RIPPLES) > 0 || Fx.rate(Effect.SWIM_FOAM) > 0
				|| Fx.rate(Effect.BOAT_WAKE) > 0 || Fx.rate(Effect.BUBBLES) > 0;
		if (!any || client.player == null) { TRACKS.clear(); return; }

		for (Entity e : world.getEntities()) {
			if (e.isRemoved() || e.isSpectator()) continue;
			if (e.squaredDistanceTo(client.player) > RANGE * RANGE) continue;
			if (!(e instanceof LivingEntity || e instanceof ItemEntity || e instanceof AbstractBoatEntity)) continue;

			Track t = TRACKS.get(e.getId());
			boolean inWater = e.isTouchingWater();
			if (t == null) {
				t = new Track();
				t.x = e.getX(); t.y = e.getY(); t.z = e.getZ();
				t.inWater = inWater;
				TRACKS.put(e.getId(), t);
			}
			// our own velocity (other entities' velocity isn't reliable on the client)
			double nvx = e.getX() - t.x, nvy = e.getY() - t.y, nvz = e.getZ() - t.z;
			double prevVy = t.vy;
			t.x = e.getX(); t.y = e.getY(); t.z = e.getZ();
			t.vx = nvx; t.vy = nvy; t.vz = nvz;
			t.seenTick = tick;

			if (inWater && !t.inWater) entrySplash(world, e, Math.min(prevVy, nvy));
			t.inWater = inWater;

			if (!inWater && !(e instanceof AbstractBoatEntity)) continue;
			double speed = Math.sqrt(nvx * nvx + nvz * nvz);

			if (e instanceof AbstractBoatEntity) {
				boatWake(world, e, nvx, nvz, speed);
				continue;
			}
			Double surface = surfaceY(world, e);
			boolean atSurface = surface != null && e.getY() + e.getHeight() * 0.5 > surface - 0.6 && e.getY() < surface + 0.2;

			if (atSurface && speed > 0.03) ripples(world, e, surface, speed);
			if (e instanceof PlayerEntity p && atSurface && (speed > 0.04 || p.isSwimming())) swimFoam(world, p, surface, speed);
			if (e.isSubmergedInWater() && (speed > 0.03 || Math.abs(nvy) > 0.03)) bubbles(e, speed);
		}

		// forget entities we haven't seen for a while
		Iterator<Track> it = TRACKS.values().iterator();
		while (it.hasNext()) if (tick - it.next().seenTick > 40) it.remove();
	}

	/** Water surface height above/at the entity, or null if there's no surface nearby. */
	private static Double surfaceY(ClientWorld world, Entity e) {
		BlockPos.Mutable m = new BlockPos.Mutable(e.getBlockX(), (int) Math.floor(e.getY()), e.getBlockZ());
		for (int i = 0; i < 4; i++) {
			FluidState fs = world.getFluidState(m);
			FluidState above = world.getFluidState(m.up());
			if (fs.isIn(FluidTags.WATER) && !above.isIn(FluidTags.WATER)) {
				return m.getY() + (double) fs.getHeight(world, m);
			}
			m.move(0, 1, 0);
		}
		return null;
	}

	private static void entrySplash(ClientWorld world, Entity e, double vy) {
		float r = Fx.rate(Effect.WATER_SPLASH);
		if (r <= 0) return;
		double fall = Math.max(0.0, -vy); // blocks per tick going down
		double size = Math.max(0.25, e.getWidth() * e.getHeight());
		double power = Math.min(4.0, (0.25 + fall * 2.2) * Math.sqrt(size));
		if (e instanceof ItemEntity) power *= 0.4;
		Double surf = surfaceY(world, e);
		double sy = surf != null ? surf : e.getY() + 0.1;
		BlockPos pos = e.getBlockPos();
		double w = e.getWidth() * 0.6 + 0.2;

		// new textured splash: crown + ripple + droplets + foam, sized by fall speed and entity size
		Fx.splash(world, e.getX(), sy, e.getZ(), power, r);
		int spray = Fx.count((float) (4 + 12 * power) * r);
		for (int i = 0; i < spray; i++) {
			double a = Fx.R.nextDouble() * Math.PI * 2;
			double sp = (0.08 + Fx.R.nextDouble() * 0.18) * (0.7 + power * 0.5);
			// vy = 0 makes vanilla's splash particle hop outward with this sideways speed
			Fx.spawn(ParticleTypes.SPLASH, e.getX() + Math.cos(a) * w, sy + 0.05, e.getZ() + Math.sin(a) * w, Math.cos(a) * sp, 0, Math.sin(a) * sp);
		}
		int drops = Fx.count((float) (2 + 8 * power) * r);
		for (int i = 0; i < drops; i++) {
			Fx.spawn(Fx.waterDroplet(world, pos, 0.55f, 0.9f + Fx.R.nextFloat() * 0.6f),
					e.getX() + Fx.rand(w * 1.3), sy + 0.2 + Fx.R.nextDouble() * (0.6 + power * 0.9), e.getZ() + Fx.rand(w * 1.3), 0, 0, 0);
		}
		int ring = Fx.count((float) (4 + 8 * power) * r);
		for (int i = 0; i < ring; i++) {
			double a = Math.PI * 2 * i / Math.max(1, ring);
			Fx.spawn(ParticleTypes.FISHING, e.getX() + Math.cos(a) * w, sy + 0.02, e.getZ() + Math.sin(a) * w, Math.cos(a) * 0.07, 0, Math.sin(a) * 0.07);
		}
		int bub = Fx.count((float) (8 + 18 * power) * r);
		for (int i = 0; i < bub; i++) {
			Fx.spawn(ParticleTypes.BUBBLE, e.getX() + Fx.rand(w), sy - 0.3 - Fx.R.nextDouble() * power * 0.4, e.getZ() + Fx.rand(w), 0, 0.02, 0);
		}
		if (power > 0.8) {
			int mist = Fx.count((float) (power * 5) * r);
			for (int i = 0; i < mist; i++) {
				Fx.spawn(Fx.waterDroplet(world, pos, 0.85f, 1.6f), e.getX() + Fx.rand(w), sy + 0.3, e.getZ() + Fx.rand(w), 0, 0, 0);
			}
		}
	}

	private static void ripples(ClientWorld world, Entity e, double surface, double speed) {
		float r = Fx.rate(Effect.WATER_RIPPLES);
		if (r <= 0) return;
		if (!Fx.chance((float) Math.min(1.0, 0.35 + speed * 8) * r)) return;
		double w = e.getWidth() * 0.5 + 0.25;
		int n = 6 + Fx.R.nextInt(5);
		for (int i = 0; i < n; i++) {
			double a = Fx.R.nextDouble() * Math.PI * 2;
			Fx.spawn(ParticleTypes.FISHING, e.getX() + Math.cos(a) * w, surface + 0.02, e.getZ() + Math.sin(a) * w,
					Math.cos(a) * 0.05, 0, Math.sin(a) * 0.05);
			if (i % 2 == 0) Fx.spawn(ParticleTypes.SPLASH, e.getX() + Math.cos(a) * w, surface + 0.05, e.getZ() + Math.sin(a) * w, Math.cos(a) * 0.04, 0, Math.sin(a) * 0.04);
		}
	}

	private static void swimFoam(ClientWorld world, PlayerEntity p, double surface, double speed) {
		float r = Fx.rate(Effect.SWIM_FOAM);
		if (r <= 0) return;
		float yaw = (float) Math.toRadians(p.getYaw());
		double fx = -Math.sin(yaw), fz = Math.cos(yaw);   // forward
		double rx = -Math.cos(yaw), rz = -Math.sin(yaw);  // right
		float energy = (float) Math.min(1.5, speed * 6 + (p.isSprinting() || p.isSwimming() ? 0.5 : 0));
		BlockPos pos = p.getBlockPos();
		// arms: alternating strokes left/right
		boolean left = (p.age / 6) % 2 == 0;
		double side = left ? -0.45 : 0.45;
		if (Fx.chance(Math.min(1f, 1.4f * energy * r) + 0.3f)) {
			double ax = p.getX() + fx * 0.5 + rx * side, az = p.getZ() + fz * 0.5 + rz * side;
			for (int i = 0; i < 5; i++)
				Fx.spawn(ParticleTypes.SPLASH, ax + Fx.rand(0.15), surface + 0.05, az + Fx.rand(0.15), rx * side * 0.1 + Fx.rand(0.04), 0, rz * side * 0.1 + Fx.rand(0.04));
			for (int i = 0; i < 3; i++) Fx.spawn(Fx.waterDroplet(world, pos, 0.5f, 0.8f), ax + Fx.rand(0.2), surface + 0.25 + Fx.R.nextDouble() * 0.4, az + Fx.rand(0.2), 0, 0, 0);
		}
		// feet: foam behind
		if (Fx.chance(Math.min(1f, 1.2f * energy * r) + 0.3f)) {
			double bx = p.getX() - fx * 0.9, bz = p.getZ() - fz * 0.9;
			for (int i = 0; i < 4; i++) Fx.spawn(ParticleTypes.FISHING, bx + Fx.rand(0.4), surface + 0.02, bz + Fx.rand(0.4), -fx * 0.04 + Fx.rand(0.02), 0, -fz * 0.04 + Fx.rand(0.02));
			if (Fx.chance(0.5f)) Fx.spawn(ParticleTypes.SPLASH, bx, surface + 0.05, bz, Fx.rand(0.05), 0, Fx.rand(0.05));
		}
	}

	private static void boatWake(ClientWorld world, Entity boat, double vx, double vz, double speed) {
		float r = Fx.rate(Effect.BOAT_WAKE);
		if (r <= 0 || speed < 0.06) return;
		BlockPos below = boat.getBlockPos();
		boolean onWater = world.getFluidState(below).isIn(FluidTags.WATER) || world.getFluidState(below.down()).isIn(FluidTags.WATER);
		if (!onWater) return;
		double surface = Math.floor(boat.getY() + 0.3) - 0.05;
		Double s = surfaceY(world, boat);
		if (s != null) surface = s;
		double dx = vx / speed, dz = vz / speed;
		double px = -dz, pz = dx; // sideways
		int n = Fx.count((float) (4 + speed * 30) * r);
		for (int i = 0; i < n; i++) {
			double back = 1.2 + Fx.R.nextDouble() * 0.6;
			double sideOff = (Fx.R.nextBoolean() ? 1 : -1) * (0.4 + Fx.R.nextDouble() * 0.5);
			Fx.spawn(ParticleTypes.FISHING, boat.getX() - dx * back + px * sideOff, surface + 0.02, boat.getZ() - dz * back + pz * sideOff,
					px * sideOff * 0.04, 0, pz * sideOff * 0.04);
		}
		for (int k = 0; k < 2 && Fx.chance((float) Math.min(1.0, speed * 8) * r); k++) {
			double sideOff = Fx.R.nextBoolean() ? 0.7 : -0.7;
			Fx.spawn(ParticleTypes.SPLASH, boat.getX() + dx * 0.8 + px * sideOff, surface + 0.05, boat.getZ() + dz * 0.8 + pz * sideOff,
					px * sideOff * 0.15, 0, pz * sideOff * 0.15);
		}
	}

	private static void bubbles(Entity e, double speed) {
		float r = Fx.rate(Effect.BUBBLES);
		if (r <= 0) return;
		int n = Fx.count((float) Math.min(3, 0.6 + speed * 8) * r);
		for (int i = 0; i < n; i++) {
		double w = e.getWidth() * 0.6;
		Fx.spawn(ParticleTypes.BUBBLE, e.getX() + Fx.rand(w), e.getY() + Fx.R.nextDouble() * e.getHeight(), e.getZ() + Fx.rand(w), 0, 0.03, 0);
		}
	}

	private WaterFx() {}
}
