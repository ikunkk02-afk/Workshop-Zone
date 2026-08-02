package io.github.ikunkk02afk.workshopzone.label;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContainerLabelDataTest {
	@Test
	void version2ExactItemRoundTrip() {
		NbtCompound nbt = new NbtCompound();
		ContainerLabelRule rule = ContainerLabelRule.exact(Identifier.ofVanilla("iron_ingot"));
		ContainerLabelData.write(nbt, rule);
		assertEquals(rule, ContainerLabelData.read(nbt, id -> true, ignored -> {}));
		NbtCompound data = nbt.getCompound(ContainerLabelData.NBT_KEY);
		assertEquals(2, data.getInt("version"));
		assertEquals("workshop_zone:exact_item", data.getString("mode"));
		assertEquals("minecraft:iron_ingot", data.getString("item"));
	}

	@Test
	void version2ItemTagRoundTrip() {
		NbtCompound nbt = new NbtCompound();
		ContainerLabelRule rule = ContainerLabelRule.itemTag(Identifier.ofVanilla("logs"));
		ContainerLabelData.write(nbt, rule);
		assertEquals(rule, ContainerLabelData.read(nbt, id -> true, ignored -> {}));
		NbtCompound data = nbt.getCompound(ContainerLabelData.NBT_KEY);
		assertEquals(2, data.getInt("version"));
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
		assertEquals(2, legacy.getCompound(ContainerLabelData.NBT_KEY).getInt("version"));
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
