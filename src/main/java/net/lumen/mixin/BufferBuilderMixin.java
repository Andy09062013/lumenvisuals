package net.lumen.mixin;

import net.lumen.LeafTag;
import net.minecraft.client.render.BufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Applies the sway tag (see LeafTag) to vertex colors while a swaying block is being meshed. */
@Mixin(BufferBuilder.class)
public class BufferBuilderMixin {
	@ModifyVariable(method = "vertex(FFFIFFIIFFF)V", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
	private int lumen$tagLeaves(int color) {
		return LeafTag.apply(color);
	}
}
