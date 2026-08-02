package io.github.ikunkk02afk.workshopzone.client;

import io.github.ikunkk02afk.workshopzone.network.WorkshopDepositResultPayload;

public final class ClientDepositState {
	private static WorkshopDepositResultPayload lastResult;
	private static long resultSequence;

	private ClientDepositState() {
	}

	public static void accept(WorkshopDepositResultPayload payload) {
		lastResult = payload;
		resultSequence++;
	}

	public static WorkshopDepositResultPayload lastResult() {
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
