package io.github.ikunkk02afk.workshopzone.network;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import io.github.ikunkk02afk.workshopzone.label.ContainerItemTags;
import io.github.ikunkk02afk.workshopzone.label.ContainerTagCandidate;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record ItemTagCandidatesPayload(
	long sessionId,
	int syncId,
	long revision,
	Identifier itemId,
	ContainerLabelEditResult result,
	List<ContainerTagCandidate> candidates,
	boolean truncated
) implements CustomPayload {
	public static final Id<ItemTagCandidatesPayload> ID = new Id<>(WorkshopZone.id("item_tag_candidates"));
	public static final PacketCodec<RegistryByteBuf, ItemTagCandidatesPayload> CODEC = CustomPayload.codecOf(
		ItemTagCandidatesPayload::write,
		ItemTagCandidatesPayload::read
	);

	public ItemTagCandidatesPayload {
		Objects.requireNonNull(itemId, "itemId");
		Objects.requireNonNull(result, "result");
		candidates = List.copyOf(candidates);
		if (sessionId < 0 || syncId < 0 || revision < 0 || candidates.size() > ContainerItemTags.MAX_CANDIDATES) {
			throw new IllegalArgumentException("Invalid item-tag candidate response");
		}
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}

	private static void write(ItemTagCandidatesPayload payload, RegistryByteBuf buf) {
		buf.writeVarLong(payload.sessionId);
		buf.writeVarInt(payload.syncId);
		buf.writeVarLong(payload.revision);
		buf.writeIdentifier(payload.itemId);
		buf.writeIdentifier(payload.result.id());
		buf.writeBoolean(payload.truncated);
		buf.writeVarInt(payload.candidates.size());
		for (ContainerTagCandidate candidate : payload.candidates) {
			buf.writeIdentifier(candidate.tagId());
			buf.writeIdentifier(candidate.representativeItemId());
		}
	}

	private static ItemTagCandidatesPayload read(RegistryByteBuf buf) {
		long sessionId = buf.readVarLong();
		int syncId = buf.readVarInt();
		long revision = buf.readVarLong();
		Identifier itemId = buf.readIdentifier();
		Identifier resultId = buf.readIdentifier();
		ContainerLabelEditResult result = ContainerLabelEditResult.fromId(resultId)
			.orElseThrow(() -> new DecoderException("Unknown item-tag candidate result: " + resultId));
		boolean truncated = buf.readBoolean();
		int size = buf.readVarInt();
		if (size < 0 || size > ContainerItemTags.MAX_CANDIDATES) {
			throw new DecoderException("Item-tag candidate count exceeds limit: " + size);
		}
		List<ContainerTagCandidate> candidates = new ArrayList<>(size);
		for (int index = 0; index < size; index++) {
			candidates.add(new ContainerTagCandidate(buf.readIdentifier(), buf.readIdentifier()));
		}
		return new ItemTagCandidatesPayload(sessionId, syncId, revision, itemId, result, candidates, truncated);
	}
}
