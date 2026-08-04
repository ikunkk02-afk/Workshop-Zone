package io.github.ikunkk02afk.workshopzone.craft;

import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.PlayerInventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class WorkshopCraftTransactionExecutor {
	private WorkshopCraftTransactionExecutor() {
	}

	public static boolean execute(
		WorkshopCraftPlan plan,
		CraftingScreenHandler handler,
		ServerPlayerEntity player
	) {
		Inventory craftingInventory = handler.getSlot(1).inventory;
		if (!(craftingInventory instanceof CraftingInventory grid) || grid.size() != 9) {
			return false;
		}
		for (int handlerSlot = 1; handlerSlot <= 9; handlerSlot++) {
			Slot slot = handler.getSlot(handlerSlot);
			if (slot.inventory != craftingInventory || !slot.getStack().isEmpty()) {
				return false;
			}
		}
		InventoryStorage targetStorage = InventoryStorage.of(craftingInventory, null);
		Map<Integer, Integer> useCounts = new HashMap<>();
		for (WorkshopCraftAssignment assignment : plan.assignments()) {
			useCounts.merge(assignment.supplyId(), 1, Integer::sum);
		}

		boolean committed = false;
		handler.onInputSlotFillStart();
		try {
			try (Transaction transaction = Transaction.openOuter()) {
				for (Map.Entry<Integer, Integer> use : useCounts.entrySet()) {
					WorkshopCraftLiveSupply live = plan.liveSupplies().get(use.getKey());
					if (live == null || use.getValue() > live.supply().amount()) {
						return false;
					}
					SingleSlotStorage<ItemVariant> source = live.supply().sourceKind() == WorkshopCraftSourceKind.PLAYER
						? PlayerInventoryStorage.of(player).getSlot(live.inventorySlot())
						: InventoryStorage.of(live.inventory(), null).getSlot(live.inventorySlot());
					long extracted = source.extract(live.supply().variant(), use.getValue(), transaction);
					if (extracted != use.getValue()) {
						return false;
					}
				}
				long inserted = 0;
				for (WorkshopCraftGridPlacement placement : plan.placements()) {
					Slot handlerSlot = handler.getSlot(placement.handlerSlotIndex());
					SingleSlotStorage<ItemVariant> target = targetStorage.getSlot(handlerSlot.getIndex());
					long amount = target.insert(placement.variant(), 1, transaction);
					if (amount != 1) {
						return false;
					}
					inserted += amount;
				}
				if (inserted != plan.assignments().size()) {
					return false;
				}
				if (!plan.recipe().entry().value().matches(grid.createRecipeInput(), player.getServerWorld())) {
					return false;
				}
				transaction.commit();
				committed = true;
			}
		} finally {
			handler.onInputSlotFillFinish(plan.recipe().entry());
		}
		if (!committed) {
			return false;
		}
		Set<Inventory> dirtiedInventories = new HashSet<>();
		Set<BlockEntity> dirtiedMembers = new HashSet<>();
		for (Integer supplyId : useCounts.keySet()) {
			WorkshopCraftLiveSupply live = plan.liveSupplies().get(supplyId);
			if (live != null) {
				dirtiedInventories.add(live.inventory());
				dirtiedMembers.addAll(live.dirtyMembers());
			}
		}
		dirtiedInventories.forEach(Inventory::markDirty);
		dirtiedMembers.forEach(BlockEntity::markDirty);
		craftingInventory.markDirty();
		handler.sendContentUpdates();
		return true;
	}
}
