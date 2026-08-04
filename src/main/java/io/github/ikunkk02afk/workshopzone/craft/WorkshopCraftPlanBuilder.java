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
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
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
		WorkshopCraftParsedRecipe recipe
	) {
		try {
			List<WorkshopCraftSupply> supplies = new ArrayList<>();
			Map<Integer, WorkshopCraftLiveSupply> liveSupplies = new HashMap<>();
			int nextSupplyId = collectPlayerSupplies(player, supplies, liveSupplies);
			var playerOnly = WorkshopCraftAssignmentSolver.solve(recipe.ingredientSlots(), supplies);
			if (playerOnly.isPresent()) {
				return finish(recipe, playerOnly.orElseThrow(), liveSupplies);
			}

			WorkshopSearchContainerCollector.Result collected = containerCollector.collect(player, session);
			if (collected.containers().isEmpty()) {
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
							nextSupplyId++, WorkshopCraftSourceKind.STORAGE, variant, stack.getCount(), stableOrder++
						);
						supplies.add(supply);
						liveSupplies.put(supply.id(), new WorkshopCraftLiveSupply(supply, inventory, slot, container));
					}
				}
			}
			var assignment = WorkshopCraftAssignmentSolver.solve(recipe.ingredientSlots(), supplies);
			if (assignment.isEmpty()) {
				return WorkshopCraftPlanBuildResult.failure(
					matchingStorageDenied && matchingStorageSeen
						? WorkshopCraftPlanStatus.DENIED : WorkshopCraftPlanStatus.INSUFFICIENT
				);
			}
			return finish(recipe, assignment.orElseThrow(), liveSupplies);
		} catch (RuntimeException exception) {
			WorkshopZone.LOGGER.error("Failed to build workshop crafting plan safely", exception);
			return WorkshopCraftPlanBuildResult.failure(WorkshopCraftPlanStatus.INTERNAL_ERROR);
		}
	}

	private static int collectPlayerSupplies(
		ServerPlayerEntity player,
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
				nextSupplyId++, WorkshopCraftSourceKind.PLAYER, ItemVariant.of(stack), stack.getCount(), stableOrder++
			);
			supplies.add(supply);
			liveSupplies.put(supply.id(), new WorkshopCraftLiveSupply(supply, inventory, slot, null));
		}
		return nextSupplyId;
	}

	private static WorkshopCraftPlanBuildResult finish(
		WorkshopCraftParsedRecipe recipe,
		WorkshopCraftAssignmentSolver.Result assignment,
		Map<Integer, WorkshopCraftLiveSupply> liveSupplies
	) {
		List<WorkshopCraftGridPlacement> placements = WorkshopCraftGridLayout.align(recipe, assignment);
		if (placements.size() != recipe.ingredientSlots().size()) {
			return WorkshopCraftPlanBuildResult.failure(WorkshopCraftPlanStatus.INTERNAL_ERROR);
		}
		Map<Identifier, SummaryBuilder> summaries = new LinkedHashMap<>();
		Set<BlockPos> usedContainers = new HashSet<>();
		for (WorkshopCraftAssignment value : assignment.assignments()) {
			Identifier itemId = Registries.ITEM.getId(value.variant().getItem());
			SummaryBuilder summary = summaries.computeIfAbsent(itemId, ignored -> new SummaryBuilder(value.variant()));
			if (value.sourceKind() == WorkshopCraftSourceKind.PLAYER) {
				summary.playerAmount++;
			} else {
				summary.storageAmount++;
				WorkshopCraftLiveSupply live = liveSupplies.get(value.supplyId());
				if (live != null && live.container() != null) {
					usedContainers.add(live.container().representativePosition());
				}
			}
		}
		List<WorkshopCraftMaterialSummary> materialSummaries = summaries.values().stream()
			.map(SummaryBuilder::build).toList();
		return WorkshopCraftPlanBuildResult.available(new WorkshopCraftPlan(
			recipe, assignment.assignments(), placements, liveSupplies, materialSummaries,
			assignment.playerItemCount(), assignment.storageItemCount(), usedContainers.size()
		));
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

		private WorkshopCraftMaterialSummary build() {
			return new WorkshopCraftMaterialSummary(
				representative.toStack(), playerAmount + storageAmount, playerAmount, storageAmount
			);
		}
	}
}
