package io.github.ikunkk02afk.workshopzone.client;

import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

import java.util.Objects;

public record WorkshopItemCandidate(
	Identifier itemId,
	String localizedName,
	ItemStack icon,
	String namespace
) {
	public WorkshopItemCandidate(Identifier itemId, String localizedName, ItemStack icon) {
		this(itemId, localizedName, icon, itemId == null ? "" : itemId.getNamespace());
	}

	public WorkshopItemCandidate {
		Objects.requireNonNull(itemId, "itemId");
		localizedName = Objects.requireNonNull(localizedName, "localizedName");
		icon = Objects.requireNonNull(icon, "icon").copy();
		namespace = Objects.requireNonNull(namespace, "namespace");
		if (!namespace.equals(itemId.getNamespace()) || !isValid(itemId, icon)) {
			throw new IllegalArgumentException("Invalid workshop item candidate");
		}
	}

	public static boolean isValid(Identifier itemId, ItemStack icon) {
		return isValidId(itemId) && icon != null && !icon.isEmpty();
	}

	public static boolean isValidId(Identifier itemId) {
		return itemId != null && !Identifier.ofVanilla("air").equals(itemId);
	}

	public WorkshopItemCandidateMetadata metadata() {
		return new WorkshopItemCandidateMetadata(itemId, localizedName, namespace);
	}
}
