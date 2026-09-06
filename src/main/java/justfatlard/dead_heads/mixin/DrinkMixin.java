package justfatlard.dead_heads.mixin;

import justfatlard.dead_heads.DeadReckoning;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The last swallow of a potion, on the server.
 *
 * <p>Vanilla has no hook for "this potion was drunk" that a mod's own potion can hang off,
 * because vanilla potions are effects and this one is a place. The head of the method is where
 * the stack is still whole and still ours to recognise; vanilla then consumes it and hands the
 * bottle back as it would for any other.
 */
@Mixin(Item.class)
public abstract class DrinkMixin {

	@Inject(method = "finishUsingItem", at = @At("HEAD"))
	private void deadHeads$reckon(ItemStack stack, Level level, LivingEntity entity, CallbackInfoReturnable<ItemStack> cir) {
		if (level.isClientSide() || !(entity instanceof ServerPlayer player)) return;
		if (DeadReckoning.isPotion(stack)) DeadReckoning.drink(player);
	}
}
