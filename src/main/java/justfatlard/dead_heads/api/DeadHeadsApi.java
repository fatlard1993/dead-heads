package justfatlard.dead_heads.api;

import justfatlard.dead_heads.DeadHeadManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Telling this mod that ground it has heads on is about to stop existing.
 *
 * <p>A death head is remembered until somebody breaks it or empties it, which is right for the
 * world a player lives in and wrong for ground that gets bulldozed. An arena is built on a plot,
 * played on, and built over; every head left in one is then a record of a whole inventory, keyed to
 * a block that is no longer there and that nobody can ever break or loot. Nothing removes it, so
 * the save only ever grows - until it outgrows what the game will read back, which takes the server
 * down at startup.
 *
 * <p>So whoever flattens the ground says so, and the heads on it are forgotten with it.
 */
public final class DeadHeadsApi {
	private DeadHeadsApi() {}

	/**
	 * Forget every head standing in this box.
	 *
	 * <p>Bounds are inclusive and need no particular order; y is ignored, because a plot is
	 * flattened from the sky down and anything in that column goes with it.
	 *
	 * @return how many were forgotten
	 */
	public static int forgetWithin(ResourceKey<Level> dimension, BlockPos one, BlockPos other) {
		return DeadHeadManager.forgetWithin(dimension, one, other);
	}

	/** Forget every head in this dimension, for ground that is going altogether. */
	public static int forgetIn(ResourceKey<Level> dimension) {
		return DeadHeadManager.forgetIn(dimension);
	}
}
