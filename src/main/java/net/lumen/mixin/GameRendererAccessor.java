package net.lumen.mixin;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.Pool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import net.minecraft.client.render.Camera;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
	/** The render target pool vanilla uses for its own post effects. */
	@Accessor("pool")
	Pool lumen$getPool();

	/** The FOV the world is actually drawn with (includes sprinting, speed effects, spyglass...). */
	@Invoker("getFov")
	float lumen$getFov(Camera camera, float tickProgress, boolean changingFov);
}
