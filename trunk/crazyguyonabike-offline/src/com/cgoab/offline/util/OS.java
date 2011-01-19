package com.cgoab.offline.util;

public class OS {
	private static final String osname = System.getProperty("os.name").toLowerCase();

	public static boolean isMac() {
		return osname.contains("mac");

	}

	public static boolean isUnix() {
		return osname.contains("nix") || osname.contains("nux");
	}

	public static boolean isWindows() {
		return osname.contains("win");

	}
}
