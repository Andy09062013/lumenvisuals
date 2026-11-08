package net.lumen;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.lumen.net.LumenHelloPayload;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Server side of Lumen. It changes no gameplay: its only job is to check that joining
 * players have Lumen installed, and if not, disconnect them with a message saying where to get it.
 * (A server can't push mods onto a client, so this is the "you have to install it" prompt.)
 */
public class Lumen implements ModInitializer {
	public static ServerConfig serverConfig;

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
			if (!serverConfig.requireClientMod) return;
			if (ServerConfigurationNetworking.canSend(handler, LumenHelloPayload.ID)) return;

			handler.disconnect(Text.literal("")
					.append(Text.literal("Lumen required\n\n").formatted(Formatting.GOLD, Formatting.BOLD))
					.append(Text.literal(serverConfig.kickMessage + "\n\n").formatted(Formatting.WHITE))
					.append(Text.literal("Download it here:\n").formatted(Formatting.GRAY))
					.append(link(serverConfig.downloadUrl))
					.append(Text.literal("\n\nPut it in your mods folder with Fabric API, then rejoin.").formatted(Formatting.GRAY)));
		});
	}
}
