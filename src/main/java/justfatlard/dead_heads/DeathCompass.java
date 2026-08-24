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

	/**
	 * Whether there is a head at the other end of this one.
	 *
	 * <p>Absent on compasses made before the distinction existed, and read as true there: nearly
	 * all of them were tethered, and the ones that were not are spent the moment they are looked
	 * at, which is where they were heading anyway.
	 */
	private static final String TETHER_KEY = "dead_heads_tethered";

	/** Pointed at a head, and spent when that head stops existing. */
	public static ItemStack forHead(ResourceKey<Level> dimension, BlockPos headPos) {
		return make(dimension, headPos, true);
	}

	/**
	 * Pointed at a place, for a death that left no head to point at.
	 *
	 * <p>Nothing will ever be harvested here, so nothing can ever spend it that way. This is the
	 * one kind that still goes when you arrive.
	 */
	public static ItemStack forPlace(ResourceKey<Level> dimension, BlockPos deathPos) {
		return make(dimension, deathPos, false);
	}

	private static ItemStack make(ResourceKey<Level> dimension, BlockPos headPos, boolean tethered) {
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
		marker.putBoolean(TETHER_KEY, tethered);
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
	 * How close counts as arrived, for the one kind of compass that ends that way.
	 *
	 * <p>Near enough that the place is in front of you rather than merely in the same chunk. There
	 * is nothing to find here - this is where somebody died with no room for a head - so standing
	 * on the spot is the whole of what the compass had to offer.
	 */
	private static final int ARRIVED_WITHIN = 8;

	/**
	 * Take back any compass that has stopped having a job.
	 *
	 * <p>One rule does nearly all of it: a compass tethered to a head is spent when that head is
	 * gone, however it went - harvested by its owner, looted by somebody else, broken, blown up,
	 * or rotted where it stood. That is the moment it stops pointing at anything worth walking
	 * to, and it is the same moment for every player carrying one, not only for whoever was
	 * standing there.
	 *
	 * <p>Walking to the place is no longer enough. Arriving and not picking the head up is a
	 * thing people do - hands full, wrong tools, coming back with a shulker - and the compass
	 * that got them there should still be there for the second trip.
	 *
	 * <p>The exception is a death that left no head. That compass points at bare ground which
	 * will never be harvested, so arriving is the only end it can have.
	 */
	public static void settle(ServerPlayer player) {
		int done = 0;
		int arrived = 0;

		if (COMPASS_SLOT) {
			ItemStack taken = justfatlard.dead_heads.integration.CompassSlot.take(
				player, stack -> spent(player, stack));
			if (!taken.isEmpty()) {
				if (tethered(taken)) done++; else arrived++;
			}
		}

		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!spent(player, stack)) continue;

			if (tethered(stack)) done++; else arrived++;
			inventory.setItem(slot, ItemStack.EMPTY);
		}

		// Said out loud, because an item leaving your hand on its own is otherwise
		// indistinguishable from never having been given one.
		if (done > 0) {
			player.sendSystemMessage(Component.literal(done == 1
				? "The head this compass was tethered to is gone. The compass is spent."
				: "The heads these compasses were tethered to are gone. The compasses are spent."));
		}
		if (arrived > 0) {
			player.sendSystemMessage(Component.literal(arrived == 1
				? "You are here. Your death compass is spent."
				: "You are here. Your death compasses are spent."));
		}
	}

	/** Whether this compass of ours has nothing left to point at. */
	private static boolean spent(ServerPlayer player, ItemStack stack) {
		if (!isDeathCompass(stack)) return false;

		LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
		if (tracker == null || tracker.target().isEmpty()) return false;
		GlobalPos target = tracker.target().get();

		if (tethered(stack)) return !DeadHeadManager.hasHead(target);

		return arrived(player, target);
	}

	private static boolean tethered(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null || data.copyTag().getBooleanOr(TETHER_KEY, true);
	}

	/** Whether the player is standing at the place this points to. */
	private static boolean arrived(ServerPlayer player, GlobalPos target) {
		// A compass for somewhere in the nether is not spent by standing on the same
		// coordinates in the overworld.
		if (!target.dimension().equals(player.level().dimension())) return false;

		return target.pos().closerThan(player.blockPosition(), ARRIVED_WITHIN);
	}

	/** True when this stack is one of ours, whichever head it points at. */
	public static boolean isDeathCompass(ItemStack stack) {
		if (stack.isEmpty() || !stack.is(Items.COMPASS)) return false;
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().contains(MARKER_KEY);
	}
}
