package com.projectkorra.projectkorra.persistence.external;

public record ExternalDataIssue(Category category, String path, String value, String message) {
	public enum Category { UNKNOWN_IDENTIFIER, INVALID_IDENTIFIER, INVALID_SLOT, INVALID_VALUE, INCOMPATIBLE_BIND, LIMIT_EXCEEDED }

	public ExternalDataIssue {
		if (category == null) category = Category.INVALID_VALUE;
		path = path == null ? "" : path;
		value = value == null ? "" : value;
		message = message == null ? "" : message;
	}
}
