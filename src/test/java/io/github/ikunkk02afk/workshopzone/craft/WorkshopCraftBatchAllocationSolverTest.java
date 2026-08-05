package io.github.ikunkk02afk.workshopzone.craft;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkshopCraftBatchAllocationSolverTest {
	@Test
	void oneIngredientWithSixtyFourItemsSupportsSixtyFourIterations() {
		assertEquals(64, max(64, ingredient(0, 10), batchSupply(0, 10, WorkshopCraftSourceKind.PLAYER, 64, 64, 0)));
	}

	@Test
	void fourRepeatedIngredientsShareCapacityAndSupportSixteenIterations() {
		List<WorkshopCraftAllocationSolver.IngredientOptions> ingredients = List.of(
			ingredient(0, 10), ingredient(1, 10), ingredient(2, 10), ingredient(3, 10)
		);
		assertEquals(16, WorkshopCraftAllocationSolver.maxIterations(
			ingredients, List.of(batchSupply(0, 10, WorkshopCraftSourceKind.STORAGE, 64, 64, 0)), 64
		));
	}

	@Test
	void actualStackLimitsAndHardLimitBoundIterations() {
		assertEquals(16, max(64, ingredient(0, 10), batchSupply(0, 10, WorkshopCraftSourceKind.STORAGE, 64, 16, 0)));
		assertEquals(1, max(64, ingredient(0, 10), batchSupply(0, 10, WorkshopCraftSourceKind.STORAGE, 64, 1, 0)));
		assertEquals(64, max(64, ingredient(0, 10), batchSupply(0, 10, WorkshopCraftSourceKind.STORAGE, 512, 512, 0)));
	}

	@Test
	void playerAndStorageOfSameVariantCombineAndOnlyDeficitUsesStorage() {
		WorkshopCraftAllocationSolver.BatchResult result = WorkshopCraftAllocationSolver.solveBatch(
			List.of(ingredient(0, 10)),
			List.of(
				batchSupply(0, 10, WorkshopCraftSourceKind.PLAYER, 10, 64, 0),
				batchSupply(1, 10, WorkshopCraftSourceKind.STORAGE, 100, 64, 1)
			),
			32
		).orElseThrow();

		assertEquals(10, result.playerItemCount());
		assertEquals(22, result.storageItemCount());
		assertEquals(32, result.movedItemCount());
	}

	@Test
	void overlappingBroadAndExactIngredientsRemainFeasibleInBatch() {
		WorkshopCraftAllocationSolver.BatchResult result = WorkshopCraftAllocationSolver.solveBatch(
			List.of(ingredient(0, 10, 11), ingredient(1, 10)),
			List.of(
				batchSupply(0, 10, WorkshopCraftSourceKind.STORAGE, 32, 64, 0),
				batchSupply(1, 11, WorkshopCraftSourceKind.STORAGE, 32, 64, 1)
			),
			32
		).orElseThrow();

		assertEquals(11, result.assignmentForIngredient(0).variantId());
		assertEquals(10, result.assignmentForIngredient(1).variantId());
	}

	@Test
	void highIterationChoosesTheVariantWithEnoughCapacity() {
		WorkshopCraftAllocationSolver.BatchResult result = WorkshopCraftAllocationSolver.solveBatch(
			List.of(ingredient(0, 10, 11)),
			List.of(
				batchSupply(0, 10, WorkshopCraftSourceKind.PLAYER, 2, 64, 0),
				batchSupply(1, 11, WorkshopCraftSourceKind.STORAGE, 64, 64, 1)
			),
			32
		).orElseThrow();

		assertEquals(11, result.assignmentForIngredient(0).variantId());
		assertEquals(32, result.storageItemCount());
	}

	@Test
	void maximumIterationsTakePriorityOverPlayerSourcePreference() {
		List<WorkshopCraftAllocationSolver.IngredientOptions> ingredients = List.of(ingredient(0, 10, 11));
		List<WorkshopCraftAllocationSolver.BatchSupply> supplies = List.of(
			batchSupply(0, 10, WorkshopCraftSourceKind.PLAYER, 2, 64, 0),
			batchSupply(1, 11, WorkshopCraftSourceKind.STORAGE, 64, 64, 1)
		);

		int maximum = WorkshopCraftAllocationSolver.maxIterations(ingredients, supplies, 64);
		WorkshopCraftAllocationSolver.BatchResult result = WorkshopCraftAllocationSolver.solveBatch(ingredients, supplies, maximum).orElseThrow();

		assertEquals(64, maximum);
		assertEquals(11, result.assignmentForIngredient(0).variantId());
	}

	@Test
	void fixedMaximumMinimizesStorageAndUsesSmallerPlayerStacksFirst() {
		WorkshopCraftAllocationSolver.BatchResult result = WorkshopCraftAllocationSolver.solveBatch(
			List.of(ingredient(0, 10, 11)),
			List.of(
				batchSupply(0, 10, WorkshopCraftSourceKind.PLAYER, 20, 64, 5),
				batchSupply(1, 10, WorkshopCraftSourceKind.PLAYER, 12, 64, 2),
				batchSupply(2, 11, WorkshopCraftSourceKind.STORAGE, 64, 64, 0)
			),
			32
		).orElseThrow();

		assertEquals(10, result.assignmentForIngredient(0).variantId());
		assertEquals(0, result.storageItemCount());
		assertEquals(List.of(1, 0), result.sourceUsages().stream().map(WorkshopCraftAllocationSolver.SourceUsage::supplyId).toList());
	}

	@Test
	void sourceVariantNeverMixesWithinOneTargetAndResultIsDeterministic() {
		List<WorkshopCraftAllocationSolver.IngredientOptions> ingredients = List.of(ingredient(0, 10, 11), ingredient(1, 10, 11));
		List<WorkshopCraftAllocationSolver.BatchSupply> supplies = List.of(
			batchSupply(0, 10, WorkshopCraftSourceKind.STORAGE, 32, 64, 0),
			batchSupply(1, 11, WorkshopCraftSourceKind.STORAGE, 32, 64, 1)
		);

		WorkshopCraftAllocationSolver.BatchResult first = WorkshopCraftAllocationSolver.solveBatch(ingredients, supplies, 32).orElseThrow();
		WorkshopCraftAllocationSolver.BatchResult second = WorkshopCraftAllocationSolver.solveBatch(ingredients, supplies, 32).orElseThrow();

		assertEquals(first.assignments(), second.assignments());
		assertEquals(2, first.assignments().stream().map(WorkshopCraftAllocationSolver.BatchAssignment::variantId).distinct().count());
		assertTrue(first.sourceUsages().stream().allMatch(usage -> usage.amount() == 32));
	}

	private static int max(
		int hardLimit,
		WorkshopCraftAllocationSolver.IngredientOptions ingredient,
		WorkshopCraftAllocationSolver.BatchSupply supply
	) {
		return WorkshopCraftAllocationSolver.maxIterations(List.of(ingredient), List.of(supply), hardLimit);
	}

	private static WorkshopCraftAllocationSolver.IngredientOptions ingredient(int index, Integer... variants) {
		return new WorkshopCraftAllocationSolver.IngredientOptions(index, List.of(variants));
	}

	private static WorkshopCraftAllocationSolver.BatchSupply batchSupply(
		int id,
		int variantId,
		WorkshopCraftSourceKind source,
		int amount,
		int maxCount,
		int stableOrder
	) {
		return new WorkshopCraftAllocationSolver.BatchSupply(id, variantId, source, amount, maxCount, stableOrder);
	}
}
