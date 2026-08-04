package io.github.ikunkk02afk.workshopzone.craft;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkshopCraftAssignmentSolverTest {
	@Test
	void playerInventoryIsPreferredAndStorageOnlySuppliesTheDeficit() {
		WorkshopCraftAllocationSolver.Result result = WorkshopCraftAllocationSolver.solve(
			List.of(
				ingredient(0, 0, 1), ingredient(1, 0, 1),
				ingredient(2, 0, 1), ingredient(3, 0, 1)
			),
			List.of(
				supply(0, WorkshopCraftSourceKind.PLAYER, 2, 0),
				supply(1, WorkshopCraftSourceKind.STORAGE, 20, 1)
			)
		).orElseThrow();

		assertEquals(2, result.playerItemCount());
		assertEquals(2, result.storageItemCount());
	}

	@Test
	void overlappingBroadAndExactIngredientsDoNotFailGreedily() {
		WorkshopCraftAllocationSolver.Result result = WorkshopCraftAllocationSolver.solve(
			List.of(
				ingredient(0, 0, 1),
				ingredient(1, 0)
			),
			List.of(
				supply(0, WorkshopCraftSourceKind.PLAYER, 1, 0),
				supply(1, WorkshopCraftSourceKind.STORAGE, 1, 1)
			)
		).orElseThrow();

		assertEquals(1, result.assignmentForIngredient(0).supplyId());
		assertEquals(0, result.assignmentForIngredient(1).supplyId());
		assertEquals(1, result.playerItemCount());
		assertEquals(1, result.storageItemCount());
	}

	@Test
	void repeatedTagLikeIngredientsRespectSupplyCounts() {
		WorkshopCraftAllocationSolver.Result result = WorkshopCraftAllocationSolver.solve(
			List.of(ingredient(0, 0, 1), ingredient(1, 0, 1), ingredient(2, 0, 1)),
			List.of(
				supply(0, WorkshopCraftSourceKind.PLAYER, 1, 0),
				supply(1, WorkshopCraftSourceKind.STORAGE, 2, 1)
			)
		).orElseThrow();
		assertEquals(3, result.assignments().size());
		assertEquals(1, result.playerItemCount());
		assertEquals(2, result.storageItemCount());
	}

	@Test
	void assignmentIsDeterministicForIdenticalInput() {
		List<WorkshopCraftAllocationSolver.IngredientOptions> ingredients = List.of(
			ingredient(0, 3, 7), ingredient(1, 3, 7)
		);
		List<WorkshopCraftAllocationSolver.Supply> supplies = List.of(
			supply(7, WorkshopCraftSourceKind.STORAGE, 2, 4),
			supply(3, WorkshopCraftSourceKind.STORAGE, 2, 2)
		);

		WorkshopCraftAllocationSolver.Result first = WorkshopCraftAllocationSolver.solve(ingredients, supplies).orElseThrow();
		WorkshopCraftAllocationSolver.Result second = WorkshopCraftAllocationSolver.solve(ingredients, supplies).orElseThrow();

		assertEquals(first.assignments(), second.assignments());
		assertEquals(List.of(3, 3), first.assignments().stream()
			.map(WorkshopCraftAllocationSolver.Assignment::supplyId).toList());
	}

	@Test
	void insufficientCapacityFailsWithoutPartialAssignment() {
		assertTrue(WorkshopCraftAllocationSolver.solve(
			List.of(ingredient(0, 0), ingredient(1, 0)),
			List.of(supply(0, WorkshopCraftSourceKind.STORAGE, 1, 0))
		).isEmpty());
	}

	private static WorkshopCraftAllocationSolver.IngredientOptions ingredient(int index, Integer... supplies) {
		return new WorkshopCraftAllocationSolver.IngredientOptions(index, List.of(supplies));
	}

	private static WorkshopCraftAllocationSolver.Supply supply(
		int id,
		WorkshopCraftSourceKind source,
		int amount,
		int stableOrder
	) {
		return new WorkshopCraftAllocationSolver.Supply(id, source, amount, stableOrder);
	}
}