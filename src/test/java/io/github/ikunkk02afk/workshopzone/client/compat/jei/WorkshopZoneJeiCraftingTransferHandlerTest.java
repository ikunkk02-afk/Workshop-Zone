package io.github.ikunkk02afk.workshopzone.client.compat.jei;

import io.github.ikunkk02afk.workshopzone.client.compat.recipeviewer.RecipeViewerTransferResult;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class WorkshopZoneJeiCraftingTransferHandlerTest {
	private static final Identifier RECIPE_ID = Identifier.ofVanilla("oak_planks");

	private FakeBridge bridge;
	private IRecipeTransferError userError;
	private IRecipeTransferHandlerHelper helper;
	private WorkshopZoneJeiCraftingTransferHandler handler;

	@BeforeEach
	void setUp() {
		bridge = new FakeBridge();
		userError = () -> IRecipeTransferError.Type.USER_FACING;
		helper = (IRecipeTransferHandlerHelper)Proxy.newProxyInstance(
			getClass().getClassLoader(),
			new Class<?>[]{IRecipeTransferHandlerHelper.class},
			(proxy, method, args) -> switch (method.getName()) {
				case "createUserErrorWithTooltip", "createUserErrorForMissingSlots" -> userError;
				case "createInternalError" -> (IRecipeTransferError)(() -> IRecipeTransferError.Type.INTERNAL);
				default -> null;
			}
		);
		handler = new WorkshopZoneJeiCraftingTransferHandler(helper, bridge);
	}

	@Test
	void simulationValidatesButNeverSubmitsRequest() {
		assertNull(handler.transferRecipeId(RECIPE_ID, false, false));
		assertEquals(List.of(RECIPE_ID), bridge.validatedRecipeIds);
		assertEquals(List.of(), bridge.requestedBatches);
	}

	@Test
	void actualSingleTransferMapsMaxTransferFalse() {
		assertNull(handler.transferRecipeId(RECIPE_ID, false, true));
		assertEquals(List.of(false), bridge.requestedBatches);
		assertEquals(List.of(RECIPE_ID), bridge.requestedRecipeIds);
	}

	@Test
	void actualBatchTransferMapsMaxTransferTrue() {
		assertNull(handler.transferRecipeId(RECIPE_ID, true, true));
		assertEquals(List.of(true), bridge.requestedBatches);
	}

	@Test
	void duplicateActualCallbackIsHandledWithoutSecondVanillaRequest() {
		bridge.requestResult = RecipeViewerTransferResult.DUPLICATE_REQUEST;
		assertNull(handler.transferRecipeId(RECIPE_ID, false, true));
	}

	@Test
	void invalidRecipeHolderReturnsJeiUserError() {
		assertSame(userError, handler.transferRecipe(null, null, null, null, false, false));
		assertEquals(List.of(), bridge.validatedRecipeIds);
	}

	@Test
	void unsupportedRecipeReturnsJeiUserErrorDuringSimulation() {
		bridge.validationResult = RecipeViewerTransferResult.UNSUPPORTED_RECIPE;
		assertSame(userError, handler.transferRecipeId(RECIPE_ID, false, false));
		assertEquals(List.of(), bridge.requestedBatches);
	}

	private static final class FakeBridge implements WorkshopZoneJeiCraftingTransferHandler.BridgeAccess {
		private RecipeViewerTransferResult validationResult = RecipeViewerTransferResult.READY;
		private RecipeViewerTransferResult requestResult = RecipeViewerTransferResult.REQUEST_SENT;
		private final List<Identifier> validatedRecipeIds = new ArrayList<>();
		private final List<Identifier> requestedRecipeIds = new ArrayList<>();
		private final List<Boolean> requestedBatches = new ArrayList<>();

		@Override
		public RecipeViewerTransferResult validate(Identifier recipeId) {
			validatedRecipeIds.add(recipeId);
			return validationResult;
		}

		@Override
		public RecipeViewerTransferResult request(Identifier recipeId, boolean batch) {
			requestedRecipeIds.add(recipeId);
			requestedBatches.add(batch);
			return requestResult;
		}
	}
}
