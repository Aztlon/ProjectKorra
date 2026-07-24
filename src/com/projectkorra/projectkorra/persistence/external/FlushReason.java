package com.projectkorra.projectkorra.persistence.external;

public enum FlushReason {
	LEGACY_SAVE,
	EXPLICIT,
	PLAYER_QUIT,
	PLUGIN_SHUTDOWN,
	PROVIDER_UNREGISTER
}
