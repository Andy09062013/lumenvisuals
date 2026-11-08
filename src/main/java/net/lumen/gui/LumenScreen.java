package net.lumen.gui;

import net.lumen.LumenClient;
import net.lumen.config.Effect;
import net.lumen.config.LumenConfig;
import net.lumen.config.Preset;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.text.Text;

/** /lumen settings: preset, every effect on/off + strength, and the terrain shader packs. */
public class LumenScreen extends Screen {
	private static final int ROWS = 8;
	private final Screen parent;
	private int page;
	private boolean packsChanged;

	public LumenScreen(Screen parent) {
		super(Text.literal("Lumen Settings"));
		this.parent = parent;
	}

	private int pageCount() {
		int total = Effect.values().length + LumenClient.PACKS.length;
		return (total + ROWS - 1) / ROWS;
	}

	@Override
	protected void init() {
		clearChildren();
		int cx = width / 2;
		int top = 34;

		addDrawableChild(ButtonWidget.builder(Text.literal("Preset: " + LumenConfig.preset.label), b -> {
			Preset[] all = {Preset.LOW, Preset.MEDIUM, Preset.HIGH, Preset.ULTRA};
			int i = 0;
			for (int k = 0; k < all.length; k++) if (all[k] == LumenConfig.preset) i = k + 1;
			LumenConfig.applyPreset(all[i % all.length]);
			LumenConfig.save();
			init();
		}).dimensions(cx - 100, top, 200, 20).build());

		Effect[] effects = Effect.values();
		int start = page * ROWS;
		for (int row = 0; row < ROWS; row++) {
			int idx = start + row;
			int y = top + 28 + row * 22;
			if (idx < effects.length) {
				Effect e = effects[idx];
				addDrawableChild(ButtonWidget.builder(toggleText(e), b -> {
					LumenConfig.get(e).enabled = !LumenConfig.get(e).enabled;
					LumenConfig.preset = Preset.CUSTOM;
					b.setMessage(toggleText(e));
				}).dimensions(cx - 155, y, 180, 20).build());
				addDrawableChild(new StrengthSlider(cx + 30, y, 125, 20, e));
			} else {
				int p = idx - effects.length;
				if (p >= LumenClient.PACKS.length) continue;
				addDrawableChild(ButtonWidget.builder(packText(p), b -> {
					setPack(p, !isPackOn(p));
					packsChanged = true;
					b.setMessage(packText(p));
				}).dimensions(cx - 155, y, 310, 20).build());
			}
		}

		int by = top + 28 + ROWS * 22 + 6;
		addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> { page = (page + pageCount() - 1) % pageCount(); init(); })
				.dimensions(cx - 155, by, 40, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> { page = (page + 1) % pageCount(); init(); })
				.dimensions(cx + 115, by, 40, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
				.dimensions(cx - 60, by, 120, 20).build());
	}

	private static Text toggleText(Effect e) {
		String why = net.lumen.Compat.unavailable(e);
		String state = LumenConfig.get(e).enabled ? "ON" : "OFF";
		if (why != null) return Text.literal(e.label + ": " + state + " (" + why + ")").formatted(net.minecraft.util.Formatting.GRAY);
		return Text.literal(e.label + ": " + state);
	}

	private Text packText(int p) {
		String why = net.lumen.Compat.packUnavailable(p);
		String base = LumenClient.PACK_NAMES[p] + " (shader pack): " + (isPackOn(p) ? "ON" : "OFF");
		if (why != null) return Text.literal(base + " (" + why + ")").formatted(net.minecraft.util.Formatting.GRAY);
		return Text.literal(base);
	}

	private static boolean isPackOn(int p) {
		ResourcePackManager m = MinecraftClient.getInstance().getResourcePackManager();
		for (ResourcePackProfile profile : m.getEnabledProfiles()) if (profile.getId().contains(LumenClient.PACKS[p])) return true;
		return false;
	}

	private static void setPack(int p, boolean on) {
		ResourcePackManager m = MinecraftClient.getInstance().getResourcePackManager();
		for (ResourcePackProfile profile : m.getProfiles()) {
			if (!profile.getId().contains(LumenClient.PACKS[p])) continue;
			if (on) m.enable(profile.getId());
			else m.disable(profile.getId());
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFFFF);
		context.drawCenteredTextWithShadow(textRenderer, Text.literal("Page " + (page + 1) + "/" + pageCount()), width / 2, 22, 0xFFAAAAAA);
		if (net.lumen.Compat.SODIUM || net.lumen.IrisCompat.shaderPackActive()) {
			context.drawCenteredTextWithShadow(textRenderer, Text.literal("Grey = limited by Sodium / Iris"), width / 2, height - 12, 0xFF888888);
		}
	}

	@Override
	public void close() {
		LumenConfig.save();
		MinecraftClient mc = MinecraftClient.getInstance();
		if (packsChanged) {
			mc.options.refreshResourcePacks(mc.getResourcePackManager());
			mc.reloadResources();
		}
		mc.setScreen(parent);
	}

	private static final class StrengthSlider extends SliderWidget {
		private final Effect effect;

		StrengthSlider(int x, int y, int w, int h, Effect effect) {
			super(x, y, w, h, Text.empty(), LumenConfig.get(effect).intensity / 2.0);
			this.effect = effect;
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			setMessage(Text.literal("Strength: " + Math.round(value * 200) + "%"));
		}

		@Override
		protected void applyValue() {
			LumenConfig.get(effect).intensity = (float) (value * 2.0);
			LumenConfig.preset = Preset.CUSTOM;
		}
	}
}
