package io.github.ikunkk02afk.workshopzone.search;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public enum WorkshopItemSearchResultCode {
	SUCCESS("success", "message.workshop_zone.search.success"),
	NOT_FOUND("not_found", "message.workshop_zone.search.not_found"),
	INVALID_SESSION("invalid_session", "message.workshop_zone.search.invalid_session"),
	STALE_SESSION("stale_session", "message.workshop_zone.search.stale_session"),
	INVALID_ITEM("invalid_item", "message.workshop_zone.search.invalid_item"),
	COOLDOWN("cooldown", "message.workshop_zone.search.cooldown"),
	NO_ACCESSIBLE_CONTAINERS("no_accessible_containers", "message.workshop_zone.search.no_accessible_containers"),
	DENIED("denied", "message.workshop_zone.search.denied"),
	INTERNAL_ERROR("internal_error", "message.workshop_zone.search.internal_error");

	private static final Map<Identifier, WorkshopItemSearchResultCode> BY_ID = createMap();
	private final Identifier id;
	private final String translationKey;

	WorkshopItemSearchResultCode(String path, String translationKey) {
		this.id = WorkshopZone.id(path);
		this.translationKey = translationKey;
	}

	public Identifier id() {
		return id;
	}

	public String translationKey() {
		return translationKey;
	}

	public static Optional<WorkshopItemSearchResultCode> fromId(Identifier id) {
		return Optional.ofNullable(BY_ID.get(id));
	}

	private static Map<Identifier, WorkshopItemSearchResultCode> createMap() {
		Map<Identifier, WorkshopItemSearchResultCode> values = new LinkedHashMap<>();
		for (WorkshopItemSearchResultCode value : values()) {
			if (values.putIfAbsent(value.id, value) != null) {
				throw new IllegalStateException("Duplicate workshop item search result id " + value.id);
			}
		}
		return Map.copyOf(values);
	}
}
