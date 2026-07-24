# Phased Entity Targeting Convention

## Purpose

This document defines how ProjectKorra core abilities and addon abilities must acquire, retain, and act on entity targets when phased encounters are enabled.

The governing rule is:

> An entity denied at `GateStage.TARGET_SELECT` must be treated as though it does not exist for that ability.

A denied entity must not:

- Become a direct or area-of-effect target.
- Obscure a valid in-phase target.
- Count toward an entity limit.
- Cause a projectile, stream, or ability to stop, dissipate, redirect, or enter another state.
- Trigger hit sounds, particles, cooldown behavior, statistics, or secondary effects.
- Be added to an affected-target cache.

Target selection is enforced in both `soft-block` and `enforce` rollout modes. `observe` mode evaluates and records the decision without blocking it.

## Selection and Application Are Separate Gates

Target selection must happen before an entity can influence ability logic. Effect helpers remain a second line of defense:

| Operation | Required path |
| --- | --- |
| Candidate selection | Ability-aware `GeneralMethods` targeting helper or `GeneralMethods.canAbilityTarget(...)` |
| Damage | `DamageHandler.damageEntity(...)` with the source ability |
| Knockback, push, or pull | `GeneralMethods.setVelocity(ability, entity, vector)` |
| Fire ticks | `PhasedEntityEffectManager.setFireTicks(...)` |
| Potion effects | `PhasedEntityEffectManager`, source-aware `TempPotionEffect`, or another gated helper |
| Movement restrictions | Source-aware `MovementHandler` or another gated helper |
| Ability-to-ability collision | The standard `CollisionManager` path |

Damage and effect gates do not replace target selection. Many existing effect methods return `void`, and an ability may remove itself or change state after invoking them. If an out-of-phase entity is selected first, the effect can be denied while the ability still behaves as if it hit something.

## Preferred GeneralMethods APIs

When an ability instance exists, pass it explicitly:

```java
List<Entity> targets = GeneralMethods.getEntitiesAroundPoint(
        this, location, radius);

List<Entity> livingTargets = GeneralMethods.getEntitiesAroundPoint(
        this,
        location,
        radius,
        entity -> entity instanceof LivingEntity);

Entity closest = GeneralMethods.getClosestEntity(
        this, location, collisionRadius);

LivingEntity closestLiving = GeneralMethods.getClosestLivingEntity(
        this, location, collisionRadius);

Entity aimedAt = GeneralMethods.getTargetedEntity(
        this, this.caster, range);

Entity aimedAtExceptPrevious = GeneralMethods.getTargetedEntity(
        this, this.caster, range, previouslyAffected);
```

These helpers preserve the corresponding legacy helper's normal entity filters and selection behavior, then remove candidates denied by the phase gate.

When selection occurs before the ability instance exists, pass the source and canonical ability identifier:

```java
Entity target = GeneralMethods.getTargetedEntity(
        caster, "MyAbility", range);

List<Entity> targets = GeneralMethods.getEntitiesAroundPoint(
        caster, "MyAbility", location, radius);

List<Entity> livingTargets = GeneralMethods.getEntitiesAroundPoint(
        caster,
        "MyAbility",
        location,
        radius,
        entity -> entity instanceof LivingEntity);
```

Use the same ability identifier that the eventual ability instance returns from `getName()`. Do not use display names, translated names, or class names unless they are also the canonical ability name.

## Legacy Sourceless Helpers

The following forms remain source- and binary-compatible but are deprecated for ability targeting:

```java
GeneralMethods.getEntitiesAroundPoint(location, radius);
GeneralMethods.getClosestEntity(location, radius);
GeneralMethods.getClosestLivingEntity(location, radius);
GeneralMethods.getTargetedEntity(caster, range);
```

During normal `CoreAbility.start()`, `CoreAbility.progress()`, and ability collision handling, ProjectKorra supplies an active ability context. Deprecated helpers inherit that context and filter targets as a compatibility bridge.

Do not rely on implicit context in:

- Ability constructors before `start()` is called.
- Bukkit listeners.
- Scheduled or delayed callbacks.
- Async tasks.
- Static utilities called outside managed ability execution.
- Third-party event handlers.

Use an explicit ability or source-and-identifier overload in those paths. Explicit provenance also makes code review and future maintenance safer.

## Custom Utility Methods

Addon utilities should make provenance part of their API. Prefer an ability-first overload:

```java
public static List<Entity> findTargets(
        final Ability ability,
        final Location center,
        final double radius) {
    return GeneralMethods.getEntitiesAroundPoint(
            ability,
            center,
            radius,
            entity -> isValidCustomTarget(entity));
}
```

If the utility must run before an ability instance exists, provide a source-and-identifier overload:

```java
public static List<Entity> findTargets(
        final LivingEntity source,
        final String abilityId,
        final Location center,
        final double radius) {
    return GeneralMethods.getEntitiesAroundPoint(
            source,
            abilityId,
            center,
            radius,
            entity -> isValidCustomTarget(entity));
}
```

