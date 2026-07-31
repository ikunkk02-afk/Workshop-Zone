package io.github.ikunkk02afk.workshopzone;

import io.github.ikunkk02afk.workshopzone.command.WorkshopZoneCommands;
import net.fabricmc.api.ModInitializer;

import net.minecraft.util.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkshopZone implements ModInitializer {
	public static final String MOD_ID = "workshop_zone";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		WorkshopZoneCommands.register();
		LOGGER.info("Initializing Workshop Zone");
	}

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}
}
