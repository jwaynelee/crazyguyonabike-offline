package com.cgoab.offline.util;

import java.util.List;

public class StringUtils {

	public static String capitalise(String str) {
		if (str == null) {
			throw new NullPointerException();
		}
		if ("".equals(str)) {
			return str;
		}
		if (str.length() < 2) {
			return str.substring(0, 1).toUpperCase();
		}
		return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
	}

	public static boolean isEmpty(String s) {
		return s == null || "".equals(s);
	}

	public static String join(List<String> strings, String separator) {
		StringBuilder buff = new StringBuilder();
		for (String string : strings) {
			buff.append(string).append(separator);
		}
		return buff.toString();
	}

	public static String nullToEmpty(String s) {
		return s == null ? "" : s;
	}

	public static String trimToNull(String str) {
		if (str == null) {
			return null;
		}
		String trimmed = str.trim();
		return "".equals(trimmed) ? null : trimmed;
	}
}
