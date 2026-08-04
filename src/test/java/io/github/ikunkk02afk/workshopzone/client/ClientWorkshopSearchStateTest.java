package io.github.ikunkk02afk.workshopzone.client;

import io.github.ikunkk02afk.workshopzone.network.RequestWorkshopItemCatalogPayload;
import io.github.ikunkk02afk.workshopzone.network.WorkshopItemCatalogPayload;
import io.github.ikunkk02afk.workshopzone.scan.WorkshopBlockType;
import io.github.ikunkk02afk.workshopzone.search.WorkshopItemCatalogResultCode;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientWorkshopSearchStateTest {
	@BeforeEach
	void reset() {
		ClientWorkshopSearchState.resetConnection();
		ClientWorkshopSearchState.beginScreen(9);
	}

	@AfterEach
	void cleanup() {
		ClientWorkshopSearchState.resetConnection();
	}

	@Test
	void catalogLoadingNeverFallsBackToRegistryCandidatesAndEmptyResponseIsReady() {
		ClientWorkshopSnapshot snapshot = snapshot(12, 3, 9);
		ClientWorkshopSearchState.enter(snapshot);
		ClientWorkshopSearchState.setSearchText("铁");
		RequestWorkshopItemCatalogPayload request = ClientWorkshopSearchState.requestCatalog(snapshot, false);

		assertTrue(ClientWorkshopSearchState.catalogLoading());
		assertTrue(ClientWorkshopSearchState.candidates().isEmpty());
		assertFalse(ClientWorkshopSearchState.catalogReady());

		ClientWorkshopSearchState.acceptCatalogNetwork(new WorkshopItemCatalogPayload(
			request.requestId(), request.sessionId(), request.revision(), request.syncId(),
			WorkshopItemCatalogResultCode.EMPTY, 0, false, List.of()
		));
		assertTrue(ClientWorkshopSearchState.consumeCatalogNetwork(snapshot));
		assertFalse(ClientWorkshopSearchState.catalogLoading());
		assertTrue(ClientWorkshopSearchState.catalogReady());
		assertTrue(ClientWorkshopSearchState.candidates().isEmpty());
	}

	@Test
	void catalogRequestsAreCoalescedWhileOneIsLoading() {
		ClientWorkshopSnapshot snapshot = snapshot(12, 3, 9);
		ClientWorkshopSearchState.enter(snapshot);
		RequestWorkshopItemCatalogPayload first = ClientWorkshopSearchState.requestCatalog(snapshot, false);

		assertNotNull(first);
		assertNull(ClientWorkshopSearchState.requestCatalog(snapshot, false));

		ClientWorkshopSearchState.acceptCatalogNetwork(new WorkshopItemCatalogPayload(
			first.requestId(), first.sessionId(), first.revision(), first.syncId(),
			WorkshopItemCatalogResultCode.EMPTY, 0, false, List.of()
		));
		assertTrue(ClientWorkshopSearchState.consumeCatalogNetwork(snapshot));
		assertTrue(ClientWorkshopSearchState.catalogReady());
	}

	@Test
	void catalogCooldownRetriesAtMostOnceAutomatically() {
		ClientWorkshopSnapshot snapshot = snapshot(12, 3, 9);
		ClientWorkshopSearchState.enter(snapshot);
		RequestWorkshopItemCatalogPayload first = ClientWorkshopSearchState.requestCatalog(snapshot, false);

		ClientWorkshopSearchState.acceptCatalogNetwork(new WorkshopItemCatalogPayload(
			first.requestId(), first.sessionId(), first.revision(), first.syncId(),
			WorkshopItemCatalogResultCode.COOLDOWN, 0, false, List.of()
		));
		ClientWorkshopSearchState.consumeCatalogNetwork(snapshot);
		assertTrue(ClientWorkshopSearchState.shouldRequestCatalog(snapshot, Long.MAX_VALUE));

		RequestWorkshopItemCatalogPayload retry = ClientWorkshopSearchState.retryCatalog(snapshot, false);
		assertNotNull(retry);
		ClientWorkshopSearchState.acceptCatalogNetwork(new WorkshopItemCatalogPayload(
			retry.requestId(), retry.sessionId(), retry.revision(), retry.syncId(),
			WorkshopItemCatalogResultCode.COOLDOWN, 0, false, List.of()
		));
		ClientWorkshopSearchState.consumeCatalogNetwork(snapshot);

		assertFalse(ClientWorkshopSearchState.shouldRequestCatalog(snapshot, Long.MAX_VALUE));
		assertNull(ClientWorkshopSearchState.retryCatalog(snapshot, false));
	}

	@Test
	void closingSearchClearsCatalogAndPendingStateBeforeReopen() {
		ClientWorkshopSnapshot snapshot = snapshot(12, 3, 9);
		ClientWorkshopSearchState.enter(snapshot);
		assertNotNull(ClientWorkshopSearchState.requestCatalog(snapshot, false));

		ClientWorkshopSearchState.closeMode();

		assertFalse(ClientWorkshopSearchState.searchMode());
		assertFalse(ClientWorkshopSearchState.catalogLoading());
		assertFalse(ClientWorkshopSearchState.catalogReady());
		assertFalse(ClientWorkshopSearchState.pending());
		assertTrue(ClientWorkshopSearchState.candidates().isEmpty());
	}

	@Test
	void revisionChangeClearsTheOldCatalogAndRequestsAgain() {
		ClientWorkshopSnapshot first = snapshot(12, 3, 9);
		ClientWorkshopSearchState.enter(first);
		RequestWorkshopItemCatalogPayload request = ClientWorkshopSearchState.requestCatalog(first, false);
		ClientWorkshopSearchState.acceptCatalogNetwork(new WorkshopItemCatalogPayload(
			request.requestId(), request.sessionId(), request.revision(), request.syncId(),
			WorkshopItemCatalogResultCode.EMPTY, 0, false, List.of()
		));
		ClientWorkshopSearchState.consumeCatalogNetwork(first);
		assertTrue(ClientWorkshopSearchState.catalogReady());

		ClientWorkshopSnapshot revised = snapshot(12, 4, 9);
		ClientWorkshopSearchState.synchronizeSnapshot(revised);

		assertFalse(ClientWorkshopSearchState.catalogReady());
		assertTrue(ClientWorkshopSearchState.shouldRequestCatalog(revised));
	}

	private static ClientWorkshopSnapshot snapshot(long sessionId, long revision, int syncId) {
		return new ClientWorkshopSnapshot(
			sessionId, revision, syncId, Identifier.ofVanilla("overworld"), BlockPos.ORIGIN, BlockPos.ORIGIN,
			WorkshopBlockType.CHEST, 0, 0, 0, false, List.of()
		);
	}
}
