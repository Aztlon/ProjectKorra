# Ability Execution Evidence for Addons

`AbilityStartEvent`, `AbilityProgressEvent`, and `AbilityEndEvent` describe an
ability's lifecycle. They do not prove that an ability hit a target, changed a
block, healed something, or otherwise completed an effect.

When an addon needs to expose a completed outcome, it can explicitly publish an
`AbilityExecutionEvidenceEvent`. ProjectKorra does not publish this event for
existing abilities and does not infer it from lifecycle activity.

## Define Evidence Keys

Create plugin-owned keys once, normally while enabling the addon. The key
defines both what happened and the unit represented by the magnitude.

```java
public final class ExampleAddon extends JavaPlugin {
    public static NamespacedKey ENTITY_HIT;
    public static NamespacedKey BLOCK_FROZEN;
    public static NamespacedKey AREA_CLEARED;

    @Override
    public void onEnable() {
        ENTITY_HIT = new NamespacedKey(this, "entity_hit");       // magnitude: applied damage
        BLOCK_FROZEN = new NamespacedKey(this, "block_frozen");  // magnitude: number of blocks
        AREA_CLEARED = new NamespacedKey(this, "area_cleared");  // magnitude: cleared radius
    }
}
```

Keys are identifiers, not built-in ProjectKorra categories. Consumers should
match the complete namespace and key and interpret magnitude only according to
the publisher's documented contract.

## Standard ProjectKorra Evidence

ProjectKorra publishes two standard post-commit contracts for effects routed
through its central helpers:

- `AbilityExecutionEvidence.ENTITY_DAMAGE` (`projectkorra:entity_damage`) is
  emitted only after health or absorption is reduced. Magnitude is the actual
  combined health/absorption reduction.
- `AbilityExecutionEvidence.VELOCITY_APPLIED`
  (`projectkorra:velocity_applied`) is emitted after a validated, clamped,
  positive velocity is assigned. Magnitude is the applied vector length.

Ability implementations must still publish their own semantic evidence for
healing, block changes, completed movement, defense, sustained modes, and other
effects that these central helpers cannot prove.

ProjectKorra also exposes narrowly semantic keys where core abilities publish
their own committed outcomes: `projectkorra:air_shield_deflected`,
`projectkorra:block_ignited`, and `projectkorra:air_pocket_created`.

## Publish Only After Commit

Call a helper from the responsible `Ability` implementation after the
represented change has succeeded. Entity and block helpers capture the target's
current location. The location helper represents an outcome without an entity
or block target.

```java
// The damage call has returned and the addon knows the damage was applied.
AbilityExecutionEvidence.publishEntity(this, ExampleAddon.ENTITY_HIT, target, appliedDamage);

// The block change has completed successfully.
AbilityExecutionEvidence.publishBlock(this, ExampleAddon.BLOCK_FROZEN, block, 1.0D);

// An area effect has completed without one specific target.
AbilityExecutionEvidence.publishLocation(this, ExampleAddon.AREA_CLEARED, center, clearedRadius);
```

The source argument is the `Ability` instance responsible for the effect. A
magnitude must be finite and non-negative. Locations must belong to a world.
Publication is synchronous and the event is intentionally non-cancellable:
listeners observe an outcome that has already happened rather than vetoing it.

## Listen for Evidence

```java
@EventHandler
public void onAbilityEvidence(final AbilityExecutionEvidenceEvent event) {
    if (!event.getEvidenceKey().equals(ExampleAddon.ENTITY_HIT)) {
        return;
    }

    final Entity target = event.getTargetEntity();
    if (target != null) {
        getLogger().info(event.getAbility().getName() + " applied "
                + event.getMagnitude() + " damage to " + target.getUniqueId());
    }
}
```

The returned location is a defensive copy. Entity and block targets are
mutually exclusive, and both are absent for location evidence.

This API is observational and adds no persistence, configuration, quest,
training, or other progression semantics. Apart from the two standard central
contracts above, core and addon code must opt in at each genuine post-commit
point.
