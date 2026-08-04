package io.github.ikunkk02afk.workshopzone.network;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import io.github.ikunkk02afk.workshopzone.search.WorkshopItemSearchContainerResult;
import io.github.ikunkk02afk.workshopzone.search.WorkshopItemSearchResultCode;
import io.github.ikunkk02afk.workshopzone.scan.WorkshopBlockType;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record WorkshopItemSearchResultPayload(
	long requestId,
	long sessionId,
	long revision,
	int syncId,
	WorkshopItemSearchResultCode resultId,
	Identifier targetItemId,
	long totalItemCount,
	int totalMatchingContainers,
	boolean truncated,
	List<WorkshopItemSearchContainerResult> results
) implements CustomPayload {
	public static final int MAX_RESULTS = 64;
	public static final Id<WorkshopItemSearchResultPayload> ID = new Id<>(WorkshopZone.id("workshop_item_search_result"));
	public static final PacketCodec<RegistryByteBuf, WorkshopItemSearchResultPayload> CODEC = CustomPayload.codecOf(
		WorkshopItemSearchResultPayload::write,
		WorkshopItemSearchResultPayload::read
	);

	public WorkshopItemSearchResultPayload {
		Objects.requireNonNull(resultId, "resultId");
		Objects.requireNonNull(targetItemId, "targetItemId");
		results = List.copyOf(results);
		if (requestId < 0 || sessionId < 0 || revision < 0 || syncId < 0 || totalItemCount < 0
			|| totalMatchingContainers < 0 || results.size() > MAX_RESULTS
			|| totalMatchingContainers < results.size()
			|| truncated != (totalMatchingContainers > results.size())) {
			throw new IllegalArgumentException("Invalid workshop item search result payload");
		}
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}

	private static void write(WorkshopItemSearchResultPayload payload, RegistryByteBuf buf) {
		buf.writeVarLong(payload.requestId);
		buf.writeVarLong(payload.sessionId);
		buf.writeVarLong(payload.revision);
		buf.writeVarInt(payload.syncId);
		buf.writeIdentifier(payload.resultId.id());
		buf.writeIdentifier(payload.targetItemId);
		buf.writeVarLong(payload.totalItemCount);
		buf.writeVarInt(payload.totalMatchingContainers);
		buf.writeBoolean(payload.truncated);
		buf.writeVarInt(payload.results.size());
		for (WorkshopItemSearchContainerResult result : payload.results) {
			buf.writeBlockPos(result.representativePosition());
			buf.writeIdentifier(result.containerType().networkId());
			buf.writeIdentifier(result.blockId());
			buf.writeVarInt(result.highlightPositions().size());
			result.highlightPositions().forEach(buf::writeBlockPos);
			buf.writeVarLong(result.containerItemCount());
			buf.writeVarInt(result.matchingSlotCount());
			buf.writeDouble(result.distanceSquared());
			buf.writeBoolean(result.multipleVariants());
		}
	}

	private static WorkshopItemSearchResultPayload read(RegistryByteBuf buf) {
		long requestId = buf.readVarLong();
		long sessionId = buf.readVarLong();
		long revision = buf.readVarLong();
		int syncId = buf.readVarInt();
		Identifier resultIdentifier = buf.readIdentifier();
		WorkshopItemSearchResultCode resultId = WorkshopItemSearchResultCode.fromId(resultIdentifier)
			.orElseThrow(() -> new DecoderException("Unknown workshop item search result " + resultIdentifier));
		Identifier targetItemId = buf.readIdentifier();
		long totalItemCount = buf.readVarLong();
		int totalMatchingContainers = buf.readVarInt();
		boolean truncated = buf.readBoolean();
		int resultCount = buf.readVarInt();
		if (resultCount < 0 || resultCount > MAX_RESULTS) {
			throw new DecoderException("Workshop item search result count exceeds limit: " + resultCount);
		}
		List<WorkshopItemSearchContainerResult> results = new ArrayList<>(resultCount);
		for (int index = 0; index < resultCount; index++) {
			BlockPos representative = buf.readBlockPos();
			Identifier containerTypeId = buf.readIdentifier();
			WorkshopBlockType containerType = WorkshopBlockType.fromNetworkId(containerTypeId)
				.filter(WorkshopBlockType::isContainer)
				.orElseThrow(() -> new DecoderException("Unknown workshop search container type: " + containerTypeId));
			Identifier blockId = buf.readIdentifier();
			int highlightCount = buf.readVarInt();
			if (highlightCount < 1 || highlightCount > 2) {
				throw new DecoderException("Invalid workshop highlight position count: " + highlightCount);
			}
			List<BlockPos> highlights = new ArrayList<>(highlightCount);
			for (int highlight = 0; highlight < highlightCount; highlight++) {
				highlights.add(buf.readBlockPos());
			}
			try {
				results.add(new WorkshopItemSearchContainerResult(
					containerType, blockId, representative, highlights, buf.readVarLong(), buf.readVarInt(), buf.readDouble(),
					buf.readBoolean(), index
				));
			} catch (IllegalArgumentException exception) {
				throw new DecoderException("Invalid workshop item search container result", exception);
			}
		}
		try {
			return new WorkshopItemSearchResultPayload(
				requestId, sessionId, revision, syncId, resultId, targetItemId,
				totalItemCount, totalMatchingContainers, truncated, results
			);
		} catch (IllegalArgumentException exception) {
			throw new DecoderException("Invalid workshop item search result payload", exception);
		}
	}
}
