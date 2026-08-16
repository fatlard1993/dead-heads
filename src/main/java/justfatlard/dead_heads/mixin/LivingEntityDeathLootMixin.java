package justfatlard.dead_heads.mixin;

import justfatlard.dead_heads.MobHeads;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Brackets the dropAllDeathLoot call inside LivingEntity#die so every item a
 * dying mob would have scattered can be collected instead.
 *
 * The window is the call site rather than dropAllDeathLoot's own HEAD/RETURN
 * because subclasses override it and drop things before delegating upward (Fox
 * spits out its held item first), and it stops short of createWitherRose, which
 * die() calls afterwards, so a wither rose still plants itself as normal.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDeathLootMixin {

	@Inject(
		method = "die",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/LivingEntity;dropAllDeathLoot(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;)V",
			shift = At.Shift.BEFORE
		)
	)
	private void deadheads$beginLootCapture(DamageSource source, CallbackInfo ci) {
		LivingEntity self = (LivingEntity)(Object) this;

		if (MobHeads.shouldCapture(self)) MobHeads.beginCapture(self);
	}

	@Inject(
		method = "die",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/LivingEntity;dropAllDeathLoot(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;)V",
			shift = At.Shift.AFTER
		)
	)
	private void deadheads$endLootCapture(DamageSource source, CallbackInfo ci) {
		MobHeads.endCapture((LivingEntity)(Object) this);
	}
}
