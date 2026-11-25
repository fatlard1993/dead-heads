package justfatlard.dead_heads;

import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * A death below the world, and the trip back to a head over the void. In the mod's own package so
 * it reaches the return trip without widening it.
 */
public final class VoidDeaths implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();
			server.runCommand("gamemode survival @a");
			context.waitTicks(5);

			BlockPos spawn = server.computeOnServer(s -> connection.getServerPlayer().blockPosition());
			BlockPos column = spawn.offset(24, 0, 24);

			// Fallen below the world: the head is where the player last stood, not over the void.
			server.runOnServer(s -> {
				ServerPlayer player = connection.getServerPlayer();
				ServerLevel level = s.overworld();
				GlobalPos ground = Footing.lastGround(player.getUUID());
				check(ground != null && ground.dimension().equals(level.dimension()), "standing at spawn was not noted as ground");
				openToTheVoid(level, column);

				player.teleportTo(level, column.getX() + 0.5, level.getMinY() - 10, column.getZ() + 0.5, java.util.Set.of(), 0, 0, true);
				DeadHeadManager.handleDeath(player, List.of(Kept.loose(new ItemStack(Items.DIAMOND))), List.of());
				player.teleportTo(level, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, java.util.Set.of(), 0, 0, true);

				GlobalPos head = DeadHeadManager.headsOf(player.getUUID()).get(0);
				check(head.pos().getX() == ground.pos().getX() && head.pos().getZ() == ground.pos().getZ(),
					"the head is at " + head.pos().toShortString() + ", not where the player last stood, " + ground.pos().toShortString());
				check(level.getBlockState(head.pos()).is(Blocks.PLAYER_HEAD), "no head was placed where the player last stood");
			});

			// A head already over the void, from before: the trip to it puts a floor under it.
			server.runOnServer(s -> {
				ServerPlayer player = connection.getServerPlayer();
				ServerLevel level = s.overworld();
				BlockPos floating = column.atY(level.getMinY() + 1);
				level.setBlockAndUpdate(floating, Blocks.PLAYER_HEAD.defaultBlockState());
				check(!Footing.solid(level, floating), "a head over the void counted as somewhere to stand");

				player.fallDistance = 100;
				DeadReckoning.go(player, GlobalPos.of(level.dimension(), floating), "test");
				check(level.getBlockState(floating.below()).is(Blocks.OBSIDIAN), "no floor was put under the head");
				check(Footing.solid(level, floating), "the head on its floor is still not somewhere to stand");
				check(player.blockPosition().getX() == floating.getX() && player.blockPosition().getZ() == floating.getZ(),
					"the trip landed at " + player.blockPosition().toShortString() + ", not at the head");

				// Emptied: the block under the player's feet goes, the floor does not
				level.removeBlock(floating, false);
			});
			context.waitTicks(40);
			server.runOnServer(s -> {
				ServerPlayer player = connection.getServerPlayer();
				check(player.isAlive(), "the player died after emptying a head over the void, or of the fall carried into the trip");
				check(player.getY() >= s.overworld().getMinY(), "the player fell through the floor, to y " + player.getY());
			});
		}
		context.waitTicks(10);
	}

	/** The column clear down through the bedrock, so below it is the void. */
	private static void openToTheVoid(ServerLevel level, BlockPos column) {
		for (int y = level.getMinY(); y <= level.getMinY() + 12; y++) {
			level.setBlockAndUpdate(column.atY(y), Blocks.AIR.defaultBlockState());
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
