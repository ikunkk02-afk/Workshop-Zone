package io.github.ikunkk02afk.workshopzone.network;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public enum ContainerLabelEditResult {
	SUCCESS("success", "message.workshop_zone.label.success"),
	WHITELIST_SUCCESS("whitelist_success", "message.workshop_zone.label.whitelist_success"),
	CLEARED("cleared", "message.workshop_zone.label.cleared"),
	INVALID_SESSION("invalid_session", "message.workshop_zone.label.invalid_session"),
	STALE_SNAPSHOT("stale_snapshot", "message.workshop_zone.label.stale"),
	NOT_CONTAINER("not_container", "message.workshop_zone.label.not_container"),
	CHUNK_UNLOADED("chunk_unloaded", "message.workshop_zone.label.chunk_unloaded"),
	BLOCK_CHANGED("block_changed", "message.workshop_zone.label.block_changed"),
	INVALID_ITEM("invalid_item", "message.workshop_zone.label.invalid_item"),
	INVALID_TAG("invalid_tag", "message.workshop_zone.label.invalid_tag"),
	EMPTY_TAG("empty_tag", "message.workshop_zone.label.empty_tag"),
	TAG_UNAVAILABLE("tag_unavailable", "message.workshop_zone.label.tag_unavailable"),
	NO_MATCHING_TAGS("no_matching_tags", "message.workshop_zone.label.no_matching_tags"),
	TOO_MANY_CANDIDATES("too_many_candidates", "message.workshop_zone.label.too_many_candidates"),
	WHITELIST_EMPTY("whitelist_empty", "message.workshop_zone.label.whitelist_empty"),
	WHITELIST_TOO_LARGE("whitelist_too_large", "message.workshop_zone.label.whitelist_too_large"),
	DUPLICATE_ENTRY("duplicate_entry", "message.workshop_zone.label.duplicate_entry"),
	INVALID_ENTRY("invalid_entry", "message.workshop_zone.label.invalid_entry"),
	INCOMPATIBLE_WHITELIST_CONTENTS("incompatible_whitelist_contents", "message.workshop_zone.label.incompatible_whitelist_contents"),
	INCOMPATIBLE_CONTENTS("incompatible_contents", "message.workshop_zone.label.incompatible_contents"),
	LABEL_CONFLICT("label_conflict", "message.workshop_zone.label.conflict"),
	DENIED("denied", "message.workshop_zone.label.denied"),
	COOLDOWN("cooldown", "message.workshop_zone.label.cooldown"),
	INTERNAL_ERROR("internal_error", "message.workshop_zone.label.internal_error");

	private static final Map<Identifier, ContainerLabelEditResult> BY_ID = createMap();
	private final Identifier id;
	private final String translationKey;

	ContainerLabelEditResult(String path, String translationKey) {
		this.id = WorkshopZone.id(path);
		this.translationKey = translationKey;
	}

	public Identifier id() {
		return id;
	}

	public String translationKey() {
		return translationKey;
	}

	public static Optional<ContainerLabelEditResult> fromId(Identifier id) {
		return Optional.ofNullable(BY_ID.get(id));
	}

	private static Map<Identifier, ContainerLabelEditResult> createMap() {
		Map<Identifier, ContainerLabelEditResult> results = new LinkedHashMap<>();
		for (ContainerLabelEditResult result : values()) {
			if (results.putIfAbsent(result.id, result) != null) {
				throw new IllegalStateException("Duplicate container label result id " + result.id);
			}
		}
		return Map.copyOf(results);
	}
}
