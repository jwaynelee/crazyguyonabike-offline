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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Utils {

	private static final int COPY_BUFFER_SIZE = 1024 * 4; /* copy 4kb chunks */

	private static final int ORDER = 1024;

	public static void close(InputStream stream) {
		if (stream != null) {
			try {
				stream.close();
			} catch (IOException e) {
				/* ignore */
			}
		}
	}

	public static void close(OutputStream stream) {
		if (stream != null) {
			try {
				stream.close();
			} catch (IOException e) {
				/* ignore */
			}
		}
	}

	/**
	 * Copies from from one stream to another, does NOT close the streams.
	 * 
	 * @param in
	 * @param out
	 * @throws IOException
	 */
	public static void copy(InputStream in, OutputStream out) throws IOException {
		byte[] buff = new byte[COPY_BUFFER_SIZE];
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
	 * Searches for the first (declared) method with the given name, ignores
	 * arguments.
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

	/**
	 * Returns implementation titiel from MANIFEST, something like
	 * "cgoab-offline".
	 * 
	 * @param klass
	 * @return
	 */
	public static String getImplementationTitleString(Class<?> klass) {
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

	/**
	 * Returns the implementation version from the MANIFEST for the given class,
	 * something like "0.1.0 2011-01-01".
	 * 
	 * @param klass
	 * @return
	 */
	public static String getImplementationVersion(Class<?> klass) {
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

	/**
	 * Returns the specification version from the MANIFEST for the given class,
	 * something like "0.1.0".
	 * 
	 * @param klass
	 * @return
	 */
	public static String getSpecificationVersion(Class<?> klass) {
		URL jarUrl = klass.getProtectionDomain().getCodeSource().getLocation();
		try {
			Manifest manifest = new JarFile(jarUrl.getFile()).getManifest();
			return (String) manifest.getMainAttributes().get(Name.SPECIFICATION_VERSION);
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

	/**
	 * Copies the contents of one file to another.
	 * 
	 * @param fromFile
	 * @param toFile
	 * @throws IOException
	 */
	public static void copyFile(File fromFile, File toFile) throws IOException {
		FileInputStream in = null;
		FileOutputStream out = null;
		try {
			in = new FileInputStream(fromFile);
			out = new FileOutputStream(toFile);
			byte[] buff = new byte[COPY_BUFFER_SIZE];
			int read;
			while ((read = in.read(buff)) > 0) {
				out.write(buff, 0, read);
			}
		} finally {
			close(in);
			close(out);
		}
	}

}
