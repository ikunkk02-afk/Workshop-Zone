package io.github.ikunkk02afk.workshopzone.craft;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import net.minecraft.util.Identifier;

import java.util.Arrays;
import java.util.Optional;

public enum WorkshopCraftMode {
	SINGLE("single"),
	BATCH("batch");

	private final Identifier id;

	WorkshopCraftMode(String path) {
		this.id = WorkshopZone.id(path);
	}

	public Identifier id() {
		return id;
	}

	public static Optional<WorkshopCraftMode> fromId(Identifier id) {
		return Arrays.stream(values()).filter(value -> value.id.equals(id)).findFirst();
	}
}
