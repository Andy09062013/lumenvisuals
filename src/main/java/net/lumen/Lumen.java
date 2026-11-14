package net.lumen;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.lumen.net.LumenHelloPayload;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Server side of Lumen. It changes no gameplay. Lumen is optional by default: players without it can
 * join and get a short chat tip with the download link. Set requireClientMod to true in
 * config/lumen-server.json to kick players who don't have it instead.
 */
public class Lumen implements ModInitializer {
	public static ServerConfig serverConfig;
	/** Players who joined without Lumen, waiting for their chat tip. */
	private static final java.util.Set<java.util.UUID> WITHOUT = java.util.concurrent.ConcurrentHashMap.newKeySet();

	/** The download link, clickable where the client's screen supports it. */
	private static net.minecraft.text.MutableText link(String url) {
		net.minecraft.text.MutableText t = Text.literal(url).formatted(Formatting.AQUA, Formatting.UNDERLINE);
		try {
			t = t.styled(st -> st.withClickEvent(new net.minecraft.text.ClickEvent.OpenUrl(java.net.URI.create(url))));
		} catch (Exception ignored) {
			// not a valid URL, just show the text
		}
		return t;
	}

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.configurationS2C().register(LumenHelloPayload.ID, LumenHelloPayload.CODEC);

		ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
			// Singleplayer / LAN host always has it
			if (!server.isDedicated()) return;
			if (serverConfig == null) serverConfig = ServerConfig.load();
			if (ServerConfigurationNetworking.canSend(handler, LumenHelloPayload.ID)) return;
			if (!serverConfig.requireClientMod) {
				if (serverConfig.suggestToPlayersWithout) {
					try {
						WITHOUT.add(((net.lumen.mixin.ServerConfigHandlerAccessor) handler).lumen$getProfile().id());
					} catch (Throwable ignored) {}
				}
				return;
			}

			handler.disconnect(Text.literal("")
					.append(Text.literal("Lumen required\n\n").formatted(Formatting.GOLD, Formatting.BOLD))
					.append(Text.literal(serverConfig.kickMessage + "\n\n").formatted(Formatting.WHITE))
					.append(Text.literal("Get it here (download + 3 quick steps):\n").formatted(Formatting.GRAY))
					.append(link(serverConfig.downloadUrl))
					.append(Text.literal("\n\nPut it in your mods folder with Fabric API, then rejoin.").formatted(Formatting.GRAY)));
		});

		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (!WITHOUT.remove(handler.getPlayer().getUuid())) return;
			handler.getPlayer().sendMessage(Text.literal("")
					.append(Text.literal("This server looks better with Lumen ").formatted(Formatting.GOLD))
					.append(Text.literal("(optional visual mod). Get it here: ").formatted(Formatting.GRAY))
					.append(link(serverConfig.downloadUrl)), false);
		});
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> WITHOUT.remove(handler.getPlayer().getUuid()));
	}
}
