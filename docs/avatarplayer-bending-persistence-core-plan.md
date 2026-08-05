# Avatarverse Core Plan — AvatarPlayer-Persisted Bending Data

## Purpose

Move durable ProjectKorra player data into Avatarverse ownership. `AvatarPlayer` becomes the owner of the active bending profile, `Persona` remains the owner of each RPG character's bending profile, and ProjectKorra's `BendingPlayer` becomes a transient runtime projection.

This document covers the changes required in Avatarverse Core. The companion ProjectKorra contract is defined in [ProjectKorra Fork Plan — Externally Persisted Bending Players](projectkorra-external-player-persistence-fork-plan.md).

## ProjectKorra fork implementation status (July 2026)

The ProjectKorra side is now implemented. Avatarverse must integrate through `com.projectkorra.projectkorra.persistence.external`; it must not wait for a partially initialized `BendingPlayer` and populate its collections directly.

The implemented lifecycle is provider-first:

1. Avatarverse registers exactly one `ExternalBendingPlayerProvider` during dependent-plugin startup.
2. ProjectKorra requests the authoritative active profile from the provider.
3. ProjectKorra validates and atomically projects the complete snapshot.
4. ProjectKorra marks the runtime ready and publishes it.
5. `BendingPlayerCreationEvent` fires only after the provider snapshot is fully applied.

Avatarverse profile data must therefore be available to, or asynchronously awaitable by, `ExternalBendingPlayerProvider.load`. `BendingPlayerCreationEvent` is now a post-initialization notification, not the point at which Avatarverse supplies initial state.

Avatarverse still owns all legacy migration. In external mode ProjectKorra neither reads nor repairs `pk_players`, `pk_presets`, `pk_cooldowns`, or `pk_board` and never adopts those rows into an empty Persona.

### Avatarverse provider checklist

Avatarverse must now provide the following:

- Register one contract-version-1 provider while the Avatarverse plugin is enabling. Registration after the first external initialization is rejected.
- Return complete snapshots for online and offline identities, including genuinely empty profiles.
- Use an opaque context token tied to the active Persona/PvP profile and a monotonic revision.
- Persist active binds and personal named presets, including `UpsertPreset`, `DeletePreset`, and `ReplaceBinds` mutations.
- Handle all typed element, subelement, bind, preset, persistent-cooldown, toggle, board-preference, and reset mutations.
- Return acceptance only after the change is durable or safely queued, with a canonical mutation or complete canonical snapshot.
- Return authoritative snapshots with rejections/conflicts when possible and retain unknown identifiers reported by ProjectKorra.
- Implement a synchronous, non-blocking cooldown classifier for runtime-, Persona-, and account-scoped keys.
- Treat `flush` as a durability barrier and never rebuild authority from runtime PK collections.
- Decide whether board preference is account- or profile-scoped, store it at that owner, and include it in every snapshot.
- Provide Avatarverse-side permanent-removal/reset tooling because PK `permaremove` is unavailable externally.
- Import any desired legacy PK rows, including personal presets; ProjectKorra will not do so.

## Target ownership model

```text
RPG Persona ──activate──> AvatarPlayer active bending state ──apply──> BendingPlayer runtime
     ^                              |
     └────────persist changes───────┘

PvPPlayer persisted profile ──activate──> AvatarPlayer active bending state
```

The following invariants are mandatory:

1. Avatarverse is the only durable authority for bending player data.
2. `BendingPlayer` state is never adopted implicitly into an empty Persona.
3. An RPG Persona's bending state is keyed by Persona identity, not player UUID.
4. Account-scoped settings are not copied between Personae.
5. Applying a profile replaces the complete relevant runtime state; it does not merge with the previous profile.
6. A failed runtime projection cannot overwrite already-persisted Avatarverse state.

## State model

Introduce an Avatarverse-owned value object, provisionally `AvatarBendingState`. It should be serializable by the existing Avatarverse persistence layer but should not contain Bukkit or ProjectKorra runtime objects.

