package net.lumen;

import net.lumen.fx.Atmosphere;
import net.lumen.mixin.GameRendererAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.render.DefaultFramebufferSet;

/** Draws the Lumen screen pass (sky, mist, lighting, bloom, grading) onto the world image. */
public final class LumenPost {
	private static boolean failed;

	public static void run() {
		if (failed) return;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || client.gameRenderer == null) return;
		if (!Atmosphere.anyScreenEffect() || IrisCompat.shaderPackActive()) return;
		try {
			ShadowMap.render(client);
			Atmosphere.update(client, LumenClient.ENV);
			PostEffectProcessor processor = client.getShaderLoader().loadPostEffect(LumenClient.CINEMATIC, DefaultFramebufferSet.MAIN_ONLY);
			if (processor != null) {
				processor.render(client.getFramebuffer(), ((GameRendererAccessor) client.gameRenderer).lumen$getPool());
			}
		} catch (Throwable t) {
			// never crash the game over visuals: turn the screen pass off for this session and log why
			failed = true;
			System.err.println("[Lumen] screen effects disabled: " + t);
			t.printStackTrace();
		}
	}

	private LumenPost() {}
}
