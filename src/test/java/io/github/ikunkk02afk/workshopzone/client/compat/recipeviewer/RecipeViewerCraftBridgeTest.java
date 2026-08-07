package io.github.ikunkk02afk.workshopzone.client.compat.recipeviewer;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeViewerCraftBridgeTest {
	private static final Identifier RECIPE = Identifier.ofVanilla("oak_planks");
	private FakeClientAccess client;
	private RecipeViewerTransferGuard guard;

	@BeforeEach
	void setUp() {
		client = new FakeClientAccess();
		guard = new RecipeViewerTransferGuard();
	}

	@Test
	void missingMinecraftClientIsRejected() {
		client.clientPresent = false;
		assertEquals(RecipeViewerTransferResult.NO_CLIENT, request(RECIPE, false));
	}

	@Test
	void missingPlayerIsRejected() {
		client.playerPresent = false;
		assertEquals(RecipeViewerTransferResult.NO_PLAYER, request(RECIPE, false));
	}

	@Test
	void missingInteractionManagerIsRejected() {
		client.interactionManagerPresent = false;
		assertEquals(RecipeViewerTransferResult.NO_INTERACTION_MANAGER, request(RECIPE, false));
	}

	@Test
	void nonCraftingScreenIsRejected() {
		client.craftingScreen = false;
		assertEquals(RecipeViewerTransferResult.INVALID_SCREEN, request(RECIPE, false));
	}

	@Test
	void nonCraftingScreenHandlerIsRejected() {
		client.craftingHandler = false;
		assertEquals(RecipeViewerTransferResult.INVALID_SCREEN, request(RECIPE, false));
	}

	@Test
	void missingAndAirRecipeIdsAreRejected() {
		assertEquals(RecipeViewerTransferResult.INVALID_RECIPE, request(null, false));
		assertEquals(RecipeViewerTransferResult.INVALID_RECIPE, request(Identifier.ofVanilla("air"), false));
	}

	@Test
	void unknownRecipeIsRejected() {
		client.recipeStatus = RecipeViewerCraftBridge.RecipeStatus.UNKNOWN;
		assertEquals(RecipeViewerTransferResult.INVALID_RECIPE, request(RECIPE, false));
	}

	@Test
	void nonCraftingRecipeIsRejected() {
		client.recipeStatus = RecipeViewerCraftBridge.RecipeStatus.UNSUPPORTED;
		assertEquals(RecipeViewerTransferResult.UNSUPPORTED_RECIPE, request(RECIPE, false));
	}

	@Test
	void emptyOrAirRecipeOutputIsRejected() {
		client.recipeStatus = RecipeViewerCraftBridge.RecipeStatus.INVALID_OUTPUT;
		assertEquals(RecipeViewerTransferResult.INVALID_RECIPE, request(RECIPE, false));
	}

	@Test
	void nonEmptyCraftingGridIsRejectedWithoutClicking() {
		client.gridEmpty = false;
		assertEquals(RecipeViewerTransferResult.GRID_NOT_EMPTY, request(RECIPE, false));
		assertTrue(client.clickedBatchValues.isEmpty());
	}

	@Test
	void validationChecksTheLiveContextWithoutSubmittingOrConsumingDuplicateState() {
		assertEquals(RecipeViewerTransferResult.READY, RecipeViewerCraftBridge.validate(RECIPE, client));
		assertTrue(client.clickedBatchValues.isEmpty());
		assertEquals(RecipeViewerTransferResult.REQUEST_SENT, request(RECIPE, false));
	}

	@Test
	void singleRequestUsesVanillaClickRecipeWithBatchFalse() {
		assertEquals(RecipeViewerTransferResult.REQUEST_SENT, request(RECIPE, false));
		assertEquals(List.of(false), client.clickedBatchValues);
	}

	@Test
	void batchRequestUsesVanillaClickRecipeWithBatchTrue() {
		assertEquals(RecipeViewerTransferResult.REQUEST_SENT, request(RECIPE, true));
		assertEquals(List.of(true), client.clickedBatchValues);
	}

	@Test
	void identicalRequestWithinFiveTicksIsSuppressed() {
		assertEquals(RecipeViewerTransferResult.REQUEST_SENT, request(RECIPE, false));
		client.tick = 104;
		assertEquals(RecipeViewerTransferResult.DUPLICATE_REQUEST, request(RECIPE, false));
		assertEquals(1, client.clickedBatchValues.size());
	}

	@Test
	void identicalRequestAtFiveTickBoundaryIsAllowed() {
		assertEquals(RecipeViewerTransferResult.REQUEST_SENT, request(RECIPE, false));
		client.tick = 105;
		assertEquals(RecipeViewerTransferResult.REQUEST_SENT, request(RECIPE, false));
		assertEquals(2, client.clickedBatchValues.size());
	}

	@Test
	void differentRecipeIdIsNotSuppressed() {
		assertEquals(RecipeViewerTransferResult.REQUEST_SENT, request(RECIPE, false));
		assertEquals(RecipeViewerTransferResult.REQUEST_SENT, request(Identifier.ofVanilla("stick"), false));
		assertEquals(2, client.clickedBatchValues.size());
	}

	@Test
	void differentSyncIdIsNotSuppressed() {
		assertEquals(RecipeViewerTransferResult.REQUEST_SENT, request(RECIPE, false));
		client.syncId++;
		assertEquals(RecipeViewerTransferResult.REQUEST_SENT, request(RECIPE, false));
	}

	@Test
	void newScreenClearsDuplicateState() {
		assertEquals(RecipeViewerTransferResult.REQUEST_SENT, request(RECIPE, false));
		client.screenIdentity = new Object();
		assertEquals(RecipeViewerTransferResult.REQUEST_SENT, request(RECIPE, false));
	}

	@Test
	void disconnectResetClearsDuplicateState() {
		assertEquals(RecipeViewerTransferResult.REQUEST_SENT, request(RECIPE, false));
		guard.clear();
		assertEquals(RecipeViewerTransferResult.REQUEST_SENT, request(RECIPE, false));
	}

	@Test
	void bridgeOnlySubmitsVanillaRecipeRequestAndNeverMutatesSlots() {
		assertEquals(RecipeViewerTransferResult.REQUEST_SENT, request(RECIPE, false));
		assertEquals(1, client.recipeLookups);
		assertEquals(1, client.gridChecks);
		assertEquals(0, client.slotMutations);
		assertFalse(client.clickedBatchValues.isEmpty());
	}

	private RecipeViewerTransferResult request(Identifier recipeId, boolean batch) {
		return RecipeViewerCraftBridge.request(
			RecipeViewerSource.JEI,
			recipeId,
			batch,
			client,
			guard
		);
	}

	private static final class FakeClientAccess implements RecipeViewerCraftBridge.ClientAccess {
		private boolean clientPresent = true;
		private boolean playerPresent = true;
		private boolean interactionManagerPresent = true;
		private boolean craftingScreen = true;
		private boolean craftingHandler = true;
		private boolean gridEmpty = true;
		private int syncId = 7;
		private long tick = 100;
		private Object screenIdentity = new Object();
		private RecipeViewerCraftBridge.RecipeStatus recipeStatus = RecipeViewerCraftBridge.RecipeStatus.VALID;
		private final List<Boolean> clickedBatchValues = new ArrayList<>();
		private int recipeLookups;
		private int gridChecks;
		private int slotMutations;

		@Override
		public boolean hasClient() {
			return clientPresent;
		}

		@Override
		public boolean hasPlayer() {
			return playerPresent;
		}

		@Override
		public boolean hasInteractionManager() {
			return interactionManagerPresent;
		}

		@Override
		public boolean isCraftingScreen() {
			return craftingScreen;
		}

		@Override
		public boolean hasCraftingScreenHandler() {
			return craftingHandler;
		}

		@Override
		public RecipeViewerCraftBridge.RecipeStatus resolveRecipe(Identifier recipeId) {
			recipeLookups++;
			return recipeStatus;
		}

		@Override
		public boolean isCraftingGridEmpty() {
			gridChecks++;
			return gridEmpty;
		}

		@Override
		public int syncId() {
			return syncId;
		}

		@Override
		public long clientTick() {
			return tick;
		}

		@Override
		public Object screenIdentity() {
			return screenIdentity;
		}

		@Override
		public void clickRecipe(boolean batch) {
			clickedBatchValues.add(batch);
		}
	}
}
