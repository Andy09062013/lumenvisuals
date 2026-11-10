package net.lumen.mixin;

import net.lumen.LumenPost;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.ObjectAllocator;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Runs Lumen's screen pass right after the world is drawn, while the depth buffer still holds the world
 * (vanilla clears it before drawing your hand). Needed for mist, haze, shadows, AO and light shafts.
 */
@Mixin(WorldRenderer.class)
public class WorldRendererPostMixin {
	/** Remember the exact matrices the world is drawn with, so screen effects line up perfectly. */
	@Inject(method = "render", at = @At("HEAD"), require = 0)
	private void lumen$captureView(ObjectAllocator allocator, RenderTickCounter tickCounter, boolean renderBlockOutline, Camera camera,
			Matrix4f positionMatrix, Matrix4f projectionMatrix, Matrix4f matrix3, GpuBufferSlice fog, Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
		net.lumen.ViewCapture.capture(positionMatrix, projectionMatrix);
	}

	@Inject(method = "render", at = @At("TAIL"), require = 0)
	private void lumen$screenPass(CallbackInfo ci) {
		LumenPost.run();
	}
}
