package net.lumen;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.lumen.config.LumenConfig;
import net.lumen.fx.AmbientFx;
import net.lumen.fx.Atmosphere;
import net.lumen.fx.Env;
import net.lumen.fx.Fx;
import net.lumen.fx.WaterFx;
import net.lumen.fx.WaterfallFx;
import net.lumen.fx.WeatherFx;
import net.lumen.gui.LumenScreen;
import net.lumen.net.LumenHelloPayload;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.lumen.fx.FireFx;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Lumen: purely visual. Nothing here touches game logic, packets, or the server.
 * Settings: type /lumen in chat.
 */
public class LumenClient implements ClientModInitializer {
	public static final String MOD_ID = "lumen";
	public static final Identifier CINEMATIC = Identifier.of(MOD_ID, "cinematic");
	public static final Env ENV = new Env();

	/** Built-in resource packs for the terrain-shader effects (toggle in /lumen or Options > Resource Packs). */
	public static final String[] PACKS = {"lumen_foliage", "lumen_water", "lumen_lighting", "lumen_fog"};
	public static final String[] PACK_NAMES = {"Swaying grass & foliage", "Sky colors on water", "Warm torchlight & blue moonlight", "Soft fog"};

	private static boolean openScreen;
	private static KeyBinding settingsKey;

	@Override
	public void onInitializeClient() {
		LumenConfig.load();

		// Listening on this channel tells Lumen servers that we have the mod
		ClientConfigurationNetworking.registerGlobalReceiver(LumenHelloPayload.ID, (payload, context) -> {});

		ModContainer mod = FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow();
		for (int i = 0; i < PACKS.length; i++) {
			ResourceManagerHelper.registerBuiltinResourcePack(Identifier.of(MOD_ID, PACKS[i]), mod,
					Text.literal("Lumen: " + PACK_NAMES[i]), ResourcePackActivationType.DEFAULT_ENABLED);
		}

		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			Atmosphere.init(client);
			ShadowMap.init(client);
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) ->
				dispatcher.register(ClientCommandManager.literal("lumen")
						.executes(ctx -> {
							openScreen = true;
							return 1;
						})
						.then(ClientCommandManager.literal("breeze").executes(ctx -> {
							Breeze.start(1f, true);
							return 1;
						}))));

		settingsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.lumen.settings", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F6,
				KeyBinding.Category.create(Identifier.of(MOD_ID, "main"))));

		ClientTickEvents.END_CLIENT_TICK.register(LumenClient::tick);
		ClientTickEvents.END_CLIENT_TICK.register(Breeze::tick);
	}

	private static void tick(MinecraftClient client) {
		FullbrightBlocker.tick(client);
		CozyLight.tick(client);
		while (settingsKey != null && settingsKey.wasPressed()) {
			if (client.currentScreen == null) openScreen = true;
		}
		if (openScreen) {
			openScreen = false;
			client.setScreen(new LumenScreen(null));
		}
		if (client.world == null || client.player == null) {
			DynamicLights.tick(client);
			return;
		}

		Fx.beginTick(client);
		ENV.update(client, client.world);
		DynamicLights.tick(client);

		if (!client.isPaused()) {
			WaterFx.tick(client, client.world);
			WaterfallFx.tick(client, client.world);
			AmbientFx.tick(client, client.world, ENV);
			WeatherFx.tick(client, client.world, ENV);
			FireFx.tick(client, client.world);
		}
		// fallback in case the per-frame hook isn't running
		Atmosphere.update(client, ENV);
	}
}
