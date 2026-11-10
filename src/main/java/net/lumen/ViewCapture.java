package net.lumen;

import net.lumen.mixin.GameRendererAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The exact camera used to draw the world this frame (view bobbing, sprint FOV, hurt tilt and any
 * other mod's camera changes included), turned into a simple form the screen shaders can use:
 * camera-relative world position = eye + (center + right * ndcX + up * ndcY) * linear depth.
 */
public final class ViewCapture {
	private static final Matrix4f VIEW = new Matrix4f();
	private static final Matrix4f PROJ = new Matrix4f();
	private static volatile boolean captured;

	public static final Vector3f EYE = new Vector3f();
	public static final Vector3f CENTER = new Vector3f(0, 0, 1);
	public static final Vector3f RIGHT = new Vector3f(1, 0, 0);
	public static final Vector3f UP = new Vector3f(0, 1, 0);

	public static void capture(Matrix4f view, Matrix4f proj) {
		if (view == null || proj == null) return;
		VIEW.set(view);
		PROJ.set(proj);
		captured = true;
	}

	/** Recompute the shader camera. Call once per frame after the world was drawn. */
	public static void update(MinecraftClient client) {
		try {
			Camera cam = client.gameRenderer.getCamera();
			float tick = client.getRenderTickCounter().getTickProgress(true);
			float fov = ((GameRendererAccessor) client.gameRenderer).lumen$getFov(cam, tick, true);
			Matrix4f basic = client.gameRenderer.getBasicProjectionMatrix(fov);
			Matrix4f view = captured ? new Matrix4f(VIEW) : new Matrix4f().rotation(cam.getRotation().conjugate(new org.joml.Quaternionf()));
			Matrix4f full = captured ? new Matrix4f(PROJ) : new Matrix4f(basic);
			// full = basic * extra (bobbing, tilt, distortion); everything before "basic" is the real eye
			Matrix4f extraView = new Matrix4f(basic).invert().mul(full).mul(view);
			Matrix4f a = extraView.invert();
			a.transformPosition(EYE.set(0, 0, 0));
			a.transformDirection(CENTER.set(0, 0, -1));
			a.transformDirection(RIGHT.set(1f / basic.m00(), 0, 0));
			a.transformDirection(UP.set(0, 1f / basic.m11(), 0));
			if (!EYE.isFinite() || !CENTER.isFinite() || !RIGHT.isFinite() || !UP.isFinite()) reset();
		} catch (Throwable t) {
			reset();
		}
	}

	private static void reset() {
		EYE.set(0, 0, 0);
		CENTER.set(0, 0, 1);
		RIGHT.set(1, 0, 0);
		UP.set(0, 1, 0);
	}

	private ViewCapture() {}
}
