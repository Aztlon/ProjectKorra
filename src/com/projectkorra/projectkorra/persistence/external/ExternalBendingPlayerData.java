package com.projectkorra.projectkorra.persistence.external;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

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
		UnknownIdentifierPolicy unknownIdentifierPolicy) {

	public ExternalBendingPlayerData {
		contextToken = Objects.requireNonNull(contextToken, "contextToken");
		if (contextToken.isBlank()) throw new IllegalArgumentException("contextToken may not be blank");
		if (revision < 0) throw new IllegalArgumentException("revision may not be negative");
		elements = List.copyOf(Objects.requireNonNull(elements, "elements"));
		subelements = List.copyOf(Objects.requireNonNull(subelements, "subelements"));
		abilities = Map.copyOf(new TreeMap<>(Objects.requireNonNull(abilities, "abilities")));
		final Map<String, Map<Integer, String>> presetCopies = new java.util.LinkedHashMap<>();
		Objects.requireNonNull(presets, "presets").forEach((name, slots) -> presetCopies.put(name, Map.copyOf(new TreeMap<>(slots))));
		presets = Map.copyOf(presetCopies);
		persistentCooldownExpirations = Map.copyOf(Objects.requireNonNull(persistentCooldownExpirations, "persistentCooldownExpirations"));
		elementEnabled = Map.copyOf(Objects.requireNonNull(elementEnabled, "elementEnabled"));
		boardPreference = boardPreference == null ? BoardPreference.UNSPECIFIED : boardPreference;
		unknownIdentifierPolicy = unknownIdentifierPolicy == null ? UnknownIdentifierPolicy.STRICT : unknownIdentifierPolicy;
	}

	public static ExternalBendingPlayerData empty(final String contextToken, final long revision) {
		return new ExternalBendingPlayerData(contextToken, revision, List.of(), List.of(), Map.of(), Map.of(), Map.of(), true, Map.of(), BoardPreference.UNSPECIFIED, UnknownIdentifierPolicy.STRICT);
	}
}
