package io.github.ikunkk02afk.workshopzone.label;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class ContainerLabelData {
	public static final String NBT_KEY = "workshop_zone:container_label";
	public static final int VERSION = 1;

	private ContainerLabelData() {
	}

	public static ContainerLabelRule read(NbtCompound blockEntityNbt) {
		return read(
			blockEntityNbt,
			id -> Registries.ITEM.getOrEmpty(id).isPresent() && !Identifier.ofVanilla("air").equals(id),
			warning -> WorkshopZone.LOGGER.warn("{}", warning)
		);
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
		if (data.getInt("version") != VERSION) {
			warningSink.accept("Ignoring unsupported Workshop Zone container label version " + data.getInt("version"));
			return ContainerLabelRule.NONE;
		}
		Identifier modeId = Identifier.tryParse(data.getString("mode"));
		Optional<ContainerLabelMode> mode = modeId == null ? Optional.empty() : ContainerLabelMode.fromId(modeId);
		if (mode.isEmpty()) {
			warningSink.accept("Ignoring unknown Workshop Zone container label mode " + data.getString("mode"));
			return ContainerLabelRule.NONE;
		}
		if (mode.get() == ContainerLabelMode.NONE) {
			return ContainerLabelRule.NONE;
		}
		Identifier itemId = Identifier.tryParse(data.getString("item"));
		if (itemId == null || !validItem.test(itemId) || Identifier.ofVanilla("air").equals(itemId)) {
			warningSink.accept("Ignoring invalid Workshop Zone exact-item label " + data.getString("item"));
			return ContainerLabelRule.NONE;
		}
		return ContainerLabelRule.exact(itemId);
	}

	public static void write(NbtCompound blockEntityNbt, ContainerLabelRule rule) {
		blockEntityNbt.remove(NBT_KEY);
		if (rule.mode() == ContainerLabelMode.NONE) {
			return;
		}
		NbtCompound data = new NbtCompound();
		data.putInt("version", VERSION);
		data.putString("mode", rule.mode().id().toString());
		data.putString("item", rule.exactItemId().orElseThrow().toString());
		blockEntityNbt.put(NBT_KEY, data);
	}
}
