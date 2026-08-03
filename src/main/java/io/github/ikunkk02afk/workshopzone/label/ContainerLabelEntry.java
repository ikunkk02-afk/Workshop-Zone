package io.github.ikunkk02afk.workshopzone.label;

import net.minecraft.util.Identifier;

import java.util.Comparator;
import java.util.Objects;

public record ContainerLabelEntry(ContainerLabelEntryType type, Identifier valueId) implements Comparable<ContainerLabelEntry> {
	public static final int MAX_IDENTIFIER_LENGTH = 256;
	private static final Identifier AIR_ID = Identifier.ofVanilla("air");
	public static final Comparator<ContainerLabelEntry> ORDER = Comparator
		.comparingInt((ContainerLabelEntry entry) -> entry.type == ContainerLabelEntryType.ITEM ? 0 : 1)
		.thenComparing(entry -> entry.valueId.toString());

	public ContainerLabelEntry {
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(valueId, "valueId");
		if (valueId.toString().length() > MAX_IDENTIFIER_LENGTH) {
			throw new IllegalArgumentException("Container label identifiers cannot exceed " + MAX_IDENTIFIER_LENGTH + " characters");
		}
		if (type == ContainerLabelEntryType.ITEM && AIR_ID.equals(valueId)) {
			throw new IllegalArgumentException("minecraft:air cannot be a container label entry");
		}
	}

	public static ContainerLabelEntry item(Identifier itemId) {
		return new ContainerLabelEntry(ContainerLabelEntryType.ITEM, itemId);
	}

	public static ContainerLabelEntry itemTag(Identifier tagId) {
		return new ContainerLabelEntry(ContainerLabelEntryType.ITEM_TAG, tagId);
	}

	@Override
	public int compareTo(ContainerLabelEntry other) {
		return ORDER.compare(this, other);
	}
}
