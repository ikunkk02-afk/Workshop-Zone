package io.github.ikunkk02afk.workshopzone.search;

import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.List;
import java.util.Objects;

public final class WorkshopItemSearchCounter {
	private WorkshopItemSearchCounter() {
	}

	public static Count count(Inventory inventory, Item targetItem) {
		Objects.requireNonNull(inventory, "inventory");
		Objects.requireNonNull(targetItem, "targetItem");
		long itemCount = 0L;
		int matchingSlots = 0;
		boolean multipleVariants = false;
		ItemStack firstVariant = null;
		for (int slot = 0; slot < inventory.size(); slot++) {
			ItemStack stack = inventory.getStack(slot);
			if (stack.isEmpty() || stack.getItem() != targetItem) {
				continue;
			}
			itemCount += (long)stack.getCount();
			matchingSlots++;
			if (firstVariant == null) {
				firstVariant = stack.copyWithCount(1);
			} else if (!ItemStack.areItemsAndComponentsEqual(firstVariant, stack)) {
				multipleVariants = true;
			}
		}
		return new Count(itemCount, matchingSlots, multipleVariants);
	}

	static Count countSamples(List<StackSample> samples, String targetItemKey) {
		Objects.requireNonNull(samples, "samples");
		Objects.requireNonNull(targetItemKey, "targetItemKey");
		long itemCount = 0L;
		int matchingSlots = 0;
		Object firstVariant = null;
		boolean multipleVariants = false;
		for (StackSample sample : samples) {
			if (!sample.itemKey().equals(targetItemKey) || sample.count() <= 0) {
				continue;
			}
			itemCount += sample.count();
			matchingSlots++;
			if (firstVariant == null) {
				firstVariant = sample.variantKey();
			} else if (!firstVariant.equals(sample.variantKey())) {
				multipleVariants = true;
			}
		}
		return new Count(itemCount, matchingSlots, multipleVariants);
	}

	record StackSample(String itemKey, int count, Object variantKey) {
		StackSample {
			Objects.requireNonNull(itemKey, "itemKey");
			Objects.requireNonNull(variantKey, "variantKey");
		}
	}

	public record Count(long itemCount, int matchingSlotCount, boolean multipleVariants) {
		public Count {
			if (itemCount < 0 || matchingSlotCount < 0 || (itemCount == 0) != (matchingSlotCount == 0)) {
				throw new IllegalArgumentException("Invalid workshop item count");
			}
		}
	}
}
