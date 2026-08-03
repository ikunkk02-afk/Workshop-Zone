package io.github.ikunkk02afk.workshopzone.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkshopSearchLayoutTest {
	@Test
	void searchFieldAndListsStayInsideEveryPlacementPanel() {
		for (WorkshopSidebarPosition position : new WorkshopSidebarPosition[] {
			WorkshopSidebarPosition.RIGHT, WorkshopSidebarPosition.LEFT, WorkshopSidebarPosition.TOP,
			WorkshopSidebarPosition.BOTTOM, WorkshopSidebarPosition.CUSTOM
		}) {
			WorkshopSidebarPlacement placement = WorkshopSidebarPlacementResolver.resolve(
				new WorkshopSidebarPlacementResolver.Input(
					1200, 800, 500, 300, 176, 166, false, position, true, true,
					true, true, 210, 0.7, 0.2
				)
			);
			WorkshopSearchLayout layout = WorkshopSearchLayout.calculate(placement.panel());
			assertTrue(placement.panel().contains(layout.searchField().left(), layout.searchField().top()));
			assertTrue(layout.searchField().right() <= placement.panel().right());
			assertTrue(layout.listArea().bottom() <= placement.panel().bottom());
		}
	}

	@Test
	void rowHitUsesScrollAndRejectsClippedAreaAndLocateButtonDoesNotOpenRow() {
		WorkshopSearchLayout layout = WorkshopSearchLayout.calculate(new WorkshopSidebarMetrics.Rect(10, 10, 210, 260));
		int index = layout.rowAt(20, layout.listArea().top() + 2, 36, 36, 10);
		assertEquals(1, index);
		assertEquals(-1, layout.rowAt(20, layout.listArea().top() - 1, 36, 0, 10));
		WorkshopSidebarMetrics.Rect row = layout.visibleRow(0, 36, 0);
		WorkshopSidebarMetrics.Rect locate = WorkshopSearchLayout.locateButton(row);
		assertTrue(locate.width() > 0);
		assertFalse(new WorkshopSidebarMetrics.Rect(row.left(), row.top(), row.width() - locate.width() - 4, row.height()).intersects(locate));
	}
}
