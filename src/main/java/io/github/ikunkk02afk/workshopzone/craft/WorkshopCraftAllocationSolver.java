package io.github.ikunkk02afk.workshopzone.craft;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Pure, registry-independent capacity matcher used to verify deterministic
 * player-first assignment. Minecraft Ingredient/ItemVariant adaptation lives in
 * {@link WorkshopCraftAssignmentSolver}.
 */
public final class WorkshopCraftAllocationSolver {
	private static final long STORAGE_COST = 1_000_000_000L;

	private WorkshopCraftAllocationSolver() {
	}

	public static Optional<Result> solve(List<IngredientOptions> inputIngredients, List<Supply> inputSupplies) {
		List<Supply> supplies = List.copyOf(inputSupplies);
		List<BatchSupply> batchSupplies = supplies.stream()
			.map(supply -> new BatchSupply(
				supply.id(), supply.id(), supply.sourceKind(), supply.amount(), 1, supply.stableOrder()
			))
			.toList();
		return solveBatch(inputIngredients, batchSupplies, 1).map(batch -> {
			Map<Integer, Supply> supplyById = new HashMap<>();
			for (Supply supply : supplies) {
				supplyById.put(supply.id(), supply);
			}
			List<Assignment> assignments = batch.assignments().stream().map(value -> {
				Supply supply = supplyById.get(value.variantId());
				if (supply == null) {
					throw new IllegalStateException("Batch allocation returned an unknown supply");
				}
				return new Assignment(value.ingredientIndex(), supply.id(), supply.sourceKind());
			}).toList();
			return new Result(assignments);
		});
	}

	public static int maxIterations(
		List<IngredientOptions> ingredients,
		List<BatchSupply> supplies,
		int hardLimit
	) {
		if (hardLimit <= 0) {
			return 0;
		}
		int low = 0;
		int high = hardLimit;
		while (low < high) {
			int candidate = low + (high - low + 1) / 2;
			if (solveBatch(ingredients, supplies, candidate).isPresent()) {
				low = candidate;
			} else {
				high = candidate - 1;
			}
		}
		return low;
	}

	public static Optional<BatchResult> solveBatch(
		List<IngredientOptions> inputIngredients,
		List<BatchSupply> inputSupplies,
		int iterations
	) {
		List<IngredientOptions> ingredients = List.copyOf(inputIngredients);
		List<BatchSupply> supplies = List.copyOf(inputSupplies);
		if (ingredients.isEmpty() || ingredients.size() > 9 || iterations <= 0) {
			return Optional.empty();
		}
		Set<Integer> ingredientIds = new HashSet<>();
		Set<Integer> supplyIds = new HashSet<>();
		if (ingredients.stream().anyMatch(ingredient -> !ingredientIds.add(ingredient.recipeIndex()))
			|| supplies.stream().anyMatch(supply -> !supplyIds.add(supply.id()))) {
			throw new IllegalArgumentException("Duplicate allocation identity");
		}

		Map<Integer, VariantCapacity> capacitiesByVariant = new HashMap<>();
		for (BatchSupply supply : supplies) {
			capacitiesByVariant.computeIfAbsent(supply.variantId(), VariantCapacity::new).add(supply);
		}
		List<VariantCapacity> variants = capacitiesByVariant.values().stream()
			.filter(variant -> variant.maxCount >= iterations && variant.totalAmount >= iterations)
			.sorted(Comparator.comparingInt(variant -> variant.variantId))
			.toList();
		if (variants.isEmpty()) {
			return Optional.empty();
		}
		Map<Integer, Integer> variantIndexById = new HashMap<>();
		for (int index = 0; index < variants.size(); index++) {
			variantIndexById.put(variants.get(index).variantId, index);
		}
		for (IngredientOptions ingredient : ingredients) {
			if (ingredient.matchingSupplyIds().stream().noneMatch(variantIndexById::containsKey)) {
				return Optional.empty();
			}
		}
		List<IngredientOptions> ordered = new ArrayList<>(ingredients);
		ordered.sort(Comparator
			.comparingInt((IngredientOptions ingredient) -> (int)ingredient.matchingSupplyIds().stream()
				.filter(variantIndexById::containsKey).count())
			.thenComparingInt(IngredientOptions::recipeIndex));

		int source = 0;
		int ingredientStart = 1;
		int variantStart = ingredientStart + ordered.size();
		int sink = variantStart + variants.size();
		Flow flow = new Flow(sink + 1);
		for (int ingredientIndex = 0; ingredientIndex < ordered.size(); ingredientIndex++) {
			int ingredientNode = ingredientStart + ingredientIndex;
			flow.add(source, ingredientNode, 1, 0);
			IngredientOptions ingredient = ordered.get(ingredientIndex);
			for (int variantIndex = 0; variantIndex < variants.size(); variantIndex++) {
				VariantCapacity variant = variants.get(variantIndex);
				if (ingredient.matchingSupplyIds().contains(variant.variantId)) {
					flow.add(ingredientNode, variantStart + variantIndex, 1, variantIndex);
				}
			}
		}
		for (int variantIndex = 0; variantIndex < variants.size(); variantIndex++) {
			VariantCapacity variant = variants.get(variantIndex);
			int capacity = (int)Math.min(ingredients.size(), variant.totalAmount / iterations);
			for (int unit = 1; unit <= capacity; unit++) {
				long previousDemand = (long)(unit - 1) * iterations;
				long nextDemand = (long)unit * iterations;
				long previousStorage = Math.max(0, previousDemand - variant.playerAmount);
				long nextStorage = Math.max(0, nextDemand - variant.playerAmount);
				long marginalStorage = nextStorage - previousStorage;
				flow.add(variantStart + variantIndex, sink, 1, marginalStorage * STORAGE_COST + variantIndex);
			}
		}
		if (flow.send(source, sink, ordered.size()) != ordered.size()) {
			return Optional.empty();
		}

		List<BatchAssignment> assignments = new ArrayList<>(ordered.size());
		Map<Integer, Integer> requiredByVariant = new HashMap<>();
		for (int ingredientIndex = 0; ingredientIndex < ordered.size(); ingredientIndex++) {
			for (Flow.Edge edge : flow.edges(ingredientStart + ingredientIndex)) {
				if (edge.to >= variantStart && edge.to < sink && edge.initialCapacity == 1 && edge.capacity == 0) {
					int variantId = variants.get(edge.to - variantStart).variantId;
					assignments.add(new BatchAssignment(ordered.get(ingredientIndex).recipeIndex(), variantId));
					requiredByVariant.merge(variantId, iterations, Integer::sum);
					break;
				}
			}
		}
		if (assignments.size() != ingredients.size()) {
			return Optional.empty();
		}
		assignments.sort(Comparator.comparingInt(BatchAssignment::ingredientIndex));

		List<SourceUsage> usages = new ArrayList<>();
		for (VariantCapacity variant : variants) {
			int remaining = requiredByVariant.getOrDefault(variant.variantId, 0);
			if (remaining == 0) {
				continue;
			}
			List<BatchSupply> orderedSources = new ArrayList<>(variant.supplies);
			orderedSources.sort(Comparator
				.comparing((BatchSupply supply) -> supply.sourceKind() == WorkshopCraftSourceKind.PLAYER ? 0 : 1)
				.thenComparingInt(supply -> supply.sourceKind() == WorkshopCraftSourceKind.PLAYER ? supply.amount() : 0)
				.thenComparingInt(BatchSupply::stableOrder)
				.thenComparingInt(BatchSupply::id));
			for (BatchSupply supply : orderedSources) {
				if (remaining == 0) {
					break;
				}
				int used = Math.min(remaining, supply.amount());
				if (used > 0) {
					usages.add(new SourceUsage(supply.id(), supply.sourceKind(), used));
					remaining -= used;
				}
			}
			if (remaining != 0) {
				return Optional.empty();
			}
		}
		return Optional.of(new BatchResult(assignments, usages, iterations));
	}

