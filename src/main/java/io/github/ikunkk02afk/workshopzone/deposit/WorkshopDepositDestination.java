package io.github.ikunkk02afk.workshopzone.deposit;

import io.github.ikunkk02afk.workshopzone.label.ContainerLabelMode;
import io.github.ikunkk02afk.workshopzone.label.ContainerLabelRule;
import io.github.ikunkk02afk.workshopzone.label.LogicalContainer;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedSlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record WorkshopDepositDestination(
	LogicalContainer container,
	ContainerLabelRule rule,
	SlottedStorage<ItemVariant> storage,
	double distanceSquared,
	int scanIndex
) {
	public WorkshopDepositDestination {
		Objects.requireNonNull(container, "container");
		Objects.requireNonNull(rule, "rule");
		Objects.requireNonNull(storage, "storage");
		if (!Double.isFinite(distanceSquared) || distanceSquared < 0 || scanIndex < 0) {
			throw new IllegalArgumentException("Invalid workshop deposit destination metadata");
		}
	}

	public static WorkshopDepositDestination create(
		LogicalContainer container,
		ContainerLabelRule rule,
		double distanceSquared,
		int scanIndex
	) {
		List<InventoryStorage> parts = container.members().stream()
			.map(member -> InventoryStorage.of((Inventory)member, null))
			.toList();
		SlottedStorage<ItemVariant> storage = parts.size() == 1
			? parts.getFirst()
			: new CombinedSlottedStorage<>(parts);
		return new WorkshopDepositDestination(container, rule, storage, distanceSquared, scanIndex);
	}

	public BlockPos representativePosition() {
		return container.representativePosition();
	}

	public boolean matches(ItemStack stack) {
		return rule.canInsert(stack);
	}

	public Optional<WorkshopDepositMatchKind> matchKind(ItemStack stack) {
		if (!rule.canInsert(stack) || stack.isEmpty()) {
			return Optional.empty();
		}
		return switch (rule.mode()) {
			case EXACT_ITEM -> Optional.of(WorkshopDepositMatchKind.SINGLE_EXACT);
			case ITEM_TAG -> Optional.of(WorkshopDepositMatchKind.SINGLE_ITEM_TAG);
			case WHITELIST -> rule.matchesExactItem(stack)
				? Optional.of(WorkshopDepositMatchKind.WHITELIST_EXACT)
				: rule.matchesItemTag(stack) ? Optional.of(WorkshopDepositMatchKind.WHITELIST_ITEM_TAG) : Optional.empty();
			case NONE -> Optional.empty();
		};
	}

	public long insert(ItemVariant variant, long maxAmount, TransactionContext transaction) {
		long inserted = 0;
		for (int pass = 0; pass < 2 && inserted < maxAmount; pass++) {
			for (int slot = 0; slot < storage.getSlotCount() && inserted < maxAmount; slot++) {
				SingleSlotStorage<ItemVariant> target = storage.getSlot(slot);
				boolean mergeable = !target.isResourceBlank() && target.getResource().equals(variant);
				if (mergeable != (pass == 0)) {
					continue;
				}
				inserted += target.insert(variant, maxAmount - inserted, transaction);
			}
		}
		return inserted;
	}

	public boolean hasMergeableStack(ItemStack source) {
		Inventory inventory = container.inventory();
		for (int slot = 0; slot < inventory.size(); slot++) {
			ItemStack existing = inventory.getStack(slot);
			if (!existing.isEmpty()
				&& ItemStack.areItemsAndComponentsEqual(existing, source)
				&& existing.isStackable()
				&& existing.getCount() < Math.min(inventory.getMaxCount(existing), existing.getMaxCount())) {
				return true;
			}
		}
		return false;
	}
}