Suggested fields:

```java
public final class AvatarBendingState {
    private List<String> elements = new ArrayList<>();
    private List<String> subelements = new ArrayList<>();
    private Map<Integer, String> activeBinds = new HashMap<>();
    private Map<String, Map<Integer, String>> personalPresets = new HashMap<>();
    private Map<String, Long> persistentCooldownExpirations = new HashMap<>();
    private boolean bendingEnabled = true;
    private Map<String, Boolean> elementEnabled = new HashMap<>();
    private long revision;
}
```

Avatarverse may retain a richer internal loadout model, but its provider adapter must expose one active slot map and a distinct map of provider-backed personal PK presets. The provider snapshot also carries an opaque active-profile/context token, board preference, and unknown-identifier policy.

Persist stable string identifiers rather than PK object instances. Resolution into `Element`, `SubElement`, and abilities occurs at the runtime boundary. Unknown identifiers must be retained and reported, not silently discarded, so data survives temporary addon removal.

### State placement

- `Persona` owns RPG-specific elements, subelements, active binds/loadouts, personal presets if scoped per character, Persona-scoped toggles, and qualifying persistent cooldowns.
- `PvpPlayer` owns its single PvP bending profile.
- `AvatarPlayer` owns the active `AvatarBendingState`, the transient `BendingPlayer` handle, and account-scoped settings such as bending-board preference if that preference is intended to follow the Minecraft account.
- Existing Persona fields may initially remain as compatibility accessors backed by the new state object. Avoid maintaining two independently mutable collections.

## Core services

Create a single service boundary, provisionally `AvatarBendingStateService`. All Avatarverse bending mutations must pass through it. This service should back the implemented ProjectKorra `ExternalBendingPlayerProvider` instead of using direct runtime collection writes.

Required responsibilities:

- Activate state from a Persona or PvP profile.
- Persist the active RPG state to the current Persona before switching away or detaching.
- Replace live PK elements, subelements, binds, persistent cooldowns, and toggles.
- Validate abilities against the active elements and unlocked skills.
- Refresh passives and dependent displays after a successful projection.
- Receive PK runtime mutation notifications and update Avatarverse state.
- Track dirty state and persist through the existing save lifecycle.
- Produce diagnostics comparing persisted, active, and live runtime state.
- Implement provider `load`, `requestMutation`, `flush`, synchronous cooldown classification, and validation-issue reporting.
- Maintain a monotonic revision for each active context and reject stale expected revisions.

Suggested API shape:

```java
public interface AvatarBendingStateService {
    CompletionStage<ExternalBendingPlayerData> load(PlayerIdentity player);
    CompletionStage<MutationDecision> mutate(BendingPlayerMutation mutation);
    CompletionStage<Void> flush(PlayerIdentity player, FlushReason reason);
    CompletionStage<ApplyResult> activate(Player player, AvatarBendingState source, String context);
    BendingStateDiagnostics inspect(AvatarPlayer player);
}
```

`ProjectKorraBridge` should become a narrow adapter used by this service. It must stop deciding ownership or importing PK state.

## Join and attachment lifecycle

The required join sequence is:

1. Avatarverse loads `RpgPlayer` or `PvpPlayer` and its persisted profiles.
2. Avatarverse identifies the active Persona/PvP context and makes its provider snapshot available.
3. ProjectKorra calls `ExternalBendingPlayerProvider.load`; the returned stage may wait for Avatarverse loading without blocking the server thread.
4. ProjectKorra constructs a non-public runtime, validates the snapshot, and atomically applies it without reading legacy player tables.
5. ProjectKorra publishes the ready runtime and fires `BendingPlayerCreationEvent`.
6. `AvatarPlayer.onAttachBendingPlayer` may store the transient ready handle.
7. Tutorial and gameplay systems waiting for bending readiness may proceed.

