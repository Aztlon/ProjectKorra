package com.projectkorra.projectkorra.persistence.external;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public sealed interface BendingPlayerMutationOperation permits
		BendingPlayerMutationOperation.AddElement, BendingPlayerMutationOperation.RemoveElement, BendingPlayerMutationOperation.ReplaceElements,
		BendingPlayerMutationOperation.AddSubelement, BendingPlayerMutationOperation.RemoveSubelement, BendingPlayerMutationOperation.ReplaceSubelements,
		BendingPlayerMutationOperation.SetBind, BendingPlayerMutationOperation.ReplaceBinds,
		BendingPlayerMutationOperation.UpsertPreset, BendingPlayerMutationOperation.DeletePreset,
		BendingPlayerMutationOperation.SetPersistentCooldown, BendingPlayerMutationOperation.RemovePersistentCooldown, BendingPlayerMutationOperation.ClearPersistentCooldowns,
		BendingPlayerMutationOperation.SetBendingEnabled, BendingPlayerMutationOperation.SetElementEnabled,
		BendingPlayerMutationOperation.SetBoardPreference, BendingPlayerMutationOperation.Reset {

	record AddElement(String element) implements BendingPlayerMutationOperation { public AddElement { element = identifier(element, "element"); } }
	record RemoveElement(String element) implements BendingPlayerMutationOperation { public RemoveElement { element = identifier(element, "element"); } }
	record ReplaceElements(List<String> elements) implements BendingPlayerMutationOperation { public ReplaceElements { elements = identifiers(elements, "elements"); } }
	record AddSubelement(String subelement) implements BendingPlayerMutationOperation { public AddSubelement { subelement = identifier(subelement, "subelement"); } }
	record RemoveSubelement(String subelement) implements BendingPlayerMutationOperation { public RemoveSubelement { subelement = identifier(subelement, "subelement"); } }
	record ReplaceSubelements(List<String> subelements) implements BendingPlayerMutationOperation { public ReplaceSubelements { subelements = identifiers(subelements, "subelements"); } }
	record SetBind(int slot, String ability) implements BendingPlayerMutationOperation { public SetBind { validateSlot(slot); if (ability != null) ability = identifier(ability, "ability"); } }
	record ReplaceBinds(Map<Integer, String> abilities) implements BendingPlayerMutationOperation { public ReplaceBinds { abilities = slots(abilities); } }
	record UpsertPreset(String name, Map<Integer, String> abilities) implements BendingPlayerMutationOperation { public UpsertPreset { name = identifier(name, "name"); abilities = slots(abilities); } }
	record DeletePreset(String name) implements BendingPlayerMutationOperation { public DeletePreset { name = identifier(name, "name"); } }
	record SetPersistentCooldown(String key, long expiration, CooldownPersistence scope) implements BendingPlayerMutationOperation {
		public SetPersistentCooldown { key = identifier(key, "key"); if (expiration < 0) throw new IllegalArgumentException("expiration may not be negative"); scope = Objects.requireNonNull(scope, "scope"); if (!scope.isPersistent()) throw new IllegalArgumentException("scope must be persistent"); }
	}
	record RemovePersistentCooldown(String key) implements BendingPlayerMutationOperation { public RemovePersistentCooldown { key = identifier(key, "key"); } }
	record ClearPersistentCooldowns() implements BendingPlayerMutationOperation {}
	record SetBendingEnabled(boolean enabled) implements BendingPlayerMutationOperation {}
	record SetElementEnabled(String element, boolean enabled) implements BendingPlayerMutationOperation { public SetElementEnabled { element = identifier(element, "element"); } }
	record SetBoardPreference(BoardPreference preference) implements BendingPlayerMutationOperation { public SetBoardPreference { preference = Objects.requireNonNull(preference, "preference"); } }
	record Reset() implements BendingPlayerMutationOperation {}

	private static String identifier(final String value, final String name) {
		if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " may not be blank");
		return value;
	}

	private static List<String> identifiers(final List<String> values, final String name) {
		final List<String> copy = List.copyOf(Objects.requireNonNull(values, name));
		copy.forEach(value -> identifier(value, name));
		return copy;
	}

	private static Map<Integer, String> slots(final Map<Integer, String> values) {
		final Map<Integer, String> copy = Map.copyOf(Objects.requireNonNull(values, "abilities"));
		copy.forEach((slot, ability) -> { validateSlot(slot); identifier(ability, "ability"); });
		return copy;
	}

	private static void validateSlot(final int slot) {
		if (slot < 1 || slot > 9) throw new IllegalArgumentException("slot must be between 1 and 9");
	}
}
