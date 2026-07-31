package io.github.ikunkk02afk.workshopzone.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import io.github.ikunkk02afk.workshopzone.scan.WorkshopAreaScanner;
import io.github.ikunkk02afk.workshopzone.scan.WorkshopBlockEntry;
import io.github.ikunkk02afk.workshopzone.scan.WorkshopScanResult;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.Locale;

public final class WorkshopZoneCommands {
	private static final int MAX_DISPLAYED_ENTRIES = 20;
	private static final WorkshopAreaScanner SCANNER = new WorkshopAreaScanner();

	private WorkshopZoneCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			registerScanCommand(dispatcher)
		);
	}

	private static void registerScanCommand(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("workshopzone")
			.requires(source -> !source.getServer().isDedicated() || source.hasPermissionLevel(2))
			.then(CommandManager.literal("scan")
				.executes(WorkshopZoneCommands::executeScan)));
	}

	private static int executeScan(CommandContext<ServerCommandSource> context) {
		ServerCommandSource source = context.getSource();
		ServerPlayerEntity player = source.getPlayer();
		if (player == null) {
			source.sendError(Text.translatable("command.workshop_zone.scan.player_only"));
			return 0;
		}

		BlockPos center = player.getBlockPos();
		WorkshopScanResult result = SCANNER.scan(
			player.getServerWorld(),
			center,
			WorkshopAreaScanner.DEFAULT_HORIZONTAL_RADIUS,
			WorkshopAreaScanner.DEFAULT_VERTICAL_RADIUS
		);

		player.sendMessage(Text.translatable("command.workshop_zone.scan.complete", result.size()), false);
		player.sendMessage(Text.translatable("command.workshop_zone.scan.container_count", result.containerCount()), false);
		player.sendMessage(
			Text.translatable("command.workshop_zone.scan.workstation_count", result.processingDeviceCount()),
			false
		);

		int displayed = Math.min(result.size(), MAX_DISPLAYED_ENTRIES);
		for (int index = 0; index < displayed; index++) {
			WorkshopBlockEntry entry = result.entries().get(index);
			BlockPos position = entry.position();
			String distance = String.format(Locale.ROOT, "%.1f", Math.sqrt(entry.distanceSquared()));
			player.sendMessage(Text.translatable(
				"command.workshop_zone.scan.entry",
				index + 1,
				Text.translatable(entry.type().translationKey()),
				position.getX(),
				position.getY(),
				position.getZ(),
				distance
			), false);
		}

		if (result.size() > displayed) {
			player.sendMessage(
				Text.translatable("command.workshop_zone.scan.too_many", result.size() - displayed),
				false
			);
		}
		return result.size();
	}
}
