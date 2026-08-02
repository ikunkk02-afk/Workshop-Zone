package io.github.ikunkk02afk.workshopzone.label;

import net.minecraft.util.Identifier;

import java.util.Objects;

public record ContainerTagCandidate(Identifier tagId, Identifier representativeItemId) {
	public ContainerTagCandidate {
		Objects.requireNonNull(tagId, "tagId");
		Objects.requireNonNull(representativeItemId, "representativeItemId");
	}
}
