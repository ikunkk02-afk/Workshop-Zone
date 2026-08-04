package io.github.ikunkk02afk.workshopzone.client;

import net.minecraft.util.Identifier;

import java.util.Objects;

public record WorkshopItemCandidateMetadata(
	Identifier itemId,
	String localizedName,
	String namespace,
	long totalCount,
	int matchingContainerCount,
	boolean multipleVariants
) {
	public WorkshopItemCandidateMetadata(Identifier itemId, String localizedName) {
		this(itemId, localizedName, itemId == null ? "" : itemId.getNamespace(), 1, 1, false);
	}

	public WorkshopItemCandidateMetadata(Identifier itemId, String localizedName, String namespace) {
		this(itemId, localizedName, namespace, 1, 1, false);
	}

	public WorkshopItemCandidateMetadata {
		Objects.requireNonNull(itemId, "itemId");
		localizedName = Objects.requireNonNull(localizedName, "localizedName");
		namespace = Objects.requireNonNull(namespace, "namespace");
		if (!namespace.equals(itemId.getNamespace()) || !WorkshopItemCandidate.isValidId(itemId)
			|| totalCount <= 0 || matchingContainerCount <= 0) {
			throw new IllegalArgumentException("Invalid workshop item candidate metadata");
		}
	}
}
