package io.github.ikunkk02afk.workshopzone.client.compat.recipeviewer;

import net.minecraft.util.Identifier;

public enum RecipeViewerSource {
	VANILLA(Identifier.of("workshop_zone", "vanilla")),
	JEI(Identifier.of("workshop_zone", "jei")),
	EMI(Identifier.of("workshop_zone", "emi")),
	REI(Identifier.of("workshop_zone", "rei"));

	private final Identifier id;

	RecipeViewerSource(Identifier id) {
		this.id = id;
	}

	public Identifier id() {
		return id;
	}
}
