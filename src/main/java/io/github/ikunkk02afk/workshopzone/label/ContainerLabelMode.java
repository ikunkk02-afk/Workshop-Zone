package io.github.ikunkk02afk.workshopzone.label;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public enum ContainerLabelMode {
	NONE("none"),
	EXACT_ITEM("exact_item");

	private static final Map<Identifier, ContainerLabelMode> BY_ID = createIdMap();
	private final Identifier id;

	ContainerLabelMode(String path) {
		this.id = WorkshopZone.id(path);
	}

	public Identifier id() {
		return id;
	}

	public static Optional<ContainerLabelMode> fromId(Identifier id) {
		return Optional.ofNullable(BY_ID.get(id));
	}

	public static Map<Identifier, ContainerLabelMode> modes() {
		return BY_ID;
	}

	private static Map<Identifier, ContainerLabelMode> createIdMap() {
		Map<Identifier, ContainerLabelMode> modes = new LinkedHashMap<>();
		for (ContainerLabelMode mode : values()) {
			ContainerLabelMode previous = modes.putIfAbsent(mode.id, mode);
			if (previous != null) {
				throw new IllegalStateException("Duplicate container label mode id " + mode.id);
			}
		}
		return Map.copyOf(modes);
	}
}
