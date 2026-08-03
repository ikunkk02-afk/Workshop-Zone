package io.github.ikunkk02afk.workshopzone.client;

import io.github.ikunkk02afk.workshopzone.network.ContainerLabelDetailsPayload;

public final class ClientContainerLabelDetailsState {
	private static ContainerLabelDetailsPayload lastResponse;
	private static long responseSequence;

	private ClientContainerLabelDetailsState() {
	}

	public static void accept(ContainerLabelDetailsPayload payload) {
		lastResponse = payload;
		responseSequence++;
	}

	public static ContainerLabelDetailsPayload lastResponse() {
		return lastResponse;
	}

	public static long responseSequence() {
		return responseSequence;
	}

	public static void reset() {
		lastResponse = null;
		responseSequence++;
	}
}
