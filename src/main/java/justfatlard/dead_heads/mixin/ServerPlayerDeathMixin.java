package justfatlard.dead_heads.mixin;

import java.util.ArrayList;
import java.util.List;
import justfatlard.dead_heads.DeadHeadManager;
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

		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (!stack.isEmpty()) {
				items.add(stack.copy());
				inv.setItem(i, ItemStack.EMPTY);
			}
		}

		if (!items.isEmpty()) {
			DeadHeadManager.handleDeath(player, items);
		}
	}
}
