package com.projectkorra.projectkorra.persistence.external;

public enum CooldownPersistence {
	RUNTIME,
	PROFILE_PERSISTENT,
	ACCOUNT_PERSISTENT;

	public boolean isPersistent() {
		return this != RUNTIME;
	}
}
