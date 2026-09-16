package justfatlard.dead_heads;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;

/**
 * The op's way back to a death, for when the compass and the potion are not enough: a player
 * who cannot reach their head, or one who is stuck in a loop at the spot that killed them.
 *
 * <p>{@code /deadheads tp [player]} sends a player to where they last died, and
 * {@code /deadheads where [player]} says where that is. The game's own record of the last death
 * is asked first, because it is written whether or not a head was left; the newest head stands
 * in for it when the record is missing, which is what an older save has.
 */
public final class DeadHeadCommands {
	private DeadHeadCommands() {}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("deadheads")
			.requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
			.then(Commands.literal("tp")
				.executes(context -> tp(context.getSource(), context.getSource().getPlayerOrException()))
				.then(Commands.argument("player", EntityArgument.player())
					.executes(context -> tp(context.getSource(), EntityArgument.getPlayer(context, "player")))))
			.then(Commands.literal("where")
				.executes(context -> where(context.getSource(), context.getSource().getPlayerOrException()))
				.then(Commands.argument("player", EntityArgument.player())
					.executes(context -> where(context.getSource(), EntityArgument.getPlayer(context, "player"))))));
	}

	private static int tp(CommandSourceStack source, ServerPlayer player) throws CommandSyntaxException {
		Optional<GlobalPos> death = lastDeath(player);
		if (death.isEmpty()) {
			source.sendFailure(Component.literal(player.getGameProfile().name() + " has no death on record"));
			return 0;
		}
		DeadReckoning.go(player, death.get(), "Taken back to where you died, at " + describe(death.get()));
		if (source.getEntity() != player) {
			source.sendSuccess(() -> Component.literal("Sent " + player.getGameProfile().name()
				+ " to their last death at " + describe(death.get())), true);
		}
		return 1;
	}

	private static int where(CommandSourceStack source, ServerPlayer player) {
		Optional<GlobalPos> death = lastDeath(player);
		if (death.isEmpty()) {
			source.sendFailure(Component.literal(player.getGameProfile().name() + " has no death on record"));
			return 0;
		}
		source.sendSuccess(() -> Component.literal(player.getGameProfile().name()
			+ " last died at " + describe(death.get())), false);
		return 1;
	}

	/**
	 * The player back to where they last died, the way {@code /deadheads tp} takes them, for
	 * another mod that offers the trip. Says so to the player either way.
	 *
	 * @return false when there is no death on record
	 */
	public static boolean goToLastDeath(ServerPlayer player) {
		Optional<GlobalPos> death = lastDeath(player);
		if (death.isEmpty()) {
			player.sendSystemMessage(Component.literal("You have no death on record"));
			return false;
		}
		DeadReckoning.go(player, death.get(), "Taken back to where you died, at " + describe(death.get()));
		return true;
	}

	/** Whether there is a death on record to go back to. */
	public static boolean hasLastDeath(ServerPlayer player) {
		return lastDeath(player).isPresent();
	}

	/** The head that death left, where there is one: it is always in the column the body fell in. */
	private static Optional<GlobalPos> lastDeath(ServerPlayer player) {
		List<GlobalPos> heads = DeadHeadManager.headsOf(player.getUUID());
		Optional<GlobalPos> recorded = player.getLastDeathLocation();
		if (recorded.isEmpty()) return heads.isEmpty() ? Optional.empty() : Optional.of(heads.get(0));

		GlobalPos died = recorded.get();
		for (GlobalPos head : heads) {
			if (head.dimension().equals(died.dimension())
					&& head.pos().getX() == died.pos().getX() && head.pos().getZ() == died.pos().getZ()) {
				return Optional.of(head);
			}
		}
		return recorded;
	}

	private static String describe(GlobalPos where) {
		BlockPos pos = where.pos();
		String dimension = where.dimension().identifier().getPath();
		return pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + " in the " + dimension;
	}
}
