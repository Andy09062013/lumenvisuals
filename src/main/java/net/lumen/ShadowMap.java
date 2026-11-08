package net.lumen;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.lumen.config.Effect;
import net.lumen.config.LumenConfig;
import net.lumen.mixin.WorldRendererAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.DynamicUniforms;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.BlockRenderLayer;
import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.chunk.AbstractChunkRenderData;
import net.minecraft.client.render.chunk.Buffers;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Real sun shadows: every frame the nearby terrain is drawn a second time from the sun's point of view
 * into a depth texture (a shadow map). The screen pass then checks each pixel against it.
 * Unlike the old screen-space trick this also catches things behind you or off screen.
 */
public final class ShadowMap {
	public static final int SIZE = 2048;
	public static final float RADIUS = 80f;   // half-width of the area that gets shadows (blocks)
	public static final float DEPTH = 192f;   // depth range along the sun direction (blocks)
	public static final float TEXEL = 2f * RADIUS / SIZE;
	public static final Identifier TEXTURE = Identifier.of("lumen", "textures/effect/shadow.png");
	public static final Identifier TEXTURE_SHORT = Identifier.of("lumen", "shadow");

	/** Values the screen shader needs to line up with the shadow map (already quantized to what it receives). */
	public static boolean ready;
	public static float lightX, lightY;     // light direction (z is always 0: the sun moves east-west)
	public static float offsetU, offsetV;   // 0..1 fractions of a texel, keeps the shadow grid fixed in the world

	private static GpuTexture depthTex, colorTex;
	private static GpuTextureView depthView, colorView;
	private static RenderPipeline pipeline;
	private static boolean failed;

	/** Hands the depth texture to the post effect as an ordinary named texture. */
	private static final class Holder extends AbstractTexture {
		void set(GpuTexture tex, GpuTextureView view) {
			this.glTexture = tex;
			this.glTextureView = view;
			this.sampler = RenderSystem.getSamplerCache().get(FilterMode.NEAREST);
		}

		@Override
		public void close() {
			// owned by ShadowMap
		}
	}

	public static void init(MinecraftClient client) {
		try {
			GpuDevice device = RenderSystem.getDevice();
			int usage = GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING;
			depthTex = device.createTexture(() -> "Lumen shadow depth", usage, TextureFormat.DEPTH32, SIZE, SIZE, 1, 1);
			colorTex = device.createTexture(() -> "Lumen shadow color", usage, TextureFormat.RGBA8, SIZE, SIZE, 1, 1);
			depthView = device.createTextureView(depthTex);
			colorView = device.createTextureView(colorTex);
			RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(depthTex, 1.0);

			Holder holder = new Holder();
			holder.set(depthTex, depthView);
			client.getTextureManager().registerTexture(TEXTURE, holder);
			client.getTextureManager().registerTexture(TEXTURE_SHORT, holder);

			pipeline = RenderPipeline.builder()
					.withLocation(Identifier.of("lumen", "pipeline/shadow"))
					.withVertexShader(Identifier.of("lumen", "core/shadow"))
					.withFragmentShader(Identifier.of("lumen", "core/shadow"))
					.withUniform("Globals", UniformType.UNIFORM_BUFFER)
					.withUniform("ChunkSection", UniformType.UNIFORM_BUFFER)
					.withSampler("Sampler0")
					.withVertexFormat(BlockRenderLayer.SOLID.getPipeline().getVertexFormat(), VertexFormat.DrawMode.QUADS)
					.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
					.withCull(false)
					.withColorWrite(false)
					.withDepthWrite(true)
					.build();
		} catch (Throwable t) {
			failed = true;
			System.err.println("[Lumen] shadow map unavailable: " + t);
		}
	}

	private static float q16(float v01) {
		return Math.round(Math.max(0f, Math.min(1f, v01)) * 65535f) / 65535f;
	}

