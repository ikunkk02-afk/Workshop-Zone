package io.github.ikunkk02afk.workshopzone.craft;

import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeEntry;

import java.util.List;

public record WorkshopCraftParsedRecipe(
	RecipeEntry<CraftingRecipe> entry,
	List<Ingredient> allIngredients,
	List<WorkshopCraftIngredientSlot> ingredientSlots,
	ItemStack output
) {
	public WorkshopCraftParsedRecipe {
		allIngredients = List.copyOf(allIngredients);
		ingredientSlots = List.copyOf(ingredientSlots);
		output = output.copy();
	}

	@Override
	public ItemStack output() {
		return output.copy();
	}
}
