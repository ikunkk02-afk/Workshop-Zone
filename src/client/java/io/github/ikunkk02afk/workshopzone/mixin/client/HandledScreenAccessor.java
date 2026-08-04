package io.github.ikunkk02afk.workshopzone.mixin.client;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {
	@Accessor("x")
	int workshopZone$getX();

	@Accessor("x")
	void workshopZone$setX(int x);

	@Accessor("y")
	int workshopZone$getY();

	@Accessor("backgroundWidth")
	int workshopZone$getBackgroundWidth();

	@Accessor("backgroundHeight")
	int workshopZone$getBackgroundHeight();
}
