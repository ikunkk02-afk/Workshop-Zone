package io.github.ikunkk02afk.workshopzone.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkshopSidebarPlacementResolverTest {
	@Test
	void autoWithoutRecipeViewerPrefersRight() {
		WorkshopSidebarPlacement placement = resolve(1920, 1080, 700, 400, 176, 166, false,
			WorkshopSidebarPosition.AUTO, true, false, 0.5, 0.1);

		assertEquals(WorkshopSidebarPosition.RIGHT, placement.resolvedPosition());
		assertFalse(placement.collapsed());
	}

	@Test
	void autoWithRecipeViewerPrefersTop() {
		WorkshopSidebarPlacement placement = resolve(1920, 1080, 700, 400, 176, 166, false,
			WorkshopSidebarPosition.AUTO, true, true, 0.5, 0.1);

		assertEquals(WorkshopSidebarPosition.TOP, placement.resolvedPosition());
		assertTrue(placement.panel().bottom() <= 400 - WorkshopSidebarMetrics.EDGE_GAP);
	}

	@Test
	void topSpaceShortageFallsBackToLeft() {
		WorkshopSidebarPlacement placement = resolve(900, 360, 500, 28, 176, 166, false,
			WorkshopSidebarPosition.AUTO, true, true, 0.5, 0.1);

		assertEquals(WorkshopSidebarPosition.LEFT, placement.resolvedPosition());
		assertTrue(placement.fallbackUsed());
	}

	@Test
	void recipeBookBlocksLeftAndFallsBackToBottom() {
		WorkshopSidebarPlacement placement = resolve(900, 420, 500, 28, 176, 166, true,
			WorkshopSidebarPosition.AUTO, true, true, 0.5, 0.1);

		assertEquals(WorkshopSidebarPosition.BOTTOM, placement.resolvedPosition());
		assertTrue(placement.panel().top() >= 28 + 166 + WorkshopSidebarMetrics.EDGE_GAP);
	}

	@Test
	void noUsableAreaUsesCollapsedTab() {
		WorkshopSidebarPlacement placement = resolve(256, 192, 40, 13, 176, 166, false,
			WorkshopSidebarPosition.AUTO, true, true, 0.5, 0.1);

		assertTrue(placement.collapsed());
		assertEquals(WorkshopSidebarMetrics.COLLAPSED_WIDTH, placement.panel().width());
	}

	@Test
	void collapsedAutoWithRecipeViewerAvoidsRightWhenAnotherTabPositionFits() {
		WorkshopSidebarPlacement placement = resolve(256, 192, 40, 13, 176, 166, false,
			WorkshopSidebarPosition.AUTO, true, true, 0.5, 0.1);

		assertTrue(placement.collapsed());
		assertEquals(WorkshopSidebarPosition.LEFT, placement.resolvedPosition());
	}

	@Test
	void collapsedAutoWithoutRecipeViewerKeepsRightPriority() {
		WorkshopSidebarPlacement placement = resolve(256, 192, 40, 13, 176, 166, false,
			WorkshopSidebarPosition.AUTO, true, false, 0.5, 0.1);

		assertTrue(placement.collapsed());
		assertEquals(WorkshopSidebarPosition.RIGHT, placement.resolvedPosition());
	}

	@Test
	void explicitRightIsRespectedWhenJeiIsPresent() {
		WorkshopSidebarPlacement placement = resolve(1920, 1080, 700, 400, 176, 166, false,
			WorkshopSidebarPosition.RIGHT, true, true, 0.5, 0.1);

		assertEquals(WorkshopSidebarPosition.RIGHT, placement.resolvedPosition());
	}

	@Test
	void explicitLeftPrefersLeft() {
		WorkshopSidebarPlacement placement = resolve(1200, 700, 600, 250, 176, 166, false,
			WorkshopSidebarPosition.LEFT, true, false, 0.5, 0.1);

		assertEquals(WorkshopSidebarPosition.LEFT, placement.resolvedPosition());
	}

	@Test
	void autoAvoidDisabledKeepsLegacyRightPriority() {
		WorkshopSidebarPlacement placement = resolve(1920, 1080, 700, 400, 176, 166, false,
			WorkshopSidebarPosition.AUTO, false, true, 0.5, 0.1);

		assertEquals(WorkshopSidebarPosition.RIGHT, placement.resolvedPosition());
	}

	@Test
	void customCoordinatesAreClampedToNormalizedRange() {
		WorkshopSidebarPlacement placement = resolve(1000, 700, 400, 250, 176, 166, false,
			WorkshopSidebarPosition.CUSTOM, true, false, -4.0, 8.0);

		assertEquals(WorkshopSidebarMetrics.EDGE_GAP, placement.panel().left());
		assertEquals(700 - WorkshopSidebarMetrics.EDGE_GAP - placement.panel().height(), placement.panel().top());
		assertTrue(placement.constrained());
	}

	@Test
	void customPositionKeepsRelativeLocationAcrossResolutions() {
		WorkshopSidebarPlacement small = resolve(800, 600, 300, 200, 176, 166, false,
			WorkshopSidebarPosition.CUSTOM, true, false, 0.75, 0.25);
		WorkshopSidebarPlacement large = resolve(1600, 1200, 600, 400, 176, 166, false,
			WorkshopSidebarPosition.CUSTOM, true, false, 0.75, 0.25);

		double smallX = normalizedX(small, 800);
		double largeX = normalizedX(large, 1600);
		assertEquals(smallX, largeX, 0.01);
	}

	@Test
	void topPanelIsCenteredOnVanillaGuiAndDoesNotOverlapIt() {
		WorkshopSidebarPlacement placement = resolve(1200, 800, 500, 300, 176, 166, false,
			WorkshopSidebarPosition.TOP, true, false, 0.5, 0.1);

		assertEquals(500 + 176 / 2, placement.panel().left() + placement.panel().width() / 2);
		assertFalse(placement.panel().intersects(new WorkshopSidebarMetrics.Rect(500, 300, 176, 166)));
	}

	@Test
	void bottomPanelIsCenteredOnVanillaGuiAndDoesNotOverlapIt() {
		WorkshopSidebarPlacement placement = resolve(1200, 900, 500, 260, 176, 166, false,
			WorkshopSidebarPosition.BOTTOM, true, false, 0.5, 0.1);

		assertEquals(500 + 176 / 2, placement.panel().left() + placement.panel().width() / 2);
		assertFalse(placement.panel().intersects(new WorkshopSidebarMetrics.Rect(500, 260, 176, 166)));
	}

	@Test
	void recipeBookOpenNeverUsesLeftFallback() {
		WorkshopSidebarPlacement placement = resolve(680, 300, 300, 30, 176, 166, true,
			WorkshopSidebarPosition.LEFT, true, false, 0.5, 0.1);

		assertFalse(placement.resolvedPosition() == WorkshopSidebarPosition.LEFT);
	}

	@Test
	void exclusionAreaEqualsActualPanelBounds() {
		WorkshopSidebarPlacement placement = resolve(1200, 800, 500, 300, 176, 166, false,
			WorkshopSidebarPosition.TOP, true, true, 0.5, 0.1);

		assertEquals(placement.panel(), placement.exclusionArea());
	}

	@Test
	void collapsedExclusionAreaContainsOnlyCollapsedButton() {
		WorkshopSidebarPlacement placement = resolve(256, 192, 40, 13, 176, 166, false,
			WorkshopSidebarPosition.AUTO, true, true, 0.5, 0.1);

		assertTrue(placement.collapsed());
		assertEquals(WorkshopSidebarMetrics.COLLAPSED_WIDTH, placement.exclusionArea().width());
		assertEquals(20, placement.exclusionArea().height());
	}

	@Test
	void dragAreaIsTitleBarMinusControlButtons() {
		WorkshopSidebarPlacement placement = resolve(1200, 800, 500, 300, 176, 166, false,
			WorkshopSidebarPosition.CUSTOM, true, false, 0.5, 0.1);

		assertEquals(WorkshopSidebarMetrics.HEADER_HEIGHT, placement.dragArea().height());
		assertTrue(placement.dragArea().width() < placement.panel().width());
	}

	private static WorkshopSidebarPlacement resolve(
		int screenWidth, int screenHeight, int guiX, int guiY, int guiWidth, int guiHeight,
		boolean recipeBookOpen, WorkshopSidebarPosition position, boolean autoAvoid,
		boolean viewerDetected, double customX, double customY
	) {
		return WorkshopSidebarPlacementResolver.resolve(new WorkshopSidebarPlacementResolver.Input(
			screenWidth, screenHeight, guiX, guiY, guiWidth, guiHeight, recipeBookOpen,
			position, autoAvoid, viewerDetected, true, false,
			WorkshopSidebarMetrics.PREFERRED_PANEL_WIDTH, customX, customY
		));
	}

	private static double normalizedX(WorkshopSidebarPlacement placement, int screenWidth) {
		int available = screenWidth - WorkshopSidebarMetrics.EDGE_GAP * 2 - placement.panel().width();
		return available == 0 ? 0.0 : (double)(placement.panel().left() - WorkshopSidebarMetrics.EDGE_GAP) / available;
	}
}
