package io.github.ikunkk02afk.workshopzone.label;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class ContainerLabelData {
	public static final String NBT_KEY = "workshop_zone:container_label";
	public static final int VERSION = 2;
	private static final int LEGACY_VERSION = 1;
	private static final Set<String> WARNED_MESSAGES = ConcurrentHashMap.newKeySet();

	private ContainerLabelData() {
	}

	public static ContainerLabelRule read(NbtCompound blockEntityNbt) {
		return read(
			blockEntityNbt,
			id -> Registries.ITEM.getOrEmpty(id).isPresent() && !Identifier.ofVanilla("air").equals(id),
			ContainerLabelData::warnOnce
		);
	}

	private static void warnOnce(String warning) {
		if (WARNED_MESSAGES.add(warning)) {
			WorkshopZone.LOGGER.warn("{}", warning);
		}
	}

	static ContainerLabelRule read(
		NbtCompound blockEntityNbt,
		Predicate<Identifier> validItem,
		Consumer<String> warningSink
	) {
		if (!blockEntityNbt.contains(NBT_KEY)) {
			return ContainerLabelRule.NONE;
		}
		if (!blockEntityNbt.contains(NBT_KEY, NbtElement.COMPOUND_TYPE)) {
			warningSink.accept("Ignoring malformed Workshop Zone container label: expected a compound");
			return ContainerLabelRule.NONE;
		}
		NbtCompound data = blockEntityNbt.getCompound(NBT_KEY);
		int version = data.getInt("version");
		if (version != LEGACY_VERSION && version != VERSION) {
			warningSink.accept("Ignoring unsupported Workshop Zone container label version " + version);
			return ContainerLabelRule.NONE;
		}
		Identifier modeId = Identifier.tryParse(data.getString("mode"));
		Optional<ContainerLabelMode> mode = modeId == null ? Optional.empty() : ContainerLabelMode.fromId(modeId);
		if (mode.isEmpty()) {
			warningSink.accept("Ignoring unknown Workshop Zone container label mode " + data.getString("mode"));
			return ContainerLabelRule.NONE;
		}
		if (mode.get() == ContainerLabelMode.NONE) {
			if (data.contains("item") || data.contains("tag")) {
				warningSink.accept("Ignoring malformed Workshop Zone none label with a value field");
			}
			return ContainerLabelRule.NONE;
		}
		if (version == LEGACY_VERSION && mode.get() != ContainerLabelMode.EXACT_ITEM) {
			warningSink.accept("Ignoring unsupported version 1 Workshop Zone label mode " + mode.get().id());
			return ContainerLabelRule.NONE;
		}
		if (mode.get() == ContainerLabelMode.EXACT_ITEM) {
			if (!data.contains("item", NbtElement.STRING_TYPE) || data.contains("tag")) {
				warningSink.accept("Ignoring malformed Workshop Zone exact-item label fields");
				return ContainerLabelRule.NONE;
			}
			Identifier itemId = Identifier.tryParse(data.getString("item"));
			if (itemId == null || !validItem.test(itemId) || Identifier.ofVanilla("air").equals(itemId)) {
				warningSink.accept("Ignoring invalid Workshop Zone exact-item label " + data.getString("item"));
				return ContainerLabelRule.NONE;
			}
			return ContainerLabelRule.exactItem(itemId);
		}
		if (!data.contains("tag", NbtElement.STRING_TYPE) || data.contains("item")) {
			warningSink.accept("Ignoring malformed Workshop Zone item-tag label fields");
			return ContainerLabelRule.NONE;
		}
		Identifier tagId = Identifier.tryParse(data.getString("tag"));
		if (tagId == null) {
			warningSink.accept("Ignoring invalid Workshop Zone item-tag label " + data.getString("tag"));
			return ContainerLabelRule.NONE;
		}
		return ContainerLabelRule.itemTag(tagId);
	}

	public static void write(NbtCompound blockEntityNbt, ContainerLabelRule rule) {
		blockEntityNbt.remove(NBT_KEY);
		if (rule.mode() == ContainerLabelMode.NONE) {
			return;
		}
		NbtCompound data = new NbtCompound();
		data.putInt("version", VERSION);
		data.putString("mode", rule.mode().id().toString());
		switch (rule.mode()) {
			case EXACT_ITEM -> data.putString("item", rule.exactItemId().orElseThrow().toString());
			case ITEM_TAG -> data.putString("tag", rule.itemTagId().orElseThrow().toString());
			case NONE -> throw new IllegalStateException("None labels are not serialized");
		}
		blockEntityNbt.put(NBT_KEY, data);
	}
}
