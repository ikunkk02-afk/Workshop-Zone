package io.github.ikunkk02afk.workshopzone.client;

import io.github.ikunkk02afk.workshopzone.deposit.WorkshopDepositResult;
import io.github.ikunkk02afk.workshopzone.network.WorkshopDepositResultPayload;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkshopDepositResultFilterTest {
	@Test
	void onlyCurrentPendingRequestForCurrentSessionAndScreenIsAccepted() {
		WorkshopDepositResultPayload payload = new WorkshopDepositResultPayload(
			44, 12, 9, WorkshopDepositResult.SUCCESS, 32, 1, 0, 1
		);
		assertTrue(WorkshopDepositResultFilter.matches(payload, 44, 12, 9));
		assertFalse(WorkshopDepositResultFilter.matches(payload, 43, 12, 9));
		assertFalse(WorkshopDepositResultFilter.matches(payload, 44, 13, 9));
		assertFalse(WorkshopDepositResultFilter.matches(payload, 44, 12, 10));
	}
}