	public record IngredientOptions(int recipeIndex, List<Integer> matchingSupplyIds) {
		public IngredientOptions {
			matchingSupplyIds = List.copyOf(matchingSupplyIds);
			if (recipeIndex < 0 || recipeIndex >= 9 || matchingSupplyIds.isEmpty()) {
				throw new IllegalArgumentException("Invalid allocation ingredient");
			}
		}
	}

	public record Supply(int id, WorkshopCraftSourceKind sourceKind, int amount, int stableOrder) {
		public Supply {
			if (id < 0 || sourceKind == null || amount <= 0 || stableOrder < 0) {
				throw new IllegalArgumentException("Invalid allocation supply");
			}
		}
	}

	public record BatchSupply(
		int id,
		int variantId,
		WorkshopCraftSourceKind sourceKind,
		int amount,
		int maxCount,
		int stableOrder
	) {
		public BatchSupply {
			if (id < 0 || variantId < 0 || sourceKind == null || amount <= 0 || maxCount < 0 || stableOrder < 0) {
				throw new IllegalArgumentException("Invalid batch allocation supply");
			}
		}
	}

	public record BatchAssignment(int ingredientIndex, int variantId) {
		public BatchAssignment {
			if (ingredientIndex < 0 || ingredientIndex >= 9 || variantId < 0) {
				throw new IllegalArgumentException("Invalid batch allocation assignment");
			}
		}
	}

	public record SourceUsage(int supplyId, WorkshopCraftSourceKind sourceKind, int amount) {
		public SourceUsage {
			if (supplyId < 0 || sourceKind == null || amount <= 0) {
				throw new IllegalArgumentException("Invalid batch allocation source usage");
			}
		}
	}

	public static final class BatchResult {
		private final List<BatchAssignment> assignments;
		private final List<SourceUsage> sourceUsages;
		private final Map<Integer, BatchAssignment> assignmentByIngredient;
		private final int iterations;
		private final int playerItemCount;
		private final int storageItemCount;

