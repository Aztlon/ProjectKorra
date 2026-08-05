package com.projectkorra.projectkorra.persistence.external;

import java.util.UUID;

public record MutationSource(Kind kind, UUID actorUuid, String actorName, String pluginName, String detail) {
	public enum Kind { COMMAND, ADDON, PROJECTKORRA, EXTERNAL_INTEGRATION, LEGACY_API }

	public MutationSource {
		if (kind == null) kind = Kind.LEGACY_API;
		actorName = actorName == null ? "" : actorName;
		pluginName = pluginName == null ? "unknown" : pluginName;
		detail = detail == null ? "" : detail;
	}

	public static MutationSource legacy(final String detail) {
		return new MutationSource(Kind.LEGACY_API, null, "", "unknown", detail);
	}

	public static MutationSource projectKorra(final String detail) {
		return new MutationSource(Kind.PROJECTKORRA, null, "", "ProjectKorra", detail);
	}
}
