package justfatlard.dead_heads.mixin;

import java.util.ArrayList;
import java.util.List;
import justfatlard.dead_heads.DeadHeadManager;
import justfatlard.dead_heads.DeathCompass;
import justfatlard.dead_heads.ExtraSlots;
import justfatlard.dead_heads.Kept;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerDeathMixin {

	@Inject(method = "die", at = @At("HEAD"))
	private void captureDeathItems(DamageSource source, CallbackInfo ci) {
		ServerPlayer player = (ServerPlayer)(Object) this;

		if (player.isCreative() || player.isSpectator()) return;
		if (player.level().getGameRules().get(GameRules.KEEP_INVENTORY)) return;

		Inventory inv = player.getInventory();
		List<Kept> items = new ArrayList<>();
		List<ItemStack> compasses = new ArrayList<>();

		// Both lists leave the inventory: what stays behind is what vanilla scatters on the
		// ground a moment later, and the compass is not going in the head it points at.
		//
		// The index travels with the item. This is one flat run over all 41 slots - pack, hotbar,
		// the four pieces of armour and the offhand - so a helmet is remembered as a helmet slot
		// and comes back on your head rather than in with the cobble.
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.isEmpty()) continue;

			if (DeathCompass.isDeathCompass(stack)) {
				compasses.add(stack.copy());
			} else {
				items.add(new Kept(stack.copy(), "", i));
			}
			inv.setItem(i, ItemStack.EMPTY);
		}

		// Before the added slots are emptied, so the compass is lifted out of the one it may be
		// sitting in rather than buried in the head along with everything else.
		compasses.addAll(DeathCompass.takeStashed(player));

		// Slots other mods put on the inventory screen. They hold inventory, so they go in the
		// head - and emptying them is also what stops the store behind them being carried across
		// the respawn with a copy of what is now in the head.
		items.addAll(ExtraSlots.empty(player));

		// Unconditional. handleDeath is also what hands back the compass, and it already knows
		// that a death with nothing to store still has a place worth pointing at - but that
		// branch was unreachable while this gate stood in front of it, so dying empty-handed
		// was the one death that produced no compass at all.
		DeadHeadManager.handleDeath(player, items, compasses);
	}
}
