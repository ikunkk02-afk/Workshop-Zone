package io.github.ikunkk02afk.workshopzone.session;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkshopSessionChecksTest {
	@Test
	void validatesEveryRefreshInvariant() {
		assertEquals(WorkshopSessionValidation.VALID, validate(7, true, 16, true, true, true));
		assertEquals(WorkshopSessionValidation.SYNC_MISMATCH, validate(8, true, 16, true, true, true));
		assertEquals(WorkshopSessionValidation.DIMENSION_MISMATCH, validate(7, false, 16, true, true, true));
		assertEquals(WorkshopSessionValidation.OUT_OF_RANGE, validate(7, true, 64.01, true, true, true));
		assertEquals(WorkshopSessionValidation.CENTER_UNLOADED, validate(7, true, 16, false, false, true));
		assertEquals(WorkshopSessionValidation.CENTER_CHANGED, validate(7, true, 16, true, false, true));
		assertEquals(WorkshopSessionValidation.HANDLER_MISMATCH, validate(7, true, 16, true, true, false));
	}

	@Test
	void refreshCooldownRequiresTwentyServerTicks() {
		assertFalse(WorkshopSessionChecks.canRefresh(119, 100));
		assertTrue(WorkshopSessionChecks.canRefresh(120, 100));
	}

	private static WorkshopSessionValidation validate(
		int currentSyncId,
		boolean sameDimension,
		double distance,
		boolean loaded,
		boolean matches,
		boolean handlerMatches
	) {
		return WorkshopSessionChecks.validate(7, currentSyncId, sameDimension, distance, loaded, matches, handlerMatches);
	}
}
