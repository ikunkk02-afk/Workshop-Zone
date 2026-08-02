package io.github.ikunkk02afk.workshopzone.label;

import io.github.ikunkk02afk.workshopzone.scan.WorkshopBlockType;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Objects;

public record LogicalContainer(
	BlockPos representativePosition,
	List<BlockPos> memberPositions,
	Inventory inventory,
	List<BlockEntity> members,
	WorkshopBlockType type,
	boolean doubleChest
) {
	public LogicalContainer {
		representativePosition = Objects.requireNonNull(representativePosition, "representativePosition").toImmutable();
		memberPositions = memberPositions.stream().map(BlockPos::toImmutable).toList();
		Objects.requireNonNull(inventory, "inventory");
		members = List.copyOf(members);
		Objects.requireNonNull(type, "type");
		if (memberPositions.size() != members.size() || members.isEmpty() || doubleChest != (members.size() == 2)) {
			throw new IllegalArgumentException("Invalid logical container membership");
		}
		if (members.stream().anyMatch(member -> !(member instanceof ContainerLabelHolder))) {
			throw new IllegalArgumentException("Logical container members must support labels");
		}
	}

	public List<ContainerLabelHolder> holders() {
		return members.stream().map(member -> (ContainerLabelHolder)member).toList();
	}

	public boolean matchesInventory(Inventory openInventory) {
		if (members.size() == 1) {
			return openInventory == members.getFirst();
		}
		return openInventory.size() == inventory.size()
			&& members.stream().allMatch(member -> containsInventory(openInventory, (Inventory)member));
	}

	private static boolean containsInventory(Inventory inventory, Inventory member) {
		return inventory == member || inventory instanceof net.minecraft.inventory.DoubleInventory combined && combined.isPart(member);
	}
}
