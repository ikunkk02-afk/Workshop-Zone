package io.github.ikunkk02afk.workshopzone.deposit;

public final class WorkshopDepositSummary {
	private WorkshopDepositSummary() {
	}

	public static WorkshopDepositResult classify(
		int movedItemCount,
		int matchedButRemainingCount,
		boolean foundMatchingItems,
		boolean allMatchingTargetsDenied
	) {
		if (!foundMatchingItems) {
			return WorkshopDepositResult.NOTHING_TO_MOVE;
		}
		if (movedItemCount > 0) {
			return matchedButRemainingCount > 0 ? WorkshopDepositResult.PARTIAL : WorkshopDepositResult.SUCCESS;
		}
		return allMatchingTargetsDenied ? WorkshopDepositResult.DENIED : WorkshopDepositResult.NO_SPACE;
	}
}
