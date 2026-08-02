package io.github.ikunkk02afk.workshopzone.network;

import io.github.ikunkk02afk.workshopzone.label.ContainerLabelMode;
import io.github.ikunkk02afk.workshopzone.label.ContainerLabelSummary;
import io.github.ikunkk02afk.workshopzone.scan.WorkshopBlockType;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Optional;

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
			ContainerLabelMode.EXACT_ITEM, Optional.of(Identifier.ofVanilla("iron_ingot")), false
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
