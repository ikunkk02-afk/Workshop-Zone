package io.github.ikunkk02afk.workshopzone.session;

public enum WorkshopOpenResult {
	SUCCESS(null),
	INVALID_SESSION("message.workshop_zone.open.invalid_session"),
	STALE_SNAPSHOT("message.workshop_zone.open.stale_snapshot"),
	NOT_IN_SESSION("message.workshop_zone.open.not_in_session"),
	OUT_OF_RANGE("message.workshop_zone.open.out_of_range"),
	CHUNK_UNLOADED("message.workshop_zone.open.chunk_unloaded"),
	BLOCK_CHANGED("message.workshop_zone.open.block_changed"),
	UNAVAILABLE("message.workshop_zone.open.unavailable"),
	DENIED("message.workshop_zone.open.denied"),
	COOLDOWN("message.workshop_zone.open.cooldown"),
	CURRENT("message.workshop_zone.open.current");

	private final String translationKey;

	WorkshopOpenResult(String translationKey) {
		this.translationKey = translationKey;
	}

	public String translationKey() {
		return translationKey;
	}
}
