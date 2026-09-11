# TaleGuard — Changelog

## v1.1.0

Hytale 0.6 support: every guard re-pointed at the 0.6.5 server, a new guard for player block damage, and a boot-time check that tells you which guards are live.

### ⚠️ Requires Hytale 0.6

- The jar is `TaleGuard-1.1.0-hytale-0.6.jar` and declares `ServerVersion` `>=0.6.0 <0.7.0`. Mixins bind to exact server descriptors, so a server on another line now refuses the jar instead of loading guards that would silently not apply. Still on 0.5? Stay on **1.0.0**.
- Install into `earlyplugins/`. A server without an interactive console must be started with `--accept-early-plugins`.

### Guards ported to 0.6

All 28 redirects across 18 mixins bind against Hytale 0.6.5 — checked against the server's bytecode and loaded on a live 0.6.5 server.

- **Crop harvest** — `FarmingUtil.harvest` takes the chunk-store accessor first and no longer gets the World, block type or rotation.
- **Crop stage change, block state change, hammer** — these interactions now resolve the block through its chunk section and abort when that lookup comes back empty; the guard sits on that lookup.
- **Builder tools** — paste, extrude, line and stack queue through the new `addToQueue` overload.
- **Block pickup** — `removeBlock` now receives the block's section, which a denial invalidates directly.
- **Item pickup** — the closest-player lookup is gone; the list of players in range (now a `Vector3dc` query) is filtered instead.
- **Explosions** — guarded where `ExplosionUtils` damages each block. 0.6 passes no tool id there, so the old "tool id contains explosion" test could never match again.
- **New: player block damage** — mining through `BreakBlockInteraction`, single blocks and area tools. Area tools only damage the blocks the hook allows.
- Removed the item-durability mixin: it was never registered, and its target method is gone in 0.6.

### Checks itself at boot

- Every redirect is looked up in the running server's code at boot, and the log says `all 28 redirect(s) match this server` — or names each guard that won't apply. The result is published as `taleguard.redirects.audit` and `taleguard.redirects.broken`.
- The check now runs from the early plugin itself. It used to live in the plugin entry point, which the server only starts when the jar is also in `mods/` — so on a normal `earlyplugins/` install it never ran.
- The list of mixins it checks is read from `taleguard.mixin.json` instead of a hand-kept copy.

### Also

- Guards that had stopped binding on 0.5.6 were repaired before the port (renamed BuilderTools handlers, `harvest` returning boolean, NPC spawns taking `Vector3dc`/`Rotation3fc`, fluid spread now guarded on its delegation).
- The manifest's `Version` is expanded (1.0.0 shipped a literal `${version}`), and the dead `ID` key is gone: the server identifies plugins as `Stoshe:TaleGuard`, so declare that in `OptionalDependencies` (`Stoshe:stoshe.taleguard` never matched).
- Mod icon (`icon-256.png`, shown in a world's Mods tab) and Stoshe Labs as the author.
- README: the protection table now lists the exact `type` each surface sends to your hook.

## v1.0.0

Initial public release. A Mixin/transformer protection bridge for Hytale that intercepts the low-level interaction surfaces the normal server event API can't reach, and routes them through one simple hook so protection plugins can enforce their rules everywhere.

### Core

- Mixin/transformer bridge that hooks interaction code paths at class-load time
- Runs as an early plugin, so its transformers are in place before the server finishes booting
- Single, shared `ProtectionHook` question: "is this player allowed to do this here?"

### Protected interaction surfaces

- Block break / place / harvest, and builder-tool / hammer edits
- Crop harvest and growth-stage changes
- Explosion block damage
- Fluid placement and flow
- Item pickup
- Seating, death-item drops and NPC spawns
- Command filtering in guarded worlds

### Developer API

- `ProtectionHook` interface (`isAllowed` / `notifyDenied`) — return false to deny
- Register against the API (`HookRegistry.registerHook`) or reflectively via the shared `taleguard.hook.registry` system property (zero compile-time coupling for soft dependencies)
- Optional hook priority; dedicated entry points for fluid flow and command filtering
- Soft dependency: if TaleGuard isn't installed, dependent plugins simply run without the extra coverage
