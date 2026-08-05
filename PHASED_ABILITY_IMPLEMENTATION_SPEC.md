# ProjectKorra Phased Ability Integration Spec

## Status
Draft v1 (Avatarverse to ProjectKorra handoff)

## Purpose
Define the ProjectKorra-side implementation required for phased/instanced combat integration with Avatarverse (external/dependency plugin) so that ability effects cannot leak outside the intended phase audience.

## Core Requirement
For phased abilities, ProjectKorra must ensure effects are neither visible nor tangible to entities outside the phased instance.

This includes all of the following:
- Damage and health-impacting effects
- Knockback, velocity, pull/push, and movement constraints
- Status effects and debuffs/buffs
- Block updates, block transforms, temporary blocks, and environmental edits
- Particle and visual-only effects
- Sound broadcast tied to the ability effect area (where applicable)

If a target/viewer is out-of-phase, the effect must not be applied or shown.

## Current Constraint (Important)
Avatarverse can currently gate some interaction paths (not all) from its side.
ProjectKorra must still implement native gating at its own effect execution points to fully prevent leakage of:
- Particle rendering
- Block updates
- Non-damage physical effects

Avatarverse-side checks are a defense layer, not the complete solution.

## Integration Model

### Authority
- Avatarverse is the authority for phase/audience truth.
- ProjectKorra consumes Avatarverse gate decisions before applying ability effects.

### Runtime Coupling
- Use a runtime service lookup (Bukkit ServicesManager) for the integration contract.
- Do not hard-couple ProjectKorra to Avatarverse implementation classes.
- If Avatarverse service is unavailable, fallback behavior is configurable:
  - `observe` mode: allow and log
  - `enforce` mode: deny on unknown/unavailable

## Required Contract (ProjectKorra Consumer View)
ProjectKorra should query a gate interface before each effect stage:

```java
public interface PhasedAbilityGate {
    GateDecision evaluate(GateRequest request);
}

public record GateRequest(
    UUID sourceEntityUuid,
    @Nullable UUID targetEntityUuid,
    String stage,              // start | target-select | collision | damage | block | particle | sound
    @Nullable String abilityId,
    @Nullable Location location,
    @Nullable UUID viewerUuid  // required for viewer-scoped effects (particle/sound)
) {}

public record GateDecision(
    boolean allowed,
    String reasonCode          // ALLOW | DENY_PHASE | DENY_AUDIENCE | UNKNOWN
) {}
```

## Mandatory Enforcement Points in ProjectKorra

### 1) Ability start
Before ability startup finalizes, validate cast eligibility for phased context.
If denied, cancel startup.

### 2) Target acquisition
Before selecting any entity target, filter candidates through gate checks.
Out-of-phase entities must never be selected as targets.

### 3) Collision and physical application
Before collision-side effects, validate gate decision.
If denied, do not apply knockback, displacement, freeze, stun, or any physical side effects.

### 4) Damage application
Before damage commit, validate gate decision.
If denied, do not apply damage or trigger damage-linked effects.

### 5) Block mutation paths
Before block updates/transforms/temp block operations, validate phased visibility/tangibility constraints.
If denied, skip operation.

### 6) Particle and visual paths
All particle emission must be viewer-filtered.
Only in-phase viewers may receive particle packets/effects.

### 7) Sound emission paths
Use audience-filtered sound emission when sound conveys ability execution context.
Out-of-phase players should not hear phased ability execution cues.

## No-Leak Rule (Normative)
For any phased ability `A`, source `S`, and player `P`:

- If `P` is out of audience for `S`'s phase, then `P` must observe zero effects from `A`.
- If target `T` is out of audience relation with `S`, then `T` must receive zero tangible effects from `A`.

Equivalent condition:

$$
\neg AudienceVisible(S, P) \Rightarrow ObserveEffect(P, A) = 0
$$

$$
\neg InteractionAllowed(S, T) \Rightarrow ApplyEffect(T, A) = 0
$$

## Telemetry and Diagnostics
ProjectKorra should log gate checks with structured fields:
- `abilityId`
- `stage`
- `sourceEntityUuid`
- `targetEntityUuid` (if any)
- `viewerUuid` (if any)
- `allowed`
- `reasonCode`

Maintain counters per stage/reason for rollout verification.

## Rollout Modes
- `observe`: evaluate and log only
- `soft-block`: block damage/collision first, log remaining stages
- `enforce`: block all denied stages (damage, block, particles, status, sound)

## Acceptance Criteria
1. Out-of-phase players do not see phased ability particles.
2. Out-of-phase players do not observe phased block mutations.
3. Out-of-phase targets do not take phased ability damage.
4. Out-of-phase targets do not receive physical/status side effects.
5. In-phase behavior remains unchanged.

## Test Matrix (ProjectKorra Side)
- Direct hit ability: in-phase vs out-of-phase target
- Projectile ability: in-phase vs out-of-phase target
- AoE ability: mixed audience in same world/chunk
- Block-modifying ability near phase boundary
- Particle-heavy ability with multiple nearby viewers
- NPC caster vs player target and player caster vs NPC target

## Avatarverse Note
Avatarverse now has early interaction gating hooks for some damage paths, but this does not replace ProjectKorra native effect-path gating.
ProjectKorra implementation is required to satisfy full no-leak behavior for particles/block updates/non-damage effects.
