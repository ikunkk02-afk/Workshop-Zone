package io.github.ikunkk02afk.workshopzone.search;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import io.github.ikunkk02afk.workshopzone.api.WorkshopRemoteOpenCallback;
import io.github.ikunkk02afk.workshopzone.api.WorkshopSearchAccessCallback;
import io.github.ikunkk02afk.workshopzone.label.LogicalContainer;
import io.github.ikunkk02afk.workshopzone.label.SilentContainerAccess;
import io.github.ikunkk02afk.workshopzone.scan.WorkshopBlockEntry;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.Objects;

public final class WorkshopContainerAccessService {
	private final OpenPermission openPermission;
	private final SearchPermission searchPermission;

	public WorkshopContainerAccessService() {
		this(
			(player, world, entry, state) -> WorkshopRemoteOpenCallback.EVENT.invoker().canOpen(
				player, world, entry.position(), state, entry.type()
			),
			(player, world, container, item) -> WorkshopSearchAccessCallback.EVENT.invoker().canSearch(
				player, world, container.representativePosition(), item
			) == WorkshopSearchAccessCallback.Result.ALLOW
		);
	}

	public WorkshopContainerAccessService(OpenPermission openPermission, SearchPermission searchPermission) {
		this.openPermission = Objects.requireNonNull(openPermission, "openPermission");
		this.searchPermission = Objects.requireNonNull(searchPermission, "searchPermission");
	}

	public AccessResult canSearch(
		ServerPlayerEntity player,
		ServerWorld world,
		WorkshopBlockEntry entry,
		LogicalContainer container,
		Item targetItem
	) {
		AccessResult containerAccess = canAccessContainer(player, world, entry, container);
		return containerAccess == AccessResult.ALLOW
			? canSearchItem(player, world, container, targetItem)
			: containerAccess;
	}

	public AccessResult canAccessContainer(
		ServerPlayerEntity player,
		ServerWorld world,
		WorkshopBlockEntry entry,
		LogicalContainer container
	) {
		for (BlockEntity member : container.members()) {
			if (member instanceof LootableContainerBlockEntity lootable && lootable.getLootTable() != null) {
				return AccessResult.UNGENERATED_LOOT;
			}
		}
		if (!container.inventory().canPlayerUse(player)) {
			return AccessResult.PLAYER_CANNOT_USE;
		}
		BlockState state = world.getBlockState(container.representativePosition());
		if (state.getBlock() instanceof ChestBlock chest) {
			Inventory accessible = ChestBlock.getInventory(chest, state, world, container.representativePosition(), false);
			if (accessible == null || !container.matchesInventory(accessible)) {
				return AccessResult.BLOCKED;
			}
		}
		for (BlockEntity member : container.members()) {
			if (!(member instanceof SilentContainerAccess access) || !access.workshopZone$canOpenSilently(player)) {
				return AccessResult.LOCKED;
			}
		}
		try {
			if (!openPermission.canOpen(player, world, entry, state)) {
				return AccessResult.OPEN_CALLBACK_DENIED;
			}
		} catch (RuntimeException exception) {
			WorkshopZone.LOGGER.debug(
				"Workshop container access callback failed for {}; denying search",
				container.representativePosition(), exception
			);
			return AccessResult.CALLBACK_ERROR;
		}
		return AccessResult.ALLOW;
	}

	public AccessResult canSearchItem(
		ServerPlayerEntity player,
		ServerWorld world,
		LogicalContainer container,
		Item targetItem
	) {
		Objects.requireNonNull(targetItem, "targetItem");
		try {
			return searchPermission.canSearch(player, world, container, targetItem)
				? AccessResult.ALLOW : AccessResult.SEARCH_CALLBACK_DENIED;
		} catch (RuntimeException exception) {
			WorkshopZone.LOGGER.debug(
				"Workshop item search access callback failed for {}; denying item {}",
				container.representativePosition(), targetItem, exception
			);
			return AccessResult.CALLBACK_ERROR;
		}
	}

	public enum AccessResult {
		ALLOW,
		PLAYER_CANNOT_USE,
		UNGENERATED_LOOT,
		BLOCKED,
		LOCKED,
		OPEN_CALLBACK_DENIED,
		SEARCH_CALLBACK_DENIED,
		CALLBACK_ERROR
	}

	@FunctionalInterface
	public interface OpenPermission {
		boolean canOpen(ServerPlayerEntity player, ServerWorld world, WorkshopBlockEntry entry, BlockState state);
	}

	@FunctionalInterface
	public interface SearchPermission {
		boolean canSearch(ServerPlayerEntity player, ServerWorld world, LogicalContainer container, Item targetItem);
	}
}
