package io.github.ikunkk02afk.workshopzone.label;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContainerLabelDataTest {
	@Test
	void version3ExactItemRoundTrip() {
		NbtCompound nbt = new NbtCompound();
		ContainerLabelRule rule = ContainerLabelRule.exact(Identifier.ofVanilla("iron_ingot"));
		ContainerLabelData.write(nbt, rule);
		assertEquals(rule, ContainerLabelData.read(nbt, id -> true, ignored -> {}));
		NbtCompound data = nbt.getCompound(ContainerLabelData.NBT_KEY);
		assertEquals(3, data.getInt("version"));
		assertEquals("workshop_zone:exact_item", data.getString("mode"));
		assertEquals("minecraft:iron_ingot", data.getString("item"));
	}

	@Test
	void version2ExactItemMigratesOnNextWrite() {
		NbtCompound legacy = exactNbt("minecraft:iron_ingot", "workshop_zone:exact_item", 2);
		ContainerLabelRule result = ContainerLabelData.read(legacy, ignored -> true, ignored -> {});
		assertEquals(ContainerLabelRule.exactItem(Identifier.ofVanilla("iron_ingot")), result);
		ContainerLabelData.write(legacy, result);
		assertEquals(3, legacy.getCompound(ContainerLabelData.NBT_KEY).getInt("version"));
	}

	@Test
	void version3ItemTagRoundTrip() {
		NbtCompound nbt = new NbtCompound();
		ContainerLabelRule rule = ContainerLabelRule.itemTag(Identifier.ofVanilla("logs"));
		ContainerLabelData.write(nbt, rule);
		assertEquals(rule, ContainerLabelData.read(nbt, id -> true, ignored -> {}));
		NbtCompound data = nbt.getCompound(ContainerLabelData.NBT_KEY);
		assertEquals(3, data.getInt("version"));
		assertEquals("workshop_zone:item_tag", data.getString("mode"));
		assertEquals("minecraft:logs", data.getString("tag"));
		assertFalse(data.contains("item"));
	}

	@Test
	void version1ExactItemMigratesOnNextWrite() {
		NbtCompound legacy = exactNbt("minecraft:iron_ingot", "workshop_zone:exact_item", 1);
		ContainerLabelRule rule = ContainerLabelData.read(legacy, id -> true, ignored -> {});
		assertEquals(ContainerLabelRule.exactItem(Identifier.ofVanilla("iron_ingot")), rule);
		ContainerLabelData.write(legacy, rule);
		assertEquals(3, legacy.getCompound(ContainerLabelData.NBT_KEY).getInt("version"));
		assertEquals("minecraft:iron_ingot", legacy.getCompound(ContainerLabelData.NBT_KEY).getString("item"));
	}

	@Test
	void clearingRemovesLabelCompound() {
		NbtCompound nbt = new NbtCompound();
		ContainerLabelData.write(nbt, ContainerLabelRule.exact(Identifier.ofVanilla("iron_ingot")));
		ContainerLabelData.write(nbt, ContainerLabelRule.NONE);
		assertFalse(nbt.contains(ContainerLabelData.NBT_KEY));
	}

	@Test
	void unknownItemIdFallsBackWithoutThrowing() {
		NbtCompound nbt = exactNbt("minecraft:not_a_real_item", "workshop_zone:exact_item", 2);
		List<String> warnings = new ArrayList<>();
		assertEquals(ContainerLabelRule.NONE, ContainerLabelData.read(nbt, id -> false, warnings::add));
		assertEquals(1, warnings.size());
	}

	@Test
	void unknownModeFallsBackWithoutThrowing() {
		NbtCompound nbt = exactNbt("minecraft:iron_ingot", "workshop_zone:future_mode", 2);
		List<String> warnings = new ArrayList<>();
		assertEquals(ContainerLabelRule.NONE, ContainerLabelData.read(nbt, id -> true, warnings::add));
		assertEquals(1, warnings.size());
	}

	@Test
	void wrongFieldCombinationFailsSafely() {
		NbtCompound nbt = exactNbt("minecraft:iron_ingot", "workshop_zone:exact_item", 2);
		nbt.getCompound(ContainerLabelData.NBT_KEY).putString("tag", "minecraft:logs");
		List<String> warnings = new ArrayList<>();
		assertEquals(ContainerLabelRule.NONE, ContainerLabelData.read(nbt, id -> true, warnings::add));
		assertEquals(1, warnings.size());
	}

	@Test
	void itemTagWithItemFieldFailsSafely() {
		NbtCompound root = new NbtCompound();
		NbtCompound data = new NbtCompound();
		data.putInt("version", 2);
		data.putString("mode", "workshop_zone:item_tag");
		data.putString("tag", "minecraft:logs");
		data.putString("item", "minecraft:oak_log");
		root.put(ContainerLabelData.NBT_KEY, data);
		assertEquals(ContainerLabelRule.NONE, ContainerLabelData.read(root, id -> true, ignored -> {}));
	}

	@Test
	void version3WhitelistRoundTripUsesDeterministicEntryOrder() {
		NbtCompound nbt = new NbtCompound();
		ContainerLabelRule rule = ContainerLabelRule.whitelist(List.of(
			ContainerLabelEntry.itemTag(Identifier.ofVanilla("logs")),
			ContainerLabelEntry.item(Identifier.ofVanilla("iron_ingot")),
			ContainerLabelEntry.item(Identifier.ofVanilla("gold_ingot"))
		));

		ContainerLabelData.write(nbt, rule);

		assertEquals(rule, ContainerLabelData.read(nbt, id -> true, ignored -> {}));
		NbtCompound data = nbt.getCompound(ContainerLabelData.NBT_KEY);
		assertEquals(3, data.getInt("version"));
		assertEquals("workshop_zone:whitelist", data.getString("mode"));
		NbtList entries = data.getList("entries", net.minecraft.nbt.NbtElement.COMPOUND_TYPE);
		assertEquals(3, entries.size());
		assertEquals("minecraft:gold_ingot", entries.getCompound(0).getString("value"));
		assertEquals("minecraft:iron_ingot", entries.getCompound(1).getString("value"));
		assertEquals("minecraft:logs", entries.getCompound(2).getString("value"));
	}

	@Test
	void version2ItemTagMigratesOnNextWrite() {
		NbtCompound root = new NbtCompound();
		NbtCompound data = new NbtCompound();
		data.putInt("version", 2);
		data.putString("mode", "workshop_zone:item_tag");
		data.putString("tag", "minecraft:logs");
		root.put(ContainerLabelData.NBT_KEY, data);

		ContainerLabelRule rule = ContainerLabelData.read(root, id -> true, ignored -> {});
		ContainerLabelData.write(root, rule);

		assertEquals(ContainerLabelRule.itemTag(Identifier.ofVanilla("logs")), rule);
		assertEquals(3, root.getCompound(ContainerLabelData.NBT_KEY).getInt("version"));
	}

	@Test
	void malformedOrOversizedWhitelistFailsSafely() {
		NbtCompound malformed = new NbtCompound();
		NbtCompound malformedData = new NbtCompound();
		malformedData.putInt("version", 3);
		malformedData.putString("mode", "workshop_zone:whitelist");
		malformedData.putString("entries", "not-a-list");
		malformed.put(ContainerLabelData.NBT_KEY, malformedData);
		List<String> malformedWarnings = new ArrayList<>();
		assertEquals(ContainerLabelRule.NONE, ContainerLabelData.read(malformed, id -> true, malformedWarnings::add));
		assertEquals(1, malformedWarnings.size());

		NbtCompound oversized = whitelistNbt(33, false);
		List<String> oversizedWarnings = new ArrayList<>();
		assertEquals(ContainerLabelRule.NONE, ContainerLabelData.read(oversized, id -> true, oversizedWarnings::add));
		assertEquals(1, oversizedWarnings.size());
	}

	@Test
	void whitelistReadDeduplicatesEntriesAndPreservesMissingTags() {
		NbtCompound root = whitelistNbt(2, true);
		ContainerLabelRule rule = ContainerLabelData.read(root, id -> true, ignored -> {});

		assertEquals(1, rule.entries().size());
		assertEquals(ContainerLabelEntry.itemTag(Identifier.of("missing", "kept")), rule.entries().getFirst());
	}

	private static NbtCompound whitelistNbt(int count, boolean duplicateMissingTag) {
		NbtCompound root = new NbtCompound();
		NbtCompound data = new NbtCompound();
		data.putInt("version", 3);
		data.putString("mode", "workshop_zone:whitelist");
		NbtList entries = new NbtList();
		for (int index = 0; index < count; index++) {
			NbtCompound entry = new NbtCompound();
			entry.putString("type", duplicateMissingTag ? "workshop_zone:item_tag" : "workshop_zone:item");
			entry.putString("value", duplicateMissingTag ? "missing:kept" : "test:item_" + index);
			entries.add(entry);
		}
		data.put("entries", entries);
		root.put(ContainerLabelData.NBT_KEY, data);
		return root;
	}

	private static NbtCompound exactNbt(String item, String mode, int version) {
		NbtCompound root = new NbtCompound();
		NbtCompound data = new NbtCompound();
		data.putInt("version", version);
		data.putString("mode", mode);
		data.putString("item", item);
		root.put(ContainerLabelData.NBT_KEY, data);
		return root;
	}
}
