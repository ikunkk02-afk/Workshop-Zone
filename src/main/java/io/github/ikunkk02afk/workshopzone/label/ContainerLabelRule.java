package io.github.ikunkk02afk.workshopzone.label;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

public record ContainerLabelRule(ContainerLabelMode mode, Optional<Identifier> valueId) {
	private static final Identifier AIR_ID = Identifier.ofVanilla("air");
	public static final ContainerLabelRule NONE = new ContainerLabelRule(ContainerLabelMode.NONE, Optional.empty());

	public ContainerLabelRule {
		Objects.requireNonNull(mode, "mode");
		valueId = Objects.requireNonNull(valueId, "valueId");
		if ((mode == ContainerLabelMode.NONE) == valueId.isPresent()) {
			throw new IllegalArgumentException("Only a non-empty label mode may carry a value id");
		}
		if (mode == ContainerLabelMode.EXACT_ITEM && valueId.filter(AIR_ID::equals).isPresent()) {
			throw new IllegalArgumentException("minecraft:air cannot be a container label");
		}
	}

	public static ContainerLabelRule none() {
		return NONE;
	}

	public static ContainerLabelRule exactItem(Item item) {
		return exactItem(Registries.ITEM.getId(Objects.requireNonNull(item, "item")));
	}

	public static ContainerLabelRule exactItem(Identifier itemId) {
		return new ContainerLabelRule(ContainerLabelMode.EXACT_ITEM, Optional.of(Objects.requireNonNull(itemId, "itemId")));
	}

	public static ContainerLabelRule itemTag(Identifier tagId) {
		return new ContainerLabelRule(ContainerLabelMode.ITEM_TAG, Optional.of(Objects.requireNonNull(tagId, "tagId")));
	}

	public static ContainerLabelRule exact(Item item) {
		return exactItem(item);
	}

	public static ContainerLabelRule exact(Identifier itemId) {
		return exactItem(itemId);
	}

	public Optional<Identifier> exactItemId() {
		return mode == ContainerLabelMode.EXACT_ITEM ? valueId : Optional.empty();
	}

	public Optional<Identifier> itemTagId() {
		return mode == ContainerLabelMode.ITEM_TAG ? valueId : Optional.empty();
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
		return switch (mode) {
			case NONE -> true;
			case EXACT_ITEM -> exactItemId().orElseThrow().equals(itemId);
			case ITEM_TAG -> tagMembership.test(itemTagKey().orElseThrow());
		};
	}

	public boolean canInsertItemId(Identifier itemId) {
		return switch (mode) {
			case NONE -> true;
			case EXACT_ITEM -> exactItemId().orElseThrow().equals(itemId);
			case ITEM_TAG -> Registries.ITEM.getOrEmpty(itemId)
				.map(item -> Registries.ITEM.getEntry(item).isIn(itemTagKey().orElseThrow()))
				.orElse(false);
		};
	}
}
