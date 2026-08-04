package io.github.ikunkk02afk.workshopzone.craft;

import net.minecraft.recipe.RecipeGridAligner;

import java.util.ArrayList;
import java.util.List;

public final class WorkshopCraftGridLayout implements RecipeGridAligner<WorkshopCraftAssignment> {
	private final List<WorkshopCraftGridPlacement> placements = new ArrayList<>();

	private WorkshopCraftGridLayout() {
	}

	public static List<WorkshopCraftGridPlacement> align(
		WorkshopCraftParsedRecipe recipe,
		WorkshopCraftAssignmentSolver.Result assignments
	) {
		List<WorkshopCraftAssignment> alignedInputs = new ArrayList<>(recipe.allIngredients().size());
		for (int index = 0; index < recipe.allIngredients().size(); index++) {
			alignedInputs.add(recipe.allIngredients().get(index).isEmpty() ? null : assignments.assignmentForIngredient(index));
		}
		WorkshopCraftGridLayout layout = new WorkshopCraftGridLayout();
		layout.alignRecipeToGrid(3, 3, 0, recipe.entry(), alignedInputs.iterator(), 1);
		return List.copyOf(layout.placements);
	}

	@Override
	public void acceptAlignedInput(WorkshopCraftAssignment assignment, int slot, int amount, int gridX, int gridY) {
		if (assignment != null) {
			placements.add(new WorkshopCraftGridPlacement(
				slot, assignment.ingredientIndex(), assignment.supplyId(), assignment.sourceKind(), assignment.variant()
			));
		}
	}
}
