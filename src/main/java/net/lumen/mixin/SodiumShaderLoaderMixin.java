package net.lumen.mixin;

import net.lumen.SodiumShaders;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Only applies when Sodium is installed: swaps in Lumen's patched terrain shaders. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader", remap = false)
public class SodiumShaderLoaderMixin {
	@Inject(method = "getShaderSource", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
	private static void lumen$patch(Identifier id, CallbackInfoReturnable<String> cir) {
		String src = SodiumShaders.override(id);
		if (src != null) cir.setReturnValue(src);
	}
}
