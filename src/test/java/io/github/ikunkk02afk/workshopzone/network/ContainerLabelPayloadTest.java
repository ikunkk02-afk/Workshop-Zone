package io.github.ikunkk02afk.workshopzone.network;

import io.github.ikunkk02afk.workshopzone.label.ContainerLabelEntry;
import io.github.ikunkk02afk.workshopzone.label.ContainerLabelMode;
import io.github.ikunkk02afk.workshopzone.label.ContainerLabelSummary;
import io.github.ikunkk02afk.workshopzone.label.ContainerTagCandidate;
import io.github.ikunkk02afk.workshopzone.scan.WorkshopBlockType;
import io.netty.handler.codec.DecoderException;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
	void whitelistEditPayloadRoundTrips() {
		List<ContainerLabelEntry> entries = List.of(
			ContainerLabelEntry.item(Identifier.ofVanilla("iron_ingot")),
			ContainerLabelEntry.itemTag(Identifier.ofVanilla("logs"))
		);
		UpdateContainerLabelPayload original = new UpdateContainerLabelPayload(
			12, 4, 9, new BlockPos(1, 64, -3), ContainerLabelOperation.SET_WHITELIST,
			Optional.empty(), entries
		);
		RegistryByteBuf buffer = buffer();
		UpdateContainerLabelPayload.CODEC.encode(buffer, original);
		assertEquals(original, UpdateContainerLabelPayload.CODEC.decode(buffer));
		assertEquals(entries, original.whitelistEntries());
		assertEquals(0, buffer.readableBytes());
	}

	@Test
	void labelDetailsPayloadsRoundTrip() {
		RequestContainerLabelDetailsPayload request = new RequestContainerLabelDetailsPayload(
			77, 12, 4, 9, new BlockPos(1, 64, -3)
		);
		RegistryByteBuf requestBuffer = buffer();
		RequestContainerLabelDetailsPayload.CODEC.encode(requestBuffer, request);
		assertEquals(request, RequestContainerLabelDetailsPayload.CODEC.decode(requestBuffer));
		assertEquals(0, requestBuffer.readableBytes());

		ContainerLabelDetailsPayload response = new ContainerLabelDetailsPayload(
			77, 12, 4, 9, new BlockPos(1, 64, -3), ContainerLabelEditResult.SUCCESS,
			ContainerLabelMode.WHITELIST,
			List.of(
				new ContainerLabelDetailsEntry(
					ContainerLabelEntry.item(Identifier.ofVanilla("iron_ingot")), false,
					Optional.of(Identifier.ofVanilla("iron_ingot"))
				),
				new ContainerLabelDetailsEntry(
					ContainerLabelEntry.itemTag(Identifier.of("missing", "tag")), true, Optional.empty()
				)
			),
			1, true, false
		);
		RegistryByteBuf responseBuffer = buffer();
		ContainerLabelDetailsPayload.CODEC.encode(responseBuffer, response);
		assertEquals(response, ContainerLabelDetailsPayload.CODEC.decode(responseBuffer));
		assertEquals(0, responseBuffer.readableBytes());
	}

	@Test
	void whitelistSnapshotContainsOnlySummary() {
		ContainerLabelSummary summary = new ContainerLabelSummary(
			ContainerLabelMode.WHITELIST, Optional.empty(), Optional.empty(),
			List.of(
				Identifier.ofVanilla("copper_ingot"), Identifier.ofVanilla("gold_ingot"),
				Identifier.ofVanilla("iron_ingot"), Identifier.ofVanilla("diamond")
			), 12, 2, false, false
		);
		WorkshopNetworkEntry original = new WorkshopNetworkEntry(
			WorkshopBlockType.CHEST, BlockPos.ORIGIN, Identifier.ofVanilla("chest"), 0,
			true, false, Optional.empty(), summary
		);
		RegistryByteBuf buffer = buffer();
		WorkshopNetworkEntry.write(buffer, original);
		WorkshopNetworkEntry decoded = WorkshopNetworkEntry.read(buffer);
		assertEquals(summary, decoded.labelSummary());
		assertEquals(summary.previewItemIds(), decoded.labelSummary().previewItemIds());
		assertEquals(0, buffer.readableBytes());
	}

	@Test
	void noneSummaryRoundTripsWithoutPreviewItems() {
		assertEquals(ContainerLabelSummary.NONE, roundTripSummary(ContainerLabelSummary.NONE));
	}

	@Test
	void oneAndFourItemWhitelistSummariesRoundTripInOrder() {
		ContainerLabelSummary one = new ContainerLabelSummary(
			ContainerLabelMode.WHITELIST, Optional.empty(), Optional.empty(),
			List.of(Identifier.ofVanilla("iron_ingot")), 1, 0, false, false
		);
		ContainerLabelSummary four = new ContainerLabelSummary(
			ContainerLabelMode.WHITELIST, Optional.empty(), Optional.empty(),
			List.of(
				Identifier.ofVanilla("copper_ingot"), Identifier.ofVanilla("gold_ingot"),
				Identifier.ofVanilla("iron_ingot"), Identifier.ofVanilla("diamond")
			), 6, 0, false, false
		);

		assertEquals(one, roundTripSummary(one));
		assertEquals(four, roundTripSummary(four));
	}

	@Test
	void partialAndFullyUnavailableWhitelistSummariesRoundTrip() {
		ContainerLabelSummary partial = new ContainerLabelSummary(
			ContainerLabelMode.WHITELIST, Optional.empty(), Optional.empty(),
			List.of(Identifier.ofVanilla("iron_ingot"), Identifier.ofVanilla("oak_log")),
			4, 2, false, false
		);
		ContainerLabelSummary fullyUnavailable = new ContainerLabelSummary(
			ContainerLabelMode.WHITELIST, Optional.empty(), Optional.empty(), List.of(),
			2, 2, false, true
		);

		assertEquals(partial, roundTripSummary(partial));
		assertEquals(fullyUnavailable, roundTripSummary(fullyUnavailable));
	}

	@Test
	void snapshotRejectsMoreThanFourPreviewItemsBeforeReadingThem() {
		RegistryByteBuf buffer = buffer();
		buffer.writeIdentifier(WorkshopBlockType.CHEST.networkId());
		buffer.writeBlockPos(BlockPos.ORIGIN);
		buffer.writeIdentifier(Identifier.ofVanilla("chest"));
		buffer.writeDouble(0.0);
		buffer.writeBoolean(true);
		buffer.writeBoolean(false);
		buffer.writeBoolean(false);
		buffer.writeIdentifier(ContainerLabelMode.WHITELIST.id());
		buffer.writeBoolean(false);
		buffer.writeBoolean(false);
		buffer.writeVarInt(5);

		assertThrows(DecoderException.class, () -> WorkshopNetworkEntry.read(buffer));
	}

	@Test
	void snapshotRejectsMorePreviewsThanUsableWhitelistEntries() {
		RegistryByteBuf buffer = buffer();
		buffer.writeIdentifier(WorkshopBlockType.CHEST.networkId());
		buffer.writeBlockPos(BlockPos.ORIGIN);
		buffer.writeIdentifier(Identifier.ofVanilla("chest"));
		buffer.writeDouble(0.0);
		buffer.writeBoolean(true);
		buffer.writeBoolean(false);
		buffer.writeBoolean(false);
		buffer.writeIdentifier(ContainerLabelMode.WHITELIST.id());
		buffer.writeBoolean(false);
		buffer.writeBoolean(false);
		buffer.writeVarInt(2);
		ContainerLabelNetworkCodecs.writeIdentifier(buffer, Identifier.ofVanilla("iron_ingot"));
		ContainerLabelNetworkCodecs.writeIdentifier(buffer, Identifier.ofVanilla("gold_ingot"));
		buffer.writeVarInt(1);
		buffer.writeVarInt(0);
		buffer.writeBoolean(false);
		buffer.writeBoolean(false);

		assertThrows(DecoderException.class, () -> WorkshopNetworkEntry.read(buffer));
	}

	@Test
	void whitelistEditPayloadRejectsMoreThanThirtyTwoEntries() {
		List<ContainerLabelEntry> entries = java.util.stream.IntStream.range(0, 33)
			.mapToObj(index -> ContainerLabelEntry.item(Identifier.of("test", "item_" + index)))
			.toList();
		assertThrows(IllegalArgumentException.class, () -> new UpdateContainerLabelPayload(
			1, 2, 3, new BlockPos(4, 5, 6), ContainerLabelOperation.SET_WHITELIST, Optional.empty(), entries
		));
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
		WorkshopNetworkEntry decoded = WorkshopNetworkEntry.read(buffer);
		assertEquals(original, decoded);
		assertEquals(List.of(Identifier.ofVanilla("iron_ingot")), decoded.labelSummary().previewItemIds());
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
		WorkshopNetworkEntry decoded = WorkshopNetworkEntry.read(buffer);
		assertEquals(original, decoded);
		assertEquals(List.of(Identifier.ofVanilla("oak_log")), decoded.labelSummary().previewItemIds());
		assertEquals(0, buffer.readableBytes());
	}

	private static RegistryByteBuf buffer() {
		return new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
	}

	private static ContainerLabelSummary roundTripSummary(ContainerLabelSummary summary) {
		WorkshopNetworkEntry original = new WorkshopNetworkEntry(
			WorkshopBlockType.CHEST, BlockPos.ORIGIN, Identifier.ofVanilla("chest"), 0,
			true, false, Optional.empty(), summary
		);
		RegistryByteBuf buffer = buffer();
		WorkshopNetworkEntry.write(buffer, original);
		WorkshopNetworkEntry decoded = WorkshopNetworkEntry.read(buffer);
		assertEquals(0, buffer.readableBytes());
		return decoded.labelSummary();
	}
}
