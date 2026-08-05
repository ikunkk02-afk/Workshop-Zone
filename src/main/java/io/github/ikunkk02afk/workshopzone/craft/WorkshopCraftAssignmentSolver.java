package io.github.ikunkk02afk.workshopzone.craft;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class WorkshopCraftAssignmentSolver {
	private WorkshopCraftAssignmentSolver() {
	}

	public static Optional<Result> solve(
		List<WorkshopCraftIngredientSlot> ingredientSlots,
		List<WorkshopCraftSupply> availableSupplies
	) {
		return solve(ingredientSlots, availableSupplies, 1);
	}

	public static int maxIterations(
		List<WorkshopCraftIngredientSlot> ingredientSlots,
		List<WorkshopCraftSupply> supplies,
		int hardLimit
	) {
		if (supplies.isEmpty()) {
			return 0;
		}
		try {
			AdaptedInput adapted = adapt(ingredientSlots, supplies);
			return WorkshopCraftAllocationSolver.maxIterations(adapted.ingredients(), adapted.supplies(), hardLimit);
		} catch (IllegalArgumentException exception) {
			return 0;
		}
	}

	public static Optional<Result> solve(
		List<WorkshopCraftIngredientSlot> ingredientSlots,
		List<WorkshopCraftSupply> supplies,
		int iterations
	) {
		if (supplies.isEmpty()) {
			return Optional.empty();
		}
		AdaptedInput adapted;
		try {
			adapted = adapt(ingredientSlots, supplies);
		} catch (IllegalArgumentException exception) {
			return Optional.empty();
		}
		return WorkshopCraftAllocationSolver.solveBatch(adapted.ingredients(), adapted.supplies(), iterations)
			.map(result -> {
				List<WorkshopCraftSourceAllocation> allocations = result.sourceUsages().stream()
					.map(usage -> new WorkshopCraftSourceAllocation(usage.supplyId(), usage.sourceKind(), usage.amount()))
					.toList();
				List<WorkshopCraftAssignment> assignments = result.assignments().stream().map(assignment -> {
					var variant = adapted.variants().get(assignment.variantId());
					WorkshopCraftSupply primary = supplies.stream()
						.filter(supply -> supply.variant().equals(variant))
						.min(Comparator
							.comparing((WorkshopCraftSupply supply) -> supply.sourceKind() == WorkshopCraftSourceKind.PLAYER ? 0 : 1)
							.thenComparingInt(WorkshopCraftSupply::stableOrder)
							.thenComparingInt(WorkshopCraftSupply::id))
						.orElseThrow();
					return new WorkshopCraftAssignment(
						assignment.ingredientIndex(), primary.id(), primary.sourceKind(), variant
					);
				}).toList();
				return new Result(assignments, allocations, iterations);
			});
	}

	private static AdaptedInput adapt(
		List<WorkshopCraftIngredientSlot> ingredientSlots,
		List<WorkshopCraftSupply> supplies
	) {
		List<WorkshopCraftIngredientSlot> ingredients = List.copyOf(ingredientSlots);
		List<WorkshopCraftSupply> available = List.copyOf(supplies);
		Map<net.fabricmc.fabric.api.transfer.v1.item.ItemVariant, Integer> variantIds = new LinkedHashMap<>();
		for (WorkshopCraftSupply supply : available) {
			variantIds.computeIfAbsent(supply.variant(), ignored -> variantIds.size());
		}
		List<net.fabricmc.fabric.api.transfer.v1.item.ItemVariant> variants = new ArrayList<>(variantIds.size());
		variantIds.entrySet().stream().sorted(Map.Entry.comparingByValue()).forEach(entry -> variants.add(entry.getKey()));
		List<WorkshopCraftAllocationSolver.IngredientOptions> pureIngredients = ingredients.stream()
			.map(ingredient -> new WorkshopCraftAllocationSolver.IngredientOptions(
				ingredient.recipeIndex(),
				variantIds.entrySet().stream()
					.filter(entry -> ingredient.ingredient().test(entry.getKey().toStack()))
					.map(Map.Entry::getValue)
					.toList()
			))
			.toList();
		List<WorkshopCraftAllocationSolver.BatchSupply> pureSupplies = available.stream()
			.map(supply -> new WorkshopCraftAllocationSolver.BatchSupply(
				supply.id(), variantIds.get(supply.variant()), supply.sourceKind(), supply.amount(),
				supply.maxCount(), supply.stableOrder()
			))
			.toList();
		return new AdaptedInput(pureIngredients, pureSupplies, List.copyOf(variants));
	}

	public static final class Result {
		private final List<WorkshopCraftAssignment> assignments;
		private final List<WorkshopCraftSourceAllocation> sourceAllocations;
		private final Map<Integer, Integer> usageBySupply;
		private final Map<Integer, WorkshopCraftAssignment> assignmentByIngredient;
		private final int iterations;
		private final int playerItemCount;
		private final int storageItemCount;

		private Result(List<WorkshopCraftAssignment> assignments) {
			this(
				assignments,
				assignments.stream().map(assignment -> new WorkshopCraftSourceAllocation(
					assignment.supplyId(), assignment.sourceKind(), 1
				)).toList(),
				1
			);
		}

		private Result(
			List<WorkshopCraftAssignment> assignments,
			List<WorkshopCraftSourceAllocation> sourceAllocations,
			int iterations
		) {
			this.assignments = List.copyOf(assignments);
			this.sourceAllocations = List.copyOf(sourceAllocations);
			this.iterations = iterations;
			Map<Integer, Integer> usages = new LinkedHashMap<>();
			Map<Integer, WorkshopCraftAssignment> byIngredient = new HashMap<>();
			int player = 0;
			int storage = 0;
			for (WorkshopCraftAssignment assignment : assignments) {
				byIngredient.put(assignment.ingredientIndex(), assignment);
			}
			for (WorkshopCraftSourceAllocation allocation : sourceAllocations) {
				usages.merge(allocation.supplyId(), allocation.amount(), Integer::sum);
				if (allocation.sourceKind() == WorkshopCraftSourceKind.PLAYER) {
					player += allocation.amount();
				} else {
					storage += allocation.amount();
				}
			}
			this.usageBySupply = Map.copyOf(usages);
			this.assignmentByIngredient = Map.copyOf(byIngredient);
			this.playerItemCount = player;
			this.storageItemCount = storage;
		}

		public List<WorkshopCraftAssignment> assignments() {
			return assignments;
		}

		public List<WorkshopCraftSourceAllocation> sourceAllocations() {
			return sourceAllocations;
		}

		public int usageForSupply(int supplyId) {
			return usageBySupply.getOrDefault(supplyId, 0);
		}

		public WorkshopCraftAssignment assignmentForIngredient(int ingredientIndex) {
			return Objects.requireNonNull(assignmentByIngredient.get(ingredientIndex), "No assignment for ingredient " + ingredientIndex);
		}

		public int playerItemCount() {
			return playerItemCount;
		}

		public int storageItemCount() {
			return storageItemCount;
		}

		public int iterations() {
			return iterations;
		}
	}

	private record AdaptedInput(
		List<WorkshopCraftAllocationSolver.IngredientOptions> ingredients,
		List<WorkshopCraftAllocationSolver.BatchSupply> supplies,
		List<net.fabricmc.fabric.api.transfer.v1.item.ItemVariant> variants
	) {
	}
}
