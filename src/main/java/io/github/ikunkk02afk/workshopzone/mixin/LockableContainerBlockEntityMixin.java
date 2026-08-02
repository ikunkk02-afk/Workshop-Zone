package io.github.ikunkk02afk.workshopzone.mixin;

import io.github.ikunkk02afk.workshopzone.label.SilentContainerAccess;
import net.minecraft.block.entity.LockableContainerBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.ContainerLock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(LockableContainerBlockEntity.class)
public abstract class LockableContainerBlockEntityMixin implements SilentContainerAccess {
	@Shadow private ContainerLock lock;

	@Override
	public boolean workshopZone$canOpenSilently(PlayerEntity player) {
		return player.isSpectator() || lock.canOpen(player.getMainHandStack());
	}
}
