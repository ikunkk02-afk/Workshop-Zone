package io.github.ikunkk02afk.workshopzone.network;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

public record ConfirmWorkshopCraftPayload(long previewId, boolean accept) implements CustomPayload {
	public static final Id<ConfirmWorkshopCraftPayload> ID = new Id<>(WorkshopZone.id("confirm_workshop_craft"));
	public static final PacketCodec<RegistryByteBuf, ConfirmWorkshopCraftPayload> CODEC = CustomPayload.codecOf(
		(payload, buf) -> {
			buf.writeVarLong(payload.previewId);
			buf.writeBoolean(payload.accept);
		},
		buf -> new ConfirmWorkshopCraftPayload(buf.readVarLong(), buf.readBoolean())
	);

	public ConfirmWorkshopCraftPayload {
		if (previewId <= 0) {
			throw new IllegalArgumentException("Workshop crafting preview id must be positive");
		}
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
