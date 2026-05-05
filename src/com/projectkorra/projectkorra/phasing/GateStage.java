package com.projectkorra.projectkorra.phasing;

public enum GateStage {
	START("start"),
	TARGET_SELECT("target-select"),
	COLLISION("collision"),
	DAMAGE("damage"),
	BLOCK("block"),
	PARTICLE("particle"),
	SOUND("sound");

	private final String id;

	GateStage(final String id) {
		this.id = id;
	}

	public String getId() {
		return this.id;
	}
}
