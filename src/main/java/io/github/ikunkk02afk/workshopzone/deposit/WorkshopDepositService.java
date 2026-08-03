package io.github.ikunkk02afk.workshopzone.deposit;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import io.github.ikunkk02afk.workshopzone.api.WorkshopDepositCallback;
import io.github.ikunkk02afk.workshopzone.api.WorkshopRemoteOpenCallback;
import io.github.ikunkk02afk.workshopzone.label.ContainerLabelService;
import io.github.ikunkk02afk.workshopzone.label.ContainerLabelSummary;
import io.github.ikunkk02afk.workshopzone.label.ContainerLabelHolder;
import io.github.ikunkk02afk.workshopzone.label.LogicalContainer;
import io.github.ikunkk02afk.workshopzone.label.SilentContainerAccess;
import io.github.ikunkk02afk.workshopzone.label.WorkshopContainerResolver;
import io.github.ikunkk02afk.workshopzone.network.DepositWorkshopItemsPayload;
import io.github.ikunkk02afk.workshopzone.network.WorkshopDepositResultPayload;
import io.github.ikunkk02afk.workshopzone.scan.WorkshopBlockEntry;
import io.github.ikunkk02afk.workshopzone.scan.WorkshopBlockType;
import io.github.ikunkk02afk.workshopzone.session.WorkshopSession;
import io.github.ikunkk02afk.workshopzone.session.WorkshopSessionManager;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.PlayerInventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class WorkshopDepositService {
	public static final int COOLDOWN_TICKS = 20;

	private final WorkshopSessionManager sessions;
	private final Map<UUID, Long> lastDepositTicks = new HashMap<>();

	public WorkshopDepositService(WorkshopSessionManager sessions) {
		this.sessions = sessions;
	}

	public WorkshopDepositResultPayload deposit(ServerPlayerEntity player, DepositWorkshopItemsPayload request) {
		WorkshopZone.LOGGER.debug(
			"Player {} requested workshop deposit requestId {} sessionId {} includeHotbar {}",
			player.getGameProfile().getName(), request.requestId(), request.sessionId(), request.includeHotbar()
		);
		WorkshopSession session = sessions.get(player.getUuid()).orElse(null);
		if (session == null || session.sessionId() != request.sessionId()) {
			return result(request, WorkshopDepositResult.INVALID_SESSION, 0, 0, 0, 0);
		}
		if (request.revision() != session.revision()) {
			return result(request, WorkshopDepositResult.STALE_SNAPSHOT, 0, 0, 0, 0);
		}
		if (request.syncId() != session.syncId()
			|| player.currentScreenHandler.syncId != request.syncId()
			|| sessions.validate(player, session) != io.github.ikunkk02afk.workshopzone.session.WorkshopSessionValidation.VALID) {
			return result(request, WorkshopDepositResult.INVALID_SESSION, 0, 0, 0, 0);
		}
		long now = player.getServerWorld().getTime();
		long previous = lastDepositTicks.getOrDefault(player.getUuid(), now - COOLDOWN_TICKS);
		if (now - previous < COOLDOWN_TICKS) {
			return result(request, WorkshopDepositResult.COOLDOWN, 0, 0, 0, 0);
		}
		lastDepositTicks.put(player.getUuid(), now);

		DestinationBuild built = buildDestinations(player, session);
		if (built.labeledEntryCount() == 0) {
			return result(request, WorkshopDepositResult.NO_LABELED_CONTAINERS, 0, 0, 0, 0);
		}
		if (built.destinations().isEmpty()) {
			return result(request, WorkshopDepositResult.NO_VALID_DESTINATIONS, 0, 0, 0, 0);
		}

		PlayerInventoryStorage playerStorage = PlayerInventoryStorage.of(player);
		int movedItems = 0;
		int movedStacks = 0;
		int matchedRemaining = 0;
		boolean foundMatchingItems = false;
		boolean allMatchingTargetsDenied = true;
		Set<BlockPos> usedDestinations = new HashSet<>();
		PlayerInventory inventory = player.getInventory();
		for (int slotIndex : WorkshopDepositSourceSlots.forRequest(request.includeHotbar())) {
			ItemStack current = inventory.getStack(slotIndex);
			if (current.isEmpty()) {
				continue;
			}
			ItemStack sourceSnapshot = current.copy();
			ItemVariant variant = ItemVariant.of(sourceSnapshot);
			List<WorkshopDepositDestination> matching = new ArrayList<>();
			for (WorkshopDepositDestination destination : built.destinations()) {
				if (destination.matches(sourceSnapshot)) {
					matching.add(destination);
				}
			}
			if (matching.isEmpty()) {
				continue;
			}
			foundMatchingItems = true;
			matching.sort(Comparator.comparing(
				destination -> new WorkshopDepositPlanner.Target(
					destination.matchKind(sourceSnapshot).orElseThrow(),
					destination.representativePosition(), destination.distanceSquared(),
					destination.scanIndex(), destination.hasMergeableStack(sourceSnapshot)
				), WorkshopDepositPlanner.TARGET_ORDER
			));

			List<WorkshopDepositDestination> allowed = new ArrayList<>();
			for (WorkshopDepositDestination destination : matching) {
				if (allowsDeposit(player, destination, variant)) {
					allowed.add(destination);
					allMatchingTargetsDenied = false;
				} else {
					WorkshopZone.LOGGER.debug(
						"Skipping denied workshop deposit destination {} for requestId {}",
						destination.representativePosition(), request.requestId()
					);
				}
			}
			if (allowed.isEmpty()) {
				matchedRemaining += sourceSnapshot.getCount();
				continue;
			}

			long inserted = 0;
			try (Transaction transaction = Transaction.openOuter()) {
				for (WorkshopDepositDestination destination : allowed) {
					long amount = destination.insert(variant, sourceSnapshot.getCount() - inserted, transaction);
					if (amount > 0) {
						inserted += amount;
						usedDestinations.add(destination.representativePosition());
					}
					if (inserted >= sourceSnapshot.getCount()) {
						break;
					}
				}
				SingleSlotStorage< ItemVariant> source = playerStorage.getSlot(slotIndex);
				long extracted = source.extract(variant, inserted, transaction);
				if (extracted != inserted) {
					WorkshopZone.LOGGER.debug(
						"Aborting workshop deposit transaction for slot {}: inserted {} but extracted {}",
						slotIndex, inserted, extracted
					);
					inserted = 0;
				} else if (inserted > 0) {
					transaction.commit();
				}
			}
			if (inserted > 0) {
				movedItems += (int)inserted;
				movedStacks++;
			}
			int remaining = inventory.getStack(slotIndex).getCount();
			if (remaining > 0) {
				matchedRemaining += remaining;
			}
		}
		inventory.markDirty();
		player.currentScreenHandler.sendContentUpdates();
		for (WorkshopDepositDestination destination : built.destinations()) {
			for (BlockEntity member : destination.container().members()) {
				member.markDirty();
			}
		}
		WorkshopDepositResult outcome = WorkshopDepositSummary.classify(
			movedItems, matchedRemaining, foundMatchingItems, allMatchingTargetsDenied && foundMatchingItems
		);
		WorkshopZone.LOGGER.debug(
			"Workshop deposit requestId {} considered {} labeled entries, {} valid destinations, moved {} items using {} containers",
			request.requestId(), built.labeledEntryCount(), built.destinations().size(), movedItems, usedDestinations.size()
		);
		return result(request, outcome, movedItems, movedStacks, matchedRemaining, usedDestinations.size());
	}

	public void clear(ServerPlayerEntity player) {
		lastDepositTicks.remove(player.getUuid());
	}

	private DestinationBuild buildDestinations(ServerPlayerEntity player, WorkshopSession session) {
		ServerWorld world = player.getServerWorld();
		List<WorkshopDepositDestination> destinations = new ArrayList<>();
		int labeledEntries = 0;
		int index = 0;
		for (WorkshopBlockEntry entry : session.scanResult().entries()) {
			if (!entry.container() || !WorkshopDepositPlanner.isEligible(entry.labelSummary())) {
				index++;
				continue;
			}
			labeledEntries++;
			WorkshopDepositDestination destination = validateDestination(player, world, session, entry, index);
			if (destination != null) {
				destinations.add(destination);
			}
			index++;
		}
		WorkshopZone.LOGGER.debug(
			"Workshop deposit session {} has {} labeled entries and {} valid destinations",
			session.sessionId(), labeledEntries, destinations.size()
		);
		return new DestinationBuild(List.copyOf(destinations), labeledEntries);
	}

	private WorkshopDepositDestination validateDestination(
		ServerPlayerEntity player,
		ServerWorld world,
		WorkshopSession session,
		WorkshopBlockEntry entry,
		int scanIndex
	) {
		if (entry.type() != WorkshopBlockType.CHEST && entry.type() != WorkshopBlockType.TRAPPED_CHEST
			&& entry.type() != WorkshopBlockType.BARREL) {
			return null;
		}
		BlockPos position = entry.position();
		if (!isLoaded(world, position) || player.squaredDistanceTo(Vec3d.ofCenter(position)) > 64.0) {
			return null;
		}
		WorkshopContainerResolver.Result resolved = WorkshopContainerResolver.resolve(world, position);
		if (!resolved.successful() || !resolved.container().representativePosition().equals(position)) {
			return null;
		}
		LogicalContainer container = resolved.container();
		ContainerLabelSummary summary = ContainerLabelService.summarize(container);
		if (!WorkshopDepositPlanner.isEligible(summary) || !summary.equals(entry.labelSummary())) {
			return null;
		}
		if (!container.inventory().canPlayerUse(player)) {
			return null;
		}
		BlockState state = world.getBlockState(position);
		if (state.getBlock() instanceof ChestBlock chest) {
			Inventory accessible = ChestBlock.getInventory(chest, state, world, position, false);
			if (accessible == null || !container.matchesInventory(accessible)) {
				return null;
			}
		}
		for (BlockEntity member : container.members()) {
			if (!(member instanceof SilentContainerAccess access) || !access.workshopZone$canOpenSilently(player)) {
				return null;
			}
		}
		try {
			if (!WorkshopRemoteOpenCallback.EVENT.invoker().canOpen(player, world, position, state, entry.type())) {
				return null;
			}
		} catch (RuntimeException exception) {
			WorkshopZone.LOGGER.debug("Workshop remote-open callback denied deposit destination {}", position, exception);
			return null;
		}
		ContainerLabelRuleHolder ruleHolder = new ContainerLabelRuleHolder(container);
		return WorkshopDepositDestination.create(container, ruleHolder.rule(),
			player.squaredDistanceTo(Vec3d.ofCenter(position)), scanIndex);
	}

	private boolean allowsDeposit(ServerPlayerEntity player, WorkshopDepositDestination destination, ItemVariant variant) {
		try {
			return WorkshopDepositCallback.EVENT.invoker().canDeposit(
				player, player.getServerWorld(), destination.representativePosition(), destination.rule(), variant
			) == WorkshopDepositCallback.Result.ALLOW;
		} catch (RuntimeException exception) {
			WorkshopZone.LOGGER.debug(
				"Workshop deposit callback failed for destination {}; denying it",
				destination.representativePosition(), exception
			);
			return false;
		}
	}

	private static boolean isLoaded(ServerWorld world, BlockPos position) {
		return world.getChunkManager().isChunkLoaded(
			ChunkSectionPos.getSectionCoord(position.getX()), ChunkSectionPos.getSectionCoord(position.getZ())
		);
	}

	private WorkshopDepositResultPayload result(
		DepositWorkshopItemsPayload request,
		WorkshopDepositResult result,
		int movedItems,
		int movedStacks,
		int remaining,
		int usedDestinations
	) {
		return new WorkshopDepositResultPayload(
			request.requestId(), request.sessionId(), request.syncId(), result,
			movedItems, movedStacks, remaining, usedDestinations
		);
	}

	private record DestinationBuild(List<WorkshopDepositDestination> destinations, int labeledEntryCount) {
	}

	private record ContainerLabelRuleHolder(LogicalContainer container) {
		private io.github.ikunkk02afk.workshopzone.label.ContainerLabelRule rule() {
			return container.holders().getFirst().workshopZone$getLabelRule();
		}
	}
}
