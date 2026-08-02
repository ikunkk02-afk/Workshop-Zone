package io.github.ikunkk02afk.workshopzone.deposit;

import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class WorkshopDepositSourceSlotsTest {
	@Test
	void defaultUsesOnlyMainInventoryOutsideHotbar() {
		assertEquals(IntStream.range(9, 36).boxed().toList(), WorkshopDepositSourceSlots.forRequest(false));
	}

	@Test
	void shiftAddsHotbarAfterMainInventory() {
		assertEquals(
			IntStream.concat(IntStream.range(9, 36), IntStream.range(0, 9)).boxed().toList(),
			WorkshopDepositSourceSlots.forRequest(true)
		);
	}

	@Test
	void armorAndOffhandAreNeverReturned() {
		assertFalse(WorkshopDepositSourceSlots.forRequest(true).stream().anyMatch(slot -> slot >= 36));
	}
}
