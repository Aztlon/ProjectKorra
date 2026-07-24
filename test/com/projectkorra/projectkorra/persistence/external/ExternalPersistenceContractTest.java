package com.projectkorra.projectkorra.persistence.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.projectkorra.projectkorra.ExternalPersistenceCoordinator;

class ExternalPersistenceContractTest {
	@Test
	void modeParsingIsBackwardCompatibleAndCaseInsensitive() {
		assertEquals(PlayerDataMode.INTERNAL, PlayerDataMode.parse(null));
		assertEquals(PlayerDataMode.INTERNAL, PlayerDataMode.parse("not-a-mode"));
		assertEquals(PlayerDataMode.EXTERNAL, PlayerDataMode.parse(" external "));
	}

	@Test
	void snapshotDefensivelyCopiesEveryCollection() {
		final List<String> elements = new ArrayList<>(List.of("Fire"));
		final Map<Integer, String> abilities = new HashMap<>(Map.of(1, "FireBlast"));
		final Map<Integer, String> presetSlots = new HashMap<>(Map.of(1, "FireBlast"));
		final Map<String, Map<Integer, String>> presets = new HashMap<>(Map.of("main", presetSlots));
		final ExternalBendingPlayerData data = new ExternalBendingPlayerData("persona:1", 7, elements, List.of(), abilities,
				presets, Map.of("ChooseElement", 123L), true, Map.of("Fire", true), BoardPreference.ENABLED,
				UnknownIdentifierPolicy.STRICT);

		elements.clear(); abilities.clear(); presetSlots.clear(); presets.clear();
		assertEquals(List.of("Fire"), data.elements());
		assertEquals("FireBlast", data.abilities().get(1));
		assertEquals("FireBlast", data.presets().get("main").get(1));
		assertThrows(UnsupportedOperationException.class, () -> data.elements().add("Water"));
		assertThrows(UnsupportedOperationException.class, () -> data.abilities().put(2, "FireBurst"));
	}

	@Test
	void invalidSnapshotIdentityAndRevisionAreRejected() {
		assertThrows(IllegalArgumentException.class, () -> ExternalBendingPlayerData.empty(" ", 0));
		assertThrows(IllegalArgumentException.class, () -> ExternalBendingPlayerData.empty("persona:1", -1));
	}

	@Test
	void acceptedDecisionRequiresExactlyOneCanonicalResult() {
		final PlayerIdentity player = new PlayerIdentity(UUID.randomUUID(), "Aang", true);
		final BendingPlayerMutation mutation = new BendingPlayerMutation(player, "persona:1", 1,
				MutationSource.projectKorra("test"), new BendingPlayerMutationOperation.AddElement("Air"));
		assertTrue(MutationDecision.Accepted.mutation("persona:1", 2, mutation).canonicalMutation() != null);
		assertThrows(IllegalArgumentException.class, () -> new MutationDecision.Accepted("persona:1", 2, null, null));
		assertThrows(IllegalArgumentException.class, () -> new MutationDecision.Accepted("persona:1", 2, mutation,
				ExternalBendingPlayerData.empty("persona:1", 2)));
	}

	@Test
	void legacyMutationConstructorDefaultsToStandardIntent() {
		final PlayerIdentity player = new PlayerIdentity(UUID.randomUUID(), "Aang", true);
		final MutationSource commandLookingSource = new MutationSource(MutationSource.Kind.COMMAND, player.uuid(),
				"Aang", "ProjectKorra", "add");
		final BendingPlayerMutation mutation = new BendingPlayerMutation(player, "persona:1", 1,
				commandLookingSource, new BendingPlayerMutationOperation.AddElement("Air"));

		assertEquals(MutationIntent.STANDARD, mutation.intent());
	}

	@Test
	void explicitAdministrativeIntentParticipatesInDtoValueBehavior() {
		final PlayerIdentity player = new PlayerIdentity(UUID.randomUUID(), "Aang", true);
		final MutationSource source = MutationSource.projectKorra("authorized-command");
		final BendingPlayerMutationOperation operation = new BendingPlayerMutationOperation.RemoveSubelement("Blood");
		final BendingPlayerMutation standard = new BendingPlayerMutation(player, "persona:1", 1, source,
				MutationIntent.STANDARD, operation);
		final BendingPlayerMutation administrative = new BendingPlayerMutation(player, "persona:1", 1, source,
				MutationIntent.ADMINISTRATIVE, operation);
		final BendingPlayerMutation administrativeCopy = new BendingPlayerMutation(player, "persona:1", 1, source,
				MutationIntent.ADMINISTRATIVE, operation);

		assertTrue(!standard.equals(administrative));
		assertEquals(administrative, administrativeCopy);
		assertEquals(administrative.hashCode(), administrativeCopy.hashCode());
		assertTrue(administrative.toString().contains("intent=ADMINISTRATIVE"));
	}

	@Test
	void mutationIntentMayNotBeNull() {
		final PlayerIdentity player = new PlayerIdentity(UUID.randomUUID(), "Aang", true);
		assertThrows(NullPointerException.class, () -> new BendingPlayerMutation(player, "persona:1", 1,
				MutationSource.projectKorra("test"), null, new BendingPlayerMutationOperation.AddElement("Air")));
	}

	@Test
	void administrativeCoordinatorPathRequiresCommandCapability() {
		assertThrows(SecurityException.class, () -> ExternalPersistenceCoordinator.mutateFromAuthorizedCommand(null,
				new BendingPlayerMutationOperation.AddElement("Air"), MutationSource.projectKorra("add"), null));
	}

	@Test
	void mutationOperationsRejectMalformedDurableData() {
		assertThrows(IllegalArgumentException.class, () -> new BendingPlayerMutationOperation.SetBind(0, "FireBlast"));
		assertThrows(IllegalArgumentException.class, () -> new BendingPlayerMutationOperation.ReplaceBinds(Map.of(10, "FireBlast")));
		assertThrows(IllegalArgumentException.class, () -> new BendingPlayerMutationOperation.AddElement(" "));
		assertThrows(IllegalArgumentException.class, () -> new BendingPlayerMutationOperation.SetPersistentCooldown(
				"ChooseElement", -1, CooldownPersistence.PROFILE_PERSISTENT));
		assertThrows(IllegalArgumentException.class, () -> new BendingPlayerMutationOperation.SetPersistentCooldown(
				"ChooseElement", 1, CooldownPersistence.RUNTIME));
	}
}
