package net.lumen.mixin;

import net.lumen.DynamicLights;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds held item light to how blocks are lit when chunks are meshed. Visual only. */
@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
	@Inject(method = "getLightmapCoordinates(Lnet/minecraft/client/render/WorldRenderer$BrightnessGetter;Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;)I",
			at = @At("RETURN"), cancellable = true, require = 0)
	private static void lumen$dynamicLight(WorldRenderer.BrightnessGetter getter, BlockRenderView world, BlockState state, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		int dyn = Math.max(DynamicLights.lightAt(pos), net.lumen.CozyLight.level());
		if (dyn <= 0) return;
		int packed = cir.getReturnValueI();
		int block = LightmapTextureManager.getBlockLightCoordinates(packed);
		if (dyn > block) {
			int sky = LightmapTextureManager.getSkyLightCoordinates(packed);
			cir.setReturnValue(LightmapTextureManager.pack(dyn, sky));
		}
	}
}
