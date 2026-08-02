package io.github.ikunkk02afk.workshopzone.deposit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkshopDepositSummaryTest {
	@Test
	void successfulAndPartialOutcomesUseMatchedRemainder() {
		assertEquals(WorkshopDepositResult.SUCCESS, WorkshopDepositSummary.classify(64, 0, true, false));
		assertEquals(WorkshopDepositResult.PARTIAL, WorkshopDepositSummary.classify(10, 54, true, false));
	}

	@Test
	void nothingToMoveAndNoSpaceAreDistinct() {
		assertEquals(WorkshopDepositResult.NOTHING_TO_MOVE, WorkshopDepositSummary.classify(0, 0, false, false));
		assertEquals(WorkshopDepositResult.NO_SPACE, WorkshopDepositSummary.classify(0, 64, true, false));
	}

	@Test
	void allMatchingDestinationsDeniedReturnsDenied() {
		assertEquals(WorkshopDepositResult.DENIED, WorkshopDepositSummary.classify(0, 64, true, true));
	}
}
