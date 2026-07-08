<p align="center">
  <img src="taleguard.png" alt="TaleGuard" width="720">
</p>

<p align="center">
  <b>A Mixin/transformer protection bridge for Hytale.</b><br>
  Intercepts the low-level interaction surfaces the normal server event API can't reach, and routes them
  through one simple hook so protection plugins — regions, islands, minigames — can enforce their rules
  <i>everywhere</i>.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Hytale-early%20plugin-8A2BE2?style=for-the-badge" alt="Hytale early plugin">
  <img src="https://img.shields.io/badge/version-1.0.0-2ea44f?style=for-the-badge" alt="version 1.0.0">
  <img src="https://img.shields.io/badge/type-Mixin%20bridge-FF8C00?style=for-the-badge" alt="Mixin bridge">
</p>

---

## What it is

Hytale's high-level events cover block break / place, but a lot of gameplay interaction happens through
paths a plugin can't cancel from an event: F-key / auto item pickup, item use, fluid placement and flow,
seating, crop harvesting, hammer / builder tools, explosions, death-item drops, and commands.

TaleGuard installs **Mixins** into those code paths at class-load time and asks a single, shared question:

> **is this player allowed to do this here?**

Any plugin can answer by registering a **`ProtectionHook`**. That means one guard layer, consistently
applied, no matter which plugin owns the world.

---

## Why it's an "early" plugin

Mixins rewrite game classes as they load, so TaleGuard must run **before** the server finishes booting.

> **Install `TaleGuard-1.0.0.jar` into your server's `earlyplugins/` folder — not `mods/`.**

Once loaded, it exposes its hook registry and its mixins are live for the rest of the session.

---

## What it protects

TaleGuard funnels these interaction surfaces through the hook (`InteractionType`):

| Surface | Type |
| --- | --- |
| Block break / place / harvest | `BLOCK_HARVEST`, `BUILDER`, `HAMMER` |
| Container open | `CONTAINER_OPEN` |
| Crop harvest / growth stage | `CROP_HARVEST`, `CROP_STAGE_CHANGE` |
| Entity damage / interact | `ENTITY_DAMAGE`, `ENTITY_INTERACT` |
| Explosion block damage | `EXPLOSION_DAMAGE` |
| Fluid place / flow | `FLUID_PLACE` |
| Item pickup (F-key / auto) | `ITEM_PICKUP` |
| Item use, seating, death-item drops, item durability, NPC spawn | (routed through the hook) |
| Command filtering in guarded worlds | (via `checkCommand`) |

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

Add TaleGuard as an **optional dependency** so it loads first when present:

```jsonc
"OptionalDependencies": { "Stoshe:stoshe.taleguard": "*" }
```

---

## Credits

- **Author:** [Stoshe](https://github.com/stoshe)
- **Website:** https://stoshe.dev
- **Repository:** https://github.com/stoshelabs/taleguard

<sub>Built for Hytale. Companion minigame using it: <a href="https://github.com/stoshelabs/aerowars">AeroWars</a>.</sub>
