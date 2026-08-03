package io.github.ikunkk02afk.workshopzone.client;

import io.github.ikunkk02afk.workshopzone.label.ContainerLabelSummary;
import io.github.ikunkk02afk.workshopzone.scan.WorkshopBlockType;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Objects;

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
	List<ItemStack> labelIcons
) {
	public ClientWorkshopEntry {
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(position, "position");
		Objects.requireNonNull(blockId, "blockId");
		Objects.requireNonNull(displayName, "displayName");
		Objects.requireNonNull(icon, "icon");
		Objects.requireNonNull(labelSummary, "labelSummary");
		labelIcons = immutablePreviewList(labelIcons, ContainerLabelSummary.MAX_PREVIEW_ITEMS);
	}

	public ItemStack labelIcon() {
		return labelIcons.isEmpty() ? ItemStack.EMPTY : labelIcons.getFirst();
	}

	static <T> List<T> immutablePreviewList(List<T> values, int maxSize) {
		List<T> result = List.copyOf(Objects.requireNonNull(values, "values"));
		if (result.size() > maxSize) {
			throw new IllegalArgumentException("Client label icon previews exceed the snapshot limit");
		}
		return result;
	}
}
