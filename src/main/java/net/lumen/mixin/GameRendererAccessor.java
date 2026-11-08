package net.lumen.mixin;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.Pool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
	/** The render target pool vanilla uses for its own post effects. */
	@Accessor("pool")
	Pool lumen$getPool();
}
