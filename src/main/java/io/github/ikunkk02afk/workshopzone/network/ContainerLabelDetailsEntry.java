package io.github.ikunkk02afk.workshopzone.network;

import io.github.ikunkk02afk.workshopzone.label.ContainerLabelEntry;
import io.github.ikunkk02afk.workshopzone.label.ContainerLabelEntryType;
import net.minecraft.util.Identifier;

import java.util.Objects;
import java.util.Optional;

public record ContainerLabelDetailsEntry(
	ContainerLabelEntry entry,
	boolean unavailable,
	Optional<Identifier> representativeItemId
) {
	public ContainerLabelDetailsEntry {
		Objects.requireNonNull(entry, "entry");
		representativeItemId = Objects.requireNonNull(representativeItemId, "representativeItemId");
		if (entry.type() == ContainerLabelEntryType.ITEM
			&& (unavailable || !representativeItemId.equals(Optional.of(entry.valueId())))) {
			throw new IllegalArgumentException("Item detail entries must use their own item as an available representative");
		}
		if (unavailable && representativeItemId.isPresent()) {
			throw new IllegalArgumentException("Unavailable detail entries cannot have a representative item");
		}
	}
}
