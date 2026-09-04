<h1 align="center">TaleGuard v1.0.0</h1>

<p align="center"><b>Initial public release.</b></p>

<p align="center">
  A Mixin/transformer protection bridge for Hytale that intercepts the low-level interaction surfaces the
  normal server event API can't reach, and routes them through one simple hook so protection plugins can
  enforce their rules everywhere.
</p>

---

## Core

- Mixin/transformer bridge that hooks interaction code paths at class-load time
- Runs as an early plugin, so its transformers are in place before the server finishes booting
- Single, shared `ProtectionHook` question: "is this player allowed to do this here?"

## Protected interaction surfaces

- Block break / place / harvest, and builder-tool / hammer edits
- Container open
- Crop harvest and growth-stage changes
- Entity damage and entity interact
- Explosion block damage
- Fluid placement and flow
- Item pickup (F-key / auto) and item use
- Seating, death-item drops, item durability, and NPC spawns
- Command filtering in guarded worlds

## Developer API

- `ProtectionHook` interface (`isAllowed` / `notifyDenied`) — return false to deny
- Register against the API (`HookRegistry.registerHook`) or reflectively via the shared
  `taleguard.hook.registry` system property (zero compile-time coupling for soft dependencies)
- Optional hook priority; dedicated entry points for fluid flow and command filtering
- Soft dependency: if TaleGuard isn't installed, dependent plugins simply run without the extra coverage

## Used by

- AeroWars, Plots, and island / Skyblock protection

---

<sub>Requires a Hytale server. Install into the server's <code>earlyplugins/</code> folder.</sub>
