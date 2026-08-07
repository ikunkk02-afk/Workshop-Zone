package io.github.ikunkk02afk.workshopzone.client.compat.jei;

import io.github.ikunkk02afk.workshopzone.client.compat.recipeviewer.RecipeViewerCraftBridge;
import io.github.ikunkk02afk.workshopzone.client.compat.recipeviewer.RecipeViewerSource;
import io.github.ikunkk02afk.workshopzone.client.compat.recipeviewer.RecipeViewerTransferResult;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public final class WorkshopZoneJeiCraftingTransferHandler
	implements IRecipeTransferHandler<CraftingScreenHandler, RecipeEntry<CraftingRecipe>> {

	private final IRecipeTransferHandlerHelper transferHelper;
	private final BridgeAccess bridge;

	public WorkshopZoneJeiCraftingTransferHandler(IRecipeTransferHandlerHelper transferHelper) {
		this(transferHelper, new BridgeAccess() {
			@Override
			public RecipeViewerTransferResult validate(Identifier recipeId, CraftingScreenHandler handler) {
				return RecipeViewerCraftBridge.validate(recipeId, handler);
			}

			@Override
			public RecipeViewerTransferResult request(
				Identifier recipeId,
				boolean batch,
				CraftingScreenHandler handler
			) {
				return RecipeViewerCraftBridge.request(RecipeViewerSource.JEI, recipeId, batch, handler);
			}
		});
	}

	WorkshopZoneJeiCraftingTransferHandler(IRecipeTransferHandlerHelper transferHelper, BridgeAccess bridge) {
		this.transferHelper = transferHelper;
		this.bridge = bridge;
	}

	@Override
	public Class<? extends CraftingScreenHandler> getContainerClass() {
		return CraftingScreenHandler.class;
	}

	@Override
	public Optional<ScreenHandlerType<CraftingScreenHandler>> getMenuType() {
		return Optional.of(ScreenHandlerType.CRAFTING);
	}

	@Override
	public RecipeType<RecipeEntry<CraftingRecipe>> getRecipeType() {
		return RecipeTypes.CRAFTING;
	}

	@Override
	@Nullable
	public IRecipeTransferError transferRecipe(
		CraftingScreenHandler container,
		RecipeEntry<CraftingRecipe> recipe,
		IRecipeSlotsView recipeSlots,
		PlayerEntity player,
		boolean maxTransfer,
		boolean doTransfer
	) {
		return transferRecipeId(container, recipe == null ? null : recipe.id(), maxTransfer, doTransfer);
	}

	@Nullable
	IRecipeTransferError transferRecipeId(Identifier recipeId, boolean maxTransfer, boolean doTransfer) {
		return transferRecipeId(null, recipeId, maxTransfer, doTransfer);
	}

	@Nullable
	IRecipeTransferError transferRecipeId(
		CraftingScreenHandler handler,
		Identifier recipeId,
		boolean maxTransfer,
		boolean doTransfer
	) {
		if (recipeId == null) {
			return userError(RecipeViewerTransferResult.INVALID_RECIPE);
		}

		RecipeViewerTransferResult result = doTransfer
			? bridge.request(recipeId, maxTransfer, handler)
			: bridge.validate(recipeId, handler);
		if (result == RecipeViewerTransferResult.READY
			|| result == RecipeViewerTransferResult.REQUEST_SENT
			|| result == RecipeViewerTransferResult.DUPLICATE_REQUEST) {
			return null;
		}
		return userError(result);
	}

	private IRecipeTransferError userError(RecipeViewerTransferResult result) {
		if (result == RecipeViewerTransferResult.INTERNAL_ERROR) {
			return transferHelper.createInternalError();
		}
		String translationKey = switch (result) {
			case INVALID_RECIPE -> "gui.workshop_zone.recipe_viewer.invalid_recipe";
			case UNSUPPORTED_RECIPE, NOT_APPLICABLE -> "gui.workshop_zone.recipe_viewer.unsupported_recipe";
			case GRID_NOT_EMPTY -> "gui.workshop_zone.recipe_viewer.grid_not_empty";
			case DUPLICATE_REQUEST -> "message.workshop_zone.recipe_viewer.duplicate_request";
			case NO_CLIENT, NO_PLAYER, NO_INTERACTION_MANAGER, INVALID_SCREEN ->
				"message.workshop_zone.recipe_viewer.stale_screen";
			default -> "message.workshop_zone.recipe_viewer.internal_error";
		};
		return transferHelper.createUserErrorWithTooltip(Text.translatable(translationKey));
	}

	interface BridgeAccess {
		RecipeViewerTransferResult validate(Identifier recipeId, CraftingScreenHandler handler);

		RecipeViewerTransferResult request(Identifier recipeId, boolean batch, CraftingScreenHandler handler);
	}
}
