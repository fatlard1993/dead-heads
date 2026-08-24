package justfatlard.dead_heads;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

/**
 * Tidy what came back, where the mod that knows how is installed.
 *
 * <p>A head hands back a pack's worth of items in the order they were taken, and the ones that
 * could not go home land wherever there was a gap. Chest Utils already holds the player's own
 * answer to what tidy means - including whether their hotbar is theirs to keep - so the sort is
 * its call rather than a second opinion invented here.
 */
public final class PackSorting {
	private PackSorting() {}

	private static final boolean PRESENT = FabricLoader.getInstance().isModLoaded("chest-utils");

	public static void sort(ServerPlayer player) {
		if (!PRESENT) return;
		justfatlard.dead_heads.integration.ChestUtilsSort.sort(player);
	}
}
