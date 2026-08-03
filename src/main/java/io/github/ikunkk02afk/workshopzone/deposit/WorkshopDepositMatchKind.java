package io.github.ikunkk02afk.workshopzone.deposit;

public enum WorkshopDepositMatchKind {
	SINGLE_EXACT(0),
	WHITELIST_EXACT(1),
	SINGLE_ITEM_TAG(2),
	WHITELIST_ITEM_TAG(3);

	private final int priority;

	WorkshopDepositMatchKind(int priority) {
		this.priority = priority;
	}

	public int priority() {
		return priority;
	}
}
