package justfatlard.dead_heads;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ground under a head, and under whoever comes to empty it.
 *
 * <p>A death below the world has no ground to leave a head on. Raised to the bottom of the world,
 * the head floated over the void, and anyone sent to it stood on the skull, emptied it, and fell
 * again with everything back on them: a new head in the same spot, every trip. So a void death
 * leaves its head where the player last stood on solid ground, and a head or landing spot with
 * nothing under it down to the void gets a block of obsidian to stand on.
 */
public final class Footing {
	private Footing() {}

	/** Not glass, which a pickaxe takes without thinking, dropping whoever stands on it. */
	private static final Block FLOOR = Blocks.OBSIDIAN;

	private static final Map<UUID, GlobalPos> lastGround = new ConcurrentHashMap<>();

	/** Every tick: where each player standing on something solid has their feet. */
	public static void track(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.onGround() || player.isSpectator() || !player.isAlive()) continue;
			BlockPos on = player.getOnPos();
			if (!solid(player.level(), on)) continue;
			lastGround.put(player.getUUID(), GlobalPos.of(player.level().dimension(), on.above()));
		}
	}

	/** Where this player last stood on solid ground since the server started, if anywhere. */
	public static GlobalPos lastGround(UUID player) {
		return lastGround.get(player);
	}

	/**
	 * Something that holds a player up. A skull holds one up only while something holds the skull
	 * up: emptying a head removes the block, and standing on it was standing on nothing.
	 */
	public static boolean solid(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.getCollisionShape(level, pos).isEmpty()) return false;
		return !(state.getBlock() instanceof AbstractSkullBlock) || solid(level, pos.below());
	}

	/** Nothing to land on from here down to the bottom of the world. */
	static boolean openToVoid(Level level, BlockPos pos) {
		for (BlockPos at = pos; at.getY() >= level.getMinY(); at = at.below()) {
			if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty()) return false;
		}
		return true;
	}

	/** A block under this spot when falling from it would end below the world. */
	static void floorUnder(ServerLevel level, BlockPos pos) {
		BlockPos below = pos.below();
		if (below.getY() < level.getMinY() || !openToVoid(level, below)) return;
		level.setBlock(below, FLOOR.defaultBlockState(), Block.UPDATE_ALL);
	}
}
