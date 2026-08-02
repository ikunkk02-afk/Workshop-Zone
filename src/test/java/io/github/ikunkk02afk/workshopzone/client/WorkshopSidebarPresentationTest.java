package io.github.ikunkk02afk.workshopzone.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkshopSidebarPresentationTest {
	@Test
	void missingSnapshotRemainsRenderableWhileLoading() {
		WorkshopSidebarPresentation state = WorkshopSidebarPresentation.resolve(false, false, false);

		assertSame(WorkshopSidebarPresentation.LOADING, state);
		assertTrue(state.frameworkVisible());
		assertFalse(state.interactive());
	}

	@Test
	void laterMatchingSnapshotBecomesInteractive() {
		assertSame(WorkshopSidebarPresentation.LOADING, WorkshopSidebarPresentation.resolve(false, false, false));
		assertSame(WorkshopSidebarPresentation.READY, WorkshopSidebarPresentation.resolve(true, true, false));
	}

	@Test
	void mismatchedSnapshotIsHiddenUntilCorrectSyncArrives() {
		assertSame(WorkshopSidebarPresentation.LOADING, WorkshopSidebarPresentation.resolve(true, false, false));
		assertSame(WorkshopSidebarPresentation.READY, WorkshopSidebarPresentation.resolve(true, true, false));
	}

	@Test
	void clearCanBeFollowedByAReadyNewSession() {
		assertSame(WorkshopSidebarPresentation.NO_SESSION, WorkshopSidebarPresentation.resolve(false, false, true));
		assertSame(WorkshopSidebarPresentation.READY, WorkshopSidebarPresentation.resolve(true, true, false));
	}
}
