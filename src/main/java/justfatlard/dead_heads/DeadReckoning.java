package justfatlard.dead_heads;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A potion that takes you back to where you died.
 *
 * <p>A chorus fruit, which is the thing in the game that moves you without asking where to,
 * brewed onto a potion of healing, which is what you want to be drinking when you arrive.
 * The compass tells you the way; this is for when the way is the problem - the lava lake, the
 * far side of the End, the bottom of the ravine you are not equipped to climb back down into.
 *
 * <p>Each bottle is one jump, and the jumps run in order of what you want most: your newest head
 * first, then each older one of yours, then - once you have stood at all of them - a stranger's
 * head that has come unlocked, chosen at random, which is what the skeleton skulls scattered
 * around a server are for. Past all of those there is nowhere left, and the potion takes you the
 * one place it has not: it kills you, and the head that death leaves is unlocked from the start.
 * The bottle knows it has nothing to offer, and says so before the swallow.
 *
 * <p>Your own death is what resets the tour. Everything you had visited is forgotten, and the
 * next bottle starts again at the newest head, which is the one that death just made.
 *
 * <p>No new item and no new potion in the registry. It is a vanilla potion carrying a colour, an
 * effect, a name, and a mark, made by a datapack brewing recipe, so a vanilla client draws it,
 * names it, and lets chorus fruit into the stand without being told anything. The name is a
 * custom name and not an item name, because a potion with contents names itself before it ever
 * looks at the item name, and the first three bottles handed out read "Uncraftable Potion" to
 * prove it. A registered potion would have been the same thing to look at and would have kept
 * every client without this mod off the server.
 */
public final class DeadReckoning {
	private DeadReckoning() {}

	private static final String MARKER_KEY = "dead_heads_reckoning";

	/** Which heads each player has already been taken to, since their last death. */
	private static final Map<UUID, Set<GlobalPos>> visited = new ConcurrentHashMap<>();

	public static boolean isPotion(ItemStack stack) {
		if (stack.isEmpty() || !stack.is(Items.POTION)) return false;
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().contains(MARKER_KEY);
	}

	/** A death starts the tour over. */
	public static void forget(UUID player) {
		visited.remove(player);
	}

	/** One swallow: the next place on the list, or the end of it. */
	public static void drink(ServerPlayer player) {
		Set<GlobalPos> seen = visited.computeIfAbsent(player.getUUID(), uuid -> ConcurrentHashMap.newKeySet());

		List<GlobalPos> own = DeadHeadManager.headsOf(player.getUUID());
		own.removeIf(seen::contains);
		if (!own.isEmpty()) {
			GlobalPos head = own.get(0);
			seen.add(head);
			go(player, head, (own.size() == 1 ? "Back to your last head" : "Back to a head of yours")
				+ " at " + where(head));
			return;
		}

		List<GlobalPos> open = DeadHeadManager.openHeads(player.getUUID());
		open.removeIf(seen::contains);
		if (!open.isEmpty()) {
			GlobalPos head = open.get(player.getRandom().nextInt(open.size()));
			seen.add(head);
			go(player, head, "Nothing of yours is left. To a stranger's head at " + where(head));
			return;
		}

		player.sendSystemMessage(Component.literal("Nowhere left to go. The potion takes you the one place it has not."));
		DeadHeadManager.unlockNextDeath(player.getUUID());

		// After the swallow finishes rather than in the middle of it, so that the bottle the
		// potion hands back is on the body when the head is packed rather than arriving after.
		MinecraftServer server = player.level().getServer();
		server.schedule(new TickTask(server.getTickCount(), () -> {
			if (!player.isRemoved() && player.isAlive()) player.kill(player.level());
		}));
	}

	private static String where(GlobalPos head) {
		BlockPos pos = head.pos();
		return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
	}

