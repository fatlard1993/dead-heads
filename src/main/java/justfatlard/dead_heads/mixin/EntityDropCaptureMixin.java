package justfatlard.dead_heads.mixin;

import justfatlard.dead_heads.MobHeads;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Swallows entity drops while a mob's death loot is being collected into a head.
 *
 * This is the one overload every other spawnAtLocation delegates into, so
 * catching it here covers the loot table, custom death loot, and equipment
 * alike. It returns a detached ItemEntity rather than null: the stack never
 * enters the world, but callers that keep tinkering with the returned entity
 * find something to tinker with instead of an NPE.
 */
@Mixin(Entity.class)
public abstract class EntityDropCaptureMixin {

	@Inject(
		method = "spawnAtLocation(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/entity/item/ItemEntity;",
		at = @At("HEAD"),
		cancellable = true
	)
	private void deadheads$captureDrop(ServerLevel level, ItemStack stack, Vec3 offset, CallbackInfoReturnable<ItemEntity> cir) {
		Entity self = (Entity)(Object) this;

		if (!MobHeads.isCapturing(self)) return;
		if (stack.isEmpty()) return;

		MobHeads.capture(stack);

		cir.setReturnValue(new ItemEntity(
			level,
			self.getX() + offset.x,
			self.getY() + offset.y,
			self.getZ() + offset.z,
			stack
		));
	}
}
