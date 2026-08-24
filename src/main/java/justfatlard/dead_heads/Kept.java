package justfatlard.dead_heads;

import net.minecraft.world.item.ItemStack;

/**
 * One item in a head, and the place it was taken from.
 *
 * <p>A death used to hand back a heap. Everything was there, and none of it was anywhere: helmet
 * in the pack, shield in with the cobble, and the first two minutes back at your body spent
 * putting your own kit on again. What a body remembers is not just what you had but where you
 * had it.
 *
 * @param stack     the item itself
 * @param namespace {@code ""} for the ordinary inventory, or the namespace of the mod whose extra
 *                  inventory slot this came out of
 * @param slot      the index within that inventory, or {@code -1} for something that was never in
 *                  a particular place - mob loot, and heads written by a version that did not
 *                  record this
 */
public record Kept(ItemStack stack, String namespace, int slot) {

	/** Something with no home to go back to. */
	public static Kept loose(ItemStack stack) {
		return new Kept(stack, "", NOWHERE);
	}

	public static final int NOWHERE = -1;

	/** True where this came out of the player's own 41 slots, armour and offhand included. */
	public boolean isVanillaSlot() {
		return namespace.isEmpty() && slot != NOWHERE;
	}

	/** True where this came out of a slot some other mod added to the inventory screen. */
	public boolean isExtraSlot() {
		return !namespace.isEmpty() && slot != NOWHERE;
	}
}
