# Dead Heads - Development Guide

For what the mod is and how it plays, see [README.md](README.md).

## Source Map

| File | What is in it |
|---|---|
| `DeadHeadManager.java` | Every tracked head: placing, opening, breaking, decay, persistence |
| `DeathCompass.java` | Making a compass, giving it, and taking it back when it is spent |
| `Kept.java` | One item and the slot it came out of |
| `Soulbound.java` | The enchantment, and carrying what wears it across the respawn |
| `DeadReckoning.java` | The potion: which head is next, and getting you there |
| `ReckoningLoot.java` | The potion in structure chests |
| `DeadHeadCommands.java` | `/deadheads tp` and `where`, for ops |
| `DeadHeadsConfig.java` | The properties file, and its page in Pandorical's mod menu |
| `ExtraSlots.java` | Emptying and refilling the slots other mods add |
| `MobHeads.java` | Which head a mob gets, and what its loot fertilises |
| `PackSorting.java` | The guard in front of the Chest Utils sort |
| `integration/CompassSlot.java` | Map++'s compass slot |
| `integration/ChestUtilsSort.java` | Chest Utils' sort |
| `integration/HeadInteraction.java` | Telling a Pandorical client the right-click is ours |
| `integration/PandoricalSlots.java` | The Pandorical half of `ExtraSlots` |
| `mixin/ServerPlayerDeathMixin.java` | Taking the inventory, slot indices and all, before vanilla scatters it; putting the soulbound back after |
| `mixin/DrinkMixin.java` | The last swallow of a potion |
| `mixin/LivingEntityDeathLootMixin.java` | The window around a mob's death loot |
| `mixin/EntityDropCaptureMixin.java` | Swallowing drops inside that window |
| `mixin/ExplosionProtectionMixin.java` | Taking locked heads out of a blast's list |
| `mixin/HeadKeepsFluidOutMixin.java` | Water and lava flowing around a player's head |

## Installation

Install server-side alongside its declared dependencies (see `fabric.mod.json`). Vanilla clients need nothing. Version targets live in `gradle.properties` (Minecraft, loader, Fabric API) and `fabric.mod.json` (Java).
