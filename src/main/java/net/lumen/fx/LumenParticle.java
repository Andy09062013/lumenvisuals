package net.lumen.fx;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.render.Camera;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import org.joml.Quaternionf;

/**
 * Lumen's own water particles (splash crowns, foam puffs, flat ripples, droplets).
 * The textures live in assets/lumen/textures/particle, which the vanilla particle atlas picks up automatically,
 * so no particle types are registered and servers without Lumen are unaffected.
 */
public class LumenParticle extends BillboardParticle {
	public enum Kind {
		SPLASH("water_splash_", 4),
		FOAM("water_foam_", 4),
		RIPPLE("water_ripple", 1),
		DROPLET("water_droplet", 1);

		final String name;
		final int frames;

		Kind(String name, int frames) {
			this.name = name;
			this.frames = frames;
		}
	}

	private static final Rotator FLAT = (q, cam, tickDelta) -> q.rotationX((float) (-Math.PI / 2));

	private final Kind kind;
	private final float baseScale;
	private final float growth;

	public LumenParticle(ClientWorld world, double x, double y, double z, double vx, double vy, double vz, Kind kind, float size, int life) {
		super(world, x, y, z, sprite(kind, 0));
		this.kind = kind;
		this.velocityX = vx;
		this.velocityY = vy;
		this.velocityZ = vz;
		this.maxAge = Math.max(2, life);
		this.baseScale = size;
		this.scale = size;
		this.collidesWithWorld = kind == Kind.DROPLET;
		switch (kind) {
			case DROPLET -> { gravityStrength = 0.9f; velocityMultiplier = 0.97f; growth = 0f; }
			case FOAM -> { gravityStrength = -0.015f; velocityMultiplier = 0.9f; growth = 0.5f; }
			case RIPPLE -> { gravityStrength = 0f; velocityMultiplier = 0.8f; growth = 1.6f; }
			default -> { gravityStrength = 0f; velocityMultiplier = 0.8f; growth = 0.45f; }
		}
	}

	static Sprite sprite(Kind kind, int frame) {
		SpriteAtlasTexture atlas = MinecraftClient.getInstance().getAtlasManager().getAtlasTexture(net.minecraft.util.Atlases.PARTICLES);
		String path = kind.frames > 1 ? kind.name + frame : kind.name;
		return atlas.getSprite(Identifier.of("lumen", path));
	}

	@Override
	public void tick() {
		super.tick();
		if (dead) return;
		float t = (float) age / maxAge;
		if (kind.frames > 1) {
			try {
				setSprite(sprite(kind, Math.min(kind.frames - 1, (int) (t * kind.frames))));
			} catch (Throwable ex) {
				markDead();
				return;
			}
		}
		scale = baseScale * (1f + growth * t);
		setAlpha(t < 0.65f ? 1f : Math.max(0f, 1f - (t - 0.65f) / 0.35f));
	}

	@Override
	public Rotator getRotator() {
		return switch (kind) {
			case RIPPLE -> FLAT;
			case SPLASH, FOAM -> Rotator.Y_AND_W_ONLY;
			default -> Rotator.ALL_AXIS;
		};
	}

	@Override
	protected RenderType getRenderType() {
		return RenderType.PARTICLE_ATLAS_TRANSLUCENT;
	}

	@SuppressWarnings("unused")
	private static void unused(Quaternionf q, Camera c) {}
}
