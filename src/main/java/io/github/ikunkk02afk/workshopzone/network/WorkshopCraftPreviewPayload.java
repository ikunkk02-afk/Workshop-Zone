package io.github.ikunkk02afk.workshopzone.network;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import io.github.ikunkk02afk.workshopzone.craft.WorkshopCraftMaterialSummary;
import io.github.ikunkk02afk.workshopzone.craft.WorkshopCraftPreviewResultCode;
import io.netty.handler.codec.DecoderException;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record WorkshopCraftPreviewPayload(
	long previewId,
	long sessionId,
	long revision,
	int syncId,
	Identifier recipeId,
	WorkshopCraftPreviewResultCode resultId,
	ItemStack output,
	List<WorkshopCraftMaterialSummary> materials,
	int storageItemCount,
	int usedContainerCount,
	int expiresInTicks
) implements CustomPayload {
	public static final int MAX_MATERIALS = 9;
	public static final Id<WorkshopCraftPreviewPayload> ID = new Id<>(WorkshopZone.id("workshop_craft_preview"));
	public static final PacketCodec<RegistryByteBuf, WorkshopCraftPreviewPayload> CODEC = CustomPayload.codecOf(
		WorkshopCraftPreviewPayload::write,
		WorkshopCraftPreviewPayload::read
	);

	public WorkshopCraftPreviewPayload {
		Objects.requireNonNull(recipeId, "recipeId");
		Objects.requireNonNull(resultId, "resultId");
		Objects.requireNonNull(output, "output");
		materials = List.copyOf(materials);
		if (previewId < 0 || sessionId < 0 || revision < 0 || syncId < 0 || storageItemCount < 0
			|| usedContainerCount < 0 || expiresInTicks < 0 || materials.size() > MAX_MATERIALS) {
			throw new IllegalArgumentException("Invalid workshop crafting preview payload");
		}
		if (resultId == WorkshopCraftPreviewResultCode.AVAILABLE
			&& (previewId == 0 || output.isEmpty() || storageItemCount == 0 || expiresInTicks == 0)) {
			throw new IllegalArgumentException("Available workshop crafting preview is incomplete");
		}
		if (resultId != WorkshopCraftPreviewResultCode.AVAILABLE && previewId != 0) {
			throw new IllegalArgumentException("Non-available workshop crafting preview cannot have a nonce");
		}
		output = output.copy();
	}

	@Override
	public ItemStack output() {
		return output.copy();
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}

	private static void write(WorkshopCraftPreviewPayload payload, RegistryByteBuf buf) {
		buf.writeVarLong(payload.previewId);
		buf.writeVarLong(payload.sessionId);
		buf.writeVarLong(payload.revision);
		buf.writeVarInt(payload.syncId);
		buf.writeIdentifier(payload.recipeId);
		buf.writeIdentifier(payload.resultId.id());
		ItemStack.PACKET_CODEC.encode(buf, payload.output);
		buf.writeVarInt(payload.materials.size());
		for (WorkshopCraftMaterialSummary material : payload.materials) {
			ItemStack.PACKET_CODEC.encode(buf, material.stack());
			buf.writeVarInt(material.totalAmount());
			buf.writeVarInt(material.playerAmount());
			buf.writeVarInt(material.storageAmount());
		}
		buf.writeVarInt(payload.storageItemCount);
		buf.writeVarInt(payload.usedContainerCount);
		buf.writeVarInt(payload.expiresInTicks);
	}

	private static WorkshopCraftPreviewPayload read(RegistryByteBuf buf) {
		long previewId = buf.readVarLong();
		long sessionId = buf.readVarLong();
		long revision = buf.readVarLong();
		int syncId = buf.readVarInt();
		Identifier recipeId = buf.readIdentifier();
		Identifier resultIdentifier = buf.readIdentifier();
		WorkshopCraftPreviewResultCode result = WorkshopCraftPreviewResultCode.fromId(resultIdentifier)
			.orElseThrow(() -> new DecoderException("Unknown workshop crafting preview result " + resultIdentifier));
		ItemStack output = ItemStack.PACKET_CODEC.decode(buf);
		int materialCount = buf.readVarInt();
		if (materialCount < 0 || materialCount > MAX_MATERIALS) {
			throw new DecoderException("Workshop crafting material count exceeds limit: " + materialCount);
		}
		List<WorkshopCraftMaterialSummary> materials = new ArrayList<>(materialCount);
		for (int index = 0; index < materialCount; index++) {
			try {
				materials.add(new WorkshopCraftMaterialSummary(
					ItemStack.PACKET_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()
				));
			} catch (IllegalArgumentException exception) {
				throw new DecoderException("Invalid workshop crafting material summary", exception);
			}
		}
		try {
			return new WorkshopCraftPreviewPayload(
				previewId, sessionId, revision, syncId, recipeId, result, output, materials,
				buf.readVarInt(), buf.readVarInt(), buf.readVarInt()
			);
		} catch (IllegalArgumentException exception) {
			throw new DecoderException("Invalid workshop crafting preview payload", exception);
		}
	}
}
