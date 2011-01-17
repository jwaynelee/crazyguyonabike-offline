package com.cgoab.offline.util;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.jar.Attributes.Name;

public class Utils {

	private static final int ORDER = 1024;

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

	public static void copy(InputStream in, OutputStream out) throws IOException {
		byte[] buff = new byte[1024 * 4];
		int bytes;
		while ((bytes = in.read(buff)) != 0) {
			out.write(buff, 0, bytes);
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

	public static void close(InputStream stream) {
		if (stream != null) {
			try {
				stream.close();
			} catch (IOException e) {
			}
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
		float mb = (float) kb / ORDER;
		return new DecimalFormat("###.#mb").format(mb);
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
