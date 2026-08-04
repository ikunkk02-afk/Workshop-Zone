package io.github.ikunkk02afk.workshopzone.craft;

import net.minecraft.recipe.Ingredient;

import java.util.Objects;

public record WorkshopCraftIngredientSlot(int recipeIndex, Ingredient ingredient) {
	public WorkshopCraftIngredientSlot {
		if (recipeIndex < 0 || recipeIndex >= 9) {
			throw new IllegalArgumentException("Crafting ingredient index must be between 0 and 8");
		}
		Objects.requireNonNull(ingredient, "ingredient");
		if (ingredient.isEmpty()) {
			throw new IllegalArgumentException("Crafting ingredient slot cannot be empty");
		}
	}
}
