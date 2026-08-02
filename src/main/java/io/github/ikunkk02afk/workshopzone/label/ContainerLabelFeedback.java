package io.github.ikunkk02afk.workshopzone.label;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ContainerLabelFeedback {
	private static final int COOLDOWN_TICKS = 20;
	private static final Map<UUID, Long> LAST_FEEDBACK_TICK = new HashMap<>();

	private ContainerLabelFeedback() {
	}

	public static void rejected(ServerPlayerEntity player, ContainerLabelSummary summary) {
		long now = player.getServerWorld().getTime();
		long previous = LAST_FEEDBACK_TICK.getOrDefault(player.getUuid(), now - COOLDOWN_TICKS);
		if (now - previous < COOLDOWN_TICKS) {
			return;
		}
		LAST_FEEDBACK_TICK.put(player.getUuid(), now);
		Text message;
		if (summary.conflict()) {
			message = Text.translatable("message.workshop_zone.label.conflict");
		} else {
			Optional<Item> item = summary.exactItemId().flatMap(Registries.ITEM::getOrEmpty);
			message = item.<Text>map(value -> Text.translatable("message.workshop_zone.label.rejected_insert", value.getName()))
				.orElseGet(() -> Text.translatable("message.workshop_zone.label.denied"));
		}
		player.sendMessage(message, true);
		player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 0.25F, 0.65F);
	}

	public static void clear(UUID playerId) {
		LAST_FEEDBACK_TICK.remove(playerId);
	}
}
