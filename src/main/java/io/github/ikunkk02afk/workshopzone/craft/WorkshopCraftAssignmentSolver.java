package io.github.ikunkk02afk.workshopzone.craft;

import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class WorkshopCraftAssignmentSolver {
	private static final long STORAGE_COST = 1_000_000_000L;

	private WorkshopCraftAssignmentSolver() {
	}

	public static Optional<Result> solve(
		List<WorkshopCraftIngredientSlot> ingredientSlots,
		List<WorkshopCraftSupply> availableSupplies
	) {
		List<WorkshopCraftIngredientSlot> ingredients = List.copyOf(ingredientSlots);
		List<WorkshopCraftSupply> supplies = List.copyOf(availableSupplies);
		if (ingredients.isEmpty() || ingredients.size() > 9) {
			return Optional.empty();
		}
		Set<Integer> ingredientIndices = new HashSet<>();
		if (ingredients.stream().anyMatch(slot -> !ingredientIndices.add(slot.recipeIndex()))) {
			throw new IllegalArgumentException("Duplicate workshop crafting ingredient index");
		}
		Map<Integer, WorkshopCraftSupply> suppliesById = new HashMap<>();
		for (WorkshopCraftSupply supply : supplies) {
			if (suppliesById.put(supply.id(), supply) != null) {
				throw new IllegalArgumentException("Duplicate workshop crafting supply id");
			}
		}

		List<SupplyGroup> groups = groupSupplies(supplies, ingredients.size());
		if (groups.isEmpty()) {
			return Optional.empty();
		}
		Map<Integer, Integer> candidateCounts = new HashMap<>();
		for (WorkshopCraftIngredientSlot ingredient : ingredients) {
			int candidates = 0;
			for (SupplyGroup group : groups) {
				if (matches(ingredient.ingredient(), group)) {
					candidates++;
				}
			}
			if (candidates == 0) {
				return Optional.empty();
			}
			candidateCounts.put(ingredient.recipeIndex(), candidates);
		}
		List<WorkshopCraftIngredientSlot> orderedIngredients = new ArrayList<>(ingredients);
		orderedIngredients.sort(Comparator
			.comparingInt((WorkshopCraftIngredientSlot slot) -> candidateCounts.get(slot.recipeIndex()))
			.thenComparingInt(WorkshopCraftIngredientSlot::recipeIndex));

		int source = 0;
		int ingredientStart = 1;
		int groupStart = ingredientStart + orderedIngredients.size();
		int sink = groupStart + groups.size();
		MinCostFlow flow = new MinCostFlow(sink + 1);
		for (int ingredientIndex = 0; ingredientIndex < orderedIngredients.size(); ingredientIndex++) {
			int ingredientNode = ingredientStart + ingredientIndex;
			flow.addEdge(source, ingredientNode, 1, 0);
			WorkshopCraftIngredientSlot ingredient = orderedIngredients.get(ingredientIndex);
			for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
				SupplyGroup group = groups.get(groupIndex);
				if (matches(ingredient.ingredient(), group)) {
					long cost = (group.key().sourceKind() == WorkshopCraftSourceKind.STORAGE ? STORAGE_COST : 0L) + groupIndex;
					flow.addEdge(ingredientNode, groupStart + groupIndex, 1, cost);
				}
			}
		}
		for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
			flow.addEdge(groupStart + groupIndex, sink, groups.get(groupIndex).capacity(), 0);
		}
		if (flow.send(source, sink, orderedIngredients.size()) != orderedIngredients.size()) {
			return Optional.empty();
		}

		Map<Integer, Integer> ingredientToGroup = new HashMap<>();
		for (int ingredientIndex = 0; ingredientIndex < orderedIngredients.size(); ingredientIndex++) {
			int ingredientNode = ingredientStart + ingredientIndex;
			for (MinCostFlow.Edge edge : flow.edges(ingredientNode)) {
				if (edge.to >= groupStart && edge.to < sink && edge.initialCapacity == 1 && edge.capacity == 0) {
					ingredientToGroup.put(orderedIngredients.get(ingredientIndex).recipeIndex(), edge.to - groupStart);
					break;
				}
			}
		}
		if (ingredientToGroup.size() != ingredients.size()) {
			return Optional.empty();
		}

		Map<Integer, Integer> remainingBySupply = new HashMap<>();
		for (WorkshopCraftSupply supply : supplies) {
			remainingBySupply.put(supply.id(), supply.amount());
		}
		List<WorkshopCraftAssignment> assignments = new ArrayList<>(ingredients.size());
		List<WorkshopCraftIngredientSlot> outputOrder = new ArrayList<>(ingredients);
		outputOrder.sort(Comparator.comparingInt(WorkshopCraftIngredientSlot::recipeIndex));
		for (WorkshopCraftIngredientSlot ingredient : outputOrder) {
			SupplyGroup group = groups.get(ingredientToGroup.get(ingredient.recipeIndex()));
			WorkshopCraftSupply selected = group.supplies().stream()
				.filter(supply -> remainingBySupply.getOrDefault(supply.id(), 0) > 0)
				.findFirst().orElseThrow();
			remainingBySupply.computeIfPresent(selected.id(), (id, amount) -> amount - 1);
			assignments.add(new WorkshopCraftAssignment(
				ingredient.recipeIndex(), selected.id(), selected.sourceKind(), selected.variant()
			));
		}
		return Optional.of(new Result(assignments));
	}

	private static List<SupplyGroup> groupSupplies(List<WorkshopCraftSupply> supplies, int requiredCount) {
		Map<GroupKey, List<WorkshopCraftSupply>> grouped = new HashMap<>();
		for (WorkshopCraftSupply supply : supplies) {
			GroupKey key = new GroupKey(Registries.ITEM.getRawId(supply.variant().getItem()), supply.sourceKind());
			grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(supply);
		}
		List<GroupKey> keys = new ArrayList<>(grouped.keySet());
		keys.sort(Comparator
			.comparing((GroupKey key) -> key.sourceKind() == WorkshopCraftSourceKind.PLAYER ? 0 : 1)
			.thenComparingInt(GroupKey::rawItemId));
		List<SupplyGroup> result = new ArrayList<>(keys.size());
		for (GroupKey key : keys) {
			List<WorkshopCraftSupply> groupSupplies = grouped.get(key);
			groupSupplies.sort(Comparator.comparingInt(WorkshopCraftSupply::stableOrder).thenComparingInt(WorkshopCraftSupply::id));
			int capacity = groupSupplies.stream().mapToInt(WorkshopCraftSupply::amount).limit(requiredCount).sum();
			capacity = Math.min(requiredCount, capacity);
			if (capacity > 0) {
				result.add(new SupplyGroup(key, List.copyOf(groupSupplies), capacity));
			}
		}
		return List.copyOf(result);
	}

	private static boolean matches(Ingredient ingredient, SupplyGroup group) {
		return ingredient.test(group.supplies().getFirst().variant().toStack());
	}

	public static final class Result {
		private final List<WorkshopCraftAssignment> assignments;
		private final Map<Integer, Integer> usageBySupply;
		private final Map<Integer, WorkshopCraftAssignment> assignmentByIngredient;
		private final int playerItemCount;
		private final int storageItemCount;

		private Result(List<WorkshopCraftAssignment> assignments) {
			this.assignments = List.copyOf(assignments);
			Map<Integer, Integer> usages = new LinkedHashMap<>();
			Map<Integer, WorkshopCraftAssignment> byIngredient = new HashMap<>();
			int player = 0;
			int storage = 0;
			for (WorkshopCraftAssignment assignment : assignments) {
				usages.merge(assignment.supplyId(), 1, Integer::sum);
				byIngredient.put(assignment.ingredientIndex(), assignment);
				if (assignment.sourceKind() == WorkshopCraftSourceKind.PLAYER) {
					player++;
				} else {
					storage++;
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
	}

	private record GroupKey(int rawItemId, WorkshopCraftSourceKind sourceKind) {
	}

	private record SupplyGroup(GroupKey key, List<WorkshopCraftSupply> supplies, int capacity) {
	}

	private static final class MinCostFlow {
		private static final long INFINITY = Long.MAX_VALUE / 4;
		private final List<List<Edge>> graph;

		private MinCostFlow(int nodeCount) {
			this.graph = new ArrayList<>(nodeCount);
			for (int index = 0; index < nodeCount; index++) {
				graph.add(new ArrayList<>());
			}
		}

		private void addEdge(int from, int to, int capacity, long cost) {
			Edge forward = new Edge(to, graph.get(to).size(), capacity, capacity, cost);
			Edge reverse = new Edge(from, graph.get(from).size(), 0, 0, -cost);
			graph.get(from).add(forward);
			graph.get(to).add(reverse);
		}

		private List<Edge> edges(int node) {
			return graph.get(node);
		}

		private int send(int source, int sink, int requestedFlow) {
			int sent = 0;
			int nodeCount = graph.size();
			while (sent < requestedFlow) {
				long[] distance = new long[nodeCount];
				Arrays.fill(distance, INFINITY);
				int[] previousNode = new int[nodeCount];
				int[] previousEdge = new int[nodeCount];
				Arrays.fill(previousNode, -1);
				distance[source] = 0;
				for (int pass = 0; pass < nodeCount - 1; pass++) {
					boolean changed = false;
					for (int node = 0; node < nodeCount; node++) {
						if (distance[node] == INFINITY) {
							continue;
						}
						List<Edge> edges = graph.get(node);
						for (int edgeIndex = 0; edgeIndex < edges.size(); edgeIndex++) {
							Edge edge = edges.get(edgeIndex);
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
					graph.get(node).get(edge.reverseIndex).capacity++;
				}
				sent++;
			}
			return sent;
		}

		private static final class Edge {
			private final int to;
			private final int reverseIndex;
			private int capacity;
			private final int initialCapacity;
			private final long cost;

			private Edge(int to, int reverseIndex, int capacity, int initialCapacity, long cost) {
				this.to = to;
				this.reverseIndex = reverseIndex;
				this.capacity = capacity;
				this.initialCapacity = initialCapacity;
				this.cost = cost;
			}
		}
	}
}
