package com.projectkorra.projectkorra.phasing;

public class GateDecision {

	private final boolean allowed;
	private final String reasonCode;

	public GateDecision(final boolean allowed, final String reasonCode) {
		this.allowed = allowed;
		this.reasonCode = reasonCode == null ? GateReasonCode.UNKNOWN : reasonCode.toUpperCase();
	}

	public static GateDecision allow() {
		return new GateDecision(true, GateReasonCode.ALLOW);
	}

	public static GateDecision deny(final String reasonCode) {
		return new GateDecision(false, reasonCode);
	}

	public static GateDecision unknown() {
		return new GateDecision(false, GateReasonCode.UNKNOWN);
	}

	public boolean isAllowed() {
		return this.allowed;
	}

	public String getReasonCode() {
		return this.reasonCode;
	}
}
