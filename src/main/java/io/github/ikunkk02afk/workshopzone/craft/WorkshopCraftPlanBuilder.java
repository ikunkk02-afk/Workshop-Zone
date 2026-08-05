package io.github.ikunkk02afk.workshopzone.craft;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import io.github.ikunkk02afk.workshopzone.api.WorkshopCraftAccessCallback;
import io.github.ikunkk02afk.workshopzone.deposit.WorkshopDepositSourceSlots;
import io.github.ikunkk02afk.workshopzone.label.LogicalContainer;
import io.github.ikunkk02afk.workshopzone.search.WorkshopAccessibleContainer;
import io.github.ikunkk02afk.workshopzone.search.WorkshopContainerAccessService;
import io.github.ikunkk02afk.workshopzone.search.WorkshopSearchContainerCollector;
import io.github.ikunkk02afk.workshopzone.session.WorkshopSession;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class WorkshopCraftPlanBuilder {
	public static final int MAX_BATCH_ITERATIONS = 64;

	private final WorkshopSearchContainerCollector containerCollector;
	private final CraftPermission craftPermission;

	public WorkshopCraftPlanBuilder() {
		this(new WorkshopContainerAccessService(), (player, world, position, recipe, variant, amount) ->
			WorkshopCraftAccessCallback.EVENT.invoker().canExtract(
				player, world, position, recipe, variant, amount
			) == WorkshopCraftAccessCallback.Result.ALLOW
		);
	}

	public WorkshopCraftPlanBuilder(WorkshopContainerAccessService accessService, CraftPermission craftPermission) {
		this.containerCollector = new WorkshopSearchContainerCollector(Objects.requireNonNull(accessService, "accessService"));
		this.craftPermission = Objects.requireNonNull(craftPermission, "craftPermission");
	}

	public WorkshopCraftPlanBuildResult build(
		ServerPlayerEntity player,
		WorkshopSession session,
		WorkshopCraftParsedRecipe recipe,
		CraftingScreenHandler handler,
		WorkshopCraftMode requestedMode,
		int requestedIterations
	) {
		try {
			if (requestedMode == null || requestedIterations < 0 || requestedIterations > MAX_BATCH_ITERATIONS) {
				return WorkshopCraftPlanBuildResult.failure(WorkshopCraftPlanStatus.INTERNAL_ERROR);
			}
			List<WorkshopCraftSupply> supplies = new ArrayList<>();
			Map<Integer, WorkshopCraftLiveSupply> liveSupplies = new HashMap<>();
			int nextSupplyId = collectPlayerSupplies(player, handler, supplies, liveSupplies);
			int searchLimit = requestedMode == WorkshopCraftMode.BATCH ? MAX_BATCH_ITERATIONS : 1;
			int playerOnlyMaxIterations = WorkshopCraftAssignmentSolver.maxIterations(
				recipe.ingredientSlots(), supplies, searchLimit
			);
			if (requestedMode == WorkshopCraftMode.SINGLE && playerOnlyMaxIterations == 1) {
				return finish(
					recipe, WorkshopCraftMode.SINGLE, 1, 1, 1,
					WorkshopCraftAssignmentSolver.solve(recipe.ingredientSlots(), supplies, 1).orElseThrow(), liveSupplies
				);
			}
			if (requestedMode == WorkshopCraftMode.BATCH
				&& requestedIterations == 0 && playerOnlyMaxIterations == MAX_BATCH_ITERATIONS) {
				return finish(
					recipe, WorkshopCraftMode.BATCH, MAX_BATCH_ITERATIONS,
					MAX_BATCH_ITERATIONS, MAX_BATCH_ITERATIONS,
					WorkshopCraftAssignmentSolver.solve(
						recipe.ingredientSlots(), supplies, MAX_BATCH_ITERATIONS
					).orElseThrow(),
					liveSupplies
				);
			}

			WorkshopSearchContainerCollector.Result collected = containerCollector.collect(player, session);
			if (collected.containers().isEmpty()) {
				int planned = requestedMode == WorkshopCraftMode.SINGLE
					? 1 : requestedIterations > 0 ? requestedIterations : playerOnlyMaxIterations;
				if (planned > 0 && playerOnlyMaxIterations >= planned) {
					WorkshopCraftMode actualMode = requestedMode == WorkshopCraftMode.BATCH && planned > 1
						? WorkshopCraftMode.BATCH : WorkshopCraftMode.SINGLE;
					return finish(
						recipe, actualMode, planned, playerOnlyMaxIterations, playerOnlyMaxIterations,
						WorkshopCraftAssignmentSolver.solve(recipe.ingredientSlots(), supplies, planned).orElseThrow(),
						liveSupplies
					);
				}
				return WorkshopCraftPlanBuildResult.failure(WorkshopCraftPlanStatus.NO_ACCESSIBLE_CONTAINERS);
			}
			ServerWorld world = player.getServerWorld();
			boolean matchingStorageSeen = false;
			boolean matchingStorageDenied = false;
			int stableOrder = supplies.size();
			for (WorkshopAccessibleContainer accessible : collected.containers()) {
				LogicalContainer container = accessible.container();
				for (var member : container.members()) {
					if (!(member instanceof Inventory inventory)) {
						return WorkshopCraftPlanBuildResult.failure(WorkshopCraftPlanStatus.INTERNAL_ERROR);
					}
					for (int slot = 0; slot < inventory.size(); slot++) {
						ItemStack stack = inventory.getStack(slot);
						if (stack.isEmpty() || recipe.ingredientSlots().stream().noneMatch(value -> value.ingredient().test(stack))) {
							continue;
						}
						matchingStorageSeen = true;
						ItemVariant variant = ItemVariant.of(stack);
						boolean allowed;
						try {
							allowed = craftPermission.canExtract(
								player, world, container.representativePosition(), recipe.entry(), variant, stack.getCount()
							);
						} catch (RuntimeException exception) {
							WorkshopZone.LOGGER.debug(
								"Workshop crafting access callback failed for {}; denying extraction",
								container.representativePosition(), exception
							);
							allowed = false;
						}
						if (!allowed) {
							matchingStorageDenied = true;
							continue;
						}
						WorkshopCraftSupply supply = new WorkshopCraftSupply(
							nextSupplyId++, WorkshopCraftSourceKind.STORAGE, variant, stack.getCount(),
							maxCraftingCount(handler, stack), stableOrder++
						);
						supplies.add(supply);
						liveSupplies.put(supply.id(), new WorkshopCraftLiveSupply(supply, inventory, slot, container));
					}
				}
			}
			int combinedMaxIterations = WorkshopCraftAssignmentSolver.maxIterations(
				recipe.ingredientSlots(), supplies, searchLimit
			);
			int plannedIterations = requestedMode == WorkshopCraftMode.SINGLE
				? 1 : requestedIterations > 0 ? requestedIterations : combinedMaxIterations;
			if (combinedMaxIterations < plannedIterations || plannedIterations <= 0) {
				return WorkshopCraftPlanBuildResult.failure(
					matchingStorageDenied && matchingStorageSeen
						? WorkshopCraftPlanStatus.DENIED : WorkshopCraftPlanStatus.INSUFFICIENT
				);
			}
			var assignment = WorkshopCraftAssignmentSolver.solve(
				recipe.ingredientSlots(), supplies, plannedIterations
			);
			if (assignment.isEmpty()) {
				return WorkshopCraftPlanBuildResult.failure(WorkshopCraftPlanStatus.INSUFFICIENT);
			}
			WorkshopCraftMode actualMode = requestedMode == WorkshopCraftMode.BATCH && plannedIterations > 1
				? WorkshopCraftMode.BATCH : WorkshopCraftMode.SINGLE;
			return finish(
				recipe, actualMode, plannedIterations, playerOnlyMaxIterations, combinedMaxIterations,
				assignment.orElseThrow(), liveSupplies
			);
		} catch (RuntimeException exception) {
			WorkshopZone.LOGGER.error("Failed to build workshop crafting plan safely", exception);
			return WorkshopCraftPlanBuildResult.failure(WorkshopCraftPlanStatus.INTERNAL_ERROR);
		}
	}

	private static int collectPlayerSupplies(
		ServerPlayerEntity player,
		CraftingScreenHandler handler,
		List<WorkshopCraftSupply> supplies,
		Map<Integer, WorkshopCraftLiveSupply> liveSupplies
	) {
		PlayerInventory inventory = player.getInventory();
		int nextSupplyId = 0;
		int stableOrder = 0;
		for (int slot : WorkshopDepositSourceSlots.forRequest(true)) {
			ItemStack stack = inventory.getStack(slot);
			if (stack.isEmpty()) {
				continue;
			}
			WorkshopCraftSupply supply = new WorkshopCraftSupply(
				nextSupplyId++, WorkshopCraftSourceKind.PLAYER, ItemVariant.of(stack), stack.getCount(),
				maxCraftingCount(handler, stack), stableOrder++
			);
			supplies.add(supply);
			liveSupplies.put(supply.id(), new WorkshopCraftLiveSupply(supply, inventory, slot, null));
		}
		return nextSupplyId;
	}

	private static WorkshopCraftPlanBuildResult finish(
		WorkshopCraftParsedRecipe recipe,
		WorkshopCraftMode craftMode,
		int plannedIterations,
		int playerOnlyMaxIterations,
		int combinedMaxIterations,
		WorkshopCraftAssignmentSolver.Result assignment,
		Map<Integer, WorkshopCraftLiveSupply> liveSupplies
	) {
		List<WorkshopCraftGridPlacement> placements = WorkshopCraftGridLayout.align(recipe, assignment);
		if (placements.size() != recipe.ingredientSlots().size()) {
			return WorkshopCraftPlanBuildResult.failure(WorkshopCraftPlanStatus.INTERNAL_ERROR);
		}
		Map<ItemVariant, SummaryBuilder> summaries = new LinkedHashMap<>();
		Set<BlockPos> usedContainers = new HashSet<>();
		for (WorkshopCraftSourceAllocation allocation : assignment.sourceAllocations()) {
			WorkshopCraftLiveSupply live = liveSupplies.get(allocation.supplyId());
			if (live == null) {
				return WorkshopCraftPlanBuildResult.failure(WorkshopCraftPlanStatus.INTERNAL_ERROR);
			}
			SummaryBuilder summary = summaries.computeIfAbsent(
				live.supply().variant(), ignored -> new SummaryBuilder(live.supply().variant())
			);
			summary.add(allocation.sourceKind(), allocation.amount());
			if (allocation.sourceKind() == WorkshopCraftSourceKind.STORAGE && live.container() != null) {
				usedContainers.add(live.container().representativePosition());
			}
		}
		List<WorkshopCraftMaterialSummary> materialSummaries = summaries.values().stream()
			.map(SummaryBuilder::build).toList();
		return WorkshopCraftPlanBuildResult.available(new WorkshopCraftPlan(
			recipe, craftMode, plannedIterations, playerOnlyMaxIterations, combinedMaxIterations,
			assignment.assignments(), assignment.sourceAllocations(), placements, liveSupplies, materialSummaries,
			assignment.playerItemCount(), assignment.storageItemCount(), usedContainers.size()
		));
	}

	private static int maxCraftingCount(CraftingScreenHandler handler, ItemStack stack) {
		int maxCount = stack.getMaxCount();
		for (int slot = 1; slot <= 9; slot++) {
			maxCount = Math.min(maxCount, handler.getSlot(slot).getMaxItemCount(stack));
		}
		return Math.max(0, maxCount);
	}

	@FunctionalInterface
	public interface CraftPermission {
		boolean canExtract(
			ServerPlayerEntity player,
			ServerWorld world,
			BlockPos representativePosition,
			RecipeEntry<CraftingRecipe> recipe,
			ItemVariant variant,
			long amount
		);
	}

	private static final class SummaryBuilder {
		private final ItemVariant representative;
		private int playerAmount;
		private int storageAmount;

		private SummaryBuilder(ItemVariant representative) {
			this.representative = representative;
		}

		private void add(WorkshopCraftSourceKind sourceKind, int amount) {
			if (sourceKind == WorkshopCraftSourceKind.PLAYER) {
				playerAmount += amount;
			} else {
				storageAmount += amount;
			}
		}

		private WorkshopCraftMaterialSummary build() {
			return new WorkshopCraftMaterialSummary(
				representative.toStack(), playerAmount + storageAmount, playerAmount, storageAmount
			);
		}
	}
}
