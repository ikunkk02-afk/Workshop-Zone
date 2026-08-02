package io.github.ikunkk02afk.workshopzone.label;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ContainerLabelService {
	private ContainerLabelService() {
	}

	public static boolean canInsert(ContainerLabelHolder holder, ItemStack stack) {
		if (stack.isEmpty()) {
			return true;
		}
		return !summary(holder).conflict() && holder.workshopZone$getLabelRule().canInsert(stack);
	}

	public static ContainerLabelSummary summary(ContainerLabelHolder holder) {
		ContainerLabelRule ownRule = holder.workshopZone$getLabelRule();
		if (!(holder instanceof BlockEntity blockEntity)
			|| !(blockEntity.getWorld() instanceof ServerWorld world)
			|| !(blockEntity.getCachedState().getBlock() instanceof ChestBlock)
			|| !blockEntity.getCachedState().contains(ChestBlock.CHEST_TYPE)) {
			return ContainerLabelSummary.of(ownRule);
		}
		BlockState state = blockEntity.getCachedState();
		if (state.get(ChestBlock.CHEST_TYPE) == net.minecraft.block.enums.ChestType.SINGLE) {
			return ContainerLabelSummary.of(ownRule);
		}
		BlockPos otherPosition = blockEntity.getPos().offset(ChestBlock.getFacing(state));
		if (!isLoaded(world, otherPosition)) {
			return ContainerLabelSummary.CONFLICT;
		}
		BlockState otherState = world.getBlockState(otherPosition);
		BlockEntity other = world.getBlockEntity(otherPosition);
		if (otherState.getBlock() != state.getBlock()
			|| !(other instanceof ContainerLabelHolder otherHolder)
			|| !otherPosition.offset(ChestBlock.getFacing(otherState)).equals(blockEntity.getPos())) {
			return ContainerLabelSummary.CONFLICT;
		}
		return ownRule.equals(otherHolder.workshopZone$getLabelRule())
			? ContainerLabelSummary.of(ownRule)
			: ContainerLabelSummary.CONFLICT;
	}

	public static ContainerLabelSummary summarizeInventories(Inventory first, Inventory second) {
		if (!(first instanceof ContainerLabelHolder firstHolder) || !(second instanceof ContainerLabelHolder secondHolder)) {
			return ContainerLabelSummary.NONE;
		}
		ContainerLabelRule firstRule = firstHolder.workshopZone$getLabelRule();
		return firstRule.equals(secondHolder.workshopZone$getLabelRule())
			? ContainerLabelSummary.of(firstRule)
			: ContainerLabelSummary.CONFLICT;
	}

	public static ContainerLabelSummary reconcile(LogicalContainer container) {
		List<ContainerLabelHolder> holders = container.holders();
		ContainerLabelRule first = holders.getFirst().workshopZone$getLabelRule();
		if (holders.size() == 1) {
			return ContainerLabelSummary.of(first);
		}
		ContainerLabelRule second = holders.get(1).workshopZone$getLabelRule();
		if (first.equals(second)) {
			return ContainerLabelSummary.of(first);
		}
		ContainerLabelRule exact = first.mode() == ContainerLabelMode.EXACT_ITEM && second.mode() == ContainerLabelMode.NONE
			? first
			: second.mode() == ContainerLabelMode.EXACT_ITEM && first.mode() == ContainerLabelMode.NONE ? second : null;
		if (exact == null || !validateContents(container.inventory(), exact).compatible()) {
			return ContainerLabelSummary.CONFLICT;
		}
		if (!applyAtomically(holders, exact)) {
			return ContainerLabelSummary.CONFLICT;
		}
		return ContainerLabelSummary.of(exact);
	}

	public static ContainerLabelSummary summarize(LogicalContainer container) {
		List<ContainerLabelHolder> holders = container.holders();
		ContainerLabelRule first = holders.getFirst().workshopZone$getLabelRule();
		return holders.stream().allMatch(holder -> holder.workshopZone$getLabelRule().equals(first))
			? ContainerLabelSummary.of(first)
			: ContainerLabelSummary.CONFLICT;
	}

	public static ContentValidation validateContents(Inventory inventory, ContainerLabelRule rule) {
		int mismatches = 0;
		Identifier firstMismatch = null;
		for (int slot = 0; slot < inventory.size(); slot++) {
			ItemStack stack = inventory.getStack(slot);
			if (!stack.isEmpty() && !rule.canInsert(stack)) {
				mismatches++;
				if (firstMismatch == null) {
					firstMismatch = Registries.ITEM.getId(stack.getItem());
				}
			}
		}
		return new ContentValidation(mismatches == 0, Optional.ofNullable(firstMismatch), mismatches);
	}

	public static boolean applyAtomically(LogicalContainer container, ContainerLabelRule requestedRule) {
		return applyAtomically(container.holders(), requestedRule);
	}

	private static boolean applyAtomically(List<ContainerLabelHolder> holders, ContainerLabelRule requestedRule) {
		List<ContainerLabelRule> previous = holders.stream().map(ContainerLabelHolder::workshopZone$getLabelRule).toList();
		List<ContainerLabelHolder> changed = new ArrayList<>();
		try {
			for (ContainerLabelHolder holder : holders) {
				changed.add(holder);
				holder.workshopZone$setLabelRule(requestedRule);
				if (!holder.workshopZone$getLabelRule().equals(requestedRule)) {
					throw new IllegalStateException("Container label holder rejected a server-side update");
				}
			}
			return true;
		} catch (RuntimeException exception) {
			WorkshopZone.LOGGER.error("Failed to update all members of a logical container; rolling back", exception);
			for (int index = 0; index < changed.size(); index++) {
				try {
					changed.get(index).workshopZone$setLabelRule(previous.get(index));
				} catch (RuntimeException rollbackFailure) {
					WorkshopZone.LOGGER.error("Failed to roll back a container label member", rollbackFailure);
				}
			}
			return false;
		}
	}

	private static boolean isLoaded(ServerWorld world, BlockPos position) {
		return world.getChunkManager().isChunkLoaded(
			ChunkSectionPos.getSectionCoord(position.getX()), ChunkSectionPos.getSectionCoord(position.getZ())
		);
	}

	public record ContentValidation(boolean compatible, Optional<Identifier> firstMismatchItemId, int mismatchSlotCount) {
	}
}
