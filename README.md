# Dead Heads

A Fabric mod that softens item loss on death: instead of your inventory scattering across the ground when you die, your dropped items are stored inside a player head placed at your death location.

Optionally, mobs do the same: a mob killed by a player leaves its drops inside a head at the spot it fell, and that head rots away if nobody claims it.

## Features

- **Death drops become a head**: when a player dies (and would normally lose their inventory), their dropped items are stored inside a placed player head at the death location instead of scattering on the ground
- **Temporary ownership lock**: for a configurable duration after death, only the original owner can retrieve the head's contents; other players are told whose head it is and how long until it unlocks
- **Auto-unlock**: after the lock duration elapses, the head unlocks for anyone, visually turning into a skeleton skull, and the original owner is notified
- **Retrieve by right-click**: right-clicking an unlocked (or owned) head empties its stored items into the player's inventory (dropping any that don't fit) and removes the head; a non-owner also receives a head item bearing the original owner's identity
- **Breaking preserves items**: breaking someone else's locked head is blocked; breaking an unlocked/owned head (or one whose block is otherwise removed) drops its stored items as normal item drops instead of losing them
- **Persists across restarts**: death head data is saved periodically and on shutdown, and reloaded on server start, so heads and their contents survive a server restart
- **Respects `keepInventory`**: does nothing if the player is in creative/spectator mode or the `keepInventory` game rule is on
- **Configurable lock duration**: `dead-heads.properties` controls how many minutes a head stays locked to its owner (0 disables locking entirely)

## Mob heads

Off by default. Turn it on per world with `/gamerule dead-heads:mob_heads true`, or change the starting value for new worlds in `dead-heads.properties`.

- **One head instead of a pile of drops**: a mob killed by a player leaves a head block at the spot it fell, holding everything it would have dropped
- **Player kills only**: a mob that drowns, burns, falls, or is killed by another mob behaves exactly as vanilla, which is also how vanilla already gates most loot
- **The head matches the mob** where vanilla has one (zombie, creeper, piglin, skeleton, wither skeleton); every other mob gets a plain skeleton skull. No custom skins are involved, so nothing is needed client-side
- **Experience is untouched**: XP orbs still drop and behave normally
- **Nothing dropped means no head**: a mob that had no drops leaves nothing behind
- **Bosses are excluded**: the wither and the ender dragon keep their vanilla drops, since a unique drop on a decay timer is a bad trade
- **Anyone can claim it**: mob heads have no owner and no lock, exactly like the item entities they replace. Right-click to take the contents, or break the head to pop them out as ordinary drops
- **Heads rot**: an unclaimed mob head disappears after a while. Its contents are consumed rather than dropped, and the ground around it gets a bonemeal growth instead, scaled to how much was inside. Take the kill before it turns, or feed the garden
- **Configurable decay**: `mob_head_decay_seconds` in `dead-heads.properties`, defaulting to twice the vanilla item despawn time (0 keeps mob heads forever). The timer is wall-clock, so heads also age while the server is offline

Player death heads are unaffected by any of this: they keep their own lock, retrieval, and persistence rules and never rot.

## Installation

Install server-side alongside its declared dependencies (see `fabric.mod.json`). Vanilla clients need nothing. Version targets live in `gradle.properties` (Minecraft, loader, Fabric API) and `fabric.mod.json` (Java).

## License

MIT, see [LICENSE](LICENSE).
