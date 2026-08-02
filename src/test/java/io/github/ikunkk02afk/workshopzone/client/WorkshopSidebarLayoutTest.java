package io.github.ikunkk02afk.workshopzone.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkshopSidebarLayoutTest {
	@Test
	void rowHitUsesScrollOffset() {
		assertEquals(0, WorkshopSidebarLayout.rowAt(15, 22, 10, 100, 20, 80, 12, 0, 10));
		assertEquals(2, WorkshopSidebarLayout.rowAt(15, 22, 10, 100, 20, 80, 12, 24, 10));
		assertEquals(3, WorkshopSidebarLayout.rowAt(15, 33, 10, 100, 20, 80, 12, 24, 10));
	}

	@Test
	void clippedAndHorizontalOutsideRowsCannotBeClicked() {
		assertEquals(-1, WorkshopSidebarLayout.rowAt(15, 19, 10, 100, 20, 80, 12, 0, 10));
		assertEquals(-1, WorkshopSidebarLayout.rowAt(15, 80, 10, 100, 20, 80, 12, 0, 10));
		assertEquals(-1, WorkshopSidebarLayout.rowAt(9, 22, 10, 100, 20, 80, 12, 0, 10));
		assertEquals(-1, WorkshopSidebarLayout.rowAt(100, 22, 10, 100, 20, 80, 12, 0, 10));
	}

	@Test
	void visibleBoundsAreClippedToListViewport() {
		WorkshopSidebarLayout.RowBounds top = WorkshopSidebarLayout.visibleRowBounds(0, 20, 50, 12, 6);
		WorkshopSidebarLayout.RowBounds outside = WorkshopSidebarLayout.visibleRowBounds(4, 20, 50, 12, 0);

		assertEquals(20, top.top());
		assertEquals(26, top.bottom());
		assertTrue(top.visible());
		assertFalse(outside.visible());
	}
}
