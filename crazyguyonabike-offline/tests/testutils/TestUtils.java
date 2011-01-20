package testutils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import testutils.photos.TestPhotos;

import com.cgoab.offline.util.Assert;
import com.cgoab.offline.util.Utils;

public class TestUtils {

	/* TODO map to test name so we get new counter for each test? */
	private static final AtomicInteger counter = new AtomicInteger();

	/* {temp}/cgoaboffline-test-123456789/ */
	public static final File TEST_DATA_DIRECTORY;

	static {
		String tmpDir = System.getProperty("java.io.tmpdir");
		String folder = "cgoaboffline-test-" + System.currentTimeMillis();
		TEST_DATA_DIRECTORY = new File(tmpDir + File.separator + folder + File.separator);
		TEST_DATA_DIRECTORY.mkdirs();
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				delete(TEST_DATA_DIRECTORY);
			}

			private void delete(File file) {
				if (file.isDirectory()) {
					/* reach file */
					for (File child : file.listFiles()) {
						delete(child);
					}
					/* delete directory */
					if (!file.delete()) {
						System.err.println("Failed to remove test dir " + file.getAbsolutePath());
					}
				} else {
					/* delete file */
					if (!file.delete()) {
						System.err.println("Failed to remove test file " + file.getAbsolutePath());
					}
				}
			}
		});
	}

	/**
	 * Returns a temporary directory unique to the currently running test method
	 * (fails if the thread is not inside a test method - {@link #getTestName()}
	 * ).
	 * 
	 * @return
	 * @throws IOException
	 */
	public static File getTestTempDirectory() throws IOException {
		File f = new File(TEST_DATA_DIRECTORY + File.separator + TestUtils.getTestName() + File.separator);
		if (!f.exists() && !f.mkdirs()) {
			throw new IOException("Failed to create " + f.getAbsolutePath());
		}
		return f;
	}

	/**
	 * Returns location of currently running test (first method in callstack
	 * with @Test) in format ${package}.${class}_${method}
	 * 
	 * @return
	 */
	public static String getTestName() {
		StackTraceElement[] trace = Thread.currentThread().getStackTrace();
		for (StackTraceElement e : trace) {
			if (isTestMethod(e)) {
				return e.getClassName() + "_" + e.getMethodName();
			}
		}
		throw new IllegalStateException("No @Test annotated void method found in stack");
	}

	private static boolean isTestMethod(StackTraceElement el) {
		try {
			Class<?> klass = TestUtils.class.getClassLoader().loadClass(el.getClassName());
			Method method = Utils.getFirstMethodWithName(el.getMethodName(), klass);
			if (method == null) {
				throw new IllegalStateException("no method [" + el.getMethodName() + "] found");
			}
			return method.getAnnotation(Test.class) != null;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Creates a new temp File (but does not create a file on the filesystem).
	 * 
	 * Returned file name looks like 0_${source}, where 0 is replaced by current
	 * counter.
	 * 
	 * @param source
	 * @return
	 * @throws IOException
	 */
	public static String createTempFileName(String source) throws IOException {
		File f = new File(getTestTempDirectory().getAbsolutePath() + File.separator + counter.getAndIncrement() + "_"
				+ source);
		Assert.isTrue(!f.exists());
		return f.getAbsolutePath();
	}
}
