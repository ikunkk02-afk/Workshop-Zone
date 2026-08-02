package io.github.ikunkk02afk.workshopzone.label;

import net.minecraft.util.Identifier;

import java.util.Objects;
import java.util.Optional;

public record ContainerLabelSummary(
	ContainerLabelMode mode,
	Optional<Identifier> exactItemId,
	Optional<Identifier> itemTagId,
	Optional<Identifier> representativeItemId,
	boolean conflict,
	boolean unavailable
) {
	public static final ContainerLabelSummary NONE = new ContainerLabelSummary(
		ContainerLabelMode.NONE, Optional.empty(), Optional.empty(), Optional.empty(), false, false
	);
	public static final ContainerLabelSummary CONFLICT = new ContainerLabelSummary(
		ContainerLabelMode.NONE, Optional.empty(), Optional.empty(), Optional.empty(), true, false
	);

	public ContainerLabelSummary {
		Objects.requireNonNull(mode, "mode");
		exactItemId = Objects.requireNonNull(exactItemId, "exactItemId");
		itemTagId = Objects.requireNonNull(itemTagId, "itemTagId");
		representativeItemId = Objects.requireNonNull(representativeItemId, "representativeItemId");
		if ((mode == ContainerLabelMode.EXACT_ITEM) != exactItemId.isPresent()
			|| (mode == ContainerLabelMode.ITEM_TAG) != itemTagId.isPresent()
			|| exactItemId.isPresent() && itemTagId.isPresent()) {
			throw new IllegalArgumentException("Container label summary fields must match its stable mode");
		}
		if (mode == ContainerLabelMode.NONE && representativeItemId.isPresent()) {
			throw new IllegalArgumentException("None and rule-conflict summaries cannot expose a representative item");
		}
		if (mode == ContainerLabelMode.EXACT_ITEM
			&& (!representativeItemId.equals(exactItemId) || conflict || unavailable)) {
			throw new IllegalArgumentException("Exact-item summaries must expose the exact item without tag state flags");
		}
		if (unavailable && mode != ContainerLabelMode.ITEM_TAG) {
			throw new IllegalArgumentException("Only item-tag summaries can be unavailable");
		}
	}

	public static ContainerLabelSummary of(ContainerLabelRule rule) {
		return switch (rule.mode()) {
			case NONE -> NONE;
			case EXACT_ITEM -> new ContainerLabelSummary(
				ContainerLabelMode.EXACT_ITEM, rule.exactItemId(), Optional.empty(), rule.exactItemId(), false, false
			);
			case ITEM_TAG -> itemTag(rule, false);
		};
	}

	public static ContainerLabelSummary itemTag(ContainerLabelRule rule, boolean contentConflict) {
		Identifier tagId = rule.itemTagId().orElseThrow();
		boolean unavailable = ContainerItemTags.availability(tagId) != ContainerItemTags.Availability.AVAILABLE;
		return new ContainerLabelSummary(
			ContainerLabelMode.ITEM_TAG,
			Optional.empty(),
			Optional.of(tagId),
			unavailable ? Optional.empty() : ContainerItemTags.representativeItemId(tagId),
			!unavailable && contentConflict,
			unavailable
		);
	}

	public boolean hasLabel() {
		return mode != ContainerLabelMode.NONE;
	}

	public boolean blocksInput() {
		return conflict || unavailable;
	}

	public boolean contentConflict() {
		return conflict && mode == ContainerLabelMode.ITEM_TAG;
	}

	public boolean ruleConflict() {
		return conflict && mode == ContainerLabelMode.NONE;
	}
}
