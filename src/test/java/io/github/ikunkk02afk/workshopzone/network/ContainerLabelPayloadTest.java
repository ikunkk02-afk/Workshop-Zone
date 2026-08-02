package io.github.ikunkk02afk.workshopzone.network;

import io.github.ikunkk02afk.workshopzone.label.ContainerLabelMode;
import io.github.ikunkk02afk.workshopzone.label.ContainerLabelSummary;
import io.github.ikunkk02afk.workshopzone.label.ContainerTagCandidate;
import io.github.ikunkk02afk.workshopzone.scan.WorkshopBlockType;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContainerLabelPayloadTest {
	@Test
	void editPayloadRoundTrips() {
		UpdateContainerLabelPayload original = new UpdateContainerLabelPayload(
			12, 4, 9, new BlockPos(1, 64, -3), ContainerLabelOperation.SET_EXACT_ITEM,
			Optional.of(Identifier.ofVanilla("iron_ingot"))
		);
		RegistryByteBuf buffer = buffer();
		UpdateContainerLabelPayload.CODEC.encode(buffer, original);
		assertEquals(original, UpdateContainerLabelPayload.CODEC.decode(buffer));
		assertEquals(0, buffer.readableBytes());
	}

	@Test
	void itemTagEditPayloadRoundTrips() {
		UpdateContainerLabelPayload original = new UpdateContainerLabelPayload(
			12, 4, 9, new BlockPos(1, 64, -3), ContainerLabelOperation.SET_ITEM_TAG,
			Optional.of(Identifier.ofVanilla("logs"))
		);
		RegistryByteBuf buffer = buffer();
		UpdateContainerLabelPayload.CODEC.encode(buffer, original);
		assertEquals(original, UpdateContainerLabelPayload.CODEC.decode(buffer));
		assertEquals(Optional.of(Identifier.ofVanilla("logs")), original.tagId());
		assertEquals(0, buffer.readableBytes());
	}

	@Test
	void candidateRequestPayloadRoundTrips() {
		RequestItemTagCandidatesPayload original = new RequestItemTagCandidatesPayload(
			12, 9, 4, Identifier.ofVanilla("oak_log")
		);
		RegistryByteBuf buffer = buffer();
		RequestItemTagCandidatesPayload.CODEC.encode(buffer, original);
		assertEquals(original, RequestItemTagCandidatesPayload.CODEC.decode(buffer));
		assertEquals(0, buffer.readableBytes());
	}

	@Test
	void candidateResponsePayloadRoundTrips() {
		ItemTagCandidatesPayload original = new ItemTagCandidatesPayload(
			12, 9, 4, Identifier.ofVanilla("oak_log"), ContainerLabelEditResult.SUCCESS,
			List.of(new ContainerTagCandidate(Identifier.ofVanilla("logs"), Identifier.ofVanilla("oak_log"))), false
		);
		RegistryByteBuf buffer = buffer();
		ItemTagCandidatesPayload.CODEC.encode(buffer, original);
		assertEquals(original, ItemTagCandidatesPayload.CODEC.decode(buffer));
		assertEquals(0, buffer.readableBytes());
	}

	@Test
	void resultPayloadRoundTrips() {
		ContainerLabelEditResultPayload original = new ContainerLabelEditResultPayload(
			12, 9, ContainerLabelEditResult.INCOMPATIBLE_CONTENTS,
			Optional.of(Identifier.ofVanilla("gold_ingot")), 3
		);
		RegistryByteBuf buffer = buffer();
		ContainerLabelEditResultPayload.CODEC.encode(buffer, original);
		assertEquals(original, ContainerLabelEditResultPayload.CODEC.decode(buffer));
		assertEquals(0, buffer.readableBytes());
	}

	@Test
	void snapshotLabelSummaryRoundTrips() {
		ContainerLabelSummary summary = new ContainerLabelSummary(
			ContainerLabelMode.EXACT_ITEM,
			Optional.of(Identifier.ofVanilla("iron_ingot")), Optional.empty(),
			Optional.of(Identifier.ofVanilla("iron_ingot")), false, false
		);
		WorkshopNetworkEntry original = new WorkshopNetworkEntry(
			WorkshopBlockType.CHEST, BlockPos.ORIGIN, Identifier.ofVanilla("chest"), 0,
			true, false, Optional.empty(), summary
		);
		RegistryByteBuf buffer = buffer();
		WorkshopNetworkEntry.write(buffer, original);
		assertEquals(original, WorkshopNetworkEntry.read(buffer));
		assertEquals(0, buffer.readableBytes());
	}

	@Test
	void itemTagSummaryRoundTrips() {
		ContainerLabelSummary summary = new ContainerLabelSummary(
			ContainerLabelMode.ITEM_TAG, Optional.empty(), Optional.of(Identifier.ofVanilla("logs")),
			Optional.of(Identifier.ofVanilla("oak_log")), true, false
		);
		WorkshopNetworkEntry original = new WorkshopNetworkEntry(
			WorkshopBlockType.CHEST, BlockPos.ORIGIN, Identifier.ofVanilla("chest"), 0,
			true, false, Optional.empty(), summary
		);
		RegistryByteBuf buffer = buffer();
		WorkshopNetworkEntry.write(buffer, original);
		assertEquals(original, WorkshopNetworkEntry.read(buffer));
		assertEquals(0, buffer.readableBytes());
	}

	private static RegistryByteBuf buffer() {
		return new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
	}
}
