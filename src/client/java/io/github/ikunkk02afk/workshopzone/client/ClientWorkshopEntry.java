package io.github.ikunkk02afk.workshopzone.client;

import io.github.ikunkk02afk.workshopzone.label.ContainerLabelSummary;
import io.github.ikunkk02afk.workshopzone.scan.WorkshopBlockType;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record ClientWorkshopEntry(
	WorkshopBlockType type,
	BlockPos position,
	Identifier blockId,
	double distanceSquared,
	boolean container,
	boolean workstation,
	boolean customName,
	Text displayName,
	ItemStack icon,
	ContainerLabelSummary labelSummary,
	ItemStack labelIcon
) {
}
