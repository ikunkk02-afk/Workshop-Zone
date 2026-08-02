package io.github.ikunkk02afk.workshopzone.label;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContainerLabelDataTest {
	@Test
	void nbtRoundTripPreservesExactItemRule() {
		NbtCompound nbt = new NbtCompound();
		ContainerLabelRule rule = ContainerLabelRule.exact(Identifier.ofVanilla("iron_ingot"));
		ContainerLabelData.write(nbt, rule);
		assertEquals(rule, ContainerLabelData.read(nbt, id -> true, ignored -> {}));
		NbtCompound data = nbt.getCompound(ContainerLabelData.NBT_KEY);
		assertEquals(1, data.getInt("version"));
		assertEquals("workshop_zone:exact_item", data.getString("mode"));
		assertEquals("minecraft:iron_ingot", data.getString("item"));
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
		NbtCompound nbt = exactNbt("minecraft:not_a_real_item", "workshop_zone:exact_item");
		List<String> warnings = new ArrayList<>();
		assertEquals(ContainerLabelRule.NONE, ContainerLabelData.read(nbt, id -> false, warnings::add));
		assertEquals(1, warnings.size());
	}

	@Test
	void unknownModeFallsBackWithoutThrowing() {
		NbtCompound nbt = exactNbt("minecraft:iron_ingot", "workshop_zone:future_mode");
		List<String> warnings = new ArrayList<>();
		assertEquals(ContainerLabelRule.NONE, ContainerLabelData.read(nbt, id -> true, warnings::add));
		assertEquals(1, warnings.size());
	}

	private static NbtCompound exactNbt(String item, String mode) {
		NbtCompound root = new NbtCompound();
		NbtCompound data = new NbtCompound();
		data.putInt("version", 1);
		data.putString("mode", mode);
		data.putString("item", item);
		root.put(ContainerLabelData.NBT_KEY, data);
		return root;
	}
}
