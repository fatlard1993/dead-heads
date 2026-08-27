package justfatlard.dead_heads;

import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
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
 * <p>Added slots only exist through Pandorical, and Pandorical is optional - so without it there
 * is nothing to empty and nowhere to put back, and the class that names its types stays unloaded.
 */
public final class ExtraSlots {
	private ExtraSlots() {}

	private static final boolean PRESENT = FabricLoader.getInstance().isModLoaded("pandorical");

	/** Everything sitting in an added slot, taken out of it, ready for the head. */
	public static List<Kept> empty(ServerPlayer player) {
		if (!PRESENT) return List.of();
		return justfatlard.dead_heads.integration.PandoricalSlots.empty(player);
	}

	/**
	 * Put one back where it came from.
	 *
	 * @return false where the slot is gone, occupied, or no longer takes this item - the caller
	 *         then finds it a place in the pack instead
	 */
	public static boolean putBack(ServerPlayer player, String namespace, int slotIndex, ItemStack stack) {
		if (!PRESENT) return false;
		return justfatlard.dead_heads.integration.PandoricalSlots.putBack(player, namespace, slotIndex, stack);
	}
}
