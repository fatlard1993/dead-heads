package justfatlard.dead_heads.integration;

import net.minecraft.server.level.ServerPlayer;

/**
 * Hands the tidying to Chest Utils.
 *
 * <p>Names a Chest Utils type directly, so it must only be loaded behind the isModLoaded guard in
 * {@link justfatlard.dead_heads.PackSorting}.
 */
public final class ChestUtilsSort {
	private ChestUtilsSort() {}

	public static void sort(ServerPlayer player) {
		justfatlard.chest_utils.action.PackSort.sort(player);
	}
}
