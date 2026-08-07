package io.github.ikunkk02afk.workshopzone.client.compat.rei;

import io.github.ikunkk02afk.workshopzone.client.compat.recipeviewer.RecipeViewerTransferResult;
import me.shedaniel.rei.api.client.registry.transfer.TransferHandler;
import me.shedaniel.rei.api.client.registry.transfer.TransferHandlerRegistry;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkshopZoneReiCraftingTransferHandlerTest {
	private static final Identifier RECIPE_ID = Identifier.ofVanilla("oak_planks");

	private FakeBridge bridge;
	private FakeContext context;
	private WorkshopZoneReiCraftingTransferHandler handler;

	@BeforeEach
	void setUp() {
		bridge = new FakeBridge();
		context = new FakeContext();
		handler = new WorkshopZoneReiCraftingTransferHandler(bridge);
	}

	@Test
	void workshopHandlerHasHigherPriorityThanReiDefaultHandler() {
		assertEquals(100.0d, handler.getPriority());
	}

	@Test
	void nonCraftingScreenCategoryOrMissingDisplayLocationIsNotApplicable() {
		context.craftingScreen = false;
		assertFalse(handler.checkApplicable(context).isApplicable());
		context.craftingScreen = true;
		context.craftingHandler = false;
		assertFalse(handler.checkApplicable(context).isApplicable());
		context.craftingHandler = true;
		context.craftingCategory = false;
		assertFalse(handler.checkApplicable(context).isApplicable());
		context.craftingCategory = true;
		context.recipeId = null;
		assertFalse(handler.checkApplicable(context).isApplicable());
	}

	@Test
	void unsupportedOrUnknownRecipeAllowsOtherReiHandlers() {
		bridge.validationResult = RecipeViewerTransferResult.UNSUPPORTED_RECIPE;
		TransferHandler.ApplicabilityResult result = handler.checkApplicable(context);
		assertFalse(result.isApplicable());
	}

	@Test
	void simulationCheckNeverSubmitsRequest() {
		context.actuallyCrafting = false;
		TransferHandler.Result result = handler.handle(context);
		assertTrue(result.isSuccessful());
		assertTrue(result.isBlocking());
		assertTrue(bridge.requestedBatches.isEmpty());
	}

	@Test
	void actualSingleTransferMapsStackedCraftingFalse() {
		TransferHandler.Result result = handler.handle(context);
		assertEquals(List.of(false), bridge.requestedBatches);
		assertEquals(List.of(RECIPE_ID), bridge.requestedRecipeIds);
		assertTrue(result.isSuccessful());
		assertTrue(result.isBlocking());
		assertTrue(result.isReturningToScreen());
	}

	@Test
	void actualBatchTransferMapsStackedCraftingTrue() {
		context.stackedCrafting = true;
		TransferHandler.Result result = handler.handle(context);
		assertEquals(List.of(true), bridge.requestedBatches);
		assertTrue(result.isSuccessful());
		assertTrue(result.isBlocking());
	}

	@Test
	void duplicateActualCallbackIsHandledWithoutAnotherViewerFallback() {
		bridge.requestResult = RecipeViewerTransferResult.DUPLICATE_REQUEST;
		TransferHandler.Result result = handler.handle(context);
		assertTrue(result.isSuccessful());
		assertTrue(result.isBlocking());
	}

	@Test
	void gridErrorBlocksDefaultReiTransferWithoutMovingAnything() {
		bridge.requestResult = RecipeViewerTransferResult.GRID_NOT_EMPTY;
		TransferHandler.Result result = handler.handle(context);
		assertFalse(result.isSuccessful());
		assertTrue(result.isBlocking());
		assertTrue(result.isReturningToScreen());
	}

	@Test
	void structurallyInapplicableHandleLetsOtherHandlersRun() {
		context.craftingCategory = false;
		TransferHandler.Result result = handler.handle(context);
		assertFalse(result.isApplicable());
		assertFalse(result.isBlocking());
		assertTrue(bridge.requestedBatches.isEmpty());
	}

	@Test
	void pluginRegistersWorkshopTransferHandler() {
		List<TransferHandler> handlers = new ArrayList<>();
		TransferHandlerRegistry registry = (TransferHandlerRegistry)Proxy.newProxyInstance(
			getClass().getClassLoader(),
			new Class<?>[]{TransferHandlerRegistry.class},
			(proxy, method, args) -> {
				if (method.getName().equals("register")) {
					handlers.add((TransferHandler)args[0]);
				}
				return null;
			}
		);
		new WorkshopZoneReiClientPlugin().registerTransferHandlers(registry);
		assertEquals(1, handlers.size());
		assertInstanceOf(WorkshopZoneReiCraftingTransferHandler.class, handlers.getFirst());
	}

	private static final class FakeContext implements WorkshopZoneReiCraftingTransferHandler.ContextAccess {
		private boolean craftingScreen = true;
		private boolean craftingHandler = true;
		private boolean craftingCategory = true;
		private Identifier recipeId = RECIPE_ID;
		private boolean actuallyCrafting = true;
		private boolean stackedCrafting;

		@Override
		public boolean isCraftingScreen() {
			return craftingScreen;
		}

		@Override
		public boolean hasCraftingScreenHandler() {
			return craftingHandler;
		}

		@Override
		public net.minecraft.screen.CraftingScreenHandler craftingHandler() {
			return null;
		}

		@Override
		public boolean isCraftingCategory() {
			return craftingCategory;
		}

		@Override
		public Identifier recipeId() {
			return recipeId;
		}

		@Override
		public boolean isActuallyCrafting() {
			return actuallyCrafting;
		}

		@Override
		public boolean isStackedCrafting() {
			return stackedCrafting;
		}
	}

	private static final class FakeBridge implements WorkshopZoneReiCraftingTransferHandler.BridgeAccess {
		private RecipeViewerTransferResult validationResult = RecipeViewerTransferResult.READY;
		private RecipeViewerTransferResult requestResult = RecipeViewerTransferResult.REQUEST_SENT;
		private final List<Identifier> requestedRecipeIds = new ArrayList<>();
		private final List<Boolean> requestedBatches = new ArrayList<>();

		@Override
		public RecipeViewerTransferResult validate(
			Identifier recipeId,
			net.minecraft.screen.CraftingScreenHandler handler
		) {
			return validationResult;
		}

		@Override
		public RecipeViewerTransferResult request(
			Identifier recipeId,
			boolean batch,
			net.minecraft.screen.CraftingScreenHandler handler
		) {
			requestedRecipeIds.add(recipeId);
			requestedBatches.add(batch);
			return requestResult;
		}
	}
}
