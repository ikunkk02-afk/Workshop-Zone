package io.github.ikunkk02afk.workshopzone.network;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import io.github.ikunkk02afk.workshopzone.craft.WorkshopCraftExecutionResultCode;
import io.github.ikunkk02afk.workshopzone.craft.WorkshopCraftMode;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.Objects;

public record WorkshopCraftExecutionResultPayload(
	long previewId,
	long sessionId,
	int syncId,
	WorkshopCraftExecutionResultCode resultId,
	Identifier recipeId,
	WorkshopCraftMode craftMode,
	int plannedIterations,
	int movedIngredientCount,
	int usedPlayerItemCount,
	int usedStorageItemCount,
	int usedContainerCount
) implements CustomPayload {
	public static final Id<WorkshopCraftExecutionResultPayload> ID = new Id<>(WorkshopZone.id("workshop_craft_execution_result"));
	public static final PacketCodec<RegistryByteBuf, WorkshopCraftExecutionResultPayload> CODEC = CustomPayload.codecOf(
		WorkshopCraftExecutionResultPayload::write,
		WorkshopCraftExecutionResultPayload::read
	);

	public WorkshopCraftExecutionResultPayload {
		Objects.requireNonNull(resultId, "resultId");
		Objects.requireNonNull(recipeId, "recipeId");
		Objects.requireNonNull(craftMode, "craftMode");
		if (previewId <= 0 || sessionId < 0 || syncId < 0 || plannedIterations <= 0 || plannedIterations > 64
			|| craftMode == WorkshopCraftMode.SINGLE && plannedIterations != 1
			|| craftMode == WorkshopCraftMode.BATCH && plannedIterations <= 1
			|| movedIngredientCount < 0
			|| usedPlayerItemCount < 0 || usedStorageItemCount < 0 || usedContainerCount < 0
			|| movedIngredientCount > 9 * 64 || usedPlayerItemCount + usedStorageItemCount != movedIngredientCount) {
			throw new IllegalArgumentException("Invalid workshop crafting execution result payload");
		}
	}


	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}

	private static void write(WorkshopCraftExecutionResultPayload payload, RegistryByteBuf buf) {
		buf.writeVarLong(payload.previewId);
		buf.writeVarLong(payload.sessionId);
		buf.writeVarInt(payload.syncId);
		buf.writeIdentifier(payload.resultId.id());
		buf.writeIdentifier(payload.recipeId);
		buf.writeIdentifier(payload.craftMode.id());
		buf.writeVarInt(payload.plannedIterations);
		buf.writeVarInt(payload.movedIngredientCount);
		buf.writeVarInt(payload.usedPlayerItemCount);
		buf.writeVarInt(payload.usedStorageItemCount);
		buf.writeVarInt(payload.usedContainerCount);
	}

	private static WorkshopCraftExecutionResultPayload read(RegistryByteBuf buf) {
		long previewId = buf.readVarLong();
		long sessionId = buf.readVarLong();
		int syncId = buf.readVarInt();
		Identifier resultIdentifier = buf.readIdentifier();
		WorkshopCraftExecutionResultCode result = WorkshopCraftExecutionResultCode.fromId(resultIdentifier)
			.orElseThrow(() -> new DecoderException("Unknown workshop crafting execution result " + resultIdentifier));
		Identifier recipeId = buf.readIdentifier();
		Identifier modeIdentifier = buf.readIdentifier();
		WorkshopCraftMode craftMode = WorkshopCraftMode.fromId(modeIdentifier)
			.orElseThrow(() -> new DecoderException("Unknown workshop crafting mode " + modeIdentifier));
		try {
			return new WorkshopCraftExecutionResultPayload(
				previewId, sessionId, syncId, result, recipeId, craftMode, buf.readVarInt(),
				buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()
			);
		} catch (IllegalArgumentException exception) {
			throw new DecoderException("Invalid workshop crafting execution result payload", exception);
		}
	}
}
