# Dead Heads

A Fabric mod that softens item loss on death: instead of your inventory scattering across the ground when you die, your dropped items are stored inside a player head placed at your death location.

## Features

- **Death drops become a head**: when a player dies (and would normally lose their inventory), their dropped items are stored inside a placed player head at the death location instead of scattering on the ground
- **Temporary ownership lock**: for a configurable duration after death, only the original owner can retrieve the head's contents; other players are told whose head it is and how long until it unlocks
- **Auto-unlock**: after the lock duration elapses, the head unlocks for anyone, visually turning into a skeleton skull, and the original owner is notified
- **Retrieve by right-click**: right-clicking an unlocked (or owned) head empties its stored items into the player's inventory (dropping any that don't fit) and removes the head; a non-owner also receives a head item bearing the original owner's identity
- **Breaking preserves items**: breaking someone else's locked head is blocked; breaking an unlocked/owned head (or one whose block is otherwise removed) drops its stored items as normal item drops instead of losing them
- **Persists across restarts**: death head data is saved periodically and on shutdown, and reloaded on server start, so heads and their contents survive a server restart
- **Respects `keepInventory`**: does nothing if the player is in creative/spectator mode or the `keepInventory` game rule is on
- **Configurable lock duration**: `dead-heads.properties` controls how many minutes a head stays locked to its owner (0 disables locking entirely)

## Requirements

Targets the Minecraft, Fabric Loader, Fabric API, and Java versions declared in this mod's `gradle.properties`. Check there for the exact currently-supported version.

## Installation

Install alongside its declared dependencies (see `fabric.mod.json`). This mod's behavior is entirely server-side.

## License

MIT License - see [LICENSE](LICENSE) for details.
