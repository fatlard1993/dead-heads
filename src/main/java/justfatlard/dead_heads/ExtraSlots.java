package justfatlard.dead_heads;

import java.util.ArrayList;
import java.util.List;
import justfatlard.pandorical.api.PandoricalApi;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * The slots other mods have added to the inventory screen: a map slot, a compass slot, whatever
 * else turns up.
 *
 * <p>They are inventory slots, so what is in them belongs in the head with everything else. They
 * were not going there before, and they were not dropping either - the store behind them is not
 * carried across a respawn, so an equipped map simply stopped existing. This is where they get
 * taken and where they get put back.
 *
 * <p>Pandorical is a hard dependency of this mod, so nothing here needs a guard.
 */
public final class ExtraSlots {
	private ExtraSlots() {}

	/** Everything sitting in an added slot, taken out of it, ready for the head. */
	public static List<Kept> empty(ServerPlayer player) {
		var slots = PandoricalApi.playerInventory();
		List<Kept> taken = new ArrayList<>();

		for (var registration : PandoricalApi.playerInventory().registeredSlots()) {
			Identifier namespace = registration.namespace();

			for (var entry : registration.slots()) {
				ItemStack stack = slots.getSlot(player, namespace, entry.slotIndex());
				if (stack.isEmpty()) continue;

				taken.add(new Kept(stack.copy(), namespace.toString(), entry.slotIndex()));
				slots.setSlot(player, namespace, entry.slotIndex(), ItemStack.EMPTY);
			}
		}
		return taken;
	}

	/**
	 * Put one back where it came from.
	 *
	 * @return false where the slot is gone, occupied, or no longer takes this item - the caller
	 *         then finds it a place in the pack instead
	 */
	public static boolean putBack(ServerPlayer player, String namespace, int slotIndex, ItemStack stack) {
		Identifier id = Identifier.tryParse(namespace);
		if (id == null) return false;

		var slots = PandoricalApi.playerInventory();

		// The mod that owned this slot may not be installed any more, in which case writing to it
		// would put the item somewhere with nothing to show it.
		boolean declared = PandoricalApi.playerInventory().registeredSlots().stream()
			.filter(registration -> registration.namespace().equals(id))
			.flatMap(registration -> registration.slots().stream())
			.anyMatch(entry -> entry.slotIndex() == slotIndex && entry.validator().test(stack));
		if (!declared) return false;

		if (!slots.getSlot(player, id, slotIndex).isEmpty()) return false;

		slots.setSlot(player, id, slotIndex, stack);
		return true;
	}
}
