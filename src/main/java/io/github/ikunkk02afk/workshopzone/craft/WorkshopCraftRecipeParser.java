package io.github.ikunkk02afk.workshopzone.craft;

import net.fabricmc.fabric.api.recipe.v1.ingredient.FabricIngredient;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.recipe.ShapelessRecipe;
import net.minecraft.registry.RegistryWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class WorkshopCraftRecipeParser {
	private WorkshopCraftRecipeParser() {
	}

	public static Optional<WorkshopCraftParsedRecipe> parse(
		RecipeEntry<?> untrustedEntry,
		RegistryWrapper.WrapperLookup registries
	) {
		if (untrustedEntry == null || untrustedEntry.id() == null || untrustedEntry.value() == null) {
			return Optional.empty();
		}
		if (!(untrustedEntry.value() instanceof CraftingRecipe craftingRecipe)
			|| craftingRecipe.getClass() != ShapedRecipe.class && craftingRecipe.getClass() != ShapelessRecipe.class
			|| !craftingRecipe.fits(3, 3)) {
			return Optional.empty();
		}
		List<Ingredient> allIngredients;
		try {
			allIngredients = List.copyOf(craftingRecipe.getIngredients());
		} catch (RuntimeException exception) {
			return Optional.empty();
		}
		if (allIngredients.isEmpty() || allIngredients.size() > 9) {
			return Optional.empty();
		}
		List<WorkshopCraftIngredientSlot> ingredients = new ArrayList<>();
		for (int index = 0; index < allIngredients.size(); index++) {
			Ingredient ingredient = allIngredients.get(index);
			if (ingredient.isEmpty()) {
				continue;
			}
			FabricIngredient fabricIngredient = (FabricIngredient)(Object)ingredient;
			if (fabricIngredient.getCustomIngredient() != null) {
				return Optional.empty();
			}
			try {
				if (ingredient.getMatchingStacks().length == 0) {
					return Optional.empty();
				}
			} catch (RuntimeException exception) {
				return Optional.empty();
			}
			ingredients.add(new WorkshopCraftIngredientSlot(index, ingredient));
		}
		if (ingredients.isEmpty() || ingredients.size() > 9) {
			return Optional.empty();
		}
		if (craftingRecipe instanceof ShapedRecipe shaped
			&& (shaped.getWidth() < 1 || shaped.getWidth() > 3 || shaped.getHeight() < 1 || shaped.getHeight() > 3
				|| shaped.getWidth() * shaped.getHeight() != allIngredients.size())) {
			return Optional.empty();
		}
		try {
			var output = craftingRecipe.getResult(registries).copy();
			if (output.isEmpty()) {
				return Optional.empty();
			}
			@SuppressWarnings("unchecked")
			RecipeEntry<CraftingRecipe> trusted = (RecipeEntry<CraftingRecipe>)(RecipeEntry<?>)untrustedEntry;
			return Optional.of(new WorkshopCraftParsedRecipe(trusted, allIngredients, ingredients, output));
		} catch (RuntimeException exception) {
			return Optional.empty();
		}
	}
}
