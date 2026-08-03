package io.github.ikunkk02afk.workshopzone.client;

import net.minecraft.util.Identifier;

import java.util.Objects;

public record WorkshopItemCandidateMetadata(Identifier itemId, String localizedName, String namespace) {
	public WorkshopItemCandidateMetadata(Identifier itemId, String localizedName) {
		this(itemId, localizedName, itemId == null ? "" : itemId.getNamespace());
	}

	public WorkshopItemCandidateMetadata {
		Objects.requireNonNull(itemId, "itemId");
		localizedName = Objects.requireNonNull(localizedName, "localizedName");
		namespace = Objects.requireNonNull(namespace, "namespace");
		if (!namespace.equals(itemId.getNamespace()) || !WorkshopItemCandidate.isValidId(itemId)) {
			throw new IllegalArgumentException("Invalid workshop item candidate metadata");
		}
	}
}
