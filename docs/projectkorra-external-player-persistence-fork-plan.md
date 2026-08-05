# ProjectKorra Fork Plan — Externally Persisted Bending Players

## Purpose

Add an external player-persistence mode to the Avatarverse ProjectKorra fork. In this mode ProjectKorra continues to own `BendingPlayer` runtime behavior, but it does not load or save durable player state in ProjectKorra tables. Avatarverse supplies the active profile and receives persistence-relevant mutations.

This document defines the ProjectKorra side of the contract. The Avatarverse implementation is described in [Avatarverse Core Plan — AvatarPlayer-Persisted Bending Data](avatarplayer-bending-persistence-core-plan.md).

## Implementation status (July 2026)

The fork changes described here are implemented. The public contract is under `com.projectkorra.projectkorra.persistence.external`, with contract version `2`. `Storage.PlayerDataMode` defaults to `INTERNAL` and is captured once during enable; reload warns when configuration differs and requires a restart.

Implemented external ownership covers elements, subelements, active binds, personal named presets, classified persistent cooldowns, bending/element toggles, and board preference. `/bending permaremove` is disabled in external mode. Internal mode retains its legacy storage behavior.

All runtime SQL mentioning `pk_players`, `pk_presets`, `pk_cooldowns`, or `pk_board` is confined to the internal storage/schema boundary. External mode skips player schema inspection and legacy cooldown conversion, and a database-level guard rejects accidental player-table access.

## Design boundary

ProjectKorra remains responsible for:

- Constructing and caching `BendingPlayer` runtime objects.
- Ability execution, permission checks, bindability, passives, and runtime cooldown evaluation.
- Runtime element, subelement, binding, toggle, and cooldown APIs.
- Existing public API compatibility wherever practical.

ProjectKorra relinquishes ownership of:

- Durable elements and subelements.
- Durable ability binds and presets.
- Persistent cooldowns.
- Durable bending and element toggle state.
- Bending-board preference when supplied externally.

The fork must not depend directly on Avatarverse classes. The contract belongs in a small ProjectKorra API package so another integration could provide it.

## Configuration and activation

Add an explicit mode, disabled by default for upstream compatibility:

```yaml
Storage:
  PlayerDataMode: INTERNAL # INTERNAL or EXTERNAL
```

`INTERNAL` retains existing behavior. `EXTERNAL` requires one registered provider before player initialization begins. If no provider is available, initialization must fail closed with a precise phase and error; it must not fall back to stale PK tables.

The selected mode should be immutable after player initialization begins. Changing it requires a controlled restart.

## External persistence API

The implemented provider contract is:

```java
public interface ExternalBendingPlayerProvider {
    String providerName();
    String providerVersion();
    int contractVersion();
    CompletionStage<ExternalBendingPlayerData> load(PlayerIdentity player);
    CompletionStage<MutationDecision> requestMutation(BendingPlayerMutation mutation);
    CompletionStage<Void> flush(PlayerIdentity player, FlushReason reason);
    CooldownPersistence classifyCooldown(PlayerIdentity player,
                                           String key,
                                           boolean persistenceRequested);
}
```

The data transfer object must contain stable strings and primitive values, not implementation-specific `Element` instances:

```java
public record ExternalBendingPlayerData(
        String contextToken,
        long revision,
        List<String> elements,
        List<String> subelements,
        Map<Integer, String> abilities,
        Map<String, Map<Integer, String>> presets,
        Map<String, Long> persistentCooldownExpirations,
        boolean bendingEnabled,
        Map<String, Boolean> elementEnabled,
        BoardPreference boardPreference,
        UnknownIdentifierPolicy unknownIdentifierPolicy
) {}
```

The API includes single-provider registration, owner-only unregistration, provider inspection, and contract-version validation. Registration stays open through dependent-plugin startup and closes immediately before external initialization. Late registration or replacement is rejected until restart.

## Initialization lifecycle

In `EXTERNAL` mode, replace the database-backed player-load phases with:

1. Create or reserve the `BendingPlayer` runtime entry in a non-ready state.
2. Ask the external provider to load active data.
3. Resolve known element and subelement identifiers.
4. Apply the complete snapshot without invoking persistence callbacks.
5. Apply binds, persistent cooldowns, and toggles.
6. Complete normal derived-state setup.
7. Mark the player ready.
8. Fire `BendingPlayerCreationEvent` only after the external snapshot is applied.

Add initialization phases that identify external failures, for example:

- `EXTERNAL_PROVIDER_UNAVAILABLE`
- `EXTERNAL_DATA_LOAD`
- `EXTERNAL_DATA_VALIDATION`
- `EXTERNAL_DATA_APPLY`

The existing retry API should repeat external loading safely. A failed load must not leave a usable partially initialized player.

Unknown element, subelement, or ability identifiers should be reported to the provider and logs. Known portions may be applied only if the provider marks partial application safe; otherwise validation should fail atomically.

## Persistence suppression

Every PK path that accesses player-data tables must respect `PlayerDataMode`.

In `EXTERNAL` mode:

