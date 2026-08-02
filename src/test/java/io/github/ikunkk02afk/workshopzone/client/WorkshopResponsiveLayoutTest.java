package io.github.ikunkk02afk.workshopzone.client;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkshopResponsiveLayoutTest {
	@Test
	void preferredWidthUsesRightSideWithoutCoveringVanillaGui() {
		WorkshopSidebarMetrics metrics = WorkshopSidebarMetrics.calculate(
			1920, 1080, 700, 400, 176, 166, false, true, false, 210
		);

		assertEquals(WorkshopSidebarMetrics.Side.RIGHT, metrics.side());
		assertEquals(210, metrics.panel().width());
		assertTrue(metrics.panel().left() >= 700 + 176);
		assertTrue(metrics.panel().right() <= 1920 - WorkshopSidebarMetrics.EDGE_GAP);
		assertFalse(metrics.collapsed());
	}

	@Test
	void leftSideIsUsedWhenRightCannotFitPreferredWidth() {
		WorkshopSidebarMetrics metrics = WorkshopSidebarMetrics.calculate(
			500, 300, 300, 60, 176, 166, false, true, false, 210
		);

		assertEquals(WorkshopSidebarMetrics.Side.LEFT, metrics.side());
		assertEquals(210, metrics.panel().width());
		assertTrue(metrics.panel().right() <= 300 - WorkshopSidebarMetrics.EDGE_GAP);
	}

	@Test
	void recipeBookPreventsUsingLeftSide() {
		WorkshopSidebarMetrics metrics = WorkshopSidebarMetrics.calculate(
			500, 300, 300, 60, 176, 166, true, true, false, 210
		);

		assertTrue(metrics.collapsed());
		assertFalse(metrics.side() == WorkshopSidebarMetrics.Side.LEFT);
	}

	@Test
	void availableSideSpaceCompressesPanelWithoutLeavingScreen() {
		WorkshopSidebarMetrics metrics = WorkshopSidebarMetrics.calculate(
			520, 300, 180, 60, 176, 166, false, true, false, 260
		);

		assertFalse(metrics.collapsed());
		assertTrue(metrics.panel().width() >= WorkshopSidebarMetrics.MIN_PANEL_WIDTH);
		assertTrue(metrics.panel().width() < 260);
		assertTrue(metrics.panel().left() >= WorkshopSidebarMetrics.EDGE_GAP);
		assertTrue(metrics.panel().right() <= 520 - WorkshopSidebarMetrics.EDGE_GAP);
	}

	@Test
	void noUsableSideSpaceFallsBackToCollapsedTab() {
		WorkshopSidebarMetrics metrics = WorkshopSidebarMetrics.calculate(
			256, 192, 40, 13, 176, 166, false, true, true, 210
		);

		assertTrue(metrics.collapsed());
		assertEquals(WorkshopSidebarMetrics.COLLAPSED_WIDTH, metrics.panel().width());
		assertTrue(metrics.panel().left() >= WorkshopSidebarMetrics.EDGE_GAP);
		assertTrue(metrics.panel().right() <= 256 - WorkshopSidebarMetrics.EDGE_GAP);
	}

	@Test
	void threeModeButtonsAndFourActionsNeverOverlapAtSupportedWidths() {
		for (int width : List.of(154, 180, 210, 280)) {
			WorkshopLabelEditorLayout layout = WorkshopLabelEditorLayout.calculate(
				new WorkshopSidebarMetrics.Rect(5, 5, width, 300), 54, 3, 4, 2
			);
			assertEquals(3, layout.modeButtons().size());
			assertEquals(4, layout.actionButtons().size());
			assertNoOverlap(layout.modeButtons());
			assertNoOverlap(layout.actionButtons());
			layout.modeButtons().forEach(rect -> assertInside(rect, layout.content()));
			layout.actionButtons().forEach(rect -> assertInside(rect, layout.content()));
			assertTrue(layout.listArea().bottom() <= layout.statusArea().top());
			assertTrue(layout.statusArea().bottom() <= layout.actionArea().top());
		}
	}

	@Test
	void layoutModeRespondsToActualPanelWidth() {
		assertEquals(WorkshopSidebarLayoutMode.NARROW, WorkshopSidebarLayoutMode.forWidth(154));
		assertEquals(WorkshopSidebarLayoutMode.COMPACT, WorkshopSidebarLayoutMode.forWidth(190));
		assertEquals(WorkshopSidebarLayoutMode.STANDARD, WorkshopSidebarLayoutMode.forWidth(240));
	}

	@Test
	void wrappedTextHeightUsesVisibleLineCount() {
		assertEquals(18, WorkshopTextLayout.heightForLines(2, 4, 9));
		assertEquals(27, WorkshopTextLayout.heightForLines(8, 3, 9));
		assertEquals(0, WorkshopTextLayout.heightForLines(0, 3, 9));
	}

	private static void assertNoOverlap(List<WorkshopSidebarMetrics.Rect> rectangles) {
		HashSet<String> seen = new HashSet<>();
		for (int first = 0; first < rectangles.size(); first++) {
			WorkshopSidebarMetrics.Rect a = rectangles.get(first);
			assertTrue(a.width() > 0 && a.height() > 0);
			assertTrue(seen.add(a.left() + ":" + a.top() + ":" + a.width() + ":" + a.height()));
			for (int second = first + 1; second < rectangles.size(); second++) {
				WorkshopSidebarMetrics.Rect b = rectangles.get(second);
				assertFalse(a.intersects(b), () -> a + " overlaps " + b);
			}
		}
	}

	private static void assertInside(WorkshopSidebarMetrics.Rect inner, WorkshopSidebarMetrics.Rect outer) {
		assertTrue(inner.left() >= outer.left());
		assertTrue(inner.top() >= outer.top());
		assertTrue(inner.right() <= outer.right());
		assertTrue(inner.bottom() <= outer.bottom());
	}
}
