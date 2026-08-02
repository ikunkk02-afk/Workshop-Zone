package io.github.ikunkk02afk.workshopzone.network;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

public record RequestWorkshopRefreshPayload(long sessionId, int syncId) implements CustomPayload {
	public static final Id<RequestWorkshopRefreshPayload> ID = new Id<>(WorkshopZone.id("request_workshop_refresh"));
	public static final PacketCodec<RegistryByteBuf, RequestWorkshopRefreshPayload> CODEC = CustomPayload.codecOf(
		(payload, buf) -> {
			buf.writeVarLong(payload.sessionId);
			buf.writeVarInt(payload.syncId);
		},
		buf -> new RequestWorkshopRefreshPayload(buf.readVarLong(), buf.readVarInt())
	);

	public RequestWorkshopRefreshPayload {
		if (sessionId < 0 || syncId < 0) {
			throw new IllegalArgumentException("Session and sync ids must be non-negative");
		}
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