- Do not read or write `pk_players` player state.
- Do not read or write `pk_presets`.
- Do not read or write persistent entries in `pk_cooldowns`.
- Do not read or write `pk_board` player preferences when externally owned.
- Do not create default rows as a side effect of joining.
- Do not delete external state during PK player removal or reset commands.

World, ability, configuration, and unrelated global ProjectKorra storage remain internal unless separately migrated.

Audit scheduled tasks, quit handlers, shutdown handlers, commands, preset services, board services, and `OfflineBendingPlayer`; suppressing only `BendingPlayer.saveElements()` is insufficient.

## Mutation protocol

Existing addons expect calls such as `addElement`, `setAbility`, `addCooldown`, and save methods to work. The fork must preserve useful source compatibility while preventing untracked divergence.

### Provider-mediated durable mutation

For persistence-relevant changes initiated through PK APIs:

1. Build a typed `BendingPlayerMutation` containing player UUID, operation, values, source, mutation intent, and expected external revision when available. Ordinary gameplay and addon APIs always use `STANDARD`. Element/subelement add/remove commands use `ADMINISTRATIVE` only after ProjectKorra's normal sender/target permission checks succeed; the free-form source is never used to infer authority. This is solely ProjectKorra's attestation about the authorized command origin. The external provider decides what `ADMINISTRATIVE` permits and must still enforce context ownership, revision consistency, structural validation, permanent-removal rules, persistence success, and canonical cleanup.
2. Submit it to the provider.
3. Await a final accepted, rejected, conflict, or failed decision according to the API's thread contract.
4. Apply the accepted canonical result to runtime state.
5. If rejected, retain or restore the previous runtime state and notify the command/addon caller when possible.

Required mutation types include:

- Replace/add/remove element.
- Replace/add/remove subelement.
- Set/clear one bind and replace all binds.
- Create/update/delete provider-backed personal presets.
- Add/remove a persistence-qualified cooldown.
- Change bending toggle or per-element toggle.
- Change externally owned board preference.
- Reset externally persisted player data.

Batch replacement is required for Persona switching. It must apply without emitting one provider mutation per collection entry.

### Projection guard

Provide an internal/exposed projection method:

```java
CompletionStage<ApplyResult> applyExternalSnapshot(
        Player player,
        ExternalBendingPlayerData data,
        ApplyReason reason
);
```

Applying an externally supplied snapshot must suppress outbound mutation callbacks and all internal persistence. Use a scoped guard that is safe across failure paths; avoid a global boolean.

### Legacy save methods

In `EXTERNAL` mode, methods such as `saveElements`, `saveSubElements`, and `saveAbility` must not touch SQL.

Preferred behavior:

- Mutation methods request provider approval and persistence.
- A subsequent legacy `save*` call becomes an idempotent provider flush or no-op because the mutation is already represented.
- Log rate-limited compatibility warnings identifying addons that rely on explicit saves.

Do not reconstruct authoritative state by snapshotting the entire runtime object during every `save*` call. Runtime collections can contain transient or partially applied data.

## Threading contract

Clearly document which provider calls occur on the server thread and which may complete asynchronously.

- Database or network work must not block the server thread.
- Runtime `BendingPlayer` mutation and Bukkit interactions must return to the server thread.
- Each player needs serialized initialization and mutation ordering.
- A mutation submitted against revision N must not overwrite revision N+1.
- Player logout during a pending request must cancel or safely complete it without recreating a detached runtime.
- Provider callbacks must have timeouts and structured failure results.

No callback should hold a ProjectKorra global lock while awaiting external work.

## Runtime replacement semantics

The snapshot apply operation must replace, not merge:

- Elements.
- Subelements.
- Ability binds.
- Externally persistent cooldowns.
- Bending and element toggles.
- Externally owned board preference.

Replacement must remove state belonging to the previously active Persona. It must then refresh all derived behavior, including passives and any caches dependent on element membership or binds.

The fork should expose one supported operation for this instead of requiring Avatarverse to mutate internal collections directly.

An empty snapshot is valid and represents an intentionally elementless profile. It must clear runtime bending state and still complete initialization successfully.

## Cooldown integration

Most ability cooldowns should remain runtime-only. Add a configurable/provider-defined predicate that determines whether a cooldown mutation is persistence-relevant.

For persistent cooldowns:

- Transfer absolute expiration timestamps.
- Ignore expired timestamps during apply.
- Notify the provider on add, remove, and reset.
- Preserve unknown cooldown keys as opaque strings.

For runtime-only cooldowns:

- Do not contact the provider.
- Preserve current performance and behavior.
- Clear them when the runtime is discarded or when the external integration requests a profile replacement policy that clears transient cooldowns.

The external API should allow Avatarverse to classify account-wide versus Persona-scoped cooldowns, but PK need only supply the key, expiration, and mutation source.

## Commands and offline access

Audit all PK commands that modify player data.

