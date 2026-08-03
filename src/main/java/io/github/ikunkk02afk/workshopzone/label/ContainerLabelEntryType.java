package io.github.ikunkk02afk.workshopzone.label;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public enum ContainerLabelEntryType {
	ITEM("item"),
	ITEM_TAG("item_tag");

	private static final Map<Identifier, ContainerLabelEntryType> BY_ID = createMap();
	private final Identifier id;

	ContainerLabelEntryType(String path) {
		id = WorkshopZone.id(path);
	}

	public Identifier id() {
		return id;
	}

	public static Optional<ContainerLabelEntryType> fromId(Identifier id) {
		return Optional.ofNullable(BY_ID.get(id));
	}

	public static Map<Identifier, ContainerLabelEntryType> types() {
		return BY_ID;
	}

	private static Map<Identifier, ContainerLabelEntryType> createMap() {
		Map<Identifier, ContainerLabelEntryType> types = new LinkedHashMap<>();
		for (ContainerLabelEntryType type : values()) {
			if (types.putIfAbsent(type.id, type) != null) {
				throw new IllegalStateException("Duplicate container label entry type id " + type.id);
			}
		}
		return Map.copyOf(types);
	}
}
