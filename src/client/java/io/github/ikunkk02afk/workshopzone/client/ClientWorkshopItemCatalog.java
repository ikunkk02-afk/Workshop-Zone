package io.github.ikunkk02afk.workshopzone.client;

import io.github.ikunkk02afk.workshopzone.network.WorkshopItemCatalogPayload;
import io.github.ikunkk02afk.workshopzone.search.WorkshopItemCatalogEntry;
import io.github.ikunkk02afk.workshopzone.search.WorkshopItemCatalogResultCode;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Objects;

public record ClientWorkshopItemCatalog(
	long requestId,
	long sessionId,
	long revision,
	int syncId,
	WorkshopItemCatalogResultCode resultId,
	int totalDistinctItems,
	boolean truncated,
	List<WorkshopItemCatalogEntry> entries
) {
	public ClientWorkshopItemCatalog {
		Objects.requireNonNull(resultId, "resultId");
		entries = List.copyOf(entries);
	}

	public static ClientWorkshopItemCatalog fromPayload(WorkshopItemCatalogPayload payload) {
		Objects.requireNonNull(payload, "payload");
		return new ClientWorkshopItemCatalog(
			payload.requestId(), payload.sessionId(), payload.revision(), payload.syncId(),
			payload.resultId(), payload.totalDistinctItems(), payload.truncated(), payload.entries()
		);
	}

	public boolean contains(Identifier itemId) {
		return entries.stream().anyMatch(entry -> entry.itemId().equals(itemId));
	}
}
