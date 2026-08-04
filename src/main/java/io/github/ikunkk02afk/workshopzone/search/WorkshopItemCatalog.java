package io.github.ikunkk02afk.workshopzone.search;

import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record WorkshopItemCatalog(
	List<WorkshopItemCatalogEntry> entries,
	int totalDistinctItems,
	boolean truncated
) {
	public static final int MAX_CATALOG_ENTRIES = 4096;

	public WorkshopItemCatalog {
		entries = List.copyOf(entries);
		if (totalDistinctItems < 0 || entries.size() > MAX_CATALOG_ENTRIES
			|| totalDistinctItems < entries.size() || truncated != (totalDistinctItems > entries.size())) {
			throw new IllegalArgumentException("Invalid workshop item catalog");
		}
		Set<Identifier> ids = new HashSet<>();
		WorkshopItemCatalogEntry previous = null;
		for (WorkshopItemCatalogEntry entry : entries) {
			Objects.requireNonNull(entry, "entry");
			if (!ids.add(entry.itemId()) || previous != null && WorkshopItemCatalogEntry.ORDER.compare(previous, entry) > 0) {
				throw new IllegalArgumentException("Workshop item catalog entries must be unique and deterministically sorted");
			}
			previous = entry;
		}
	}

	public static WorkshopItemCatalog empty() {
		return new WorkshopItemCatalog(List.of(), 0, false);
	}

	public static WorkshopItemCatalog fromEntries(List<WorkshopItemCatalogEntry> source, int maxEntries) {
		Objects.requireNonNull(source, "source");
		if (maxEntries < 0 || maxEntries > MAX_CATALOG_ENTRIES) {
			throw new IllegalArgumentException("Invalid workshop item catalog limit");
		}
		List<WorkshopItemCatalogEntry> sorted = new ArrayList<>(source);
		sorted.sort(WorkshopItemCatalogEntry.ORDER);
		Set<Identifier> ids = new HashSet<>();
		for (WorkshopItemCatalogEntry entry : sorted) {
			Objects.requireNonNull(entry, "entry");
			if (!ids.add(entry.itemId())) {
				throw new IllegalArgumentException("Duplicate workshop item catalog entry " + entry.itemId());
			}
		}
		int totalDistinctItems = sorted.size();
		return new WorkshopItemCatalog(
			List.copyOf(sorted.subList(0, Math.min(totalDistinctItems, maxEntries))),
			totalDistinctItems,
			totalDistinctItems > maxEntries
		);
	}
}
