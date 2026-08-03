package io.github.ikunkk02afk.workshopzone.network;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public enum ContainerLabelOperation {
	SET_EXACT_ITEM("set_exact_item"),
	SET_ITEM_TAG("set_item_tag"),
	SET_WHITELIST("set_whitelist"),
	CLEAR("clear");

	private static final Map<Identifier, ContainerLabelOperation> BY_ID = createMap();
	private final Identifier id;

	ContainerLabelOperation(String path) {
		id = WorkshopZone.id(path);
	}

	public Identifier id() {
		return id;
	}

	public static Optional<ContainerLabelOperation> fromId(Identifier id) {
		return Optional.ofNullable(BY_ID.get(id));
	}

	private static Map<Identifier, ContainerLabelOperation> createMap() {
		Map<Identifier, ContainerLabelOperation> values = new LinkedHashMap<>();
		for (ContainerLabelOperation operation : values()) {
			if (values.putIfAbsent(operation.id, operation) != null) {
				throw new IllegalStateException("Duplicate container label operation id " + operation.id);
			}
		}
		return Map.copyOf(values);
	}
}
