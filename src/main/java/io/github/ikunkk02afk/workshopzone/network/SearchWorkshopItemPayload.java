package io.github.ikunkk02afk.workshopzone.network;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.Objects;

public record SearchWorkshopItemPayload(
	long requestId,
	long sessionId,
	long revision,
	int syncId,
	Identifier targetItemId
) implements CustomPayload {
	public static final Id<SearchWorkshopItemPayload> ID = new Id<>(WorkshopZone.id("search_workshop_item"));
	public static final PacketCodec<RegistryByteBuf, SearchWorkshopItemPayload> CODEC = CustomPayload.codecOf(
		(payload, buf) -> {
			buf.writeVarLong(payload.requestId);
			buf.writeVarLong(payload.sessionId);
			buf.writeVarLong(payload.revision);
			buf.writeVarInt(payload.syncId);
			buf.writeIdentifier(payload.targetItemId);
		},
		buf -> new SearchWorkshopItemPayload(
			buf.readVarLong(), buf.readVarLong(), buf.readVarLong(), buf.readVarInt(), buf.readIdentifier()
		)
	);

	public SearchWorkshopItemPayload {
		Objects.requireNonNull(targetItemId, "targetItemId");
		if (requestId < 0 || sessionId < 0 || revision < 0 || syncId < 0) {
			throw new IllegalArgumentException("Workshop item search identity fields must be non-negative");
		}
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
