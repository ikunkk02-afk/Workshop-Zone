package io.github.ikunkk02afk.workshopzone.deposit;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public enum WorkshopDepositResult {
	SUCCESS("success", "message.workshop_zone.deposit.success"),
	PARTIAL("partial", "message.workshop_zone.deposit.partial"),
	NOTHING_TO_MOVE("nothing_to_move", "message.workshop_zone.deposit.nothing_to_move"),
	NO_LABELED_CONTAINERS("no_labeled_containers", "message.workshop_zone.deposit.no_labeled_containers"),
	NO_VALID_DESTINATIONS("no_valid_destinations", "message.workshop_zone.deposit.no_labeled_containers"),
	NO_SPACE("no_space", "message.workshop_zone.deposit.no_space"),
	INVALID_SESSION("invalid_session", "message.workshop_zone.deposit.invalid_session"),
	STALE_SNAPSHOT("stale_snapshot", "message.workshop_zone.deposit.stale_snapshot"),
	COOLDOWN("cooldown", "message.workshop_zone.deposit.cooldown"),
	DENIED("denied", "message.workshop_zone.deposit.denied"),
	INTERNAL_ERROR("internal_error", "message.workshop_zone.deposit.internal_error");

	private static final Map<Identifier, WorkshopDepositResult> BY_ID = createMap();
	private final Identifier id;
	private final String translationKey;

	WorkshopDepositResult(String path, String translationKey) {
		this.id = WorkshopZone.id("deposit/" + path);
		this.translationKey = translationKey;
	}

	public Identifier id() {
		return id;
	}

	public String translationKey() {
		return translationKey;
	}

	public static Optional<WorkshopDepositResult> fromId(Identifier id) {
		return Optional.ofNullable(BY_ID.get(id));
	}

	private static Map<Identifier, WorkshopDepositResult> createMap() {
		Map<Identifier, WorkshopDepositResult> results = new LinkedHashMap<>();
		for (WorkshopDepositResult result : values()) {
			if (results.putIfAbsent(result.id, result) != null) {
				throw new IllegalStateException("Duplicate workshop deposit result id " + result.id);
			}
		}
		return Map.copyOf(results);
	}
}
