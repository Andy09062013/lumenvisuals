package net.lumen.net;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Empty marker packet. A client with Lumen installed listens on this channel,
 * which is how a server running Lumen can tell whether the joining player has it.
 */
public record LumenHelloPayload() implements CustomPayload {
	public static final LumenHelloPayload INSTANCE = new LumenHelloPayload();
	public static final CustomPayload.Id<LumenHelloPayload> ID = new CustomPayload.Id<>(Identifier.of("lumen", "hello"));
	public static final PacketCodec<PacketByteBuf, LumenHelloPayload> CODEC = PacketCodec.unit(INSTANCE);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