- Online commands must use the mutation protocol.
- Offline mutations require an explicit provider offline-mutation API or must be rejected with a clear message.
- Commands must not instantiate `OfflineBendingPlayer` from legacy SQL in external mode.
- Reset and delete commands must identify their scope and require provider acceptance.
- Read commands should query the live runtime when online and the provider when offline.

Personal preset commands use external preset support. Personal presets are loaded in the provider snapshot and create/update/delete through typed mutations. Applying either a personal preset or a configuration-defined global preset submits one `ReplaceBinds` mutation. No `Preset` SQL path executes in external mode.

The Avatarverse provider must therefore persist the `presets` map or provide a lossless mapping to its loadout model. ProjectKorra does not migrate legacy `pk_presets` rows.

## Events and observability

Add events or structured callbacks for:

- External player load started/completed/failed.
- External snapshot applied.
- Durable mutation accepted/rejected/failed.
- Runtime/provider revision mismatch.
- Unknown identifiers encountered.

Expose diagnostics containing:

- Player data mode.
- Provider name and contract version.
- Initialization state and phase.
- External revision.
- Last successful load and mutation.
- Pending request count.
- Last provider failure.
- Whether a projection guard is active.

Logs must include UUID, operation, context/source, revision, and failure category without dumping sensitive or excessively large state.

## Compatibility requirements

- Preserve `BendingPlayer` lookup and ability-facing APIs.
- Preserve `BendingPlayerCreationEvent` semantics, with the stronger guarantee that supplied state is already applied.
- Maintain existing behavior in `INTERNAL` mode.
- Addons using standard mutation methods should be provider-aware without recompilation where binary compatibility permits.
- Addons directly mutating returned collections cannot be made reliable automatically. Deprecate mutable collection exposure where possible and provide instrumented mutation methods.
- During transition, add a diagnostic scanner or warnings for collection changes followed by legacy `save*` calls.

## Security and failure behavior

- Fail closed when external persistence is configured but unavailable.
- Never fall back from external mode to legacy PK player tables automatically.
- Validate snapshot sizes, slot ranges, identifiers, and expiration timestamps.
- Treat provider exceptions as initialization/mutation failures, not permission to apply unpersisted state.
- Ensure a rejected mutation cannot momentarily grant an element or bind long enough to cast an ability.
- Rate-limit retries and compatibility warnings.

## Testing requirements

The Maven build now has a dedicated `test/` source root, JUnit 5, and Surefire. Contract tests cover immutable DTO behavior and mutation validation. SQL-guard and architectural tests enforce that player-table literals stay inside the internal storage/schema boundary. The fork also builds successfully as a packaged plugin.

The scenario lists below remain the required live integration matrix for the Avatarverse provider and server test harness; pure unit tests cannot exercise Bukkit lifecycle, Persona switching, or a real provider/database combination by themselves.

### Internal-mode regression

- Existing SQL load/save behavior remains unchanged.
- Existing commands, presets, cooldowns, board preferences, and initialization events retain their behavior.

### External initialization

- Successful populated snapshot.
- Successful empty snapshot.
- Missing provider.
- Provider timeout or exception.
- Unknown identifiers.
- Retry after failure.
- Disconnect during load.
- No legacy table reads or writes.

### Mutation tests

- Accept and reject each mutation type.
- Mutation ordering and revision conflicts.
- Snapshot projection does not echo mutations.
- Legacy `save*` calls do not access SQL.
- Direct PK commands use the provider.
- Concurrent mutation and profile replacement.
- Addon-style mutation followed by explicit save.

### Profile replacement

- Fire profile to Water profile leaves no Fire state.
- Configured profile to empty profile clears everything relevant.
- Binds and subelements invalid for the new elements are absent.
- Persistent cooldown replacement and runtime-only cooldown policy.
- Passives and board state refresh correctly.

## Delivery sequence

Steps 1–6 are implemented in the fork. Steps 7–8 are integration/rollout work for Avatarverse and the server environment.

1. Inventory every player-table read/write and publish the audit with tests around the current behavior.
2. Introduce `PlayerDataMode`, provider API, DTOs, mutation types, and diagnostics with internal mode still default.
3. Implement external initialization and atomic snapshot application.
4. Route elements, subelements, and binds through the mutation protocol.
5. Route presets, selected cooldowns, toggles, and board preference.
6. Audit offline commands, scheduled saves, quit, shutdown, reset, and deletion.
7. Run Avatarverse dual-mode/shadow tests while PK legacy tables remain untouched.
8. Enable external mode for a controlled cohort, then globally.

## Fork acceptance criteria

- External mode performs zero player-state reads or writes against ProjectKorra player tables.
- A provider-supplied empty profile initializes successfully and remains empty.
- `BendingPlayerCreationEvent` fires only after the external snapshot is fully applied.
- Complete profile replacement cannot leak state from the prior profile.
- Provider-rejected mutations cannot be used by abilities or later persisted accidentally.
- Standard PK commands and addon mutation APIs either persist through the provider or fail explicitly.
- Internal mode passes its existing regression suite unchanged.
- Provider failures are diagnosable, retryable, and never trigger silent legacy fallback.
