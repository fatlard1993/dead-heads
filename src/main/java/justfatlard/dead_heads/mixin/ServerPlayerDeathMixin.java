package justfatlard.dead_heads.mixin;

import java.util.ArrayList;
import java.util.List;
import justfatlard.dead_heads.DeadHeadManager;
import justfatlard.dead_heads.DeathCompass;
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
		List<ItemStack> items = new ArrayList<>();
		List<ItemStack> compasses = new ArrayList<>();

		// Both lists leave the inventory: what stays behind is what vanilla scatters on the
		// ground a moment later, and the compass is not going in the head it points at.
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (!stack.isEmpty()) {
				(DeathCompass.isDeathCompass(stack) ? compasses : items).add(stack.copy());
				inv.setItem(i, ItemStack.EMPTY);
			}
		}

		compasses.addAll(DeathCompass.takeStashed(player));

		// Unconditional. handleDeath is also what hands back the compass, and it already knows
		// that a death with nothing to store still has a place worth pointing at - but that
		// branch was unreachable while this gate stood in front of it, so dying empty-handed
		// was the one death that produced no compass at all.
		DeadHeadManager.handleDeath(player, items, compasses);
	}
}
