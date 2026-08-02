package io.github.ikunkk02afk.workshopzone.label;

import net.minecraft.util.Identifier;

import java.util.Objects;
import java.util.Optional;

public record ContainerLabelSummary(ContainerLabelMode mode, Optional<Identifier> exactItemId, boolean conflict) {
	public static final ContainerLabelSummary NONE = new ContainerLabelSummary(ContainerLabelMode.NONE, Optional.empty(), false);
	public static final ContainerLabelSummary CONFLICT = new ContainerLabelSummary(ContainerLabelMode.NONE, Optional.empty(), true);

	public ContainerLabelSummary {
		Objects.requireNonNull(mode, "mode");
		exactItemId = Objects.requireNonNull(exactItemId, "exactItemId");
		if (conflict && (mode != ContainerLabelMode.NONE || exactItemId.isPresent())) {
			throw new IllegalArgumentException("Conflicting labels do not expose either side as authoritative");
		}
		if (!conflict && (mode == ContainerLabelMode.EXACT_ITEM) != exactItemId.isPresent()) {
			throw new IllegalArgumentException("Exact-item summary and item id must be present together");
		}
	}

	public static ContainerLabelSummary of(ContainerLabelRule rule) {
		return rule.mode() == ContainerLabelMode.NONE
			? NONE
			: new ContainerLabelSummary(rule.mode(), rule.exactItemId(), false);
	}

	public boolean hasLabel() {
		return !conflict && mode != ContainerLabelMode.NONE;
	}
}
