package io.github.ikunkk02afk.workshopzone.search;

import net.minecraft.util.Identifier;

import java.util.Comparator;
import java.util.Objects;

public record WorkshopItemCatalogEntry(
	Identifier itemId,
	long totalCount,
	int matchingContainerCount,
	boolean multipleVariants
) {
	public static final int MAX_ITEM_ID_LENGTH = 256;
	public static final Comparator<WorkshopItemCatalogEntry> ORDER = Comparator
		.comparingLong(WorkshopItemCatalogEntry::totalCount).reversed()
		.thenComparing(Comparator.comparingInt(WorkshopItemCatalogEntry::matchingContainerCount).reversed())
		.thenComparing(entry -> entry.itemId().toString());

	public WorkshopItemCatalogEntry {
		Objects.requireNonNull(itemId, "itemId");
		String id = itemId.toString();
		if (Identifier.ofVanilla("air").equals(itemId) || id.length() > MAX_ITEM_ID_LENGTH
			|| totalCount <= 0 || matchingContainerCount <= 0) {
			throw new IllegalArgumentException("Invalid workshop item catalog entry");
		}
	}
}
