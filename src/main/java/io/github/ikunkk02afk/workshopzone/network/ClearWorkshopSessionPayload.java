package io.github.ikunkk02afk.workshopzone.network;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

public record ClearWorkshopSessionPayload(long sessionId, int syncId) implements CustomPayload {
	public static final Id<ClearWorkshopSessionPayload> ID = new Id<>(WorkshopZone.id("clear_workshop_session"));
	public static final PacketCodec<RegistryByteBuf, ClearWorkshopSessionPayload> CODEC = CustomPayload.codecOf(
		(payload, buf) -> {
			buf.writeVarLong(payload.sessionId);
			buf.writeVarInt(payload.syncId);
		},
		buf -> new ClearWorkshopSessionPayload(buf.readVarLong(), buf.readVarInt())
	);

	public ClearWorkshopSessionPayload {
		if (sessionId < 0 || syncId < 0) {
			throw new IllegalArgumentException("Session and sync ids must be non-negative");
		}
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
