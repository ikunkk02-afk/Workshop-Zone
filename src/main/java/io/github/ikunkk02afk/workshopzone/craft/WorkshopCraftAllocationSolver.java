package io.github.ikunkk02afk.workshopzone.craft;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
		List<IngredientOptions> ingredients = List.copyOf(inputIngredients);
		List<Supply> supplies = new ArrayList<>(inputSupplies);
		if (ingredients.isEmpty() || ingredients.size() > 9) {
			return Optional.empty();
		}
		Set<Integer> ingredientIds = new HashSet<>();
		Set<Integer> supplyIds = new HashSet<>();
		if (ingredients.stream().anyMatch(ingredient -> !ingredientIds.add(ingredient.recipeIndex()))
			|| supplies.stream().anyMatch(supply -> !supplyIds.add(supply.id()))) {
			throw new IllegalArgumentException("Duplicate allocation identity");
		}
		supplies.sort(Comparator
			.comparing((Supply supply) -> supply.sourceKind() == WorkshopCraftSourceKind.PLAYER ? 0 : 1)
			.thenComparingInt(Supply::stableOrder)
			.thenComparingInt(Supply::id));
		Map<Integer, Integer> supplyIndexById = new HashMap<>();
		for (int index = 0; index < supplies.size(); index++) {
			supplyIndexById.put(supplies.get(index).id(), index);
		}
		for (IngredientOptions ingredient : ingredients) {
			if (ingredient.matchingSupplyIds().stream().noneMatch(supplyIndexById::containsKey)) {
				return Optional.empty();
			}
		}
		List<IngredientOptions> ordered = new ArrayList<>(ingredients);
		ordered.sort(Comparator
			.comparingInt((IngredientOptions ingredient) -> ingredient.matchingSupplyIds().size())
			.thenComparingInt(IngredientOptions::recipeIndex));

		int source = 0;
		int ingredientStart = 1;
		int supplyStart = ingredientStart + ordered.size();
		int sink = supplyStart + supplies.size();
		Flow flow = new Flow(sink + 1);
		for (int ingredientIndex = 0; ingredientIndex < ordered.size(); ingredientIndex++) {
			int ingredientNode = ingredientStart + ingredientIndex;
			flow.add(source, ingredientNode, 1, 0);
			IngredientOptions ingredient = ordered.get(ingredientIndex);
			for (int supplyIndex = 0; supplyIndex < supplies.size(); supplyIndex++) {
				Supply supply = supplies.get(supplyIndex);
				if (ingredient.matchingSupplyIds().contains(supply.id())) {
					flow.add(
						ingredientNode, supplyStart + supplyIndex, 1,
						(supply.sourceKind() == WorkshopCraftSourceKind.STORAGE ? STORAGE_COST : 0L) + supplyIndex
					);
				}
			}
		}
		for (int supplyIndex = 0; supplyIndex < supplies.size(); supplyIndex++) {
			flow.add(supplyStart + supplyIndex, sink, Math.min(9, supplies.get(supplyIndex).amount()), 0);
		}
		if (flow.send(source, sink, ordered.size()) != ordered.size()) {
			return Optional.empty();
		}
		List<Assignment> assignments = new ArrayList<>(ordered.size());
		for (int ingredientIndex = 0; ingredientIndex < ordered.size(); ingredientIndex++) {
			for (Flow.Edge edge : flow.edges(ingredientStart + ingredientIndex)) {
				if (edge.to >= supplyStart && edge.to < sink && edge.initialCapacity == 1 && edge.capacity == 0) {
					Supply supply = supplies.get(edge.to - supplyStart);
					assignments.add(new Assignment(ordered.get(ingredientIndex).recipeIndex(), supply.id(), supply.sourceKind()));
					break;
				}
			}
		}
		assignments.sort(Comparator.comparingInt(Assignment::ingredientIndex));
		return assignments.size() == ordered.size() ? Optional.of(new Result(assignments)) : Optional.empty();
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
