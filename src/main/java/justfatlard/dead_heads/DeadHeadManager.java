package justfatlard.dead_heads;

import net.minecraft.util.Prediction;
import com.mojang.authlib.GameProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;

public class DeadHeadManager {
	private static final Map<DimPos, DeadHeadEntry> entries = new HashMap<>();
	private static int tickCounter = 0;
	private static boolean dirty = false;
	private static long lastSaveTimeMs = 0;
	private static final long SAVE_INTERVAL_MS = 30_000;

	record DimPos(String dimension, BlockPos pos) {}

	/** Compasses waiting on a player, held from death until respawn. */
	private static final Map<UUID, List<ItemStack>> pendingCompasses = new ConcurrentHashMap<>();

	/**
	 * Keep a compass through the death that would otherwise take it. Anything put in a dead
	 * player's inventory goes down with them, so it waits here until they are standing again.
	 */
	private static void hold(ServerPlayer player, ItemStack compass) {
		pendingCompasses.computeIfAbsent(player.getUUID(), uuid -> new ArrayList<>()).add(compass);
	}

	/** Hand over the compasses this player died holding, plus the one for the head they left. */
	public static void onRespawn(ServerPlayer player) {
		List<ItemStack> pending = pendingCompasses.remove(player.getUUID());
		if (pending == null) return;

		for (ItemStack compass : pending) DeathCompass.give(player, compass);
	}

	private static DimPos keyFor(Level world, BlockPos pos) {
		return new DimPos(world.dimension().identifier().toString(), pos);
	}

	/**
	 * Whether a head is still standing here.
	 *
	 * <p>Answered from the record rather than from the world, deliberately: a head in an unloaded
	 * chunk is still a head, and asking the world would either force the chunk or report the head
	 * missing because nobody is nearby. A compass should not expire because its owner walked
	 * away.
	 */
	public static boolean hasHead(GlobalPos target) {
		return entries.containsKey(
			new DimPos(target.dimension().identifier().toString(), target.pos()));
	}

	static class DeadHeadEntry {
		/** Null for mob heads: they belong to nobody, exactly like the item entities they replace. */
		final UUID ownerUuid;
		final String ownerName;
		final List<Kept> items;
		final long deathTimeMs;
		final boolean mobHead;
		boolean unlocked;

		DeadHeadEntry(UUID ownerUuid, String ownerName, List<Kept> items, long deathTimeMs, boolean mobHead, boolean unlocked) {
			this.ownerUuid = ownerUuid;
			this.ownerName = ownerName;
			this.items = items;
			this.deathTimeMs = deathTimeMs;
			this.mobHead = mobHead;
			this.unlocked = unlocked;
		}

		boolean isOwner(Player player) {
			return ownerUuid != null && ownerUuid.equals(player.getUUID());
		}
	}

	/**
	 * @param items     everything the player died with, bound for the head
	 * @param compasses our own compasses, taken off the body rather than stored in it: a pointer
	 *                  buried with the thing it points at is no pointer at all
	 */
	public static void handleDeath(ServerPlayer player, List<Kept> items, List<ItemStack> compasses) {
		for (ItemStack compass : compasses) hold(player, compass);

		ServerLevel level = player.level();
		BlockPos deathPos = player.blockPosition();

		// A head is the better thing for the compass to point at, but a death always has a
		// place. The two deaths that produced no compass at all were the two where no head was
		// placed: dying empty-handed, and dying somewhere a head will not fit. Neither is a
		// moment to withhold the one item that says where you were.
		BlockPos headPos = items.isEmpty() ? null : findHeadPosition(level, deathPos, false);

		if (!items.isEmpty() && headPos == null) {
			dropItems(level, deathPos, items);
			player.sendSystemMessage(Component.literal("No room for your head here, your items dropped on the ground"));
		}

		// No head to point at, so point at the place instead, and stop here: everything below
		// builds the head and the entry that tracks it.
		if (headPos == null) {
			hold(player, DeathCompass.forPlace(level.dimension(), deathPos));
			return;
		}

		int rotation = Mth.floor((player.getYRot() * 16.0F / 360.0F) + 0.5F) & 15;
		BlockState headState = Blocks.PLAYER_HEAD.defaultBlockState().setValue(SkullBlock.ROTATION, rotation);
		level.setBlock(headPos, headState, Block.UPDATE_ALL);

		if (level.getBlockEntity(headPos) instanceof SkullBlockEntity skull) {
			ItemStack profileStack = new ItemStack(Items.PLAYER_HEAD);
			profileStack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(player.getGameProfile()));
			skull.applyComponentsFromItemStack(profileStack);
			skull.setChanged();
		}

