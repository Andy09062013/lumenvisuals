package net.lumen.mixin;

import net.lumen.LumenPost;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs Lumen's screen pass right after the world is drawn, while the depth buffer still holds the world
 * (vanilla clears it before drawing your hand). Needed for mist, haze, shadows, AO and light shafts.
 */
@Mixin(WorldRenderer.class)
public class WorldRendererPostMixin {
	@Inject(method = "render", at = @At("TAIL"), require = 0)
	private void lumen$screenPass(CallbackInfo ci) {
		LumenPost.run();
	}
}
