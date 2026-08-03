package io.github.ikunkk02afk.workshopzone.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkshopSidebarHeaderLayoutTest {
	@Test
	void minimumPanelWidthKeepsAllCompactControlsWithoutOverlap() {
		WorkshopSidebarHeaderLayout.Layout layout = WorkshopSidebarHeaderLayout.calculate(10, 20, 154, true);
		assertEquals(6, layout.controls().size());
		assertTrue(layout.controls().containsKey(WorkshopSidebarHeaderLayout.Control.SEARCH));
		assertTrue(layout.controls().containsKey(WorkshopSidebarHeaderLayout.Control.DEPOSIT));
		assertTrue(layout.controls().containsKey(WorkshopSidebarHeaderLayout.Control.LABEL));
		assertNoOverlap(layout.controls().values().stream().toList());
		int leftmost = layout.controls().values().stream().mapToInt(WorkshopSidebarMetrics.Rect::left).min().orElseThrow();
		assertTrue(layout.titleRight() <= leftmost - 3);
	}

	@Test
	void narrowSpacePreservesCollapseSearchAndDepositFirst() {
		WorkshopSidebarHeaderLayout.Layout layout = WorkshopSidebarHeaderLayout.calculate(0, 0, 100, true);
		assertTrue(layout.controls().containsKey(WorkshopSidebarHeaderLayout.Control.COLLAPSE));
		assertTrue(layout.controls().containsKey(WorkshopSidebarHeaderLayout.Control.SEARCH));
		assertTrue(layout.controls().containsKey(WorkshopSidebarHeaderLayout.Control.DEPOSIT));
		assertFalse(layout.controls().containsKey(WorkshopSidebarHeaderLayout.Control.POSITION));
		assertNoOverlap(layout.controls().values().stream().toList());
	}

	private static void assertNoOverlap(List<WorkshopSidebarMetrics.Rect> rectangles) {
		for (int first = 0; first < rectangles.size(); first++) {
			for (int second = first + 1; second < rectangles.size(); second++) {
				assertFalse(rectangles.get(first).intersects(rectangles.get(second)));
			}
		}
	}
}
