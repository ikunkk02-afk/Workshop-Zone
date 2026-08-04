package io.github.ikunkk02afk.workshopzone.craft;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;


import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkshopCraftPendingConfirmationTest {
	@Test
	void currentIdentityIsAcceptedUntilExpiry() {
		UUID player = UUID.randomUUID();
		WorkshopCraftPendingConfirmation pending = pending(player, 100, 300);

		assertEquals(WorkshopCraftPendingValidation.VALID, WorkshopCraftPendingChecks.validate(
			pending, 41, player, 11, 4, 9, 299
		));
		assertEquals(WorkshopCraftPendingValidation.EXPIRED, WorkshopCraftPendingChecks.validate(
			pending, 41, player, 11, 4, 9, 300
		));
	}

	@Test
	void repeatedWrongPlayerAndStaleIdentitiesAreRejected() {
		UUID player = UUID.randomUUID();
		WorkshopCraftPendingConfirmation pending = pending(player, 100, 300);
		assertEquals(WorkshopCraftPendingValidation.INVALID_CONFIRMATION, WorkshopCraftPendingChecks.validate(
			pending, 42, player, 11, 4, 9, 120
		));
		assertEquals(WorkshopCraftPendingValidation.INVALID_CONFIRMATION, WorkshopCraftPendingChecks.validate(
			pending, 41, UUID.randomUUID(), 11, 4, 9, 120
		));
		assertEquals(WorkshopCraftPendingValidation.STALE_SESSION, WorkshopCraftPendingChecks.validate(
			pending, 41, player, 12, 4, 9, 120
		));
		assertEquals(WorkshopCraftPendingValidation.STALE_SESSION, WorkshopCraftPendingChecks.validate(
			pending, 41, player, 11, 5, 9, 120
		));
		assertEquals(WorkshopCraftPendingValidation.INVALID_CONFIRMATION, WorkshopCraftPendingChecks.validate(
			pending, 41, player, 11, 4, 10, 120
		));
	}

	@Test
	void storeKeepsAtMostOnePendingPreviewAndConsumesOnlyOnce() {
		UUID player = UUID.randomUUID();
		WorkshopCraftPendingStore store = new WorkshopCraftPendingStore();
		WorkshopCraftPendingConfirmation first = pending(player, 100, 300);
		WorkshopCraftPendingConfirmation second = new WorkshopCraftPendingConfirmation(
			42, player, 11, 4, 9, Identifier.ofVanilla("stick"), 101, 301,
			Identifier.ofVanilla("stick"), 4, List.of()
		);

		assertFalse(store.put(first).isPresent());
		assertEquals(first, store.put(second).orElseThrow());
		assertEquals(second, store.get(player).orElseThrow());
		assertTrue(store.consume(player, 41).isEmpty());
		assertEquals(second, store.consume(player, 42).orElseThrow());
		assertTrue(store.consume(player, 42).isEmpty());
	}

	private static WorkshopCraftPendingConfirmation pending(UUID player, long created, long expires) {
		return new WorkshopCraftPendingConfirmation(
			41, player, 11, 4, 9, Identifier.ofVanilla("crafting_table"), created, expires,
			Identifier.ofVanilla("crafting_table"), 1, List.of()
		);
	}
}
