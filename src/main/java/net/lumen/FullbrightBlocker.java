package net.lumen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns off fullbright resource packs (like Vanilla Tweaks "Fullbright"): any enabled pack that replaces the
 * lightmap shader, or is named fullbright. Lumen has its own Cozy fullbright option in /lumen instead.
 */
public final class FullbrightBlocker {
	private static final Identifier LIGHTMAP = Identifier.ofVanilla("shaders/core/lightmap.fsh");
	private static String lastSeen = "";

	private static boolean gammaWarned;

	public static void tick(MinecraftClient client) {
		blockGammaFullbright(client);
		ResourcePackManager manager = client.getResourcePackManager();
		if (manager == null || client.getOverlay() != null) return; // not while resources are reloading
		StringBuilder key = new StringBuilder();
		for (ResourcePackProfile p : manager.getEnabledProfiles()) key.append(p.getId()).append('|');
		if (key.toString().equals(lastSeen)) return;
		lastSeen = key.toString();

		List<ResourcePackProfile> bad = new ArrayList<>();
		for (ResourcePackProfile p : manager.getEnabledProfiles()) {
			String id = p.getId();
			if (id.contains("lumen_") || id.equals("vanilla") || id.equals("fabric") || id.startsWith("fabric/")) continue;
			if (isFullbright(p)) bad.add(p);
		}
		if (bad.isEmpty()) return;

		StringBuilder names = new StringBuilder();
		for (ResourcePackProfile p : bad) {
			manager.disable(p.getId());
			if (!names.isEmpty()) names.append(", ");
			names.append(p.getDisplayName().getString());
		}
		client.options.refreshResourcePacks(manager);
		client.reloadResources();
		SystemToast.show(client.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION,
				Text.literal("Fullbright pack disabled"),
				Text.literal(names + " was turned off. Press F6 to enable Lumen's built-in fullbright."));
	}

	/** Client fullbright features (Dawn/Feather, gamma mods) push brightness past the vanilla max. Put it back. */
	private static void blockGammaFullbright(MinecraftClient client) {
		try {
			double g = client.options.getGamma().getValue();
			if (g <= 1.0001) return;
			client.options.getGamma().setValue(1.0);
			if (!gammaWarned) {
				gammaWarned = true;
				SystemToast.show(client.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION,
						Text.literal("Fullbright disabled"),
						Text.literal("Your client's fullbright was turned off. Press F6 to enable Lumen's built-in fullbright."));
			}
		} catch (Throwable ignored) {}
	}

	private static boolean isFullbright(ResourcePackProfile p) {
		String text = (p.getId() + " " + p.getDisplayName().getString() + " " + p.getDescription().getString()).toLowerCase(Locale.ROOT);
		if (text.contains("fullbright") || text.contains("full bright")) return true;
		try (ResourcePack pack = p.createResourcePack()) {
			return pack.open(ResourceType.CLIENT_RESOURCES, LIGHTMAP) != null;
		} catch (Throwable t) {
			return false;
		}
	}

	private FullbrightBlocker() {}
}