		hold(player, DeathCompass.forHead(level.dimension(), headPos));

		entries.put(keyFor(level, headPos), new DeadHeadEntry(
			player.getUUID(),
			player.getGameProfile().name(),
			items,
			System.currentTimeMillis(),
			false,
			false
		));
		dirty = true;

		player.sendSystemMessage(Component.literal(
			"Your items are stored in your head at " + headPos.getX() + ", " + headPos.getY() + ", " + headPos.getZ()
		));
	}

	/**
	 * A player-killed mob's drops, placed as a head at the death spot instead of
	 * scattered. Unlike a player head this one is unowned and never locked (mob
	 * loot is free for all in vanilla too) and it rots on a timer, which is what
	 * keeps a mob farm from carpeting the world in skulls.
	 *
	 * If there is nowhere to put the head the drops just fall as usual: better
	 * plain vanilla than swallowed loot.
	 */
	public static void handleMobDeath(LivingEntity mob, List<ItemStack> drops) {
		if (drops.isEmpty()) return;
		// A mob was not wearing its loot in any particular slot.
		List<Kept> items = new ArrayList<>(drops.size());
		for (ItemStack stack : drops) items.add(Kept.loose(stack));
		if (!(mob.level() instanceof ServerLevel level)) return;

		BlockPos deathPos = mob.blockPosition();
		BlockPos headPos = findHeadPosition(level, deathPos, true);

		if (headPos == null) {
			dropItems(level, deathPos, items);
			return;
		}

		int rotation = Mth.floor((mob.getYRot() * 16.0F / 360.0F) + 0.5F) & 15;
		BlockState headState = MobHeads.headBlockFor(mob.getType())
			.defaultBlockState()
			.setValue(SkullBlock.ROTATION, rotation);
		level.setBlock(headPos, headState, Block.UPDATE_ALL);

		entries.put(keyFor(level, headPos), new DeadHeadEntry(
			null,
			mob.getName().getString(),
			items,
			System.currentTimeMillis(),
			true,
			true
		));
		dirty = true;
	}

	/**
	 * Armour goes back on, not in.
	 *
	 * <p>Everything left the body as one flat list, so which piece was worn is not recorded - but
	 * a helmet is a helmet, and an empty head slot on somebody who just walked back to their own
	 * corpse is where it came from. Walking home and then re-dressing out of the pack is a chore
	 * the death already charged for.
	 *
	 * <p>Only into an empty slot: anything they are wearing now they put on after dying, and
	 * swapping it for the older piece is not a favour. Slot type rather than an armour tag, so a
	 * carved pumpkin or a skull goes back to the head it was on.
	 */
	private static void handBack(ServerPlayer player, ItemStack stack) {
		EquipmentSlot slot = player.getEquipmentSlotForItem(stack);

		if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR && player.getItemBySlot(slot).isEmpty()) {
			player.setItemSlot(slot, stack);
			return;
		}

		if (!player.getInventory().add(stack)) {
			player.drop(stack, false, Prediction.SERVER_ONLY);
		}
	}

	/**
	 * Give a player their own kit back the way they were wearing it.
	 *
	 * <p>Only ever into a slot that is empty. Somebody who died naked, ran back in borrowed
	 * armour and picked up their head should not have the borrowed set silently swapped out from
	 * under them - so anything whose place is taken simply goes into the pack, which is what
	 * every death before this one did with everything.
	 *
	 * <p>Armour is put on by writing the slot rather than by equipping it, because at this point
	 * it is not a thing being put on: it is the thing that was already on, coming back.
	 */
	private static void restore(ServerPlayer player, List<Kept> items) {
		var inventory = player.getInventory();
		List<Kept> homeless = new ArrayList<>();

		for (Kept kept : items) {
			if (kept.stack().isEmpty()) continue;
			ItemStack stack = kept.stack().copy();

			if (kept.isVanillaSlot() && kept.slot() < inventory.getContainerSize()
					&& inventory.getItem(kept.slot()).isEmpty()) {
				inventory.setItem(kept.slot(), stack);
				continue;
			}

			if (kept.isExtraSlot() && ExtraSlots.putBack(player, kept.namespace(), kept.slot(), stack)) {
				continue;
			}

			homeless.add(new Kept(stack, "", Kept.NOWHERE));
		}

		// After the placed ones, so that a stack with a home does not lose it to something that
		// had none arriving first.
		for (Kept kept : homeless) handBack(player, kept.stack());

		inventory.setChanged();

		// And now that everything is back, tidy what could not go home. Only for the owner, and
		// only ever their own head: sorting somebody's pack because they looted a stranger's
		// skull would be a rummage they did not ask for.
		PackSorting.sort(player);
	}

	private static void dropItems(ServerLevel level, BlockPos pos, List<Kept> items) {
		for (Kept kept : items) {
			if (!kept.stack().isEmpty()) Block.popResource(level, pos, kept.stack().copy());
		}
	}

	/** The items alone, for the things that only care what is in a head and not where it was. */
	private static List<ItemStack> stacksOf(List<Kept> items) {
		List<ItemStack> stacks = new ArrayList<>(items.size());
		for (Kept kept : items) stacks.add(kept.stack());
		return stacks;
	}

	public static InteractionResult onUseBlock(Player player, Level world, InteractionHand hand, BlockHitResult hitResult) {
		if (world.isClientSide() || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;

		BlockPos pos = hitResult.getBlockPos();
		DimPos key = keyFor(world, pos);
		DeadHeadEntry entry = entries.get(key);
		if (entry == null) return InteractionResult.PASS;

		boolean isOwner = entry.isOwner(player);

		if (!entry.unlocked && !isOwner) {
			long remainingMs = DeadHeadsConfig.getLockDurationMs() - (System.currentTimeMillis() - entry.deathTimeMs);
			long remainingSec = Math.max(1, remainingMs / 1000);
			String timeStr = remainingSec >= 60
				? (remainingSec / 60) + "m " + (remainingSec % 60) + "s"
				: remainingSec + "s";
			player.sendSystemMessage(Component.literal(
				"This head belongs to " + entry.ownerName + ". Unlocks in " + timeStr
			));
			return InteractionResult.FAIL;
		}

		ServerPlayer serverPlayer = (ServerPlayer) player;

		if (isOwner) {
			restore(serverPlayer, entry.items);
		} else {
			for (Kept kept : entry.items) {
				if (!kept.stack().isEmpty()) handBack(serverPlayer, kept.stack().copy());
			}
		}

		// The consolation trophy is a player-head thing: a mob head has no owner
		// to bear, and handing out a skull per looted mob would be a free supply
		if (!isOwner && !entry.mobHead) {
			ItemStack headItem = new ItemStack(Items.PLAYER_HEAD);
			headItem.set(DataComponents.PROFILE, ResolvableProfile.createResolved(
				new GameProfile(entry.ownerUuid, entry.ownerName)
			));
			if (!serverPlayer.getInventory().add(headItem)) {
				serverPlayer.drop(headItem, false, Prediction.SERVER_ONLY);
			}
		}

		world.removeBlock(pos, false);
		entries.remove(key);
		dirty = true;

		// The head is gone, so the compass that pointed at it is spent. Only this player's, and
		// only now: everybody else's goes on the next sweep, which is the same rule reaching
		// them a second later.
		DeathCompass.settle(serverPlayer);

		return InteractionResult.SUCCESS;
	}

	public static boolean onBlockBreakBefore(Level world, Player player, BlockPos pos, BlockState state, BlockEntity entity) {
		if (world.isClientSide()) return true;

		DeadHeadEntry entry = entries.get(keyFor(world, pos));
		if (entry == null) return true;

		if (!entry.unlocked && !entry.isOwner(player)) {
			player.sendSystemMessage(Component.literal("This head belongs to " + entry.ownerName));
			return false;
		}

		return true;
	}

	public static void onBlockBreakAfter(Level world, Player player, BlockPos pos, BlockState state, BlockEntity entity) {
		if (world.isClientSide()) return;

		DeadHeadEntry entry = entries.remove(keyFor(world, pos));
		if (entry == null) return;

		dropItems((ServerLevel) world, pos, entry.items);
		dirty = true;

		// Broken rather than harvested, but the head is just as gone.
		if (player instanceof ServerPlayer serverBreaker) {
			DeathCompass.settle(serverBreaker);
		}
	}

	public static void tick(MinecraftServer server) {
		if (++tickCounter < 20) return;
		tickCounter = 0;

		// Once a second. A head can stop existing without the player who holds its compass being
		// anywhere near it - somebody else harvested it, or it rotted - so the compass cannot
		// wait for them to do something before it notices.
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			DeathCompass.settle(player);
		}

		long now = System.currentTimeMillis();
		long lockDuration = DeadHeadsConfig.getLockDurationMs();
		long decayDuration = DeadHeadsConfig.getMobHeadDecayMs();

		Iterator<Map.Entry<DimPos, DeadHeadEntry>> iter = entries.entrySet().iterator();
		while (iter.hasNext()) {
			Map.Entry<DimPos, DeadHeadEntry> mapEntry = iter.next();
			DimPos dimPos = mapEntry.getKey();
			DeadHeadEntry entry = mapEntry.getValue();

			ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimPos.dimension()));
			ServerLevel level = server.getLevel(dimKey);
			if (level == null) continue;

			// getBlockState force-loads the chunk, and with mob heads there can be
			// a lot of these, so heads in unloaded chunks wait their turn instead
			if (!level.hasChunkAt(dimPos.pos())) continue;

			BlockState state = level.getBlockState(dimPos.pos());
			boolean isSkull = state.getBlock() instanceof SkullBlock;

			if (!isSkull) {
				dropItems(level, dimPos.pos(), entry.items);
				iter.remove();
				dirty = true;
				continue;
			}

			// A mob head rots: the loot is consumed and the ground gets it
			// instead. Take the kill before it turns, or feed the garden.
			if (entry.mobHead) {
				if (decayDuration > 0 && (now - entry.deathTimeMs) >= decayDuration) {
					int charges = MobHeads.fertilityCharges(stacksOf(entry.items));
					level.removeBlock(dimPos.pos(), false);
					MobHeads.fertilize(level, dimPos.pos(), charges);
					iter.remove();
					dirty = true;
				}
				continue;
			}

			if (!entry.unlocked && lockDuration > 0 && (now - entry.deathTimeMs) >= lockDuration) {
				entry.unlocked = true;
				int rotation = state.getValue(SkullBlock.ROTATION);
				level.setBlock(dimPos.pos(), Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, rotation), Block.UPDATE_ALL);
				dirty = true;

				ServerPlayer owner = server.getPlayerList().getPlayer(entry.ownerUuid);
				if (owner != null) {
					owner.sendSystemMessage(Component.literal(
						"Your death head at " + dimPos.pos().getX() + ", " + dimPos.pos().getY() + ", " + dimPos.pos().getZ() + " is now unlocked"
					));
				}
			}
		}

		if (dirty) {
			long now2 = System.currentTimeMillis();
			if (now2 - lastSaveTimeMs >= SAVE_INTERVAL_MS) {
				save(server);
				dirty = false;
				lastSaveTimeMs = now2;
			}
		}
	}

	public static void onServerStarted(MinecraftServer server) {
		load(server);
	}

	public static void onServerStopping(MinecraftServer server) {
		if (dirty) {
			save(server);
			dirty = false;
		}
		entries.clear();
	}

	private static Path getDataPath(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("dead-heads.dat");
	}

	private static void load(MinecraftServer server) {
		Path path = getDataPath(server);
		if (!Files.exists(path)) return;

		HolderLookup.Provider registries = server.registryAccess();

		try {
			CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.defaultQuota());
			ListTag list = root.getListOrEmpty("entries");

			entries.clear();
			for (int i = 0; i < list.size(); i++) {
				if (!(list.get(i) instanceof CompoundTag tag)) continue;

				String dimension = tag.getStringOr("dimension", "");
				BlockPos pos = new BlockPos(tag.getIntOr("x", 0), tag.getIntOr("y", 0), tag.getIntOr("z", 0));
				boolean mobHead = tag.getBooleanOr("mobHead", false);
				UUID ownerUuid = mobHead
					? null
					: new UUID(tag.getLongOr("uuidMost", 0L), tag.getLongOr("uuidLeast", 0L));
				String ownerName = tag.getStringOr("ownerName", "");
				long deathTime = tag.getLongOr("deathTime", 0L);
				boolean unlocked = tag.getBooleanOr("unlocked", false);

				List<Kept> items = new ArrayList<>();
				ListTag itemList = tag.getListOrEmpty("items");
				for (int j = 0; j < itemList.size(); j++) {
					if (!(itemList.get(j) instanceof CompoundTag itemNbt)) continue;

					// Written before this mod recorded where things were: everything in an older
					// head comes back the way it always did, in the first free square.
					String namespace = itemNbt.getStringOr("ns", "");
					int slot = itemNbt.getIntOr("slot", Kept.NOWHERE);

					itemNbt.read(ItemStack.MAP_CODEC, registries.createSerializationContext(NbtOps.INSTANCE))
						.ifPresent(stack -> items.add(new Kept(stack, namespace, slot)));
				}

				entries.put(new DimPos(dimension, pos), new DeadHeadEntry(ownerUuid, ownerName, items, deathTime, mobHead, unlocked));
			}

			Main.LOGGER.info("[{}] Loaded {} death heads", Main.MOD_ID, entries.size());
		} catch (IOException e) {
			Main.LOGGER.error("[{}] Failed to load death head data: {}", Main.MOD_ID, e.getMessage());
		}
	}

	static void save(MinecraftServer server) {
		Path path = getDataPath(server);
		HolderLookup.Provider registries = server.registryAccess();

		CompoundTag root = new CompoundTag();
		ListTag list = new ListTag();

		for (Map.Entry<DimPos, DeadHeadEntry> mapEntry : entries.entrySet()) {
			DimPos dimPos = mapEntry.getKey();
			DeadHeadEntry entry = mapEntry.getValue();

			CompoundTag tag = new CompoundTag();
			tag.putString("dimension", dimPos.dimension());
			tag.putInt("x", dimPos.pos().getX());
			tag.putInt("y", dimPos.pos().getY());
			tag.putInt("z", dimPos.pos().getZ());
			if (entry.ownerUuid != null) {
				tag.putLong("uuidMost", entry.ownerUuid.getMostSignificantBits());
				tag.putLong("uuidLeast", entry.ownerUuid.getLeastSignificantBits());
			}
			tag.putString("ownerName", entry.ownerName);
			tag.putLong("deathTime", entry.deathTimeMs);
			tag.putBoolean("mobHead", entry.mobHead);
			tag.putBoolean("unlocked", entry.unlocked);

			ListTag itemList = new ListTag();
			for (Kept kept : entry.items) {
				if (kept.stack().isEmpty()) continue;

				CompoundTag itemNbt = new CompoundTag();
				itemNbt.store(ItemStack.MAP_CODEC, registries.createSerializationContext(NbtOps.INSTANCE), kept.stack());
				if (kept.slot() != Kept.NOWHERE) itemNbt.putInt("slot", kept.slot());
				if (!kept.namespace().isEmpty()) itemNbt.putString("ns", kept.namespace());
				itemList.add(itemNbt);
			}
			tag.put("items", itemList);

			list.add(tag);
		}

		root.put("entries", list);

		try {
			Files.createDirectories(path.getParent());
			NbtIo.writeCompressed(root, path);
		} catch (IOException e) {
			Main.LOGGER.error("[{}] Failed to save death head data: {}", Main.MOD_ID, e.getMessage());
		}
	}

	/**
	 * The death spot, or the first replaceable block above it.
	 *
	 * A tracked head is never a candidate: two deaths in the same place would
	 * otherwise overwrite the first head and take its contents with it, which is
	 * rare for players and routine at a mob grinder.
	 *
	 * requireFree decides what happens when nothing is available. Mob heads give
	 * up and let the drops fall as vanilla would. A player death falls back to
	 * replacing whatever is at the death spot, as it always has, because
	 * scattering a full inventory is the thing this mod exists to prevent.
	 */
	private static BlockPos findHeadPosition(ServerLevel level, BlockPos deathPos, boolean requireFree) {
		int minY = level.getMinY();
		int maxY = level.getMaxY();

		int x = deathPos.getX();
		int y = Mth.clamp(deathPos.getY(), minY + 1, maxY - 1);
		int z = deathPos.getZ();

		for (int dy = 0; dy <= 10; dy++) {
			BlockPos candidate = new BlockPos(x, Math.min(y + dy, maxY - 1), z);
			if (entries.containsKey(keyFor(level, candidate))) continue;

			BlockState at = level.getBlockState(candidate);
			// Replaceable is not enough: water is replaceable, and a skull has no waterlogged
			// state, so the fluid flows straight back in and destroys it - measured at under two
			// seconds. The head went, the entry stayed, and the items inside it went with it.
			// Climbing past the fluid puts the head at the surface, which is further from the
			// body than ideal and infinitely closer than gone.
			if (at.canBeReplaced() && at.getFluidState().isEmpty()) return candidate;
		}

		if (requireFree) return null;

		// The fallback drowns for the same reason, so it is only a fallback on dry land.
		BlockPos fallback = new BlockPos(x, y, z);
		if (!level.getBlockState(fallback).getFluidState().isEmpty()) return null;
		return entries.containsKey(keyFor(level, fallback)) ? null : fallback;
	}
}
