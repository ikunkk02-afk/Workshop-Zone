package io.github.ikunkk02afk.workshopzone.client;

import io.github.ikunkk02afk.workshopzone.network.ItemTagCandidatesPayload;

public final class ClientItemTagState {
	private static ItemTagCandidatesPayload lastResponse;
	private static long responseSequence;

	private ClientItemTagState() {
	}

	public static void accept(ItemTagCandidatesPayload payload) {
		lastResponse = payload;
		responseSequence++;
	}

	public static ItemTagCandidatesPayload lastResponse() {
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
