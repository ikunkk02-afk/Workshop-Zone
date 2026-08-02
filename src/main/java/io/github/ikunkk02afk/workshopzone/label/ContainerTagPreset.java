package io.github.ikunkk02afk.workshopzone.label;

import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public enum ContainerTagPreset {
	LOGS("minecraft:logs", "category.workshop_zone.logs", "minecraft:oak_log", 0),
	PLANKS("minecraft:planks", "category.workshop_zone.planks", "minecraft:oak_planks", 10),
	LEAVES("minecraft:leaves", "category.workshop_zone.leaves", "minecraft:oak_leaves", 20),
	SAPLINGS("minecraft:saplings", "category.workshop_zone.saplings", "minecraft:oak_sapling", 30),
	WOOL("minecraft:wool", "category.workshop_zone.wool", "minecraft:white_wool", 40),
	COALS("minecraft:coals", "category.workshop_zone.coals", "minecraft:coal", 50),
	ARROWS("minecraft:arrows", "category.workshop_zone.arrows", "minecraft:arrow", 60),
	ORES("c:ores", "category.workshop_zone.ores", "minecraft:iron_ore", 70),
	INGOTS("c:ingots", "category.workshop_zone.ingots", "minecraft:iron_ingot", 80),
	GEMS("c:gems", "category.workshop_zone.gems", "minecraft:diamond", 90),
	FOODS("c:foods", "category.workshop_zone.foods", "minecraft:bread", 100),
	SEEDS("c:seeds", "category.workshop_zone.seeds", "minecraft:wheat_seeds", 110);

	private static final List<ContainerTagPreset> ORDERED = Arrays.stream(values())
		.sorted(Comparator.comparingInt(ContainerTagPreset::priority))
		.toList();

	private final Identifier tagId;
	private final String translationKey;
	private final Identifier iconItemId;
	private final int priority;

	ContainerTagPreset(String tagId, String translationKey, String iconItemId, int priority) {
		this.tagId = Identifier.of(tagId);
		this.translationKey = translationKey;
		this.iconItemId = Identifier.of(iconItemId);
		this.priority = priority;
	}

	public Identifier tagId() {
		return tagId;
	}

	public String translationKey() {
		return translationKey;
	}

	public Identifier iconItemId() {
		return iconItemId;
	}

	public int priority() {
		return priority;
	}

	public Text displayName() {
		return Text.translatable(translationKey);
	}

	public static List<ContainerTagPreset> ordered() {
		return ORDERED;
	}

	public static Optional<ContainerTagPreset> find(Identifier tagId) {
		return ORDERED.stream().filter(preset -> preset.tagId.equals(tagId)).findFirst();
	}

	public static Text displayName(Identifier tagId) {
		return find(tagId).<Text>map(ContainerTagPreset::displayName)
			.orElseGet(() -> Text.translatable("gui.workshop_zone.label.unknown_tag", "#" + tagId));
	}
}
