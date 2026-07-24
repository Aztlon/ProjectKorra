package com.projectkorra.projectkorra.persistence.external;

import java.util.Objects;
import java.util.UUID;

public record PlayerIdentity(UUID uuid, String lastKnownName, boolean online) {
	public PlayerIdentity {
		Objects.requireNonNull(uuid, "uuid");
		lastKnownName = lastKnownName == null ? uuid.toString() : lastKnownName;
	}
}
