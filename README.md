# Dead Heads

A Minecraft Fabric mod. Dying leaves a head where you fell, holding everything you had, and a compass that knows the way back.

## What This Mod Does

Vanilla scatters your inventory across the ground and starts a five-minute timer on it. Everything about that is hostile: the pile drifts, half of it is in lava, and the clock runs whether or not you know where you are.

Here your things go **into your head** — a real player head, wearing your face, placed at the spot. Nothing despawns. Nothing scatters. The head sits there until somebody empties it, and for the first few minutes that somebody can only be you.

You also respawn holding a compass pointed at it, because knowing your things are safe is not the same as knowing where they are.

Optionally, mobs do the same: one killed by a player leaves its drops in a head at the spot it fell, and that head rots if nobody claims it.

## Getting Your Things Back

Right-click the head. It empties and disappears.

**Back where they were**, not into the first free square. The helmet goes on your head, the shield to your offhand, the pickaxe under the key it was under. Handed back as a heap — everything present, none of it anywhere — recovery would start with two minutes of putting your own kit on again.

Only ever into a slot that is **empty**. If you died naked, ran back in borrowed armour and picked up your head, the borrowed set is not swapped out from under you; anything whose place is taken goes into the pack instead, the way it always did.

Where [Chest Utils](https://github.com/fatlard1993/chest-utils) is installed, your pack is sorted afterwards — with your hotbar lock honoured, because that mod holds your answer to what tidy means. Only when you collect **your own** head. Looting a stranger's skull does not rummage through your pack.

## The Death Compass

You respawn with a compass aimed at your head. It is an ordinary compass carrying a lodestone target, so there is no new item, no model, and nothing client-side.

**It is pinned to one head, not to your last death.** Vanilla's recovery compass forgets the body you were walking to the moment you die again on the way. This one does not: a second death hands you a second compass and the first still points where it pointed.

**It is spent when its head is gone** — harvested by you, looted by somebody else, broken, blown up, or rotted where it stood. That is the moment it stops pointing at anything worth walking to, and it is the same moment for everyone carrying one. You are told when it happens.

Walking to the place is not enough. Arriving and *not* picking the head up is a thing people do — hands full, wrong tools, coming back with a shulker — and the compass that got you there should still be there for the second trip.

The one exception is a death that left no head at all, somewhere there was no room for one. That compass points at bare ground nothing will ever harvest, so arriving is the only end it can have.

Being handed your first one earns **Retracing Your Steps**.

## Where The Compass Lives

It is put somewhere you will look rather than a square in the pack: [Map++](https://github.com/fatlard1993/map-plus-plus)'s compass slot if you have that mod, otherwise your offhand, then the pack, then the floor. An ordinary compass already in the slot is moved down into the pack rather than thrown away.

It is also lifted **out** of that slot when you die, because the slot's store is inventory like any other and a compass buried in the head it points at is no compass at all.

## Whose Head It Is

For a configurable while after you die, only you can empty it. Anybody else is told whose head it is and how long is left.

After that it unlocks for anyone, and visibly: it turns into a skeleton skull, and you are told it has. Somebody who empties a head that is not theirs also gets a player head bearing the owner's face, which is the only consolation this mod offers and about the right amount.

Breaking a locked head is refused. Breaking an unlocked one pops its contents out as ordinary drops rather than losing them, and so does anything else that removes the block.

## Mob Heads

Off by default. `/gamerule dead-heads:mob_heads true` per world, or `mob_heads_default` in the config for new ones.

- **Player kills only.** A mob that drowns, burns, falls, or is killed by another mob behaves exactly as vanilla, which is how vanilla already gates most loot.
- **The head matches the mob** where vanilla has one — zombie, creeper, piglin, skeleton, wither skeleton — and everything else gets a plain skeleton skull. No custom skins, so nothing is needed client-side.
- **No owner and no lock**, exactly like the item entities they replace.
- **Nothing dropped means no head.**
- **Bosses are excluded.** A unique drop on a decay timer is a bad trade.
- **Experience is untouched.** XP orbs drop and behave normally.
- **They rot.** An unclaimed mob head disappears after a while and its contents are consumed rather than dropped — the ground around it gets a bonemeal growth instead, scaled to how much was inside. Take the kill before it turns, or feed the garden.

Player death heads never rot, and none of this touches them.

## Details Worth Knowing

- **It respects `keepInventory`.** Creative, spectator, or the game rule on, and this mod does nothing at all.
- **Two deaths cannot share a spot.** A tracked head is never a candidate position for the next one, which is rare for players and routine at a mob grinder.
- **Extra inventory slots come too.** Slots other mods add to the inventory screen are emptied into the head with everything else, and put back on recovery. Left alone they would be neither dropped nor kept: the store behind them is not carried across a respawn, and an equipped map would simply stop existing.
- **Heads in unloaded chunks are still heads.** The record, not the world, is what a compass asks — so a compass does not expire because its owner walked away.
- **It survives restarts.** Head data is saved periodically and on shutdown, and reloaded on start.
- **Older heads still open.** Ones written before this mod recorded slots hand their contents back the way they always did, in the first free square.

## Configuration

`config/dead-heads.properties`:

| Key | Default | |
|---|---|---|
| `lock_duration_minutes` | 5 | How long a head stays yours. 0 disables locking |
| `mob_heads_default` | false | Starting value of the game rule for new worlds |
| `mob_head_decay_seconds` | 600 | How long an unclaimed mob head lasts. 0 keeps them forever |

The decay timer is wall-clock, so mob heads age while the server is offline.

## Pandorical

Dead Heads runs server-side and works on a vanilla client. [Pandorical](https://github.com/fatlard1993/pandorical) is optional, and used for one thing: telling the client that a right-click on a player head is the server's to answer. Without that the client predicts a block placement, the server opens the head instead, and the skull flickers in and out with the stack count briefly wrong.

Map++ and Chest Utils integrations are likewise optional and guarded — absent, their features simply are not there.

## Source Map

| File | What is in it |
|---|---|
| `DeadHeadManager.java` | Every tracked head: placing, opening, breaking, decay, persistence |
| `DeathCompass.java` | Making a compass, giving it, and taking it back when it is spent |
| `Kept.java` | One item and the slot it came out of |
| `ExtraSlots.java` | Emptying and refilling the slots other mods add |
| `MobHeads.java` | Which head a mob gets, and what its loot fertilises |
| `PackSorting.java` | The guard in front of the Chest Utils sort |
| `integration/CompassSlot.java` | Map++'s compass slot |
| `integration/ChestUtilsSort.java` | Chest Utils' sort |
| `integration/HeadInteraction.java` | Telling a Pandorical client the right-click is ours |
| `mixin/ServerPlayerDeathMixin.java` | Taking the inventory, slot indices and all, before vanilla scatters it |
| `mixin/LivingEntityDeathLootMixin.java` | The window around a mob's death loot |
| `mixin/EntityDropCaptureMixin.java` | Swallowing drops inside that window |

## Installation

Install server-side alongside its declared dependencies (see `fabric.mod.json`). Vanilla clients need nothing. Version targets live in `gradle.properties` (Minecraft, loader, Fabric API) and `fabric.mod.json` (Java).

## License

MIT, see [LICENSE](LICENSE).
