package com.projectkorra.projectkorra.persistence.external;

import java.util.List;

public record ApplyResult(Status status, long revision, List<ExternalDataIssue> issues, String message, Throwable cause) {
	public enum Status { APPLIED, REJECTED, FAILED, SUPERSEDED }
	public ApplyResult { issues = issues == null ? List.of() : List.copyOf(issues); }
	public boolean applied() { return this.status == Status.APPLIED; }
	public static ApplyResult applied(final long revision, final List<ExternalDataIssue> issues) { return new ApplyResult(Status.APPLIED, revision, issues, "", null); }
}
