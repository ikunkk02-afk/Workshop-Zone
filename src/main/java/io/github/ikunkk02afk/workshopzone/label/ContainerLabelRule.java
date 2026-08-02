package io.github.ikunkk02afk.workshopzone.label;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.Objects;
import java.util.Optional;

public record ContainerLabelRule(ContainerLabelMode mode, Optional<Identifier> exactItemId) {
	private static final Identifier AIR_ID = Identifier.ofVanilla("air");
	public static final ContainerLabelRule NONE = new ContainerLabelRule(ContainerLabelMode.NONE, Optional.empty());

	public ContainerLabelRule {
		Objects.requireNonNull(mode, "mode");
		exactItemId = Objects.requireNonNull(exactItemId, "exactItemId");
		if ((mode == ContainerLabelMode.EXACT_ITEM) != exactItemId.isPresent()) {
			throw new IllegalArgumentException("Exact-item mode and item must be present together");
		}
		if (exactItemId.filter(AIR_ID::equals).isPresent()) {
			throw new IllegalArgumentException("minecraft:air cannot be a container label");
		}
	}

	public static ContainerLabelRule exact(Item item) {
		return exact(Registries.ITEM.getId(Objects.requireNonNull(item, "item")));
	}

	public static ContainerLabelRule exact(Identifier itemId) {
		return new ContainerLabelRule(ContainerLabelMode.EXACT_ITEM, Optional.of(Objects.requireNonNull(itemId, "itemId")));
	}

	public boolean canInsert(ItemStack stack) {
		if (stack.isEmpty()) {
			return true;
		}
		return canInsertItemId(Registries.ITEM.getId(stack.getItem()));
	}

	public boolean canInsertItemId(Identifier itemId) {
		return mode == ContainerLabelMode.NONE || exactItemId.orElseThrow().equals(itemId);
	}
}
