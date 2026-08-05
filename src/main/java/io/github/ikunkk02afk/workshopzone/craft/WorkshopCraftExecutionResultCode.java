package io.github.ikunkk02afk.workshopzone.craft;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import net.minecraft.util.Identifier;

import java.util.Arrays;
import java.util.Optional;

public enum WorkshopCraftExecutionResultCode {
	SUCCESS("success", "message.workshop_zone.craft.success"),
	CANCELLED("cancelled", "message.workshop_zone.craft.cancelled"),
	EXPIRED("expired", "message.workshop_zone.craft.preview_expired"),
	INVALID_CONFIRMATION("invalid_confirmation", "message.workshop_zone.craft.preview_expired"),
	STALE_SESSION("stale_session", "message.workshop_zone.craft.no_session"),
	GRID_CHANGED("grid_changed", "message.workshop_zone.craft.grid_not_empty"),
	RECIPE_CHANGED("recipe_changed", "message.workshop_zone.craft.unsupported_recipe"),
	MATERIALS_CHANGED("materials_changed", "message.workshop_zone.craft.materials_changed"),
	BATCH_CHANGED("batch_changed", "message.workshop_zone.craft.batch_changed"),
	ACCESS_DENIED("access_denied", "message.workshop_zone.craft.access_denied"),
	TRANSACTION_FAILED("transaction_failed", "message.workshop_zone.craft.transaction_failed"),
	INTERNAL_ERROR("internal_error", "message.workshop_zone.craft.internal_error");

	private final Identifier id;
	private final String translationKey;

	WorkshopCraftExecutionResultCode(String path, String translationKey) {
		this.id = WorkshopZone.id(path);
		this.translationKey = translationKey;
	}

	public Identifier id() {
		return id;
	}

	public String translationKey() {
		return translationKey;
	}

	public static Optional<WorkshopCraftExecutionResultCode> fromId(Identifier id) {
		return Arrays.stream(values()).filter(value -> value.id.equals(id)).findFirst();
	}
}
