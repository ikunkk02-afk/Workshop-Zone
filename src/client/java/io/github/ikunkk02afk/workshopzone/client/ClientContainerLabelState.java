package io.github.ikunkk02afk.workshopzone.client;

import io.github.ikunkk02afk.workshopzone.network.ContainerLabelEditResultPayload;

public final class ClientContainerLabelState {
	private static ContainerLabelEditResultPayload lastResult;
	private static long resultSequence;

	private ClientContainerLabelState() {
	}

	public static void accept(ContainerLabelEditResultPayload payload) {
		lastResult = payload;
		resultSequence++;
	}

	public static ContainerLabelEditResultPayload lastResult() {
		return lastResult;
	}

	public static long resultSequence() {
		return resultSequence;
	}

	public static void reset() {
		lastResult = null;
		resultSequence++;
	}
}