The current distinction between a cached `BendingPlayer` and one that completed initialization remains useful. Replace PK-database readiness assumptions with external-runtime readiness and Avatarverse-projection readiness.

If Avatarverse profile loading is incomplete, keep the provider load stage pending within the configured timeout. Do not return a temporary empty snapshot unless the authoritative profile is genuinely empty. A timeout or unavailable provider causes ProjectKorra initialization to fail closed.

## Persona switch transaction

Refactor `RpgPlayer.switchTo` so bending activation is a defined transaction rather than several unrelated mutations.

Required order:

1. Reject re-entrant switches and acquire a per-player switch guard.
2. End and persist the previous Persona session, including bending state already represented in the active Avatarverse state.
3. Deactivate previous Persona objectives and other existing session-owned systems.
4. Set the target Persona as the switching target, but do not expose it as fully active yet.
5. Copy the target Persona's complete bending profile into `AvatarPlayer` active state.
6. Replace all relevant `BendingPlayer` runtime collections and settings.
7. Validate elements, subelements, binds, cooldowns, and toggles.
8. Refresh passives, board state, held-slot behavior, and other derived PK state.
9. Apply the rest of the Persona inventory, location, presentation, quest, and session state.
10. Commit `currentPersona`, publish `PersonaSwitchEvent`, and release the guard.

For step 6, call `ExternalBendingPlayerPersistence.applyExternalSnapshot(player, data, ApplyReason.PROFILE_REPLACEMENT)` and await its result. `PROFILE_REPLACEMENT` supersedes pending work for the old context and clears runtime-only cooldowns. Administrative reapply should use `ADMINISTRATIVE_REAPPLY`; conflict repair is provider/coordinator driven and preserves request ordering.

On failure, keep the target Persona data intact, disable bending for that runtime, record diagnostics, and either restore the previous active profile or fail the switch before externally committing it. Never persist a partially projected runtime back into either Persona.

An elementless new Persona is valid. Applying it must explicitly clear PK elements, subelements, binds, Persona-scoped cooldowns, and element toggles.

## Mutation flow

Avatarverse-originated changes must update the active Avatarverse state first and then project the committed result to PK.

Examples include:

- Tutorial element selection.
- Element rechoose or administrative element changes.
- Subelement rolls and selection.
- Skill binding and loadout changes.
- Persona death and deletion.
- Tutorial recovery.
- PvP element menus.

The preferred sequence is:

```text
validate request
    -> mutate Avatarverse state
    -> mark owner dirty / save when durability is required immediately
    -> apply a complete authoritative snapshot through ProjectKorra
    -> refresh derived runtime state
    -> emit Avatarverse domain event
```

For an ordinary Avatarverse-originated update within the same context, project the new complete snapshot with `ADMINISTRATIVE_REAPPLY`; it preserves runtime-only cooldowns and request ordering. Use `PROFILE_REPLACEMENT` only when changing the owning Persona/profile context.

PK-originated mutations, including PK commands or addons, arrive through `ExternalBendingPlayerProvider.requestMutation`. Avatarverse validates the request against RPG rules, context token, and expected revision, updates its active state, persists or safely queues it durably, and returns a final accepted, rejected, conflict, or failed decision. ProjectKorra does not apply the requested runtime change before acceptance.

An acceptance must return a strictly advancing revision and exactly one of:

- A canonical mutation targeting the same player and context.
- A complete canonical snapshot whose context and revision match the acceptance metadata.

For rejection or conflict, Avatarverse should include an authoritative snapshot when readily available. A conflict without a snapshot causes ProjectKorra to block further durable mutations and reload from the provider.

## Direct-write cleanup

Audit and replace all direct durable PK calls, including:

- `BendingPlayer.saveElements()`
- `BendingPlayer.saveSubElements()`
- `BendingPlayer.saveAbility(...)`
- PK preset creation, mutation, and deletion
- persistent cooldown writes
- bending-board preference writes

