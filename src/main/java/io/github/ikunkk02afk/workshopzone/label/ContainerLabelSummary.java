package io.github.ikunkk02afk.workshopzone.label;

import net.minecraft.util.Identifier;

import java.util.Objects;
import java.util.Optional;

public record ContainerLabelSummary(
	ContainerLabelMode mode,
	Optional<Identifier> exactItemId,
	Optional<Identifier> itemTagId,
	Optional<Identifier> representativeItemId,
	int whitelistEntryCount,
	int unavailableEntryCount,
	boolean conflict,
	boolean unavailable
) {
	public static final ContainerLabelSummary NONE = new ContainerLabelSummary(
		ContainerLabelMode.NONE, Optional.empty(), Optional.empty(), Optional.empty(), 0, 0, false, false
	);
	public static final ContainerLabelSummary CONFLICT = new ContainerLabelSummary(
		ContainerLabelMode.NONE, Optional.empty(), Optional.empty(), Optional.empty(), 0, 0, true, false
	);

	public ContainerLabelSummary(
		ContainerLabelMode mode,
		Optional<Identifier> exactItemId,
		Optional<Identifier> itemTagId,
		Optional<Identifier> representativeItemId,
		boolean conflict,
		boolean unavailable
	) {
		this(
			mode, exactItemId, itemTagId, representativeItemId,
			mode == ContainerLabelMode.WHITELIST ? 1 : 0,
			mode == ContainerLabelMode.ITEM_TAG && unavailable ? 1 : 0,
			conflict, unavailable
		);
	}

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
		if ((mode == ContainerLabelMode.WHITELIST) != (whitelistEntryCount > 0)) {
			throw new IllegalArgumentException("Whitelist summary count must match its mode");
		}
		if (whitelistEntryCount < 0 || whitelistEntryCount > ContainerLabelRule.MAX_ENTRIES || unavailableEntryCount < 0
			|| mode == ContainerLabelMode.WHITELIST && unavailableEntryCount > whitelistEntryCount) {
			throw new IllegalArgumentException("Invalid container label summary entry counts");
		}
		if (mode == ContainerLabelMode.ITEM_TAG && unavailableEntryCount != (unavailable ? 1 : 0)
			|| mode == ContainerLabelMode.NONE && unavailableEntryCount != 0) {
			throw new IllegalArgumentException("Unavailable entry count must match the summary mode");
		}
		if (mode == ContainerLabelMode.NONE && representativeItemId.isPresent()) {
			throw new IllegalArgumentException("None and rule-conflict summaries cannot expose a representative item");
		}
		if (mode == ContainerLabelMode.EXACT_ITEM
			&& (!representativeItemId.equals(exactItemId) || conflict || unavailable || unavailableEntryCount != 0)) {
			throw new IllegalArgumentException("Exact-item summaries must expose the exact item without state flags");
		}
		if (unavailable && (mode != ContainerLabelMode.ITEM_TAG && mode != ContainerLabelMode.WHITELIST || conflict)) {
			throw new IllegalArgumentException("Unavailable summaries cannot also be conflicts");
		}
		if (mode == ContainerLabelMode.WHITELIST && unavailable != (unavailableEntryCount == whitelistEntryCount)) {
			throw new IllegalArgumentException("Whitelist unavailable flag must mean every entry is unavailable");
		}
	}

	public static ContainerLabelSummary of(ContainerLabelRule rule) {
		return switch (rule.mode()) {
			case NONE -> NONE;
			case EXACT_ITEM -> new ContainerLabelSummary(
				ContainerLabelMode.EXACT_ITEM, rule.exactItemId(), Optional.empty(), rule.exactItemId(), 0, 0, false, false
			);
			case ITEM_TAG -> itemTag(rule, false);
			case WHITELIST -> whitelist(rule, false);
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
			0,
			unavailable ? 1 : 0,
			!unavailable && contentConflict,
			unavailable
		);
	}

	public static ContainerLabelSummary whitelist(ContainerLabelRule rule, boolean contentConflict) {
		if (rule.mode() != ContainerLabelMode.WHITELIST) {
			throw new IllegalArgumentException("Expected a whitelist rule");
		}
		int unavailableEntries = 0;
		Optional<Identifier> representative = Optional.empty();
		for (ContainerLabelEntry entry : rule.entries()) {
			if (entry.type() == ContainerLabelEntryType.ITEM) {
				if (representative.isEmpty()) {
					representative = Optional.of(entry.valueId());
				}
				continue;
			}
			if (ContainerItemTags.availability(entry.valueId()) != ContainerItemTags.Availability.AVAILABLE) {
				unavailableEntries++;
			} else if (representative.isEmpty()) {
				representative = ContainerItemTags.representativeItemId(entry.valueId());
			}
		}
		boolean fullyUnavailable = unavailableEntries == rule.entries().size();
		return new ContainerLabelSummary(
			ContainerLabelMode.WHITELIST, Optional.empty(), Optional.empty(), representative,
			rule.entries().size(), unavailableEntries, !fullyUnavailable && contentConflict, fullyUnavailable
		);
	}

	public boolean hasLabel() {
		return mode != ContainerLabelMode.NONE;
	}

	public boolean blocksInput() {
		return ruleConflict() || unavailable;
	}

	public boolean contentConflict() {
		return conflict && mode != ContainerLabelMode.NONE;
	}

	public boolean ruleConflict() {
		return conflict && mode == ContainerLabelMode.NONE;
	}

	public boolean partiallyUnavailable() {
		return unavailableEntryCount > 0 && !unavailable;
	}
}
