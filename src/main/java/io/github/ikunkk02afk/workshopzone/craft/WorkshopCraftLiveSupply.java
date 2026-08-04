package io.github.ikunkk02afk.workshopzone.craft;

import io.github.ikunkk02afk.workshopzone.label.LogicalContainer;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;

import java.util.List;
import java.util.Objects;

public record WorkshopCraftLiveSupply(
	WorkshopCraftSupply supply,
	Inventory inventory,
	int inventorySlot,
	LogicalContainer container
) {
	public WorkshopCraftLiveSupply {
		Objects.requireNonNull(supply, "supply");
		Objects.requireNonNull(inventory, "inventory");
		if (inventorySlot < 0 || inventorySlot >= inventory.size()) {
			throw new IllegalArgumentException("Invalid workshop crafting source slot");
		}
	}

	public List<BlockEntity> dirtyMembers() {
		return container == null ? List.of() : container.members();
	}
}
