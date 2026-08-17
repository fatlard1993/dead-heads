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

	/** A head a player has yet to be pointed at, held from death until respawn. */
	private record PendingCompass(ResourceKey<Level> dimension, BlockPos pos) {}

	private static final Map<UUID, PendingCompass> pendingCompass = new ConcurrentHashMap<>();

	/** Hand over the compass for the head this player just left behind. */
	public static void onRespawn(ServerPlayer player) {
		PendingCompass pending = pendingCompass.remove(player.getUUID());
		if (pending == null) return;

		DeathCompass.give(player, pending.dimension(), pending.pos());
	}

	private static DimPos keyFor(Level world, BlockPos pos) {
		return new DimPos(world.dimension().identifier().toString(), pos);
	}

	static class DeadHeadEntry {
		/** Null for mob heads: they belong to nobody, exactly like the item entities they replace. */
		final UUID ownerUuid;
		final String ownerName;
		final List<ItemStack> items;
		final long deathTimeMs;
		final boolean mobHead;
		boolean unlocked;

		DeadHeadEntry(UUID ownerUuid, String ownerName, List<ItemStack> items, long deathTimeMs, boolean mobHead, boolean unlocked) {
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

	public static void handleDeath(ServerPlayer player, List<ItemStack> items) {
		if (items.isEmpty()) return;

		ServerLevel level = player.level();
		BlockPos deathPos = player.blockPosition();
		BlockPos headPos = findHeadPosition(level, deathPos, false);

		if (headPos == null) {
			dropItems(level, deathPos, items);
			player.sendSystemMessage(Component.literal("No room for your head here, your items dropped on the ground"));
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

		// Handed over at respawn rather than now: the player is dead, and anything
		// put in their inventory here goes down with them.
		pendingCompass.put(player.getUUID(), new PendingCompass(level.dimension(), headPos));

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
	public static void handleMobDeath(LivingEntity mob, List<ItemStack> items) {
		if (items.isEmpty()) return;
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

	private static void dropItems(ServerLevel level, BlockPos pos, List<ItemStack> items) {
		for (ItemStack stack : items) {
			if (!stack.isEmpty()) Block.popResource(level, pos, stack.copy());
		}
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

		for (ItemStack stack : entry.items) {
			if (!stack.isEmpty()) {
				if (!serverPlayer.getInventory().add(stack.copy())) {
					serverPlayer.drop(stack.copy(), false, Prediction.SERVER_ONLY);
				}
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

		// The compass has done its job the moment the head is empty.
		if (player instanceof ServerPlayer serverCollector) {
			DeathCompass.reclaim(serverCollector, pos);
		}

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

		// Broken rather than used, but the head is just as gone.
		if (player instanceof ServerPlayer serverBreaker) {
			DeathCompass.reclaim(serverBreaker, pos);
		}
	}

	public static void tick(MinecraftServer server) {
		if (++tickCounter < 20) return;
		tickCounter = 0;

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
					int charges = MobHeads.fertilityCharges(entry.items);
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

				List<ItemStack> items = new ArrayList<>();
				ListTag itemList = tag.getListOrEmpty("items");
				for (int j = 0; j < itemList.size(); j++) {
					if (itemList.get(j) instanceof CompoundTag itemNbt) {
						itemNbt.read(ItemStack.MAP_CODEC, registries.createSerializationContext(NbtOps.INSTANCE))
							.ifPresent(items::add);
					}
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
			for (ItemStack stack : entry.items) {
				if (!stack.isEmpty()) {
					CompoundTag itemNbt = new CompoundTag();
					itemNbt.store(ItemStack.MAP_CODEC, registries.createSerializationContext(NbtOps.INSTANCE), stack);
					itemList.add(itemNbt);
				}
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
			if (level.getBlockState(candidate).canBeReplaced()) return candidate;
		}

		if (requireFree) return null;

		BlockPos fallback = new BlockPos(x, y, z);
		return entries.containsKey(keyFor(level, fallback)) ? null : fallback;
	}
}
