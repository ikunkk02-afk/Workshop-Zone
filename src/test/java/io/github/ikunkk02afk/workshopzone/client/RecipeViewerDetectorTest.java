package io.github.ikunkk02afk.workshopzone.client;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeViewerDetectorTest {
	@Test
	void emptyModSetDetectsNothing() {
		assertTrue(RecipeViewerDetector.detect(Set.of()).isEmpty());
	}

	@Test
	void detectsJeiByExactModId() {
		assertEquals(Set.of(DetectedRecipeViewer.JEI), RecipeViewerDetector.detect(Set.of("jei")));
	}

	@Test
	void detectsEmiByExactModId() {
		assertEquals(Set.of(DetectedRecipeViewer.EMI), RecipeViewerDetector.detect(Set.of("emi")));
	}

	@Test
	void detectsReiByExactModId() {
		assertEquals(Set.of(DetectedRecipeViewer.REI), RecipeViewerDetector.detect(Set.of("roughlyenoughitems")));
	}

	@Test
	void recordsMultipleViewersWithoutChoosingOne() {
		assertEquals(
			Set.of(DetectedRecipeViewer.JEI, DetectedRecipeViewer.EMI),
			RecipeViewerDetector.detect(Set.of("jei", "emi"))
		);
	}

	@Test
	void addonIdsAreNotMisidentifiedAsMainViewers() {
		assertTrue(RecipeViewerDetector.detect(Set.of(
			"jei_addon", "emi_loot", "roughlyenoughitems-addon"
		)).isEmpty());
	}

	@Test
	void viewerStableIdsUseWorkshopZoneNamespace() {
		assertEquals("workshop_zone:jei", DetectedRecipeViewer.JEI.stableId());
		assertEquals("workshop_zone:emi", DetectedRecipeViewer.EMI.stableId());
		assertEquals("workshop_zone:rei", DetectedRecipeViewer.REI.stableId());
	}
}
