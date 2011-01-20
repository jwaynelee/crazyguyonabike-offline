package com.cgoab.offline.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.List;
import java.util.jar.Attributes.Name;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class Utils {

	private static final int ORDER = 1024;

	public static void close(InputStream stream) {
		if (stream != null) {
			try {
				stream.close();
			} catch (IOException e) {
			}
		}
	}

	public static void close(OutputStream stream) {
		if (stream != null) {
			try {
				stream.close();
			} catch (IOException e) {
			}
		}
	}

	public static void copy(InputStream in, OutputStream out) throws IOException {
		byte[] buff = new byte[1024 * 4];
		int bytes;
		while ((bytes = in.read(buff)) > 0) {
			out.write(buff, 0, bytes);
		}
	}

	public static String formatBytes(long bytes) {
		if (bytes < ORDER) {
			return bytes + "b";
		}
		float kb = (float) bytes / ORDER;
		if (kb < ORDER) {
			return Math.round(kb) + "kb";
		}
		float mb = kb / ORDER;
		return new DecimalFormat("###.#mb").format(mb);
	}

	/**
	 * Searches for a method by name, ignores arguments.
	 * 
	 * @param name
	 * @param klass
	 * @return
	 */
	public static Method getFirstMethodWithName(String name, Class<?> klass) {
		Method[] methods = klass.getDeclaredMethods();
		for (Method method : methods) {
			if (method.getName().equals(name)) {
				return method;
			}
		}
		return null;
	}

	public static String getNameString(Class<?> klass) {
		URL jarUrl = klass.getProtectionDomain().getCodeSource().getLocation();
		try {
			Manifest manifest = new JarFile(jarUrl.getFile()).getManifest();
			return (String) manifest.getMainAttributes().get(Name.IMPLEMENTATION_TITLE);
		} catch (MalformedURLException e) {
			return null;
		} catch (IOException e) {
			return null;
		}
	}

	public static String getVersionString(Class<?> klass) {
		URL jarUrl = klass.getProtectionDomain().getCodeSource().getLocation();
		try {
			Manifest manifest = new JarFile(jarUrl.getFile()).getManifest();
			return (String) manifest.getMainAttributes().get(Name.IMPLEMENTATION_VERSION);
		} catch (MalformedURLException e) {
			return null;
		} catch (IOException e) {
			return null;
		}
	}

	public static void print(List<?> toPrint) {
		for (Object object : toPrint) {
			System.out.println(object);
		}
	}

	public static void copyFile(File fromFile, File toFile) throws IOException {
		FileInputStream in = null;
		FileOutputStream out = null;
		try {
			in = new FileInputStream(fromFile);
			out = new FileOutputStream(toFile);
			byte[] buff = new byte[8 * 1024]; // 4kb chunks
			int read;
			while ((read = in.read(buff)) > 0) {
				out.write(buff, 0, read);
			}
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					/* ignore */
				}
			}
			if (out != null) {
				try {
					out.close();
				} catch (IOException e) {
					/* ignore */
				}
			}
		}
	}

}
