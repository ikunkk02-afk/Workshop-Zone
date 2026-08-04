package io.github.ikunkk02afk.workshopzone.craft;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import net.minecraft.util.Identifier;

import java.util.Arrays;
import java.util.Optional;

public enum WorkshopCraftPreviewResultCode {
	AVAILABLE("available", null),
	NOT_NEEDED("not_needed", null),
	INSUFFICIENT("insufficient", "message.workshop_zone.craft.insufficient"),
	UNSUPPORTED_RECIPE("unsupported_recipe", "message.workshop_zone.craft.unsupported_recipe"),
	GRID_NOT_EMPTY("grid_not_empty", "message.workshop_zone.craft.grid_not_empty"),
	INVALID_SESSION("invalid_session", "message.workshop_zone.craft.no_session"),
	STALE_SESSION("stale_session", "message.workshop_zone.craft.no_session"),
	NO_ACCESSIBLE_CONTAINERS("no_accessible_containers", "message.workshop_zone.craft.no_accessible_containers"),
	DENIED("denied", "message.workshop_zone.craft.access_denied"),
	COOLDOWN("cooldown", "message.workshop_zone.craft.cooldown"),
	INTERNAL_ERROR("internal_error", "message.workshop_zone.craft.internal_error");

	private final Identifier id;
	private final String translationKey;

	WorkshopCraftPreviewResultCode(String path, String translationKey) {
		this.id = WorkshopZone.id(path);
		this.translationKey = translationKey;
	}

	public Identifier id() {
		return id;
	}

	public String translationKey() {
		return translationKey;
	}

	public boolean cancelsVanillaRequest() {
		return this == AVAILABLE || this == GRID_NOT_EMPTY;
	}

	public static Optional<WorkshopCraftPreviewResultCode> fromId(Identifier id) {
		return Arrays.stream(values()).filter(value -> value.id.equals(id)).findFirst();
	}
}
