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
			Component.translatable("item.dead-heads.death_compass"));

		CompoundTag marker = new CompoundTag();
		marker.putLong(MARKER_KEY, headPos.asLong());
		compass.set(DataComponents.CUSTOM_DATA, CustomData.of(marker));

		return compass;
	}

	/** Put one in the player's hands, or at their feet if there is no room. */
	public static void give(ServerPlayer player, ResourceKey<Level> dimension, BlockPos headPos) {
		ItemStack compass = forHead(dimension, headPos);
		if (!player.getInventory().add(compass)) {
			player.drop(compass, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
	}

	/**
	 * Take back the compass pointing at this head, wherever the player is keeping
	 * it. Called when the head gives up its contents, so the compass leaves at the
	 * moment it stops having a job: an inventory full of dead compasses pointing at
	 * places you have already been is worse than no compass at all.
	 */
	public static void reclaim(ServerPlayer player, BlockPos headPos) {
		long target = headPos.asLong();
		List<ItemStack> contents = player.getInventory().getNonEquipmentItems();

		for (int slot = 0; slot < contents.size(); slot++) {
			ItemStack stack = contents.get(slot);
			if (pointsAt(stack, target)) {
				contents.set(slot, ItemStack.EMPTY);
			}
		}
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
