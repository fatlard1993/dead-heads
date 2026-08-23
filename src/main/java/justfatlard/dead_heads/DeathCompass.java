package justfatlard.dead_heads;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;

/**
 * A compass that knows where you died, handed over on respawn and taken back
 * when you have your things again.
 *
 * <p>The head already marks the spot; the problem is finding the spot. Vanilla's
 * recovery compass points at your last death, which is close but wrong in the
 * case that matters: die again on the way back and it forgets the body you were
 * walking to. This one is pinned to a specific head, so a second death gives you
 * a second compass rather than losing the first.
 *
 * <p>Implemented as an ordinary compass carrying a lodestone target with
 * {@code tracked = false}, which is the vanilla way to aim a compass at a place
 * with no lodestone in it. No new item, no model, no client-side anything.
 */
public final class DeathCompass {
	private DeathCompass() {}

	/** Marks our compasses so they can be found again without guessing from the target. */
	private static final String MARKER_KEY = "dead_heads_compass";

	public static ItemStack forHead(ResourceKey<Level> dimension, BlockPos headPos) {
		ItemStack compass = new ItemStack(Items.COMPASS);

		compass.set(DataComponents.LODESTONE_TRACKER,
			new LodestoneTracker(Optional.of(GlobalPos.of(dimension, headPos)), false));
		compass.set(DataComponents.CUSTOM_NAME,
			// Literal, not a key. This mod is server-side and installs nothing on the
			// client, so the client has no lang file to resolve a key against: a translatable
			// name arrived in the player's hand reading "item.dead-heads.death_compass", which
			// is why one of these could be picked up and still feel like it never came.
			Component.literal("Death Compass"));

		CompoundTag marker = new CompoundTag();
		marker.putLong(MARKER_KEY, headPos.asLong());
		compass.set(DataComponents.CUSTOM_DATA, CustomData.of(marker));

		return compass;
	}

	/** Whether the inventory screen has a compass slot to put this in. */
	private static final boolean COMPASS_SLOT =
		net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("map-plus-plus");

	/**
	 * Put one where a compass belongs: the compass slot, or failing that the offhand.
	 *
	 * <p>Somewhere a player will look, rather than a square in the pack. With Map Plus Plus
	 * installed that is the slot it adds; without it, the offhand is the only other place a
	 * compass has a home of its own, and it leaves the hotbar free for what a freshly dead
	 * player is about to pick back up.
	 *
	 * <p>The pack and then the ground remain behind those, because a compass that is somewhere
	 * awkward still beats one that is nowhere: found on the floor was one of the ways this went
	 * wrong, but it was the inventory being full that put it there, not this order of
	 * preference.
	 */
	public static void give(ServerPlayer player, ItemStack compass) {
		if (COMPASS_SLOT
			&& justfatlard.dead_heads.integration.CompassSlot.offer(player, compass)) {
			return;
		}

		if (player.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty()) {
			player.setItemSlot(EquipmentSlot.OFFHAND, compass);
			return;
		}

		if (!player.getInventory().add(compass)) {
			player.drop(compass, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
	}

	/**
	 * Every compass of ours the player is keeping somewhere the death sweep does not reach,
	 * taken out so it can be handed back after the respawn.
	 */
	public static List<ItemStack> takeStashed(ServerPlayer player) {
		if (!COMPASS_SLOT) return List.of();

		ItemStack stashed = justfatlard.dead_heads.integration.CompassSlot.take(
			player, DeathCompass::isDeathCompass);

		return stashed.isEmpty() ? List.of() : List.of(stashed);
	}

	/**
	 * Take back the compass pointing at this head, wherever the player is keeping
	 * it. Called when the head gives up its contents, so the compass leaves at the
	 * moment it stops having a job: an inventory full of dead compasses pointing at
	 * places you have already been is worse than no compass at all.
	 */
	public static void reclaim(ServerPlayer player, BlockPos headPos) {
		long target = headPos.asLong();

		// Wherever it is kept has to include the slot it may have been put in, or a compass handed
		// to that slot would be the one that never leaves.
		if (COMPASS_SLOT) {
			justfatlard.dead_heads.integration.CompassSlot.reclaim(player, stack -> pointsAt(stack, target));
		}

		// The whole container rather than the pack alone: the offhand is a place people keep a
		// compass, and it is a slot the death sweep already reaches.
		Inventory inventory = player.getInventory();

		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (pointsAt(inventory.getItem(slot), target)) {
				inventory.setItem(slot, ItemStack.EMPTY);
			}
		}
	}

	/**
	 * How close counts as arrived.
	 *
	 * <p>Near enough that the place is in front of you rather than merely in the same chunk, and
	 * far enough that the compass does not survive the walk in. Eight blocks is about the range
	 * at which a head on the ground is visible, so the compass goes at the moment it stops
	 * telling you anything you cannot already see.
	 */
	private static final int ARRIVED_WITHIN = 8;

	/**
	 * Drop any of our compasses whose destination the player has now reached.
	 *
	 * <p>Breaking the head already reclaims the compass that pointed at it, but that only ever
	 * covered the deaths that left a head. A compass made for a headless death had nothing to
	 * reclaim it and would have followed the player around for good. Arriving is the thing both
	 * kinds have in common.
	 */
	public static void consumeOnArrival(ServerPlayer player) {
		int spent = 0;

		if (COMPASS_SLOT
			&& !justfatlard.dead_heads.integration.CompassSlot.take(
				player, stack -> arrived(player, stack)).isEmpty()) {
			spent++;
		}

		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (arrived(player, inventory.getItem(slot))) {
				inventory.setItem(slot, ItemStack.EMPTY);
				spent++;
			}
		}

		// Said out loud, because an item leaving your hand on its own is otherwise
		// indistinguishable from never having been given one - and dying within sight of
		// where you respawn spends the compass about a second after you get it.
		if (spent > 0) {
			player.sendSystemMessage(Component.literal(
				spent == 1 ? "You are here. Your death compass is spent."
					: "You are here. Your death compasses are spent."));
		}
	}

	/** Whether this is one of ours and the player is standing at the place it points to. */
	private static boolean arrived(ServerPlayer player, ItemStack stack) {
		if (!isDeathCompass(stack)) return false;

		LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
		if (tracker == null || tracker.target().isEmpty()) return false;

		GlobalPos target = tracker.target().get();
		// A compass for somewhere in the nether is not spent by standing on the same
		// coordinates in the overworld.
		if (!target.dimension().equals(player.level().dimension())) return false;

		return target.pos().closerThan(player.blockPosition(), ARRIVED_WITHIN);
	}

	private static boolean pointsAt(ItemStack stack, long headPos) {
		if (stack.isEmpty() || !stack.is(Items.COMPASS)) return false;

		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) return false;

		CompoundTag tag = data.copyTag();
		return tag.getLongOr(MARKER_KEY, Long.MIN_VALUE) == headPos;
	}

	/** True when this stack is one of ours, whichever head it points at. */
	public static boolean isDeathCompass(ItemStack stack) {
		if (stack.isEmpty() || !stack.is(Items.COMPASS)) return false;
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().contains(MARKER_KEY);
	}
}
