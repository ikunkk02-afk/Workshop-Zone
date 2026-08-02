package io.github.ikunkk02afk.workshopzone.deposit;

import net.minecraft.entity.player.PlayerInventory;

import java.util.ArrayList;
import java.util.List;

public final class WorkshopDepositSourceSlots {
	private WorkshopDepositSourceSlots() {
	}

	public static List<Integer> forRequest(boolean includeHotbar) {
		int hotbarSize = PlayerInventory.getHotbarSize();
		List<Integer> slots = new ArrayList<>(includeHotbar ? PlayerInventory.MAIN_SIZE : PlayerInventory.MAIN_SIZE - hotbarSize);
		for (int slot = hotbarSize; slot < PlayerInventory.MAIN_SIZE; slot++) {
			slots.add(slot);
		}
		if (includeHotbar) {
			for (int slot = 0; slot < hotbarSize; slot++) {
				slots.add(slot);
			}
		}
		return List.copyOf(slots);
	}
}
