package com.cgoab.offline.util;

public class Assert {
	public static void equals(String expected, String actual) {
		if (!expected.equals(actual)) {
			throw new AssertionError("Assertion Failed: " + expected + " != " + actual);
		}
	}

	public static void isFalse(boolean condition, String message) {
		if (condition) {
			throw new AssertionError("Assertion Failed: " + (message == null ? "expected false" : message));
		}
	}

	public static void isNull(Object o) {
		isNull(o, null);
	}

	public static void isNull(Object o, String message) {
		if (o != null) {
			throw new AssertionError("Assertion Failed: " + (message == null ? "expected null" : message));
		}
	}

	public static void isTrue(boolean condition) {
		isTrue(condition, null);
	}

	public static void isTrue(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError("Assertion Failed: " + (message == null ? "expected true" : message));
		}
	}

	public static void notEmpty(String name, String msg) {
		if (StringUtils.isEmpty(name)) {
			throw new AssertionError(msg);
		}
	}

	public static void notNull(Object o) {
		notNull(o, null);
	}

	public static void notNull(Object o, String msg) {
		if (o == null) {
			throw new AssertionError("Assertion Failed: " + (msg == null ? "expected non-null" : msg));
		}
	}

	public static void same(Object o1, Object o2, String msg) {
		if (o1 != o2) {
			throw new AssertionError(msg);
		}
	}
}