Avoid designing new ability utilities whose only inputs are a location and radius. Without source provenance, the utility cannot determine which phased encounter owns the query.

## Direct Bukkit and Third-Party Queries

An addon does not have to use `GeneralMethods` to find entities, but every candidate must pass the same gate before it participates in selection logic.

### World or Entity Nearby Queries

Filter candidates before sorting, selecting, limiting, counting, or changing ability state:

```java
List<Entity> targets = world.getNearbyEntities(
                center, radius, radius, radius)
        .stream()
        .filter(entity -> GeneralMethods.canAbilityTarget(this, entity))
        .filter(this::isValidCustomTarget)
        .toList();
```

Before the ability exists:

```java
boolean allowed = GeneralMethods.canAbilityTarget(
        caster, "MyAbility", candidate);
```

### Ray Tracing

Use a ray-trace overload that accepts an entity predicate when available:

```java
RayTraceResult result = world.rayTraceEntities(
        origin,
        direction,
        range,
        raySize,
        entity -> GeneralMethods.canAbilityTarget(this, entity)
                && isValidCustomTarget(entity));
```

Filtering only the first returned hit after ray tracing is not sufficient. If the first hit is out-of-phase, rejecting it after the query can still hide a valid target behind it. Supply the predicate to the ray trace, iterate past denied hits, or collect and evaluate all geometric candidates before choosing one.

The same warning applies to APIs such as `Player#getTargetEntity(...)` that return only one preselected entity without accepting an ability-aware predicate.

### Custom Hitboxes and NPC APIs

For bounding boxes, spatial indexes, NPC registries, metadata lists, or third-party combat APIs:

1. Resolve the candidate to its Bukkit `Entity` and UUID.
2. Evaluate `GeneralMethods.canAbilityTarget(...)`.
3. Exclude denied candidates before distance comparisons or collision resolution.

The rule applies to players, mobs, NPCs, armor stands, projectiles, falling blocks, items, and other non-living entities. Do not gate only `LivingEntity` candidates if the ability can collide with or push other entity types.

## Ordering Rules

Phase filtering must occur at the candidate boundary.

Correct:

```java
Entity closest = candidates.stream()
        .filter(entity -> GeneralMethods.canAbilityTarget(this, entity))
        .min(Comparator.comparingDouble(
                entity -> entity.getLocation().distanceSquared(center)))
        .orElse(null);
```

Incorrect:

```java
Entity closest = candidates.stream()
        .min(Comparator.comparingDouble(
                entity -> entity.getLocation().distanceSquared(center)))
        .orElse(null);

if (!GeneralMethods.canAbilityTarget(this, closest)) {
    closest = null;
}
```

The incorrect version allows a nearer denied entity to obscure an allowed entity.

Apply phase filtering before:

- `findFirst`, `min`, `max`, or nearest-target calculations.
- `limit`, target caps, or batching.
- Random target selection.
- Line-of-sight obstruction decisions involving entities.
- Adding an entity to a hit or cooldown set.
- Setting flags such as `hit`, `progressing`, `collided`, or `hasTarget`.
- Removing a projectile or stream.

For mixed-phase area effects, skip each denied entity independently. Do not cancel the entire ability because one nearby entity is denied.

## Ability Lifecycle

Only an allowed collision may affect the ability's lifecycle.

Incorrect:

```java
Entity entity = GeneralMethods.getClosestEntity(location, radius);
if (entity != null) {
    DamageHandler.damageEntity(entity, damage, this);
    remove();
}
```

Correct:

```java
Entity entity = GeneralMethods.getClosestEntity(this, location, radius);
if (entity != null) {
    DamageHandler.damageEntity(entity, damage, this);
    remove();
}
```

Because the second query excludes denied entities, `remove()` represents a real collision from the ability's perspective.

This rule also applies to:

- Setting a projectile to falling or dissipating.
- Removing one stream from a multi-stream ability.
- Consuming a pierce count or charge.
- Redirecting toward a target.
- Starting a combo or secondary projectile.
- Playing hit-only audiovisual effects.
- Starting damage-over-time bookkeeping.

## Scheduled, Delayed, and Cached Targets

Phase membership can change after initial selection. Recheck a retained target immediately before it is used:

```java
Entity target = selectTarget();

new BukkitRunnable() {
    @Override
    public void run() {
        if (!GeneralMethods.canAbilityTarget(MyAbility.this, target)) {
            return;
        }

        GeneralMethods.setVelocity(MyAbility.this, target, knockback);
        DamageHandler.damageEntity(target, damage, MyAbility.this);
    }
}.runTaskLater(ProjectKorra.plugin, delay);
```

Recheck targets held in:

- Scheduled tasks.
- Long-lived homing projectiles.
- Combo state.
- `affectedEntities`, `hurtEntities`, or similar collections.
- Channelled abilities.
- Target-lock or tether systems.

