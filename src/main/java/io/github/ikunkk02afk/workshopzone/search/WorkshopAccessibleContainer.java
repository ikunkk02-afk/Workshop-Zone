package io.github.ikunkk02afk.workshopzone.search;

import io.github.ikunkk02afk.workshopzone.label.LogicalContainer;
import io.github.ikunkk02afk.workshopzone.scan.WorkshopBlockEntry;

import java.util.Objects;

public record WorkshopAccessibleContainer(
	WorkshopBlockEntry entry,
	LogicalContainer container,
	double distanceSquared,
	int scanIndex
) {
	public WorkshopAccessibleContainer {
		Objects.requireNonNull(entry, "entry");
		Objects.requireNonNull(container, "container");
		if (!entry.position().equals(container.representativePosition()) || entry.type() != container.type()
			|| !Double.isFinite(distanceSquared) || distanceSquared < 0 || scanIndex < 0) {
			throw new IllegalArgumentException("Invalid accessible workshop container");
		}
	}
}