Known starting points include:

- `ProjectKorraBridge`
- `RpgPlayer.switchTo`
- `AvatarPlayer.onAttachBendingPlayer`
- `TutorialMenu`
- `CreationGUI`
- `ElementRechooseGUI`
- RPG and PvP `Elemenu`/`Subelemenu` implementations
- `NonbenderSubelementGUI`
- Persona death cleanup
- binding and loadout helpers
- admin element commands and quest element events

Direct mutation of `bp.getElements()`, `bp.getSubElements()`, or `bp.getAbilities()` should be limited to the projection adapter. Add architectural tests that fail when new direct save calls or unauthorized collection mutations are introduced.

## Loadouts and presets

Avatarverse owns both active binds and ProjectKorra personal named presets in external mode. These are related but distinct contract fields:

- `abilities` is the active slot 1–9 bind map.
- `presets` is `Map<String, Map<Integer, String>>` containing provider-backed personal named presets.
- Creating, updating, and deleting a personal preset arrives as typed provider mutations.
- Applying a personal preset arrives as one `ReplaceBinds` mutation; the preset itself remains stored.
- ProjectKorra configuration-defined global presets remain internal configuration. Applying one still produces a provider-mediated `ReplaceBinds` mutation.
- ProjectKorra performs no `pk_presets` SQL in external mode.

Avatarverse therefore needs to choose and document one storage mapping:

1. Persist PK personal presets as a distinct named-preset collection alongside Avatarverse loadouts; or
2. Map them onto the Avatarverse loadout model while preserving stable PK preset names and complete CRUD semantics through the provider.

Legacy `pk_presets` rows, if they should survive, must be imported by Avatarverse migration. ProjectKorra will not import them. The special `offhand_swap` behavior should operate through an Avatarverse/provider bind replacement, not direct PK collection mutation. Null and literal `"null"` binds should be rejected at the state boundary.

## Cooldown policy

Classify cooldowns before migrating them:

- **Runtime-only:** ordinary short ability cooldowns; remain only in `BendingPlayer` and disappear when its runtime is discarded.
- **Persona-persistent:** must survive reconnect, switch, or server transfer; stored as absolute expiration timestamps in the Persona bending state.
- **Account-persistent:** anti-abuse or account actions that must cross Personae; stored on `AvatarPlayer` outside the Persona profile.

The default should be runtime-only. Persistence must be opt-in by cooldown key/category. On Persona switch, remove old Persona-persistent runtime cooldowns and apply only unexpired cooldowns belonging to the new Persona. Unknown persistent keys should be retained until expiration.

Implement `classifyCooldown` as a synchronous, non-blocking lookup returning `RUNTIME`, `PROFILE_PERSISTENT`, or `ACCOUNT_PERSISTENT`. The existing ProjectKorra `database` argument is only a persistence hint. Do not perform database or network work in this classifier.

## Persistence and concurrency

- Use the existing dirty-tracking/save lifecycle for ordinary mutations.
- A provider mutation must be durable or safely queued for durability before Avatarverse returns acceptance. Force a save where required by that guarantee.
- Store a bending-state schema version.
- Supply an opaque context token for the active Persona/PvP profile and a non-negative monotonic revision. Enforce expected-revision conflicts, especially across servers.
- Quit and shutdown saves must serialize Avatarverse state only; they must not snapshot arbitrary PK runtime collections.
- Redis messages should carry state version or invalidation information when bending changes can occur cross-server.

`flush` is an ordering/durability barrier, not a request for ProjectKorra to send a runtime snapshot. Handle `PLAYER_QUIT`, `PLUGIN_SHUTDOWN`, and legacy-save reasons without reconstructing authority from `BendingPlayer` collections.

## Legacy migration

Migration must be explicit and idempotent. Do not retain the current behavior where an empty Persona silently adopts the live PK elements.

For each PK UUID record:

