# ProjectKorra Phasing Fork Notes

Avatarverse now registers a `PhasedAbilityGate` to synchronize ProjectKorra bending with tutorial/NPC phase visibility. The integration is conservative, but a few leaks cannot be solved perfectly from the ProjectKorra 1.11.1 gate requests Avatarverse receives.

## Current Limitations

- Generic `ParticleEffect.display(...)` requests are source-less. The gate receives a viewer UUID and location, but no ability, caster, target, or owning phase.
- Some `TempBlock` calls are created without an ability. Those blocks can be globally allowed or denied by location, but they cannot reliably use per-viewer overlays.
- `START`, `TARGET_SELECT`, `SOUND`, and full ability-vs-ability collision stages exist in the phasing API, but they are not consistently wired in the inspected 1.11.1 jar.
- Location-only particle gating can become ambiguous when multiple tutorial instances overlap. Avatarverse denies those ambiguous requests to avoid showing another player's phased tutorial effects.

## Desired Fork Changes

- Propagate the active ability and caster into particle gate requests from ProjectKorra internals.
- Add source-aware particle display overloads or an internal ability context so addon abilities can scope particles without manual viewer loops.
- Wire sound, target selection, ability start, and ability-vs-ability collision checks through `PhasedIntegrationManager` where applicable.
- Prefer `TempBlock` and `TempFallingBlock` constructors that receive the active ability throughout ProjectKorra core so block overlays can be viewer-scoped.
- Preserve `GateDecision.unknown()` fail-open behavior for provider exceptions or missing phase services.
