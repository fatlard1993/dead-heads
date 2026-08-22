package justfatlard.dead_heads.integration;

import justfatlard.map_plus_plus.Main;
import justfatlard.map_plus_plus.inventory.MapPlusPlusInventory;
import justfatlard.pandorical.api.PandoricalApi;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Puts a death compass where a compass goes, when there is somewhere for it to go.
 *
 * <p>Map Plus Plus gives the inventory screen a slot for a compass. A death compass is a compass,
 * so a player carrying that mod should find it there rather than in the first free square of their
 * pack, which is usually the one they were about to put cobble in.
 *
 * <p>Names Map Plus Plus and Pandorical types directly, so it must only be loaded behind the
 * isModLoaded guard in {@link justfatlard.dead_heads.DeathCompass}. Pandorical needs no guard of
 * its own: Map Plus Plus registers the slot through it, so where one is present so is the other.
 */
public final class CompassSlot {
	private CompassSlot() {}

	/** @return true where the compass was put in the slot and needs no other home */
	public static boolean offer(ServerPlayer player, ItemStack compass) {
		var slots = PandoricalApi.playerInventory();

		// Only into an empty slot. Whatever is in there is the compass they chose to keep there,
		// and quietly swapping it for one that points at a corpse is not a favour.
		ItemStack existing = slots.getSlot(player, Main.SLOTS_NAMESPACE, MapPlusPlusInventory.COMPASS_SLOT);
		if (!existing.isEmpty()) return false;

		slots.setSlot(player, Main.SLOTS_NAMESPACE, MapPlusPlusInventory.COMPASS_SLOT, compass);
		return true;
	}

	/** Take ours back out of the slot, if that is where it ended up. */
	public static void reclaim(ServerPlayer player, java.util.function.Predicate<ItemStack> ours) {
		take(player, ours);
	}

	/**
	 * Take ours out of the slot and hand it back, or {@link ItemStack#EMPTY} where the slot holds
	 * something else.
	 *
	 * <p>The slot lives in a Fabric attachment that is not copied across a death, so anything left
	 * in there when the player dies is gone rather than dropped. A compass that has to survive the
	 * death has to be lifted out of the slot before it happens.
	 */
	public static ItemStack take(ServerPlayer player, java.util.function.Predicate<ItemStack> ours) {
		var slots = PandoricalApi.playerInventory();

		ItemStack held = slots.getSlot(player, Main.SLOTS_NAMESPACE, MapPlusPlusInventory.COMPASS_SLOT);
		if (!ours.test(held)) return ItemStack.EMPTY;

		slots.setSlot(player, Main.SLOTS_NAMESPACE, MapPlusPlusInventory.COMPASS_SLOT, ItemStack.EMPTY);
		return held;
	}
}