	/** Move a player to a head, or to any spot given as one, standing them somewhere clear beside it. */
	static void go(ServerPlayer player, GlobalPos head, String said) {
		ServerLevel from = player.level();
		ServerLevel to = from.getServer().getLevel(head.dimension());
		if (to == null) {
			player.sendSystemMessage(Component.literal("That head is somewhere this world no longer has"));
			return;
		}

		BlockPos feet = standingSpot(to, head.pos());

		from.playSound(null, player.getX(), player.getY(), player.getZ(),
			SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
		player.stopRiding();
		player.teleportTo(to, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, Set.of(),
			player.getYRot(), player.getXRot(), true);
		to.playSound(null, player.getX(), player.getY(), player.getZ(),
			SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);

		// Beside a head on the sea floor: breath enough to empty it and swim up.
		if (to.getFluidState(feet.above()).is(FluidTags.WATER)) {
			player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 45 * 20));
		}

		player.sendSystemMessage(Component.literal(said));
	}

	/** How far a landing spot is looked for, in steps through open space from the head. */
	private static final int SEARCH_STEPS = 12;
	/** And how many open blocks at most, so a head at the bottom of a ravine does not walk it all. */
	private static final int SEARCH_CELLS = 4096;
	/** How far round a sealed-in head a spot is looked for through the rock. */
	private static final int SEALED_RADIUS = 6;

	/**
	 * The nearest place to stand beside the head: room for feet and head, and ground under them.
	 *
	 * <p>Walked out to through open space from the head, not measured through rock, so the spot is
	 * in the same cave as the head rather than a nearer one on the far side of a wall. On top of
	 * the head counts, and wins a tie: a skull is a low block, and on it is at the spot. A head
	 * sealed in has no open way out, so then it is the nearest spot through the rock, and failing
	 * that the first one straight up.
	 */
	private static BlockPos standingSpot(ServerLevel level, BlockPos head) {
		BlockPos walked = walkToStand(level, head);
		if (walked != null) return walked;
		BlockPos near = nearestStand(level, head);
		if (near != null) return near;
		for (BlockPos up = head.above(); up.getY() < level.getMaxY() - 1; up = up.above()) {
			if (standable(level, up)) return up;
		}
		return head.above();
	}

	/** Breadth first through open space; UP is tried before the sides, so on the head wins a tie. */
	private static BlockPos walkToStand(ServerLevel level, BlockPos head) {
		ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
		Map<BlockPos, Integer> steps = new HashMap<>();
		steps.put(head, 0);
		frontier.add(head);
		while (!frontier.isEmpty() && steps.size() < SEARCH_CELLS) {
			BlockPos at = frontier.poll();
			int step = steps.get(at);
			if (step > 0 && standable(level, at)) return at;
			if (step >= SEARCH_STEPS) continue;
			for (Direction side : Direction.values()) {
				BlockPos next = at.relative(side);
				if (steps.containsKey(next) || !clear(level, next)) continue;
				steps.put(next, step + 1);
				frontier.add(next);
			}
		}
		return null;
	}

	private static BlockPos nearestStand(ServerLevel level, BlockPos head) {
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos at : BlockPos.betweenClosed(head.offset(-SEALED_RADIUS, -SEALED_RADIUS, -SEALED_RADIUS),
				head.offset(SEALED_RADIUS, SEALED_RADIUS, SEALED_RADIUS))) {
			double distance = at.distSqr(head);
			if (distance < bestDistance && standable(level, at)) {
				best = at.immutable();
				bestDistance = distance;
			}
		}
		return best;
	}

	private static boolean standable(ServerLevel level, BlockPos feet) {
		return roomToStand(level, feet) && ground(level, feet.below());
	}

	private static boolean roomToStand(ServerLevel level, BlockPos feet) {
		return clear(level, feet) && clear(level, feet.above());
	}

	/** Nothing to collide with and nothing to burn in. */
	private static boolean clear(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.getCollisionShape(level, pos).isEmpty() && !state.getFluidState().is(FluidTags.LAVA)
			&& !state.is(BlockTags.FIRE);
	}

	/** Something to stand on, which lava is not. */
	private static boolean ground(ServerLevel level, BlockPos pos) {
		return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}
}
