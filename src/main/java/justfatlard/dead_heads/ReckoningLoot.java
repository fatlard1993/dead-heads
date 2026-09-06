package justfatlard.dead_heads;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetComponentsFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.core.Holder;
import net.minecraft.world.level.storage.loot.providers.number.ints.ConstantValue;

/**
 * A Potion of Dead Reckoning in the chests worth opening.
 *
 * <p>Brewing one takes a chorus fruit, which takes the End, which is a long way from the first
 * death that needs one. The chests of the places people die in - dungeons, mineshafts,
 * strongholds, the far cities - carry one now and then, so a bottle can be found before it can
 * be made. One in five chests, one bottle: a find, not a supply.
 *
 * <p>The bottle is the same one the recipe makes, component for component, built here in code
 * only because the game offers no way to add to its own chests from a datapack.
 */
public final class ReckoningLoot {
	private ReckoningLoot() {}

	private static final float CHANCE = 0.2F;
	private static final int COLOUR = 8077216;

	private static final Set<ResourceKey<LootTable>> CHESTS = Set.of(
		BuiltInLootTables.SIMPLE_DUNGEON, BuiltInLootTables.ABANDONED_MINESHAFT,
		BuiltInLootTables.STRONGHOLD_CORRIDOR, BuiltInLootTables.STRONGHOLD_CROSSING, BuiltInLootTables.STRONGHOLD_LIBRARY,
		BuiltInLootTables.DESERT_PYRAMID, BuiltInLootTables.JUNGLE_TEMPLE, BuiltInLootTables.IGLOO_CHEST,
		BuiltInLootTables.PILLAGER_OUTPOST, BuiltInLootTables.WOODLAND_MANSION,
		BuiltInLootTables.SHIPWRECK_TREASURE, BuiltInLootTables.BURIED_TREASURE, BuiltInLootTables.RUINED_PORTAL,
		BuiltInLootTables.NETHER_BRIDGE, BuiltInLootTables.BASTION_TREASURE, BuiltInLootTables.BASTION_OTHER,
		BuiltInLootTables.END_CITY_TREASURE, BuiltInLootTables.ANCIENT_CITY,
		BuiltInLootTables.TRIAL_CHAMBERS_REWARD, BuiltInLootTables.TRIAL_CHAMBERS_REWARD_RARE);

	public static void register() {
		LootTableEvents.MODIFY.register((key, table, source, registries) -> {
			if (!source.isBuiltin() || !CHESTS.contains(key)) return;
			table.withPool(pool());
		});
	}

	private static LootPool.Builder pool() {
		CompoundTag mark = new CompoundTag();
		mark.putBoolean("dead_heads_reckoning", true);
		return LootPool.lootPool()
			.setRolls(Holder.direct(new ConstantValue(1)))
			.when(LootItemRandomChanceCondition.randomChance(CHANCE))
			.add(LootItem.lootTableItem(Items.POTION)
				.apply(SetComponentsFunction.setComponent(DataComponents.POTION_CONTENTS, new PotionContents(
					Optional.empty(), Optional.of(COLOUR),
					List.of(new MobEffectInstance(MobEffects.INSTANT_HEALTH, 1)), Optional.empty())))
				.apply(SetComponentsFunction.setComponent(DataComponents.CUSTOM_NAME,
					Component.literal("Potion of Dead Reckoning").withStyle(style -> style.withItalic(false))))
				.apply(SetComponentsFunction.setComponent(DataComponents.LORE, new ItemLore(List.of(
					Component.literal("Drink to return to where you died").withStyle(style -> style.withItalic(false).withColor(net.minecraft.ChatFormatting.GRAY))))))
				.apply(SetComponentsFunction.setComponent(DataComponents.CUSTOM_DATA, CustomData.of(mark))));
	}
}
