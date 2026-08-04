package io.github.ikunkk02afk.workshopzone.network;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

public record RequestWorkshopItemCatalogPayload(
	long requestId,
	long sessionId,
	long revision,
	int syncId
) implements CustomPayload {
	public static final Id<RequestWorkshopItemCatalogPayload> ID = new Id<>(WorkshopZone.id("request_workshop_item_catalog"));
	public static final PacketCodec<RegistryByteBuf, RequestWorkshopItemCatalogPayload> CODEC = CustomPayload.codecOf(
		(payload, buf) -> {
			buf.writeVarLong(payload.requestId);
			buf.writeVarLong(payload.sessionId);
			buf.writeVarLong(payload.revision);
			buf.writeVarInt(payload.syncId);
		},
		buf -> new RequestWorkshopItemCatalogPayload(
			buf.readVarLong(), buf.readVarLong(), buf.readVarLong(), buf.readVarInt()
		)
	);

	public RequestWorkshopItemCatalogPayload {
		if (requestId < 0 || sessionId < 0 || revision < 0 || syncId < 0) {
			throw new IllegalArgumentException("Workshop item catalog request identity fields must be non-negative");
		}
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
