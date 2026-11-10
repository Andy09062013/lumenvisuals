package net.lumen.mixin;

import net.lumen.DynamicLights;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Entities (and your hand/held item) get lit by nearby held lights too. Visual only. */
@Mixin(EntityRenderer.class)
public class EntityRendererMixin {
	@Inject(method = "getBlockLight", at = @At("RETURN"), cancellable = true, require = 0)
	private void lumen$dynamicLight(Entity entity, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		int dyn = Math.max(DynamicLights.lightAt(pos), net.lumen.CozyLight.level());
		if (dyn > cir.getReturnValueI()) cir.setReturnValue(dyn);
	}
}
