package justfatlard.dead_heads;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;

/**
 * The mob half of the mod: a player-killed mob leaves its drops inside a head
 * block instead of scattering item entities, and that head rots away on a timer.
 *
 * Drops are intercepted by bracketing LivingEntity#dropAllDeathLoot (see
 * mixin/LivingEntityDeathLootMixin) and swallowing every Entity#spawnAtLocation
 * made inside that window (see mixin/EntityDropCaptureMixin). Experience is
 * dropped by dropExperience through ExperienceOrb, not spawnAtLocation, so it is
 * untouched and still falls normally.
 */
public final class MobHeads {
	private static Entity capturing = null;
	private static List<ItemStack> captured = null;

	/** Bonemeal charges beyond the first, one per this many items in the head. */
	private static final int ITEMS_PER_EXTRA_CHARGE = 8;
	private static final int MAX_CHARGES = 8;

	private MobHeads() {}

	/**
	 * Only real mobs, only when a player got the kill, only when the rule is on.
	 *
	 * The player-kill gate is vanilla's own lastHurtByPlayerMemoryTime, the exact
	 * flag dropAllDeathLoot uses to decide whether the loot table runs at all, so
	 * a mob that drowns, burns, falls, or is killed by another mob is left
	 * completely alone.
	 *
	 * Bosses are excluded: a nether star inside a head on a decay timer is a
	 * uniquely bad trade, and boss deaths already have their own drop rituals.
	 * ArmorStand is a LivingEntity but not a Mob, so breaking one still behaves
	 * exactly as vanilla.
	 */
	public static boolean shouldCapture(LivingEntity entity) {
		if (!(entity instanceof Mob)) return false;
		if (entity instanceof WitherBoss || entity instanceof EnderDragon) return false;
		if (!(entity.level() instanceof ServerLevel level)) return false;
		if (!DeadHeadsGameRules.mobHeadsEnabled(level)) return false;
		return entity.getLastHurtByPlayerMemoryTime() > 0;
	}

	public static void beginCapture(Entity entity) {
		capturing = entity;
		captured = new ArrayList<>();
	}

	public static boolean isCapturing(Entity entity) {
		return capturing != null && capturing == entity;
	}

	public static void capture(ItemStack stack) {
		if (captured != null && !stack.isEmpty()) captured.add(stack.copy());
	}

	/**
	 * Closes the capture window and hands anything collected to the manager. A
	 * mob that dropped nothing at all leaves no head, which keeps mob farms of
	 * empty-handed mobs from littering the world with empty skulls.
	 */
	public static void endCapture(LivingEntity entity) {
		if (!isCapturing(entity)) return;

		List<ItemStack> items = captured;
		capturing = null;
		captured = null;

		if (items == null || items.isEmpty()) return;

		DeadHeadManager.handleMobDeath(entity, items);
	}

	/**
	 * Vanilla has heads for exactly six mobs. Everything else gets a plain
	 * skeleton skull, which is also what a real skeleton gets, so it is the
	 * fallback rather than a case here. Deliberately no profile-component skin
	 * payloads: those would render a cow face on a vanilla client but only by
	 * making every client fetch a texture over the network, and this mod is
	 * worth keeping free of client-side requirements.
	 */
	public static Block headBlockFor(EntityType<?> type) {
		if (type == EntityTypes.ZOMBIE) return Blocks.ZOMBIE_HEAD;
		if (type == EntityTypes.CREEPER) return Blocks.CREEPER_HEAD;
		if (type == EntityTypes.PIGLIN) return Blocks.PIGLIN_HEAD;
		if (type == EntityTypes.WITHER_SKELETON) return Blocks.WITHER_SKELETON_SKULL;
		if (type == EntityTypes.ENDER_DRAGON) return Blocks.DRAGON_HEAD;
		return Blocks.SKELETON_SKULL;
	}

	/**
	 * A fat kill fertilizes more than a lean one, so letting a head rot is a real
	 * trade rather than a flat effect.
	 */
	public static int fertilityCharges(List<ItemStack> items) {
		int count = 0;
		for (ItemStack stack : items) count += stack.getCount();
		return Mth.clamp(1 + count / ITEMS_PER_EXTRA_CHARGE, 1, MAX_CHARGES);
	}

	public static void fertilize(ServerLevel level, BlockPos pos, int charges) {
		for (int i = 0; i < charges; i++) fertilizeOnce(level, pos);
	}

	/**
	 * Vanilla bonemeal growth, searching outward from where the head stood. The
	 * block below is the primary target; the four horizontal neighbours catch
	 * crops planted beside it; the diagonal-below ring catches grass next to a
	 * head sitting on path, stone, or any other unbonemealable ground. Without
	 * that fallback chain the effect silently no-ops over most terrain, so when
	 * nothing in range is growable the charge escapes as a visible puff instead.
	 */
	private static void fertilizeOnce(ServerLevel level, BlockPos pos) {
		if (tryGrow(level, pos.below())) return;
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			if (tryGrow(level, pos.relative(direction))) return;
		}
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			if (tryGrow(level, pos.relative(direction).below())) return;
		}
		level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, pos, 5);
	}

	private static boolean tryGrow(ServerLevel level, BlockPos pos) {
		if (BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL), level, pos)) {
			level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, pos, 15);
			return true;
		}
		return false;
	}
}
