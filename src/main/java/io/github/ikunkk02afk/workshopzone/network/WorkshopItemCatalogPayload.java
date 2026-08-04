package io.github.ikunkk02afk.workshopzone.network;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import io.github.ikunkk02afk.workshopzone.search.WorkshopItemCatalog;
import io.github.ikunkk02afk.workshopzone.search.WorkshopItemCatalogEntry;
import io.github.ikunkk02afk.workshopzone.search.WorkshopItemCatalogResultCode;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record WorkshopItemCatalogPayload(
	long requestId,
	long sessionId,
	long revision,
	int syncId,
	WorkshopItemCatalogResultCode resultId,
	int totalDistinctItems,
	boolean truncated,
	List<WorkshopItemCatalogEntry> entries
) implements CustomPayload {
	public static final int MAX_CATALOG_ENTRIES = WorkshopItemCatalog.MAX_CATALOG_ENTRIES;
	public static final int MAX_IDENTIFIER_LENGTH = WorkshopItemCatalogEntry.MAX_ITEM_ID_LENGTH;
	public static final Id<WorkshopItemCatalogPayload> ID = new Id<>(WorkshopZone.id("workshop_item_catalog"));
	public static final PacketCodec<RegistryByteBuf, WorkshopItemCatalogPayload> CODEC = CustomPayload.codecOf(
		WorkshopItemCatalogPayload::write,
		WorkshopItemCatalogPayload::read
	);

	public WorkshopItemCatalogPayload {
		Objects.requireNonNull(resultId, "resultId");
		entries = List.copyOf(entries);
		if (requestId < 0 || sessionId < 0 || revision < 0 || syncId < 0 || totalDistinctItems < 0
			|| entries.size() > MAX_CATALOG_ENTRIES || totalDistinctItems < entries.size()
			|| truncated != (totalDistinctItems > entries.size())) {
			throw new IllegalArgumentException("Invalid workshop item catalog payload");
		}
		WorkshopItemCatalog validated = new WorkshopItemCatalog(entries, totalDistinctItems, truncated);
		entries = validated.entries();
		boolean emptyResult = resultId != WorkshopItemCatalogResultCode.SUCCESS;
		if (emptyResult != entries.isEmpty() || emptyResult && (totalDistinctItems != 0 || truncated)) {
			throw new IllegalArgumentException("Workshop item catalog result does not match its entries");
		}
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}

	private static void write(WorkshopItemCatalogPayload payload, RegistryByteBuf buf) {
		buf.writeVarLong(payload.requestId);
		buf.writeVarLong(payload.sessionId);
		buf.writeVarLong(payload.revision);
		buf.writeVarInt(payload.syncId);
		buf.writeString(payload.resultId.id().toString(), MAX_IDENTIFIER_LENGTH);
		buf.writeVarInt(payload.totalDistinctItems);
		buf.writeBoolean(payload.truncated);
		buf.writeVarInt(payload.entries.size());
		for (WorkshopItemCatalogEntry entry : payload.entries) {
			buf.writeString(entry.itemId().toString(), MAX_IDENTIFIER_LENGTH);
			buf.writeVarLong(entry.totalCount());
			buf.writeVarInt(entry.matchingContainerCount());
			buf.writeBoolean(entry.multipleVariants());
		}
	}

	private static WorkshopItemCatalogPayload read(RegistryByteBuf buf) {
		long requestId = buf.readVarLong();
		long sessionId = buf.readVarLong();
		long revision = buf.readVarLong();
		int syncId = buf.readVarInt();
		Identifier resultIdentifier = readIdentifier(buf, "catalog result");
		WorkshopItemCatalogResultCode resultId = WorkshopItemCatalogResultCode.fromId(resultIdentifier)
			.orElseThrow(() -> new DecoderException("Unknown workshop item catalog result " + resultIdentifier));
		int totalDistinctItems = buf.readVarInt();
		boolean truncated = buf.readBoolean();
		int entryCount = buf.readVarInt();
		if (entryCount < 0 || entryCount > MAX_CATALOG_ENTRIES) {
			throw new DecoderException("Workshop item catalog entry count exceeds limit: " + entryCount);
		}
		List<WorkshopItemCatalogEntry> entries = new ArrayList<>(entryCount);
		for (int index = 0; index < entryCount; index++) {
			try {
				entries.add(new WorkshopItemCatalogEntry(
					readIdentifier(buf, "catalog item"), buf.readVarLong(), buf.readVarInt(), buf.readBoolean()
				));
			} catch (IllegalArgumentException exception) {
				throw new DecoderException("Invalid workshop item catalog entry", exception);
			}
		}
		try {
			return new WorkshopItemCatalogPayload(
				requestId, sessionId, revision, syncId, resultId, totalDistinctItems, truncated, entries
			);
		} catch (IllegalArgumentException exception) {
			throw new DecoderException("Invalid workshop item catalog payload", exception);
		}
	}

	private static Identifier readIdentifier(RegistryByteBuf buf, String field) {
		String value = buf.readString(MAX_IDENTIFIER_LENGTH);
		Identifier id = Identifier.tryParse(value);
		if (id == null) {
			throw new DecoderException("Invalid workshop item " + field + " identifier " + value);
		}
		return id;
	}
}
