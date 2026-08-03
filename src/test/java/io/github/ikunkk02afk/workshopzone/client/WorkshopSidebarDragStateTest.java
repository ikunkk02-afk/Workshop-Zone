package io.github.ikunkk02afk.workshopzone.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkshopSidebarDragStateTest {
	private static final WorkshopSidebarMetrics.Rect PANEL = new WorkshopSidebarMetrics.Rect(200, 120, 210, 166);
	private static final WorkshopSidebarMetrics.Rect TITLE = new WorkshopSidebarMetrics.Rect(200, 120, 210, 54);
	private static final WorkshopSidebarMetrics.Rect BUTTON = new WorkshopSidebarMetrics.Rect(390, 124, 16, 16);

	@Test
	void dragCannotStartFromControlButton() {
		WorkshopSidebarDragState state = new WorkshopSidebarDragState();

		assertFalse(state.beginDrag(398, 130, PANEL, TITLE, List.of(BUTTON)));
		assertFalse(state.dragging());
	}

	@Test
	void dragStartsOnlyFromTitleBarAndStaysOnScreen() {
		WorkshopSidebarDragState state = new WorkshopSidebarDragState();
		assertTrue(state.beginDrag(230, 140, PANEL, TITLE, List.of(BUTTON)));

		WorkshopSidebarMetrics.Rect moved = state.updateDrag(-500, -500, 800, 600);

		assertEquals(WorkshopSidebarMetrics.EDGE_GAP, moved.left());
		assertEquals(WorkshopSidebarMetrics.EDGE_GAP, moved.top());
	}

	@Test
	void customCoordinatesAreProducedOnlyWhenDragFinishes() {
		WorkshopSidebarDragState state = new WorkshopSidebarDragState();
		assertTrue(state.beginDrag(230, 140, PANEL, TITLE, List.of(BUTTON)));
		state.updateDrag(430, 340, 800, 600);

		assertTrue(state.previewPosition().isEmpty());
		assertTrue(state.finishDrag(800, 600).isPresent());
		assertFalse(state.dragging());
	}

	@Test
	void cancelRestoresOriginalPanelAndProducesNoSavedPosition() {
		WorkshopSidebarDragState state = new WorkshopSidebarDragState();
		assertTrue(state.beginDrag(230, 140, PANEL, TITLE, List.of(BUTTON)));
		state.updateDrag(500, 400, 800, 600);

		assertEquals(PANEL, state.cancelDrag());
		assertTrue(state.previewPosition().isEmpty());
		assertFalse(state.dragging());
	}
}
