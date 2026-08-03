package io.github.ikunkk02afk.workshopzone.label;

import net.minecraft.util.Identifier;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public record ContainerLabelSummary(
	ContainerLabelMode mode,
	Optional<Identifier> exactItemId,
	Optional<Identifier> itemTagId,
	List<Identifier> previewItemIds,
	int whitelistEntryCount,
	int unavailableEntryCount,
	boolean conflict,
	boolean unavailable
) {
	public static final int MAX_PREVIEW_ITEMS = 4;
	private static final Identifier AIR_ID = Identifier.ofVanilla("air");
	public static final ContainerLabelSummary NONE = new ContainerLabelSummary(
		ContainerLabelMode.NONE, Optional.empty(), Optional.empty(), List.of(), 0, 0, false, false
	);
	public static final ContainerLabelSummary CONFLICT = new ContainerLabelSummary(
		ContainerLabelMode.NONE, Optional.empty(), Optional.empty(), List.of(), 0, 0, true, false
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
			mode, exactItemId, itemTagId, representativeItemId.stream().toList(),
			mode == ContainerLabelMode.WHITELIST ? 1 : 0,
			mode == ContainerLabelMode.ITEM_TAG && unavailable ? 1 : 0,
			conflict, unavailable
		);
	}

	public ContainerLabelSummary(
		ContainerLabelMode mode,
		Optional<Identifier> exactItemId,
		Optional<Identifier> itemTagId,
		Optional<Identifier> representativeItemId,
		int whitelistEntryCount,
		int unavailableEntryCount,
		boolean conflict,
		boolean unavailable
	) {
		this(
			mode, exactItemId, itemTagId, representativeItemId.stream().toList(),
			whitelistEntryCount, unavailableEntryCount, conflict, unavailable
		);
	}

	public ContainerLabelSummary {
		Objects.requireNonNull(mode, "mode");
		exactItemId = Objects.requireNonNull(exactItemId, "exactItemId");
		itemTagId = Objects.requireNonNull(itemTagId, "itemTagId");
		previewItemIds = List.copyOf(Objects.requireNonNull(previewItemIds, "previewItemIds"));
		if (previewItemIds.size() > MAX_PREVIEW_ITEMS || new LinkedHashSet<>(previewItemIds).size() != previewItemIds.size()) {
			throw new IllegalArgumentException("Container label previews must be unique and bounded");
		}
		for (Identifier previewItemId : previewItemIds) {
			Objects.requireNonNull(previewItemId, "previewItemId");
			if (AIR_ID.equals(previewItemId) || previewItemId.toString().length() > ContainerLabelEntry.MAX_IDENTIFIER_LENGTH) {
				throw new IllegalArgumentException("Invalid container label preview item");
			}
		}
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
		if (mode == ContainerLabelMode.NONE && !previewItemIds.isEmpty()) {
			throw new IllegalArgumentException("None and rule-conflict summaries cannot expose preview items");
		}
		if (mode == ContainerLabelMode.EXACT_ITEM
			&& (!previewItemIds.equals(exactItemId.stream().toList()) || conflict || unavailable || unavailableEntryCount != 0)) {
			throw new IllegalArgumentException("Exact-item summaries must expose the exact item without state flags");
		}
		if (mode == ContainerLabelMode.ITEM_TAG && previewItemIds.size() != (unavailable ? 0 : 1)) {
			throw new IllegalArgumentException("Item-tag summaries must expose exactly one available preview item");
		}
		if (unavailable && (mode != ContainerLabelMode.ITEM_TAG && mode != ContainerLabelMode.WHITELIST || conflict)) {
			throw new IllegalArgumentException("Unavailable summaries cannot also be conflicts");
		}
		if (mode == ContainerLabelMode.WHITELIST && unavailable != (unavailableEntryCount == whitelistEntryCount)) {
			throw new IllegalArgumentException("Whitelist unavailable flag must mean every entry is unavailable");
		}
		if (mode == ContainerLabelMode.WHITELIST
			&& previewItemIds.size() > whitelistEntryCount - unavailableEntryCount) {
			throw new IllegalArgumentException("Whitelist previews cannot exceed the number of usable entries");
		}
		if (mode == ContainerLabelMode.WHITELIST && unavailable && !previewItemIds.isEmpty()) {
			throw new IllegalArgumentException("Fully unavailable whitelist summaries cannot expose normal preview items");
		}
		if (mode == ContainerLabelMode.WHITELIST && !unavailable && previewItemIds.isEmpty()) {
			throw new IllegalArgumentException("Usable whitelist summaries require a preview item");
		}
	}

	public static ContainerLabelSummary of(ContainerLabelRule rule) {
		return switch (rule.mode()) {
			case NONE -> NONE;
			case EXACT_ITEM -> new ContainerLabelSummary(
				ContainerLabelMode.EXACT_ITEM, rule.exactItemId(), Optional.empty(), rule.exactItemId().stream().toList(), 0, 0, false, false
			);
			case ITEM_TAG -> itemTag(rule, false);
			case WHITELIST -> whitelist(rule, false);
		};
	}

	public static ContainerLabelSummary itemTag(ContainerLabelRule rule, boolean contentConflict) {
		Identifier tagId = rule.itemTagId().orElseThrow();
		Optional<Identifier> representative = ContainerItemTags.availability(tagId) == ContainerItemTags.Availability.AVAILABLE
			? ContainerItemTags.representativeItemId(tagId)
			: Optional.empty();
		boolean unavailable = representative.isEmpty();
		return new ContainerLabelSummary(
			ContainerLabelMode.ITEM_TAG,
			Optional.empty(),
			Optional.of(tagId),
			representative.stream().toList(),
			0,
			unavailable ? 1 : 0,
			!unavailable && contentConflict,
			unavailable
		);
	}

	public static ContainerLabelSummary whitelist(ContainerLabelRule rule, boolean contentConflict) {
		return whitelist(rule, contentConflict, tagId ->
			ContainerItemTags.availability(tagId) == ContainerItemTags.Availability.AVAILABLE
				? ContainerItemTags.representativeItemId(tagId)
				: Optional.empty()
		);
	}

	static ContainerLabelSummary whitelist(
		ContainerLabelRule rule,
		boolean contentConflict,
		Function<Identifier, Optional<Identifier>> representativeResolver
	) {
		if (rule.mode() != ContainerLabelMode.WHITELIST) {
			throw new IllegalArgumentException("Expected a whitelist rule");
		}
		Objects.requireNonNull(representativeResolver, "representativeResolver");
		int unavailableEntries = 0;
		LinkedHashSet<Identifier> previews = new LinkedHashSet<>();
		for (ContainerLabelEntry entry : rule.entries()) {
			if (entry.type() == ContainerLabelEntryType.ITEM) {
				if (previews.size() < MAX_PREVIEW_ITEMS) {
					previews.add(entry.valueId());
				}
				continue;
			}
			Optional<Identifier> representative = representativeResolver.apply(entry.valueId());
			if (representative.isEmpty()) {
				unavailableEntries++;
			} else if (previews.size() < MAX_PREVIEW_ITEMS) {
				previews.add(representative.orElseThrow());
			}
		}
		boolean fullyUnavailable = unavailableEntries == rule.entries().size();
		return new ContainerLabelSummary(
			ContainerLabelMode.WHITELIST, Optional.empty(), Optional.empty(), List.copyOf(previews),
			rule.entries().size(), unavailableEntries, !fullyUnavailable && contentConflict, fullyUnavailable
		);
	}

	public Optional<Identifier> representativeItemId() {
		return previewItemIds.stream().findFirst();
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
