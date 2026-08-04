package io.github.ikunkk02afk.workshopzone.search;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public enum WorkshopItemCatalogResultCode {
	SUCCESS("success", "message.workshop_zone.search.success"),
	EMPTY("empty", "gui.workshop_zone.search.catalog_empty"),
	INVALID_SESSION("invalid_session", "message.workshop_zone.search.catalog_invalid_session"),
	STALE_SESSION("stale_session", "message.workshop_zone.search.catalog_stale"),
	COOLDOWN("cooldown", "message.workshop_zone.search.catalog_cooldown"),
	NO_ACCESSIBLE_CONTAINERS("no_accessible_containers", "message.workshop_zone.search.no_accessible_containers"),
	DENIED("denied", "message.workshop_zone.search.catalog_denied"),
	INTERNAL_ERROR("internal_error", "message.workshop_zone.search.catalog_internal_error");

	private static final Map<Identifier, WorkshopItemCatalogResultCode> BY_ID = createMap();
	private final Identifier id;
	private final String translationKey;

	WorkshopItemCatalogResultCode(String path, String translationKey) {
		this.id = WorkshopZone.id(path);
		this.translationKey = translationKey;
	}

	public Identifier id() {
		return id;
	}

	public String translationKey() {
		return translationKey;
	}

	public static Optional<WorkshopItemCatalogResultCode> fromId(Identifier id) {
		return Optional.ofNullable(BY_ID.get(id));
	}

	private static Map<Identifier, WorkshopItemCatalogResultCode> createMap() {
		Map<Identifier, WorkshopItemCatalogResultCode> values = new LinkedHashMap<>();
		for (WorkshopItemCatalogResultCode value : values()) {
			if (values.putIfAbsent(value.id, value) != null) {
				throw new IllegalStateException("Duplicate workshop item catalog result id " + value.id);
			}
		}
		return Map.copyOf(values);
	}
}
