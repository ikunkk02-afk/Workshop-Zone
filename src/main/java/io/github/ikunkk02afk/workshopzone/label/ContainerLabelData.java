package io.github.ikunkk02afk.workshopzone.label;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class ContainerLabelData {
	public static final String NBT_KEY = "workshop_zone:container_label";
	public static final int VERSION = 3;
	private static final int LEGACY_VERSION = 1;
	private static final int PREVIOUS_VERSION = 2;
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
		if (version != LEGACY_VERSION && version != PREVIOUS_VERSION && version != VERSION) {
			warningSink.accept("Ignoring unsupported Workshop Zone container label version " + version);
			return ContainerLabelRule.NONE;
		}
		Identifier modeId = Identifier.tryParse(data.getString("mode"));
		ContainerLabelMode mode = modeId == null ? null : ContainerLabelMode.fromId(modeId).orElse(null);
		if (mode == null) {
			warningSink.accept("Ignoring unknown Workshop Zone container label mode " + data.getString("mode"));
			return ContainerLabelRule.NONE;
		}
		if (mode == ContainerLabelMode.NONE) {
			if (data.contains("item") || data.contains("tag") || data.contains("entries")) {
				warningSink.accept("Ignoring malformed Workshop Zone none label with value fields");
				return ContainerLabelRule.NONE;
			}
			return ContainerLabelRule.NONE;
		}
		if (version == LEGACY_VERSION && mode != ContainerLabelMode.EXACT_ITEM) {
			warningSink.accept("Ignoring unsupported version 1 Workshop Zone label mode " + mode.id());
			return ContainerLabelRule.NONE;
		}
		try {
			return switch (mode) {
				case EXACT_ITEM -> readExact(data, validItem);
				case ITEM_TAG -> readTag(data);
				case WHITELIST -> version == VERSION ? readWhitelist(data, validItem, warningSink) : malformed(warningSink, "Whitelist labels require version 3");
				case NONE -> ContainerLabelRule.NONE;
			};
		} catch (RuntimeException exception) {
			warningSink.accept("Ignoring malformed Workshop Zone container label: " + exception.getMessage());
			return ContainerLabelRule.NONE;
		}
	}

	private static ContainerLabelRule readExact(NbtCompound data, Predicate<Identifier> validItem) {
		if (!data.contains("item", NbtElement.STRING_TYPE) || data.contains("tag") || data.contains("entries")) {
			throw new IllegalArgumentException("exact-item label fields are invalid");
		}
		Identifier itemId = Identifier.tryParse(data.getString("item"));
		if (itemId == null || !validItem.test(itemId) || Identifier.ofVanilla("air").equals(itemId)) {
			throw new IllegalArgumentException("invalid exact-item label " + data.getString("item"));
		}
		return ContainerLabelRule.exactItem(itemId);
	}

	private static ContainerLabelRule readTag(NbtCompound data) {
		if (!data.contains("tag", NbtElement.STRING_TYPE) || data.contains("item") || data.contains("entries")) {
			throw new IllegalArgumentException("item-tag label fields are invalid");
		}
		Identifier tagId = Identifier.tryParse(data.getString("tag"));
		if (tagId == null) {
			throw new IllegalArgumentException("invalid item-tag label " + data.getString("tag"));
		}
		return ContainerLabelRule.itemTag(tagId);
	}

	private static ContainerLabelRule readWhitelist(
		NbtCompound data,
		Predicate<Identifier> validItem,
		Consumer<String> warningSink
	) {
		if (!data.contains("entries", NbtElement.LIST_TYPE) || data.contains("item") || data.contains("tag")) {
			return malformed(warningSink, "whitelist entries must be a list");
		}
		NbtList nbtEntries = data.getList("entries", NbtElement.COMPOUND_TYPE);
		if (nbtEntries.size() < 1 || nbtEntries.size() > ContainerLabelRule.MAX_ENTRIES) {
			return malformed(warningSink, "whitelist entry count is outside 1.." + ContainerLabelRule.MAX_ENTRIES);
		}
		List<ContainerLabelEntry> entries = new ArrayList<>(nbtEntries.size());
		for (int index = 0; index < nbtEntries.size(); index++) {
			NbtCompound nbtEntry = nbtEntries.getCompound(index);
			Identifier typeId = Identifier.tryParse(nbtEntry.getString("type"));
			ContainerLabelEntryType type = typeId == null ? null : ContainerLabelEntryType.fromId(typeId).orElse(null);
			Identifier valueId = Identifier.tryParse(nbtEntry.getString("value"));
			if (type == null || valueId == null) {
				return malformed(warningSink, "unknown or invalid whitelist entry at index " + index);
			}
			if (type == ContainerLabelEntryType.ITEM
				&& (!validItem.test(valueId) || Identifier.ofVanilla("air").equals(valueId))) {
				return malformed(warningSink, "invalid whitelist item at index " + index);
			}
			entries.add(new ContainerLabelEntry(type, valueId));
		}
		return ContainerLabelRule.whitelist(entries);
	}

	private static ContainerLabelRule malformed(Consumer<String> warningSink, String reason) {
		warningSink.accept("Ignoring malformed Workshop Zone whitelist label: " + reason);
		return ContainerLabelRule.NONE;
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
			case WHITELIST -> {
				NbtList entries = new NbtList();
				for (ContainerLabelEntry entry : rule.entries()) {
					NbtCompound nbtEntry = new NbtCompound();
					nbtEntry.putString("type", entry.type().id().toString());
					nbtEntry.putString("value", entry.valueId().toString());
					entries.add(nbtEntry);
				}
				data.put("entries", entries);
			}
			case NONE -> throw new IllegalStateException("None labels are not serialized");
		}
		blockEntityNbt.put(NBT_KEY, data);
	}
}