1. Read legacy elements, subelements, binds, presets, cooldowns, and board preference.
2. Identify the Avatarverse player and candidate Persona.
3. Automatically attribute Persona-scoped data only if there is one unambiguous target.
4. Prefer already-populated Avatarverse Persona data when it conflicts; record the conflict.
5. Store account-scoped settings on `AvatarPlayer`.
6. Record source checksum, target ID, migration version, result, and timestamp.
7. Leave ambiguous records quarantined for staff review.

During dual-read validation, comparison is observational only. It must not repair Avatarverse from PK. Legacy tables remain available for rollback until the external-persistence release is proven stable.

## Diagnostics and administration

Extend activation and bending inspection to show:

- Active context and Persona ID.
- Persisted profile elements, subelements, loadout, and state version.
- `AvatarPlayer` active state.
- Live `BendingPlayer` projection.
- Runtime attachment and projection status.
- Last mutation source and last successful projection.
- Unknown identifiers and rejected PK mutations.
- Legacy migration status and conflict details.

Recovery actions must operate on Avatarverse state and then re-project it. Remove recommendations that infer chosen state solely from PK.

Provide an administrative `reapply` operation that replaces runtime state from Avatarverse without changing persisted data, plus a separately permissioned legacy-import operation for exceptional recovery.

ProjectKorra `/bending permaremove` is deliberately disabled in external mode. Avatarverse must provide any permanent-removal/reset administrative workflow it requires; the ProjectKorra `permaRemoved` flag is not part of the external contract.

Provider disable or unregistration makes all external players unavailable and requires a controlled restart before another provider can register. Avatarverse should treat accidental provider disable as a service-fatal condition rather than attempt hot replacement.

## Testing requirements

### Unit tests

- State serialization and schema upgrade.
- Unknown identifier retention.
- Complete replacement versus merge behavior.
- Mutation validation and rejected mutation rollback.
- Cooldown classification and expiration.
- Loadout validation.

### Integration tests

- Join with an existing Persona and empty PK runtime.
- Join with an elementless tutorial Persona.
- Switch between different elements, subelements, binds, and cooldowns.
- Switch from a configured Persona to an elementless Persona.
- Disconnect during each switch phase.
- PK-originated element, subelement, bind, and toggle changes.
- Avatarverse save failure and PK projection failure.
- Multiple rapid switches and re-entrant switch attempts.
- PvP profile attachment without an RPG Persona.

### Migration tests

- One UUID and one unambiguous Persona.
- Multiple Personae with a clear matching profile.
- Multiple ambiguous Personae.
- Avatarverse/PK conflicts.
- Unknown addon elements and abilities.
- Re-running a completed migration.

## Delivery sequence

ProjectKorra contract/fork work is complete. The sequence below now describes Avatarverse implementation, migration, integration testing, and rollout.

1. Add state model, schema versioning, diagnostics, and architectural tests without changing authority.
2. Consolidate current Persona/loadout access behind `AvatarBendingStateService`.
3. Convert all Core mutation paths away from PK save methods.
4. Add shadow legacy importer and comparison reports.
5. Integrate the fork's external-persistence API in dual-write/shadow mode.
6. Enable external-persistence reads for a test cohort.
7. Enable Avatarverse-only persistence with legacy PK tables retained read-only.
8. Remove compatibility adoption and dual-write code after the rollback window.

## Core acceptance criteria

- No Avatarverse production code calls a PK player-data persistence method.
- An empty Persona always clears the active PK projection and never imports PK data implicitly.
- Persona switching cannot leak the previous Persona's elements, subelements, binds, or persistent cooldowns.
- Restarting and reconnecting restore the same state exclusively from Avatarverse.
- PK commands and compatible addons cannot create untracked durable divergence.
- Legacy migration is idempotent, observable, and reversible during the rollout window.
- Existing tutorial readiness and recovery behavior works without PK database initialization.
