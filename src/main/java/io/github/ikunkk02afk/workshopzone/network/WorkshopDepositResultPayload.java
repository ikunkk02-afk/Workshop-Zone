package io.github.ikunkk02afk.workshopzone.network;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import io.github.ikunkk02afk.workshopzone.deposit.WorkshopDepositResult;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.Objects;

public record WorkshopDepositResultPayload(
	long requestId,
	long sessionId,
	int syncId,
	WorkshopDepositResult result,
	int movedItemCount,
	int movedSourceStackCount,
	int matchedButRemainingCount,
	int usedDestinationCount
) implements CustomPayload {
	public static final Id<WorkshopDepositResultPayload> ID = new Id<>(WorkshopZone.id("workshop_deposit_result"));
	public static final PacketCodec<RegistryByteBuf, WorkshopDepositResultPayload> CODEC = CustomPayload.codecOf(
		WorkshopDepositResultPayload::write,
		WorkshopDepositResultPayload::read
	);

	public WorkshopDepositResultPayload {
		Objects.requireNonNull(result, "result");
		if (requestId < 0 || sessionId < 0 || syncId < 0 || movedItemCount < 0 || movedSourceStackCount < 0
			|| matchedButRemainingCount < 0 || usedDestinationCount < 0) {
			throw new IllegalArgumentException("Invalid workshop deposit result payload");
		}
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}

	private static void write(WorkshopDepositResultPayload payload, RegistryByteBuf buf) {
		buf.writeVarLong(payload.requestId);
		buf.writeVarLong(payload.sessionId);
		buf.writeVarInt(payload.syncId);
		buf.writeIdentifier(payload.result.id());
		buf.writeVarInt(payload.movedItemCount);
		buf.writeVarInt(payload.movedSourceStackCount);
		buf.writeVarInt(payload.matchedButRemainingCount);
		buf.writeVarInt(payload.usedDestinationCount);
	}

	private static WorkshopDepositResultPayload read(RegistryByteBuf buf) {
		long requestId = buf.readVarLong();
		long sessionId = buf.readVarLong();
		int syncId = buf.readVarInt();
		Identifier resultId = buf.readIdentifier();
		WorkshopDepositResult result = WorkshopDepositResult.fromId(resultId)
			.orElseThrow(() -> new DecoderException("Unknown workshop deposit result " + resultId));
		try {
			return new WorkshopDepositResultPayload(
				requestId, sessionId, syncId, result,
				buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()
			);
		} catch (IllegalArgumentException exception) {
			throw new DecoderException("Invalid workshop deposit result payload", exception);
		}
	}
}
