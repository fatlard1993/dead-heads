package justfatlard.dead_heads;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * The one enchantment that means "this stays with me".
 *
 * <p>A head holds everything and a compass finds it, and for nearly everything that is the right
 * deal: the walk back is the cost of dying. A few things are not worth the walk being possible at
 * all - the sword that took an evening to make, the map that is the only copy - and those get a
 * word on them that says so. One level, any item that takes an enchantment, found and not made:
 * treasure, on the same terms as Mending.
 *
 * <p>The keeping is done through the body. A dead player still has an inventory, and vanilla
 * saves it like any other; the trick is only that vanilla empties it first. So the soulbound
 * things are lifted out before that sweep and put back into the corpse after it, and on respawn
 * they are carried across to the new body slot for slot. A server that restarts while somebody is
 * on the death screen loses nothing, because the corpse went to disk with them still in it.
 */
public final class Soulbound {
	private Soulbound() {}

	public static final ResourceKey<Enchantment> KEY = ResourceKey.create(
		Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath(Main.MOD_ID, "soulbound"));

	/** What each dying player is keeping, between the sweep at the top of the death and the end of it. */
	private static final Map<UUID, List<Kept>> pending = new ConcurrentHashMap<>();

	public static boolean has(ServerLevel level, ItemStack stack) {
		ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
		if (enchantments == null || enchantments.isEmpty()) return false;

		Holder<Enchantment> soulbound = level.registryAccess()
			.lookupOrThrow(Registries.ENCHANTMENT)
			.get(KEY)
			.orElse(null);

		return soulbound != null && enchantments.getLevel(soulbound) > 0;
	}

	/** Set aside at the top of the death, to go back on the body at the bottom of it. */
	public static void hold(ServerPlayer player, List<Kept> kept) {
		if (kept.isEmpty()) return;
		pending.put(player.getUUID(), kept);
	}

	/**
	 * Put the held things back into the dead player's inventory, now that vanilla has finished
	 * emptying it.
	 *
	 * <p>Their own slot where they had one. Something out of a slot another mod added has no slot
	 * here to go back to, and the store behind that slot does not survive the respawn, so it goes
	 * in the pack.
	 */
	public static void settleOnBody(ServerPlayer player) {
		List<Kept> kept = pending.remove(player.getUUID());
		if (kept == null) return;

		Inventory inventory = player.getInventory();
		for (Kept item : kept) {
			ItemStack stack = item.stack();
			if (item.isVanillaSlot() && item.slot() < inventory.getContainerSize()
					&& inventory.getItem(item.slot()).isEmpty()) {
				inventory.setItem(item.slot(), stack);
			} else if (!inventory.add(stack)) {
				player.drop(stack, false, Prediction.SERVER_ONLY);
			}
		}
		inventory.setChanged();
	}

	/**
	 * Carry the soulbound things from the body to the new player.
	 *
	 * <p>Only those. The corpse can be holding other things by now - the bottle a potion left
	 * behind after its last swallow killed you - and those died with you like everything else.
	 */
	public static void restore(ServerPlayer dead, ServerPlayer reborn) {
		ServerLevel level = dead.level();
		Inventory from = dead.getInventory();
		Inventory to = reborn.getInventory();
		List<ItemStack> carried = new ArrayList<>();

		for (int slot = 0; slot < from.getContainerSize(); slot++) {
			ItemStack stack = from.getItem(slot);
			if (stack.isEmpty() || !has(level, stack)) continue;

			from.setItem(slot, ItemStack.EMPTY);
			if (slot < to.getContainerSize() && to.getItem(slot).isEmpty()) {
				to.setItem(slot, stack);
			} else if (!to.add(stack)) {
				reborn.drop(stack, false, Prediction.SERVER_ONLY);
			}
			carried.add(stack);
		}

		if (carried.isEmpty()) return;
		to.setChanged();

		// Said out loud, because a thing that did not leave is otherwise indistinguishable from
		// a thing that came back on its own, and only one of those is a promise kept.
		reborn.sendSystemMessage(Component.literal(carried.size() == 1
			? carried.get(0).getHoverName().getString() + " is soulbound and stayed with you"
			: carried.size() + " soulbound items stayed with you"));
	}
}
