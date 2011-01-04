package boot;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class Bootstrap {

	private static final String SWT_DIR = "libsswt/";

	private static final String LIBS_DIR = "libs/";

	private static final String SWT_JAR_VERSION = "3.6.1";

	// TODO load from MANIFEST.MF
	private static final String MAIN_KLASS = "com.cgoab.offline.Application";

	private static final String JARJAR_SCHEME = "jj";

	static class Connection extends URLConnection {

		ClassLoader loader;

		protected Connection(URL url, ClassLoader loader) {
			super(url);
			this.loader = loader;
		}

		@Override
		public void connect() throws IOException {
		}

		@Override
		public InputStream getInputStream() throws IOException {
			String file = url.getFile();
			InputStream s = loader.getResourceAsStream(file);
			if (s == null) {
				throw new MalformedURLException();
			}
			return s;
		}
	}

	static class Handler extends URLStreamHandler {

		private ClassLoader loader;

		public Handler(ClassLoader loader) {
			this.loader = loader;
		}

		@Override
		protected URLConnection openConnection(URL u) throws IOException {
			return new Connection(u, loader);
		}

		protected void parseURL(URL url, String spec, int start, int limit) {
			String file;
			if (spec.startsWith(JARJAR_SCHEME + ":")) //$NON-NLS-1$
				file = spec.substring(JARJAR_SCHEME.length() + 1);
			else if (url.getFile().equals("./")) //$NON-NLS-1$
				file = spec;
			else if (url.getFile().endsWith("/")) //$NON-NLS-1$
				file = url.getFile() + spec;
			else
				file = spec;
			setURL(url, JARJAR_SCHEME, "", -1, null, null, file, null, null); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	static class JJFactory implements URLStreamHandlerFactory {

		ClassLoader loader;

		public JJFactory(ClassLoader loader) {
			this.loader = loader;
		}

		@Override
		public URLStreamHandler createURLStreamHandler(String protocol) {
			if (JARJAR_SCHEME.equals(protocol)) {
				return new Handler(loader);
			}
			return null;
		}
	}

	private static String swtJarForPlatform() {
		String swtJar = "swt-" + SWT_JAR_VERSION + "-";
		if (isWindows()) {
			swtJar += "win32";
		} else if (isLinux()) {
			swtJar += "linux";
		} else if (isMac()) {
			swtJar += "osx";
		} else {
			throw new IllegalStateException("Platform is neither Windows or Linux");
		}

		if (is64bit()) {
			swtJar += "_64";
		}

		return swtJar + ".jar";
	}

	/**
	 * Adds URL handler for jj - loads resources from app classloader
	 * 
	 * Creates classpath for all entries in "libs", using "jar:jj:libs/foo.jar"
	 * format
	 * 
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		// find jars
		ClassLoader appLoader = ClassLoader.getSystemClassLoader();
		URL.setURLStreamHandlerFactory(new JJFactory(appLoader));
		URL source = Bootstrap.class.getProtectionDomain().getCodeSource().getLocation();
		JarFile file = new JarFile(source.getFile());
		Enumeration<JarEntry> files = file.entries();
		List<URL> classpath = new ArrayList<URL>();
		while (files.hasMoreElements()) {
			JarEntry e = files.nextElement();
			if (e.getName().startsWith(LIBS_DIR)) {
				classpath.add(new URL("jar:" + JARJAR_SCHEME + ":" + e.getName() + "!/"));
			}
		}
		URL swtJarUrl = new URL("jar:jj:" + SWT_DIR + swtJarForPlatform() + "!/");
		try {
			swtJarUrl.openConnection().connect();
		} catch (IOException e) {
			e.printStackTrace();
			System.err.println("SWT jar for this platform does not exist (looking for " + swtJarUrl + ")");
			System.exit(-1);
		}
		classpath.add(0, swtJarUrl);
		URL[] urls = classpath.toArray(new URL[0]);
		ClassLoader parent = ClassLoader.getSystemClassLoader().getParent();
		ClassLoader loader = new URLClassLoader(urls, parent);
		Thread.currentThread().setContextClassLoader(loader);
		Class<?> klass = loader.loadClass(MAIN_KLASS);
		Runnable app = (Runnable) klass.getMethod("defaultApplication").invoke(null);
		app.run();
	}

	private static boolean is64bit() {
		return System.getProperty("sun.arch.data.model").equals("64");
	}

	private static final String osname = System.getProperty("os.name").toLowerCase();

	public static boolean isWindows() {
		return osname.contains("win");

	}

	public static boolean isMac() {
		return osname.contains("mac");

	}

	public static boolean isLinux() {
		return osname.contains("linux");
	}
}
