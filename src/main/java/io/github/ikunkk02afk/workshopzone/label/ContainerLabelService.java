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
		return !summary(holder).blocksInput() && holder.workshopZone$getLabelRule().canInsert(stack);
	}

	public static ContainerLabelSummary summary(ContainerLabelHolder holder) {
		ContainerLabelRule ownRule = holder.workshopZone$getLabelRule();
		if (!(holder instanceof BlockEntity blockEntity) || !(blockEntity.getWorld() instanceof ServerWorld world)) {
			return ContainerLabelSummary.of(ownRule);
		}
		WorkshopContainerResolver.Result resolved = WorkshopContainerResolver.resolve(world, blockEntity.getPos());
		if (resolved.successful()) {
			return summarize(resolved.container());
		}
		if (blockEntity.getCachedState().getBlock() instanceof ChestBlock
			&& blockEntity.getCachedState().contains(ChestBlock.CHEST_TYPE)
			&& blockEntity.getCachedState().get(ChestBlock.CHEST_TYPE) != net.minecraft.block.enums.ChestType.SINGLE) {
			return ContainerLabelSummary.CONFLICT;
		}
		return ContainerLabelSummary.of(ownRule);
	}

	public static ContainerLabelSummary summarizeInventories(Inventory first, Inventory second) {
		if (!(first instanceof ContainerLabelHolder firstHolder) || !(second instanceof ContainerLabelHolder secondHolder)) {
			return ContainerLabelSummary.NONE;
		}
		ContainerLabelRule firstRule = firstHolder.workshopZone$getLabelRule();
		if (!firstRule.equals(secondHolder.workshopZone$getLabelRule())) {
			return ContainerLabelSummary.CONFLICT;
		}
		if (firstRule.mode() == ContainerLabelMode.NONE || firstRule.mode() == ContainerLabelMode.EXACT_ITEM) {
			return ContainerLabelSummary.of(firstRule);
		}
		boolean contentConflict = !validateContents(first, firstRule).compatible()
			|| !validateContents(second, firstRule).compatible();
		return firstRule.mode() == ContainerLabelMode.ITEM_TAG
			? ContainerLabelSummary.itemTag(firstRule, contentConflict)
			: ContainerLabelSummary.whitelist(firstRule, contentConflict);
	}

	public static ContainerLabelSummary reconcile(LogicalContainer container) {
		List<ContainerLabelHolder> holders = container.holders();
		ContainerLabelRule first = holders.getFirst().workshopZone$getLabelRule();
		if (holders.size() == 1) {
			return ContainerLabelSummary.of(first);
		}
		ContainerLabelRule second = holders.get(1).workshopZone$getLabelRule();
		if (first.equals(second)) {
			return summarizeMatchingRule(container.inventory(), first);
		}
		ContainerLabelRule labeled = first.mode() != ContainerLabelMode.NONE && second.mode() == ContainerLabelMode.NONE
			? first
			: second.mode() != ContainerLabelMode.NONE && first.mode() == ContainerLabelMode.NONE ? second : null;
		if (labeled == null
			|| ContainerLabelSummary.of(labeled).unavailable()
			|| !validateContents(container.inventory(), labeled).compatible()) {
			return ContainerLabelSummary.CONFLICT;
		}
		if (!applyAtomically(holders, labeled)) {
			return ContainerLabelSummary.CONFLICT;
		}
		return summarizeMatchingRule(container.inventory(), labeled);
	}

	public static ContainerLabelSummary summarize(LogicalContainer container) {
		List<ContainerLabelHolder> holders = container.holders();
		ContainerLabelRule first = holders.getFirst().workshopZone$getLabelRule();
		return holders.stream().allMatch(holder -> holder.workshopZone$getLabelRule().equals(first))
			? summarizeMatchingRule(container.inventory(), first)
			: ContainerLabelSummary.CONFLICT;
	}

	private static ContainerLabelSummary summarizeMatchingRule(Inventory inventory, ContainerLabelRule rule) {
		return switch (rule.mode()) {
			case NONE, EXACT_ITEM -> ContainerLabelSummary.of(rule);
			case ITEM_TAG -> {
				Identifier tagId = rule.itemTagId().orElseThrow();
				if (ContainerItemTags.availability(tagId) != ContainerItemTags.Availability.AVAILABLE) {
					yield ContainerLabelSummary.itemTag(rule, false);
				}
				yield ContainerLabelSummary.itemTag(rule, !validateContents(inventory, rule).compatible());
			}
			case WHITELIST -> ContainerLabelSummary.whitelist(rule, !validateContents(inventory, rule).compatible());
		};
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

	public record ContentValidation(boolean compatible, Optional<Identifier> firstMismatchItemId, int mismatchSlotCount) {
	}
}
