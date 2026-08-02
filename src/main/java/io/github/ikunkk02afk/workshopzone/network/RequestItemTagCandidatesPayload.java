package io.github.ikunkk02afk.workshopzone.network;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.Objects;

public record RequestItemTagCandidatesPayload(
	long sessionId,
	int syncId,
	long revision,
	Identifier itemId
) implements CustomPayload {
	public static final Id<RequestItemTagCandidatesPayload> ID = new Id<>(WorkshopZone.id("request_item_tag_candidates"));
	public static final PacketCodec<RegistryByteBuf, RequestItemTagCandidatesPayload> CODEC = CustomPayload.codecOf(
		RequestItemTagCandidatesPayload::write,
		RequestItemTagCandidatesPayload::read
	);

	public RequestItemTagCandidatesPayload {
		Objects.requireNonNull(itemId, "itemId");
		if (sessionId < 0 || syncId < 0 || revision < 0) {
			throw new IllegalArgumentException("Invalid item-tag candidate request");
		}
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}

	private static void write(RequestItemTagCandidatesPayload payload, RegistryByteBuf buf) {
		buf.writeVarLong(payload.sessionId);
		buf.writeVarInt(payload.syncId);
		buf.writeVarLong(payload.revision);
		buf.writeIdentifier(payload.itemId);
	}

	private static RequestItemTagCandidatesPayload read(RegistryByteBuf buf) {
		return new RequestItemTagCandidatesPayload(
			buf.readVarLong(), buf.readVarInt(), buf.readVarLong(), buf.readIdentifier()
		);
	}
}
