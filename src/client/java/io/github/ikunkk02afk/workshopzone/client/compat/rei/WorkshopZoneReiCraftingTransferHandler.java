package io.github.ikunkk02afk.workshopzone.client.compat.rei;

import io.github.ikunkk02afk.workshopzone.client.compat.recipeviewer.RecipeViewerCraftBridge;
import io.github.ikunkk02afk.workshopzone.client.compat.recipeviewer.RecipeViewerSource;
import io.github.ikunkk02afk.workshopzone.client.compat.recipeviewer.RecipeViewerTransferResult;
import me.shedaniel.rei.api.client.registry.transfer.TransferHandler;
import me.shedaniel.rei.api.common.display.Display;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class WorkshopZoneReiCraftingTransferHandler implements TransferHandler {
	private static final double PRIORITY = 100.0d;
	private static final Identifier CRAFTING_CATEGORY_ID = Identifier.of("minecraft", "plugins/crafting");

	private final BridgeAccess bridge;

	public WorkshopZoneReiCraftingTransferHandler() {
		this(new BridgeAccess() {
			@Override
			public RecipeViewerTransferResult validate(Identifier recipeId) {
				return RecipeViewerCraftBridge.validate(recipeId);
			}

			@Override
			public RecipeViewerTransferResult request(Identifier recipeId, boolean batch) {
				return RecipeViewerCraftBridge.request(RecipeViewerSource.REI, recipeId, batch);
			}
		});
	}

	WorkshopZoneReiCraftingTransferHandler(BridgeAccess bridge) {
		this.bridge = bridge;
	}

	@Override
	public double getPriority() {
		return PRIORITY;
	}

	@Override
	public ApplicabilityResult checkApplicable(Context context) {
		return checkApplicable(new ReiContextAccess(context));
	}

	ApplicabilityResult checkApplicable(ContextAccess context) {
		if (!isStructurallyApplicable(context)) {
			return ApplicabilityResult.createNotApplicable();
		}
		RecipeViewerTransferResult validation = bridge.validate(context.recipeId());
		return switch (validation) {
			case READY -> ApplicabilityResult.createApplicable();
			case GRID_NOT_EMPTY, NO_CLIENT, NO_PLAYER, NO_INTERACTION_MANAGER, INVALID_SCREEN, INTERNAL_ERROR ->
				ApplicabilityResult.createApplicableWithError(failed(validation));
			default -> ApplicabilityResult.createNotApplicable();
		};
	}

	@Override
	public Result handle(Context context) {
		return handle(new ReiContextAccess(context));
	}

	Result handle(ContextAccess context) {
		if (!isStructurallyApplicable(context)) {
			return Result.createNotApplicable();
		}
		RecipeViewerTransferResult result = context.isActuallyCrafting()
			? bridge.request(context.recipeId(), context.isStackedCrafting())
			: bridge.validate(context.recipeId());
		return switch (result) {
			case READY, REQUEST_SENT, DUPLICATE_REQUEST -> Result.createSuccessful().blocksFurtherHandling(true);
			case INVALID_RECIPE, UNSUPPORTED_RECIPE, NOT_APPLICABLE -> Result.createNotApplicable();
			default -> failed(result);
		};
	}

	private static boolean isStructurallyApplicable(ContextAccess context) {
		return context != null
			&& context.isCraftingScreen()
			&& context.hasCraftingScreenHandler()
			&& context.isCraftingCategory()
			&& context.recipeId() != null;
	}

	private static Result failed(RecipeViewerTransferResult result) {
		String translationKey = switch (result) {
			case GRID_NOT_EMPTY -> "gui.workshop_zone.recipe_viewer.grid_not_empty";
			case INVALID_RECIPE -> "gui.workshop_zone.recipe_viewer.invalid_recipe";
			case UNSUPPORTED_RECIPE, NOT_APPLICABLE -> "gui.workshop_zone.recipe_viewer.unsupported_recipe";
			case DUPLICATE_REQUEST -> "message.workshop_zone.recipe_viewer.duplicate_request";
			case NO_CLIENT, NO_PLAYER, NO_INTERACTION_MANAGER, INVALID_SCREEN ->
				"message.workshop_zone.recipe_viewer.stale_screen";
			default -> "message.workshop_zone.recipe_viewer.internal_error";
		};
		return Result.createFailed(Text.translatable(translationKey)).blocksFurtherHandling(true);
	}

	interface BridgeAccess {
		RecipeViewerTransferResult validate(Identifier recipeId);

		RecipeViewerTransferResult request(Identifier recipeId, boolean batch);
	}

	interface ContextAccess {
		boolean isCraftingScreen();

		boolean hasCraftingScreenHandler();

		boolean isCraftingCategory();

		Identifier recipeId();

		boolean isActuallyCrafting();

		boolean isStackedCrafting();
	}

	private static final class ReiContextAccess implements ContextAccess {
		private final Context context;

		private ReiContextAccess(Context context) {
			this.context = context;
		}

		@Override
		public boolean isCraftingScreen() {
			return context != null && context.getContainerScreen() instanceof CraftingScreen;
		}

		@Override
		public boolean hasCraftingScreenHandler() {
			return context != null && context.getMenu() instanceof CraftingScreenHandler;
		}

		@Override
		public boolean isCraftingCategory() {
			Display display = context == null ? null : context.getDisplay();
			return display != null
				&& CRAFTING_CATEGORY_ID.equals(display.getCategoryIdentifier().getIdentifier());
		}

		@Override
		public Identifier recipeId() {
			Display display = context == null ? null : context.getDisplay();
			return display == null ? null : display.getDisplayLocation().orElse(null);
		}

		@Override
		public boolean isActuallyCrafting() {
			return context != null && context.isActuallyCrafting();
		}

		@Override
		public boolean isStackedCrafting() {
			return context != null && context.isStackedCrafting();
		}
	}
}