		private BatchResult(List<BatchAssignment> assignments, List<SourceUsage> sourceUsages, int iterations) {
			this.assignments = List.copyOf(assignments);
			this.sourceUsages = List.copyOf(sourceUsages);
			this.iterations = iterations;
			Map<Integer, BatchAssignment> byIngredient = new LinkedHashMap<>();
			for (BatchAssignment assignment : assignments) {
				byIngredient.put(assignment.ingredientIndex(), assignment);
			}
			this.assignmentByIngredient = Map.copyOf(byIngredient);
			this.playerItemCount = sourceUsages.stream()
				.filter(usage -> usage.sourceKind() == WorkshopCraftSourceKind.PLAYER)
				.mapToInt(SourceUsage::amount).sum();
			this.storageItemCount = sourceUsages.stream()
				.filter(usage -> usage.sourceKind() == WorkshopCraftSourceKind.STORAGE)
				.mapToInt(SourceUsage::amount).sum();
		}

		public List<BatchAssignment> assignments() {
			return assignments;
		}

		public List<SourceUsage> sourceUsages() {
			return sourceUsages;
		}

		public BatchAssignment assignmentForIngredient(int ingredientIndex) {
			return java.util.Objects.requireNonNull(
				assignmentByIngredient.get(ingredientIndex), "No assignment for ingredient " + ingredientIndex
			);
		}

		public int iterations() {
			return iterations;
		}

		public int playerItemCount() {
			return playerItemCount;
		}

		public int storageItemCount() {
			return storageItemCount;
		}

		public int movedItemCount() {
			return playerItemCount + storageItemCount;
		}
	}

	public record Assignment(int ingredientIndex, int supplyId, WorkshopCraftSourceKind sourceKind) {
	}

	public record Result(List<Assignment> assignments) {
		public Result {
			assignments = List.copyOf(assignments);
		}

		public int playerItemCount() {
			return (int)assignments.stream().filter(value -> value.sourceKind() == WorkshopCraftSourceKind.PLAYER).count();
		}

		public int storageItemCount() {
			return assignments.size() - playerItemCount();
		}

		public Assignment assignmentForIngredient(int ingredientIndex) {
			return assignments.stream().filter(value -> value.ingredientIndex() == ingredientIndex).findFirst().orElseThrow();
		}
	}

	private static final class VariantCapacity {
		private final int variantId;
		private final List<BatchSupply> supplies = new ArrayList<>();
		private long totalAmount;
		private long playerAmount;
		private int maxCount = Integer.MAX_VALUE;

		private VariantCapacity(int variantId) {
			this.variantId = variantId;
		}

		private void add(BatchSupply supply) {
			supplies.add(supply);
			totalAmount += supply.amount();
			if (supply.sourceKind() == WorkshopCraftSourceKind.PLAYER) {
				playerAmount += supply.amount();
			}
			maxCount = Math.min(maxCount, supply.maxCount());
		}
	}

	private static final class Flow {
		private static final long INFINITY = Long.MAX_VALUE / 4;
		private final List<List<Edge>> graph;

		private Flow(int nodes) {
			graph = new ArrayList<>(nodes);
			for (int index = 0; index < nodes; index++) {
				graph.add(new ArrayList<>());
			}
		}

		private void add(int from, int to, int capacity, long cost) {
			Edge forward = new Edge(to, graph.get(to).size(), capacity, capacity, cost);
			Edge reverse = new Edge(from, graph.get(from).size(), 0, 0, -cost);
			graph.get(from).add(forward);
			graph.get(to).add(reverse);
		}

		private List<Edge> edges(int node) {
			return graph.get(node);
		}

		private int send(int source, int sink, int requested) {
			int sent = 0;
			while (sent < requested) {
				long[] distance = new long[graph.size()];
				int[] previousNode = new int[graph.size()];
				int[] previousEdge = new int[graph.size()];
				Arrays.fill(distance, INFINITY);
				Arrays.fill(previousNode, -1);
				distance[source] = 0;
				for (int pass = 0; pass < graph.size() - 1; pass++) {
					boolean changed = false;
					for (int node = 0; node < graph.size(); node++) {
						if (distance[node] == INFINITY) {
							continue;
						}
						for (int edgeIndex = 0; edgeIndex < graph.get(node).size(); edgeIndex++) {
							Edge edge = graph.get(node).get(edgeIndex);
							long candidate = distance[node] + edge.cost;
							if (edge.capacity > 0 && candidate < distance[edge.to]) {
								distance[edge.to] = candidate;
								previousNode[edge.to] = node;
								previousEdge[edge.to] = edgeIndex;
								changed = true;
							}
						}
					}
					if (!changed) {
						break;
					}
				}
				if (previousNode[sink] < 0) {
					break;
				}
				for (int node = sink; node != source; node = previousNode[node]) {
					Edge edge = graph.get(previousNode[node]).get(previousEdge[node]);
					edge.capacity--;
					graph.get(node).get(edge.reverse).capacity++;
				}
				sent++;
			}
			return sent;
		}

		private static final class Edge {
			private final int to;
			private final int reverse;
			private int capacity;
			private final int initialCapacity;
			private final long cost;

			private Edge(int to, int reverse, int capacity, int initialCapacity, long cost) {
				this.to = to;
				this.reverse = reverse;
				this.capacity = capacity;
				this.initialCapacity = initialCapacity;
				this.cost = cost;
			}
		}
	}
}
