package io.github.ikunkk02afk.workshopzone.network;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

public record DepositWorkshopItemsPayload(
	long requestId,
	long sessionId,
	long revision,
	int syncId,
	boolean includeHotbar
) implements CustomPayload {
	public static final Id<DepositWorkshopItemsPayload> ID = new Id<>(WorkshopZone.id("deposit_workshop_items"));
	public static final PacketCodec<RegistryByteBuf, DepositWorkshopItemsPayload> CODEC = CustomPayload.codecOf(
		(payload, buf) -> {
			buf.writeVarLong(payload.requestId);
			buf.writeVarLong(payload.sessionId);
			buf.writeVarLong(payload.revision);
			buf.writeVarInt(payload.syncId);
			buf.writeBoolean(payload.includeHotbar);
		},
		buf -> new DepositWorkshopItemsPayload(
			buf.readVarLong(), buf.readVarLong(), buf.readVarLong(), buf.readVarInt(), buf.readBoolean()
		)
	);

	public DepositWorkshopItemsPayload {
		if (requestId < 0 || sessionId < 0 || revision < 0 || syncId < 0) {
			throw new IllegalArgumentException("Workshop deposit identity fields must be non-negative");
		}
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
