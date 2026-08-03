package io.github.ikunkk02afk.workshopzone.label;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Predicate;

public record ContainerLabelRule(ContainerLabelMode mode, List<ContainerLabelEntry> entries) {
	public static final int MAX_ENTRIES = 32;
	public static final ContainerLabelRule NONE = new ContainerLabelRule(ContainerLabelMode.NONE, List.of());

	public ContainerLabelRule {
		Objects.requireNonNull(mode, "mode");
		Objects.requireNonNull(entries, "entries");
		if (entries.size() > MAX_ENTRIES) {
			throw new IllegalArgumentException("A container label rule cannot exceed " + MAX_ENTRIES + " entries");
		}
		TreeSet<ContainerLabelEntry> normalized = new TreeSet<>(ContainerLabelEntry.ORDER);
		normalized.addAll(entries);
		entries = List.copyOf(normalized);
		switch (mode) {
			case NONE -> {
				if (!entries.isEmpty()) {
					throw new IllegalArgumentException("None labels cannot carry entries");
				}
			}
			case EXACT_ITEM -> requireSingleType(entries, ContainerLabelEntryType.ITEM, "Exact-item labels");
			case ITEM_TAG -> requireSingleType(entries, ContainerLabelEntryType.ITEM_TAG, "Item-tag labels");
			case WHITELIST -> {
				if (entries.isEmpty()) {
					throw new IllegalArgumentException("Whitelist labels require at least one entry");
				}
			}
		}
	}

	public ContainerLabelRule(ContainerLabelMode mode, Optional<Identifier> valueId) {
		this(mode, legacyEntries(mode, valueId));
	}

	private static List<ContainerLabelEntry> legacyEntries(ContainerLabelMode mode, Optional<Identifier> valueId) {
		Objects.requireNonNull(mode, "mode");
		Objects.requireNonNull(valueId, "valueId");
		return switch (mode) {
			case NONE -> {
				if (valueId.isPresent()) {
					throw new IllegalArgumentException("None labels cannot carry a value id");
				}
				yield List.of();
			}
			case EXACT_ITEM -> List.of(ContainerLabelEntry.item(valueId.orElseThrow(
				() -> new IllegalArgumentException("Exact-item labels require a value id")
			)));
			case ITEM_TAG -> List.of(ContainerLabelEntry.itemTag(valueId.orElseThrow(
				() -> new IllegalArgumentException("Item-tag labels require a value id")
			)));
			case WHITELIST -> throw new IllegalArgumentException("Whitelist labels require explicit entries");
		};
	}

	private static void requireSingleType(List<ContainerLabelEntry> entries, ContainerLabelEntryType type, String name) {
		if (entries.size() != 1 || entries.getFirst().type() != type) {
			throw new IllegalArgumentException(name + " require exactly one " + type + " entry");
		}
	}

	public static ContainerLabelRule none() {
		return NONE;
	}

	public static ContainerLabelRule exactItem(Item item) {
		return exactItem(Registries.ITEM.getId(Objects.requireNonNull(item, "item")));
	}

	public static ContainerLabelRule exactItem(Identifier itemId) {
		return new ContainerLabelRule(ContainerLabelMode.EXACT_ITEM, List.of(ContainerLabelEntry.item(itemId)));
	}

	public static ContainerLabelRule itemTag(Identifier tagId) {
		return new ContainerLabelRule(ContainerLabelMode.ITEM_TAG, List.of(ContainerLabelEntry.itemTag(tagId)));
	}

	public static ContainerLabelRule whitelist(List<ContainerLabelEntry> entries) {
		return new ContainerLabelRule(ContainerLabelMode.WHITELIST, entries);
	}

	public static ContainerLabelRule exact(Item item) {
		return exactItem(item);
	}

	public static ContainerLabelRule exact(Identifier itemId) {
		return exactItem(itemId);
	}

	public Optional<Identifier> exactItemId() {
		return mode == ContainerLabelMode.EXACT_ITEM ? Optional.of(entries.getFirst().valueId()) : Optional.empty();
	}

	public Optional<Identifier> itemTagId() {
		return mode == ContainerLabelMode.ITEM_TAG ? Optional.of(entries.getFirst().valueId()) : Optional.empty();
	}

	public Optional<TagKey<Item>> itemTagKey() {
		return itemTagId().map(ContainerItemTags::key);
	}

	public boolean canInsert(ItemStack stack) {
		return canInsert(
			stack.isEmpty(),
			stack.isEmpty() ? null : Registries.ITEM.getId(stack.getItem()),
			stack::isIn
		);
	}

	boolean canInsert(boolean empty, Identifier itemId, Predicate<TagKey<Item>> tagMembership) {
		if (empty) {
			return true;
		}
		Objects.requireNonNull(itemId, "itemId");
		Objects.requireNonNull(tagMembership, "tagMembership");
		return switch (mode) {
			case NONE -> true;
			case EXACT_ITEM -> entries.getFirst().valueId().equals(itemId);
			case ITEM_TAG -> tagMembership.test(ContainerItemTags.key(entries.getFirst().valueId()));
			case WHITELIST -> entries.stream().anyMatch(entry -> switch (entry.type()) {
				case ITEM -> entry.valueId().equals(itemId);
				case ITEM_TAG -> tagMembership.test(ContainerItemTags.key(entry.valueId()));
			});
		};
	}

	public boolean canInsertItemId(Identifier itemId) {
		return switch (mode) {
			case NONE -> true;
			case EXACT_ITEM -> entries.getFirst().valueId().equals(itemId);
			case ITEM_TAG -> Registries.ITEM.getOrEmpty(itemId)
				.map(item -> Registries.ITEM.getEntry(item).isIn(ContainerItemTags.key(entries.getFirst().valueId())))
				.orElse(false);
			case WHITELIST -> {
				if (entries.stream().anyMatch(entry -> entry.type() == ContainerLabelEntryType.ITEM && entry.valueId().equals(itemId))) {
					yield true;
				}
				yield Registries.ITEM.getOrEmpty(itemId).map(item -> entries.stream().anyMatch(entry ->
					entry.type() == ContainerLabelEntryType.ITEM_TAG
						&& Registries.ITEM.getEntry(item).isIn(ContainerItemTags.key(entry.valueId()))
				)).orElse(false);
			}
		};
	}

	public boolean matchesExactItem(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		Identifier itemId = Registries.ITEM.getId(stack.getItem());
		return entries.stream().anyMatch(entry -> entry.type() == ContainerLabelEntryType.ITEM && entry.valueId().equals(itemId));
	}

	public boolean matchesItemTag(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		return entries.stream().anyMatch(entry -> entry.type() == ContainerLabelEntryType.ITEM_TAG
			&& stack.isIn(ContainerItemTags.key(entry.valueId())));
	}

	public List<ContainerLabelEntry> itemEntries() {
		List<ContainerLabelEntry> result = new ArrayList<>();
		for (ContainerLabelEntry entry : entries) {
			if (entry.type() == ContainerLabelEntryType.ITEM) {
				result.add(entry);
			}
		}
		return List.copyOf(result);
	}

	public List<ContainerLabelEntry> itemTagEntries() {
		List<ContainerLabelEntry> result = new ArrayList<>();
		for (ContainerLabelEntry entry : entries) {
			if (entry.type() == ContainerLabelEntryType.ITEM_TAG) {
				result.add(entry);
			}
		}
		return List.copyOf(result);
	}
}
