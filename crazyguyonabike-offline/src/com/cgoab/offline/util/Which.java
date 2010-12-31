package com.cgoab.offline.util;

import java.io.File;
import java.util.StringTokenizer;

/**
 * Searches for executables on the system PATH.
 */
public class Which {

	/**
	 * Returns the fully qualified path to the first occurrence of the command
	 * on the PATH or <tt>null</tt> if not found.
	 * 
	 * On windows checks for ".exe" in addition to raw command name.
	 * 
	 * @param command
	 *            command to search for
	 * @return
	 */
	public static String find(String command) {
		String paths = System.getenv("PATH");
		StringTokenizer tok = new StringTokenizer(paths, File.pathSeparator);
		while (tok.hasMoreTokens()) {
			String path = tok.nextToken();
			String cmdPath = path + File.separator + command;
			if (new File(cmdPath).exists()) {
				return cmdPath;
			}
			if (OS.isWindows() && new File(cmdPath + ".exe").exists()) {
				return cmdPath + ".exe";
			}
		}
		return null;
	}

	public static void main(String[] args) {
		System.out.println(Which.find("convert"));
	}
}
