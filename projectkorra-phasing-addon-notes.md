# ProjectKorra Phasing Addon Notes

## Particle Source Propagation

ProjectKorra now tracks the currently executing ability while ability start, progress, and ability collision handlers run. Particle calls made through `ParticleEffect.display(...)`, `GeneralMethods.displayColoredParticle(...)`, or `ColoredParticle.display(...)` during those flows will automatically send the active ability/caster into phasing gate requests.

## Addon Impact

- Existing addon calls to the old particle helper methods should continue to compile.
- Particle calls made from async tasks, delayed scheduler callbacks, static utilities, listeners, or code that runs outside normal ability execution may not have an active ability context.
- For out-of-context particle work, pass the source ability explicitly with the new ability-first overloads, such as `ParticleEffect.FLAME.display(ability, location, amount, offsetX, offsetY, offsetZ)`.
- For grouped custom logic, wrap the work in `PhasedIntegrationManager.runWithAbilityContext(ability, () -> { ... })`.
- Raw Bukkit particle APIs bypass ProjectKorra phasing gates and should be avoided for phased addon effects.

## Ability Start Gate

ProjectKorra now evaluates `GateStage.START` from `CoreAbility.start()` after the normal enabled/unlocked checks and before `AbilityStartEvent` is fired. The gate request includes the ability name, caster UUID, and ability location when available.

Addon abilities that rely on side effects after `start()` should treat a denied start the same way they would treat another cancelled startup path: the ability will be removed and will not be registered for progress. If addon code starts abilities from unusual flows, make sure the ability has its caster/location state initialized before calling `start()`.

## Sound Gate

ProjectKorra sound emission now has a central phased path through `PhasedSoundManager`. Migrated core ability and element-helper sounds evaluate `GateStage.SOUND` per viewer before sending the sound packet. The request includes the active ability/caster when available, the sound location, and the viewer UUID.

Addon impact:

- Existing element helper calls such as `playAirbendingSound(location)`, `playEarthbendingSound(location)`, `playFirebendingSound(location)`, `playWaterbendingSound(location)`, and lightning helper calls continue to compile.
- Sound helper calls made during normal ability `start()`, `progress()`, and collision handling generally inherit the active ability context.
- Delayed scheduler callbacks, static utilities, listeners, or other out-of-context code should use `PhasedSoundManager.playSound(ability, location, sound, volume, pitch)`, `PhasedSoundManager.playSoundToViewer(ability, player, location, sound, volume, pitch)`, or `PhasedIntegrationManager.runWithAbilityContext(ability, () -> { ... })`.
- Fire and lightning sound helpers also have ability-first overloads, for example `playLightningbendingSound(ability, location)`, for scheduled lightning-style effects.
- Passive chi-block sounds can pass an explicit source with `ChiPassive.blockChi(source, target)`. The existing `ChiPassive.blockChi(target)` overload remains available.
- Raw Bukkit sound APIs such as `World#playSound(...)` and `Player#playSound(...)` bypass ProjectKorra phasing gates and should be avoided for phased addon effects.

## Ability-vs-Ability Collision Gate

ProjectKorra now evaluates `GateStage.COLLISION` for real `CollisionManager` ability-vs-ability collisions after geometry says two abilities touched and before `AbilityCollisionEvent` is fired. Denied collisions are treated as if the two abilities did not collide: no `AbilityCollisionEvent` is fired and neither ability's `handleCollision(...)` method runs.

Collision gate requests include the source caster UUID, target caster UUID, source ability name, target ability name, and a collision location. Allowed collision handlers still run inside the active ability context for the handler's ability.

## TempBlock Provenance

Core `TempBlock` creation now generally carries caster and ability provenance into `GateStage.BLOCK` requests. Existing addon constructors such as `new TempBlock(block, material)` still compile and inherit the active ability context when called during normal ProjectKorra ability execution.

Addon code that creates `TempBlock`s from schedulers, listeners, static helpers, or before an ability calls `start()` should use source-aware constructors, such as `new TempBlock(block, data, ability)` or `new TempBlock(block, material, player, "AbilityName")`. `TempBlock#setAbility(...)` remains available for compatibility, but it runs after the constructor and is too late to affect the initial overlay-vs-world-mutation decision.

## TempFallingBlock Creation and Visibility

An ability-owned `TempFallingBlock` now spawns and registers synchronously even when a viewer-less `GateStage.BLOCK` request would be denied. The entity is always visible to its caster and is hidden or shown to other players using viewer-scoped block decisions. Collision and final placement remain independently gated.

Addons such as JedCore EarthShard should pass their `CoreAbility` to the constructor and inspect `getCreationResult()` (or `wasCreated()` / `wasDenied()`) before using `getFallingBlock()`. `CreationResult.DENIED_BY_PHASED_BLOCK_GATE` is currently possible only for source-less construction; an ability-owned block is not invalidated by that viewer-less decision.

## Hit and Effect Hardening

ProjectKorra now evaluates `GateStage.EFFECT` for non-HP entity effects such as movement locks, temporary potion effects, targeted potion applications, and fire tick changes. Velocity and knockback continue to use `GateStage.COLLISION`, while HP damage continues to use `GateStage.DAMAGE`.

Old `TempPotionEffect` constructors still compile and inherit the active ability context when called during normal ProjectKorra ability execution. Addon code that applies potion effects, fire ticks, movement locks, or other entity effects from schedulers, listeners, static utilities, or other out-of-context flows should pass an explicit source ability where available or use `PhasedIntegrationManager.runWithAbilityContext(...)`.

Raw Bukkit APIs such as `Entity#setVelocity(...)`, `Entity#setFireTicks(...)`, `LivingEntity#addPotionEffect(...)`, and `LivingEntity#removePotionEffect(...)` bypass ProjectKorra phasing gates unless routed through ProjectKorra's gated helpers.

## Addon Audit Checklist

When updating addon repos, search for these patterns and route out-of-context calls through source-aware ProjectKorra helpers or `PhasedIntegrationManager.runWithAbilityContext(...)`:

- `spawnParticle`, `ParticleEffect.display`, `displayColoredParticle`
- `playSound`
- `new TempBlock`
- `new TempFallingBlock`
- `setVelocity`, `setFireTicks`
- `addPotionEffect`, `removePotionEffect`, `new TempPotionEffect`, `new MovementHandler`
- delayed scheduler callbacks, async tasks, listener-only effects, and static helper methods

## Current Scope

This note covers particle source propagation, the central ability start gate, the sound gate, ability-vs-ability collision gating, TempBlock provenance, TempFallingBlock creation and visibility, and small hit/effect hardening. Target selection and broader effect APIs still need their own source propagation passes.