`PhasedIntegrationManager.runWithAbilityContext(ability, action)` can propagate provenance through grouped synchronous helper calls:

```java
PhasedIntegrationManager.runWithAbilityContext(this, () -> {
    customUtilityThatUsesLegacyHelpers();
});
```

Explicit source-aware helpers are still preferred. Context wrapping does not freeze a previous decision, and retained entities still need to be rechecked when they are used later.

Do not call Bukkit entity queries or the phase provider asynchronously. Return to the server thread before selecting or affecting Bukkit entities.

## Target Selection Does Not Authorize Effects

An entity allowed at `TARGET_SELECT` must still pass the gate associated with each applied effect. Use ProjectKorra's source-aware helpers:

```java
GeneralMethods.setVelocity(this, target, velocity);
DamageHandler.damageEntity(target, damage, this);
PhasedEntityEffectManager.setFireTicks(this, target, fireTicks);
PhasedEntityEffectManager.addPotionEffect(this, livingTarget, effect);
new TempPotionEffect(livingTarget, effect, this);
```

Avoid raw calls such as:

```java
target.setVelocity(velocity);
target.setFireTicks(fireTicks);
livingTarget.addPotionEffect(effect);
livingTarget.damage(damage);
```

Raw Bukkit calls bypass ProjectKorra's collision, effect, and damage gates.

## Ability-to-Ability Collisions

Entity targeting and ability-to-ability collision are different paths.

Abilities registered with ProjectKorra's `CollisionManager` already evaluate `GateStage.COLLISION` before events and collision handlers run. Addons should use that system for normal ability collisions.

If an addon manually compares two abilities or removes nearby ability instances, it must preserve both sources and evaluate an ability-collision gate before either ability changes state. `GeneralMethods.canAbilityTarget(...)` is for entity targets and is not a substitute for an ability-to-ability collision request.

## Reference Migrations

The initial core migrations demonstrate three common patterns:

- `FireBlast` uses the ability-aware closest-entity helper, so a denied entity cannot stop the projectile or obscure a valid target.
- `WaterManipulation` uses an ability-aware area query for projectile hits and a source-and-identifier query for targeting that can occur before an instance exists.
- `AirSwipe` uses an ability-aware area query and rechecks each entity inside its delayed task.

See:

- [`FireBlast.java`](../src/com/projectkorra/projectkorra/firebending/FireBlast.java)
- [`WaterManipulation.java`](../src/com/projectkorra/projectkorra/waterbending/WaterManipulation.java)
- [`AirSwipe.java`](../src/com/projectkorra/projectkorra/airbending/AirSwipe.java)

## Migration Procedure

For each core or addon ability:

1. Find every entity acquisition path.
2. Identify whether an ability instance exists at that point.
3. Replace sourceless `GeneralMethods` calls with an explicit ability or source-and-identifier overload.
4. Add `canAbilityTarget(...)` to custom Bukkit, ray-trace, NPC, or spatial-index queries.
5. Ensure filtering happens before nearest selection, limits, collision flags, and lifecycle changes.
6. Recheck targets used by schedulers or retained across ticks.
7. Route damage and physical/status effects through source-aware ProjectKorra helpers.
8. Verify ability-to-ability collision paths use `CollisionManager` or an equivalent phased collision request.
9. Test both allowed and denied targets without changing normal in-phase behavior.

Useful audit searches include:

```text
getEntitiesAroundPoint
getClosestEntity
getClosestLivingEntity
getTargetedEntity
getNearbyEntities
rayTraceEntities
getTargetEntity
affectedEntities
hurtEntities
runTaskLater
runTaskTimer
setVelocity
setFireTicks
addPotionEffect
damage
remove
```

## Test Matrix

Every migrated targeting pattern should cover:

- One allowed target.
- One denied target.
- A denied target closer than an allowed target.
- Allowed and denied targets occupying the same area of effect.
- No allowed targets, confirming the ability continues or expires normally rather than reporting a hit.
- A non-living denied entity when the ability interacts with non-living entities.
- A target whose phase eligibility changes before a delayed effect runs.
- Target limits calculated after phase filtering.
- Normal in-phase damage, knockback, hit tracking, and ability removal.
- Provider absence or errors under the configured fallback behavior.
- `observe`, `soft-block`, and `enforce` rollout behavior where relevant.

## Review Checklist

An ability conforms to this convention when all of the following are true:

- Every entity candidate has source provenance.
- Denied entities are excluded before selection and collision logic.
- Denied entities cannot alter ability state or lifecycle.
- Delayed and cached targets are rechecked.
- Entity effects use source-aware gated helpers.
- Ability-to-ability collisions use their dedicated gate.
- In-phase target ordering, limits, damage, and effects remain unchanged.
- Sourceless compatibility helpers are not introduced into new ability code.
