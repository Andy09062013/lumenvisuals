package net.lumen.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.network.ServerConfigurationNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Who is joining, so the server can tell them about Lumen once they're in. */
@Mixin(ServerConfigurationNetworkHandler.class)
public interface ServerConfigHandlerAccessor {
	@Invoker("getProfile")
	GameProfile lumen$getProfile();
}
