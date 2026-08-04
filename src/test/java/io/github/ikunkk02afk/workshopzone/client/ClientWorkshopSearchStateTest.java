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
	void staleCatalogResponseIsIgnoredUntilTheCurrentRequestArrives() {
		ClientWorkshopSnapshot snapshot = snapshot(12, 3, 9);
		ClientWorkshopSearchState.enter(snapshot);
		RequestWorkshopItemCatalogPayload first = ClientWorkshopSearchState.requestCatalog(snapshot, false);
		RequestWorkshopItemCatalogPayload current = ClientWorkshopSearchState.requestCatalog(snapshot, false);

		ClientWorkshopSearchState.acceptCatalogNetwork(new WorkshopItemCatalogPayload(
			first.requestId(), first.sessionId(), first.revision(), first.syncId(),
			WorkshopItemCatalogResultCode.EMPTY, 0, false, List.of()
		));
		assertFalse(ClientWorkshopSearchState.consumeCatalogNetwork(snapshot));
		assertTrue(ClientWorkshopSearchState.catalogLoading());

		ClientWorkshopSearchState.acceptCatalogNetwork(new WorkshopItemCatalogPayload(
			current.requestId(), current.sessionId(), current.revision(), current.syncId(),
			WorkshopItemCatalogResultCode.EMPTY, 0, false, List.of()
		));
		assertTrue(ClientWorkshopSearchState.consumeCatalogNetwork(snapshot));
		assertTrue(ClientWorkshopSearchState.catalogReady());
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
