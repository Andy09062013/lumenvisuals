package net.lumen.mixin;

import net.lumen.LeafTag;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fabric API's terrain renderer (Indigo) meshes each block here; we flag swaying blocks while it does. */
@Pseudo
@Mixin(targets = "net.fabricmc.fabric.impl.client.indigo.renderer.render.TerrainRenderContext", remap = false)
public class IndigoTerrainMixin {
	@Inject(method = "bufferModel", at = @At("HEAD"), require = 0, remap = false)
	private void lumen$begin(BlockStateModel model, BlockState state, BlockPos pos, CallbackInfo ci) {
		LeafTag.begin(state);
	}

	@Inject(method = "bufferModel", at = @At("RETURN"), require = 0, remap = false)
	private void lumen$end(BlockStateModel model, BlockState state, BlockPos pos, CallbackInfo ci) {
		LeafTag.end();
	}
}
