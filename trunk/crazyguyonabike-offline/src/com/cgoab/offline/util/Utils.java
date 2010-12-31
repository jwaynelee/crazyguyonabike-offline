package com.cgoab.offline.util;

import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.util.List;

public class Utils {

	private static final int ORDER = 1024;

	public static String formatBytes(long bytes) {
		if (bytes < ORDER) {
			return bytes + "b";
		}
		float kb = (float) bytes / ORDER;
		if (kb < ORDER) {
			return Math.round(kb) + "KB";
		}
		float mb = (float) kb / ORDER;
		return new DecimalFormat("###.#MB").format(mb);
	}

	public static void print(List<?> toPrint) {
		for (Object object : toPrint) {
			System.out.println(object);
		}
	}

	/**
	 * Searches for a method by name, ignores arguments.
	 * 
	 * @param name
	 * @param klass
	 * @return
	 */
	public static Method getFirstMethodWithName(String name, Class<?> klass) {
		Method[] methods = klass.getMethods();
		for (Method method : methods) {
			if (method.getName().equals(name)) {
				return method;
			}
		}
		return null;
	}
}
