<p align="center">
  <img src="https://github.com/stoshelabs/taleguard/blob/main/taleguard.png?raw=true" alt="TaleGuard" width="720">
</p>

<p align="center">
  <b>A Mixin/transformer protection bridge for Hytale.</b><br>
  Intercepts the low-level interaction surfaces the normal server event API can't reach, and routes them
  through one simple hook so protection plugins — regions, islands, minigames — can enforce their rules
  <i>everywhere</i>.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Hytale-early%20plugin-8A2BE2?style=for-the-badge" alt="Hytale early plugin">
  <img src="https://img.shields.io/badge/version-1.1.0-2ea44f?style=for-the-badge" alt="version 1.1.0">
  <img src="https://img.shields.io/badge/Hytale%20server-%3E%3D0.6.0%20%3C0.7.0-1E90FF?style=for-the-badge" alt="Hytale server >=0.6.0 <0.7.0">
  <img src="https://img.shields.io/badge/type-Mixin%20bridge-FF8C00?style=for-the-badge" alt="Mixin bridge">
</p>

---

## What it is

Hytale's high-level events cover block break / place, but a lot of gameplay interaction happens through
paths a plugin can't cancel from an event: auto item pickup, fluid placement and flow, seating, crop
harvesting, hammer / builder tools, explosions, death-item drops, mob spawns, and commands.

TaleGuard installs **Mixins** into those code paths at class-load time and asks a single, shared question:

> **is this player allowed to do this here?**

Any plugin can answer by registering a **`ProtectionHook`**. That means one guard layer, consistently
applied, no matter which plugin owns the world.

---

## Installing

| Hytale server | TaleGuard |
| --- | --- |
| `>=0.6.0 <0.7.0` | **1.1.x** — `TaleGuard-1.1.0-hytale-0.6.jar` |
| 0.5.x | 1.0.0 |

Mixins bind to exact method descriptors in the server, so each build targets one Hytale line — the
`hytale-` part of the jar name — and declares it in its manifest. A server on another line refuses the jar
instead of loading guards that would not apply.

Mixins rewrite game classes as they load, so TaleGuard must run **before** the server finishes booting:

> **Put the jar in your server's `earlyplugins/` folder — not `mods/`.**

The server asks you to confirm early plugins when it boots. A server without an interactive console (a
panel, systemd, Docker) cannot answer, so start it with **`--accept-early-plugins`** — without it, the
server stops with *"Early plugins require interactive confirmation"*.

### It checks itself at boot

Every guard names an exact call in the server's code, and a server update can move one. TaleGuard reads
its own mixins at boot and looks each target up in the running server, so the boot log tells you where
you stand:

```text
[TaleGuard] all 28 redirect(s) match this server.
```

Anything that no longer binds is named, one line per guard, followed by *"The flags behind them are NOT
enforced on this server"*. The result is also published as system properties for other plugins to read:
`taleguard.redirects.audit` (`broken/checked`, e.g. `0/28`) and `taleguard.redirects.broken`.

---

## What it protects

Each surface calls your hook's `isAllowed(uuid, world, x, y, z, type)` with this `type`:

| Surface | `type` |
| --- | --- |
| Block damage / mining by a player — single blocks and area tools | `HARVEST` |
| Picking up a block (harvest-by-interaction) | `HARVEST` |
| Crop harvest and growth-stage changes | `CROP_HARVEST` |
| Block state changes (doors, gates and other stateful blocks) | `INTERACT` |
| Hammer cycling a block through its group | `HAMMER` |
| Builder tools — on-use, paste, extrude, line, stack | `BUILDER` |
| Fluid placement | `FLUID` |
| Seating | `ENTITY_INTERACT` |
| Automatic item pickup | `ITEM_PICKUP` |
| Death item drops | `DEATH_DROP` |
| Explosion block damage | `EXPLOSION` (no player: `uuid` is null) |
| Mob / NPC spawns — world spawning, spawn markers, NPC plugin | `MOB_SPAWN` (no player: `uuid` is null) |

Two surfaces have entry points of their own: **fluid flow** asks `isFluidFlowAllowed(...)`, and
**commands** in guarded worlds ask `isCommandAllowed(uuid, command)` (with `getCommandDenialMessage()`
for the reply). Denials on `DEATH_DROP`, `EXPLOSION` and `MOB_SPAWN` are silent — `notifyDenied` is not
called, since there is nobody to tell.

Plugins that already build on TaleGuard: **AeroWars**, **Plots**, and island/**Skyblock** protection.

---

## For developers

TaleGuard is a **soft dependency**: there is no compile-time coupling. Register a hook and TaleGuard calls
it; if TaleGuard isn't installed, your registration is a harmless no-op.

**The hook interface**

```java
public interface ProtectionHook {
    // return false to DENY the interaction
    boolean isAllowed(UUID playerUuid, String worldName, double x, double y, double z, String type);
    void    notifyDenied(UUID playerUuid, String worldName, double x, double y, double z, String type);
}
```

**Registering — reflective (zero dependency, recommended for soft-depends)**

```java
@SuppressWarnings("unchecked")
Map<String, Object> registry =
    (Map<String, Object>) System.getProperties().get("taleguard.hook.registry");
if (registry != null) {
    registry.put("myplugin", myProtectionHook); // your object exposing isAllowed / notifyDenied
}
```

**Registering — against the API**

```java
HookRegistry.registerHook("myplugin", myProtectionHook);
```

A hook may also expose `int getPriority()` — lower runs first. Fluid flow and command filtering have their
own entry points (`HookRegistry.checkFluidFlow(...)`, `HookRegistry.checkCommand(...)`).

Add TaleGuard as an **optional dependency** so it loads first when present. The server identifies plugins
by `Group:Name`:

```jsonc
"OptionalDependencies": { "Stoshe:TaleGuard": "*" }
```