	/** Draws the shadow map for this frame. Call before the screen pass. */
	public static void render(MinecraftClient client) {
		ready = false;
		if (failed || pipeline == null || client.world == null) return;
		if (!LumenConfig.on(Effect.SUN_SHADOWS) || client.world.getRegistryKey() != World.OVERWORLD) return;

		float elev = LumenClient.ENV.sunElevation;
		boolean morning = LumenClient.ENV.morning;
		float horiz = (float) Math.sqrt(Math.max(0f, 1f - elev * elev)) * (morning ? 1f : -1f);
		// at night the moon (opposite the sun) casts the shadows
		float lx = elev < 0 ? -horiz : horiz;
		float ly = Math.abs(elev);
		if (ly < 0.06f) return; // sun right at the horizon: shadows would be endless streaks

		// quantize exactly like the shader will see it, so both line up perfectly
		lx = q16(lx * 0.5f + 0.5f) * 2f - 1f;
		ly = q16(ly * 0.5f + 0.5f) * 2f - 1f;
		Vector3f L = new Vector3f(lx, ly, 0f).normalize();
		Vector3f X = new Vector3f(0f, 0f, 1f);
		Vector3f Y = new Vector3f(L).cross(X).normalize();

		Camera cam = client.gameRenderer.getCamera();
		Vec3d c = cam.getCameraPos();
		double cx = c.x * X.x + c.y * X.y + c.z * X.z;
		double cy = c.x * Y.x + c.y * Y.y + c.z * Y.z;
		float ou = q16((float) ((cx - Math.floor(cx / TEXEL) * TEXEL) / TEXEL));
		float ov = q16((float) ((cy - Math.floor(cy / TEXEL) * TEXEL) / TEXEL));
		float offX = ou * TEXEL, offY = ov * TEXEL;

		// camera-relative world position -> shadow clip space
		Matrix4f m = new Matrix4f(
				X.x / RADIUS, Y.x / RADIUS, -L.x / DEPTH, 0f,
				X.y / RADIUS, Y.y / RADIUS, -L.y / DEPTH, 0f,
				X.z / RADIUS, Y.z / RADIUS, -L.z / DEPTH, 0f,
				offX / RADIUS, offY / RADIUS, 0f, 1f);

		try {
			BuiltChunkStorage storage = ((WorldRendererAccessor) client.worldRenderer).lumen$getChunks();
			if (storage == null || storage.chunks == null) return;

			AbstractTexture atlas = client.getTextureManager().getTexture(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
			GpuTextureView atlasView = atlas.getGlTextureView();
			int atlasW = atlasView.getWidth(0), atlasH = atlasView.getHeight(0);

			List<DynamicUniforms.ChunkSectionsValue> sections = new ArrayList<>();
			List<RenderPass.RenderObject<GpuBufferSlice[]>> draws = new ArrayList<>();
			int maxIndices = 0;
			BlockRenderLayer[] layers = {BlockRenderLayer.SOLID, BlockRenderLayer.CUTOUT};
			float reach = RADIUS + 14f;

			for (ChunkBuilder.BuiltChunk chunk : storage.chunks) {
				if (chunk == null) continue;
				AbstractChunkRenderData data = chunk.getCurrentRenderData();
				if (data == null || !data.hasData()) continue;
				BlockPos o = chunk.getOrigin();
				float px = (float) (o.getX() + 8 - c.x), py = (float) (o.getY() + 8 - c.y), pz = (float) (o.getZ() + 8 - c.z);
				float sx = px * X.x + py * X.y + pz * X.z;
				float sy = px * Y.x + py * Y.y + pz * Y.z;
				float sz = px * L.x + py * L.y + pz * L.z;
				if (Math.abs(sx) > reach || Math.abs(sy) > reach || Math.abs(sz) > DEPTH) continue;

				int sectionIndex = -1;
				for (BlockRenderLayer layer : layers) {
					Buffers buffers = data.getBuffersForLayer(layer);
					if (buffers == null || buffers.getIndexCount() <= 0) continue;
					if (sectionIndex < 0) {
						sectionIndex = sections.size();
						sections.add(new DynamicUniforms.ChunkSectionsValue(m, o.getX(), o.getY(), o.getZ(), 1f, atlasW, atlasH));
					}
					final int idx = sectionIndex;
					GpuBuffer indexBuffer = buffers.getIndexBuffer();
					if (indexBuffer == null) maxIndices = Math.max(maxIndices, buffers.getIndexCount());
					draws.add(new RenderPass.RenderObject<>(0, buffers.getVertexBuffer(), indexBuffer,
							indexBuffer == null ? null : buffers.getIndexType(), 0, buffers.getIndexCount(),
							(slices, uploader) -> uploader.upload("ChunkSection", slices[idx])));
				}
			}

			var encoder = RenderSystem.getDevice().createCommandEncoder();
			if (draws.isEmpty()) {
				encoder.clearDepthTexture(depthTex, 1.0);
			} else {
				GpuBufferSlice[] slices = RenderSystem.getDynamicUniforms().writeChunkSections(sections.toArray(new DynamicUniforms.ChunkSectionsValue[0]));
				RenderSystem.ShapeIndexBuffer seq = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS);
				GpuBuffer seqBuffer = seq.getIndexBuffer(Math.max(maxIndices, 6));
				try (RenderPass pass = encoder.createRenderPass(() -> "Lumen shadow map", colorView, OptionalInt.of(0), depthView, OptionalDouble.of(1.0))) {
					RenderSystem.bindDefaultUniforms(pass);
					pass.setPipeline(pipeline);
					pass.bindTexture("Sampler0", atlasView, RenderSystem.getSamplerCache().get(FilterMode.NEAREST));
					pass.drawMultipleIndexed(draws, seqBuffer, seq.getIndexType(), List.of("ChunkSection"), slices);
				}
			}

			lightX = lx;
			lightY = ly;
			offsetU = ou;
			offsetV = ov;
			ready = true;
		} catch (Throwable t) {
			failed = true;
			System.err.println("[Lumen] shadow map disabled: " + t);
			t.printStackTrace();
		}
	}

	private ShadowMap() {}
}
