package io.github.ikunkk02afk.workshopzone.search;

import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

public final class WorkshopItemCatalogBuilder {
	private final Map<Identifier, MutableEntry> entries = new HashMap<>();

	public void addContainer(Inventory inventory, Predicate<Item> itemAccess) {
		Objects.requireNonNull(inventory, "inventory");
		Objects.requireNonNull(itemAccess, "itemAccess");
		Set<Identifier> countedInContainer = new HashSet<>();
		Map<Item, Boolean> accessByItem = new IdentityHashMap<>();
		for (int slot = 0; slot < inventory.size(); slot++) {
			ItemStack stack = inventory.getStack(slot);
			if (stack.isEmpty()) {
				continue;
			}
			Item item = stack.getItem();
			Identifier itemId = Registries.ITEM.getId(item);
			if (item == Items.AIR || Identifier.ofVanilla("air").equals(itemId)
				|| itemId.toString().length() > WorkshopItemCatalogEntry.MAX_ITEM_ID_LENGTH
				|| !accessByItem.computeIfAbsent(item, itemAccess::test)) {
				continue;
			}
			MutableEntry entry = entries.computeIfAbsent(itemId, ignored -> new MutableEntry());
			entry.add(stack, countedInContainer.add(itemId));
		}
	}

	public WorkshopItemCatalog build(int maxEntries) {
		return WorkshopItemCatalog.fromEntries(entries.entrySet().stream()
			.map(entry -> entry.getValue().toEntry(entry.getKey()))
			.toList(), maxEntries);
	}

	static WorkshopItemCatalog aggregateSamples(
		java.util.List<java.util.List<StackSample>> containers,
		Predicate<Identifier> itemAccess,
		int maxEntries
	) {
		Objects.requireNonNull(containers, "containers");
		Objects.requireNonNull(itemAccess, "itemAccess");
		Map<Identifier, SampleMutableEntry> entries = new HashMap<>();
		for (java.util.List<StackSample> container : containers) {
			Set<Identifier> countedInContainer = new HashSet<>();
			for (StackSample sample : container) {
				if (sample.count() <= 0 || Identifier.ofVanilla("air").equals(sample.itemId())
					|| !itemAccess.test(sample.itemId())) {
					continue;
				}
				entries.computeIfAbsent(sample.itemId(), ignored -> new SampleMutableEntry())
					.add(sample, countedInContainer.add(sample.itemId()));
			}
		}
		return WorkshopItemCatalog.fromEntries(entries.entrySet().stream()
			.map(entry -> entry.getValue().toEntry(entry.getKey()))
			.toList(), maxEntries);
	}

	record StackSample(Identifier itemId, long count, Object variantKey) {
		StackSample {
			Objects.requireNonNull(itemId, "itemId");
			Objects.requireNonNull(variantKey, "variantKey");
		}
	}

	private static final class MutableEntry {
		private long totalCount;
		private int matchingContainerCount;
		private ItemStack firstVariant;
		private boolean multipleVariants;

		private void add(ItemStack stack, boolean firstInContainer) {
			totalCount = Math.addExact(totalCount, stack.getCount());
			if (firstInContainer) {
				matchingContainerCount = Math.incrementExact(matchingContainerCount);
			}
			if (firstVariant == null) {
				firstVariant = stack.copyWithCount(1);
			} else if (!ItemStack.areItemsAndComponentsEqual(firstVariant, stack)) {
				multipleVariants = true;
			}
		}

		private WorkshopItemCatalogEntry toEntry(Identifier itemId) {
			return new WorkshopItemCatalogEntry(itemId, totalCount, matchingContainerCount, multipleVariants);
		}
	}

	private static final class SampleMutableEntry {
		private long totalCount;
		private int matchingContainerCount;
		private Object firstVariant;
		private boolean multipleVariants;

		private void add(StackSample sample, boolean firstInContainer) {
			totalCount = Math.addExact(totalCount, sample.count());
			if (firstInContainer) {
				matchingContainerCount = Math.incrementExact(matchingContainerCount);
			}
			if (firstVariant == null) {
				firstVariant = sample.variantKey();
			} else if (!firstVariant.equals(sample.variantKey())) {
				multipleVariants = true;
			}
		}

		private WorkshopItemCatalogEntry toEntry(Identifier itemId) {
			return new WorkshopItemCatalogEntry(itemId, totalCount, matchingContainerCount, multipleVariants);
		}
	}
}
