package com.cgoab.offline.util.resizer;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cgoab.offline.util.FutureCompletionListener;
import com.cgoab.offline.util.ListenableCancellableTask;
import com.cgoab.offline.util.OS;
import com.cgoab.offline.util.StringUtils;
import com.cgoab.offline.util.Which;

/**
 * Wrapper task around ImageMagick "convert" process.
 */
public class ImageMagickResizeTask extends ListenableCancellableTask<File> {

	private static final Logger LOG = LoggerFactory.getLogger(ImageMagickResizeTask.class);

	public static final String MAGICK_COMMAND = "convert";

	/* windows provides a "convert" command, mogrify is less common */
	private static final String MAGICK_COMMAND_TO_CHECK_INSTALLATION = "mogrify";

	/*
	 * Version with "Fill Area Flag ('^' flag)"
	 * 
	 * http://www.imagemagick.org/Usage/resize/#fill
	 */
	public static final MagickVersion REQUIRED_VERSION = new MagickVersion(6, 3, 8, 3);

	/*
	 * Example
	 * 
	 * Version: ImageMagick 6.6.6-7 2010-12-22 Q8 http://www.imagemagick.org
	 * Copyright: Copyright (C) 1999-2011 ImageMagick Studio LLC Features:
	 * OpenMP
	 */
	private static final Pattern VERSION_PATTERN = Pattern
			.compile("Version: ImageMagick (\\d)\\.(\\d)\\.(\\d)-(\\d)?.*");

	private static final int JPEG_QUALITY = 70;

	private final File source, destination;
	private final String cmdPath;
	private final Object lock = new Object();
	private Process process; /* guarded by lock */

	public ImageMagickResizeTask(String cmd, File source, File destination, FutureCompletionListener<File> listener,
			Object data) {
		super(listener, data);
		this.cmdPath = cmd;
		this.source = source;
		this.destination = destination;
	}

	@Override
	public File call() throws IOException, InterruptedException {
		/* first check if the photo was already resized */
		if (destination.exists()) {
			if (destination.lastModified() >= source.lastModified()) {
				// already resized
				LOG.debug("Resized photo {} already exists, skipping", destination.getName());
				return destination;
			}
			// delete the old resized image and start again
			destination.delete();
		}

		List<String> args = new ArrayList<String>();
		args.add(cmdPath);
		args.add(source.getAbsolutePath());
		args.add("-resize");
		/* shrink shortest side to 1000px but don't enlarge */
		args.add("1000x1000^>");
		args.add("-quality");
		args.add(String.valueOf(JPEG_QUALITY));
		args.add(destination.getAbsolutePath());
		ProcessBuilder pb = new ProcessBuilder(args);
		pb.redirectErrorStream(true);

		LOG.debug("Launching process [{}]", StringUtils.join(args, " "));
		synchronized (lock) {
			/* publish the process so it can be cancelled from another thread */
			process = pb.start();
		}
		BufferedReader os = new BufferedReader(new InputStreamReader(process.getInputStream()));
		StringBuilder buff = new StringBuilder();
		String line;

		/*
		 * If the process writes to stdout it may fill its buffer and block
		 * waiting for its parent (us) to drain it. If we call waitFor() first
		 * then we deadlock. So we first make sure to drain stdout until the
		 * process closes it (usually when it is complete). Thus waitFor() will
		 * usually return immediately (process already finished). A side affect
		 * of this is that we cannot be interrupted (I/O is not interrupted).
		 */
		while ((line = os.readLine()) != null) {
			LOG.debug(">> " + line);
			buff.append(line).append("\n");
		}
		os.close();
		LOG.debug("Waiting for process completion");
		int code;

		try {
			code = process.waitFor();
		} catch (InterruptedException e) {
			/* If the process was cancelled then waitFor() fails with an IE */
			LOG.debug("Interrupted whilst waiting for exit code");
			throw e;
		} finally {
			synchronized (lock) {
				process = null;
			}
		}

		LOG.debug("Process returned exit code [{}]", code);
		if (code != 0) {
			/* raise error showing message(s) written to stdout/stderr */
			throw new MagickException("Process returned exit code " + code + ":\n\n" + buff);
		}
		return destination;
	}

	@Override
	protected void cancel() {
		synchronized (lock) {
			if (process != null) {
				/*
				 * Forcefully kill the process. Thread interruption won't do
				 * anything as the thread spends its time waiting on I/O (see
				 * note above). If it is already terminated this should be
				 * ignored
				 */
				process.destroy();
			}
		}
	}

	public static class MagickException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		public MagickException(String message) {
			super(message);
		}
	}

	/**
	 * Searches for ImageMagick on the path and asserts the version is at least
	 * the required version.
	 * 
	 * @return path to valid install of ImageMagick
	 * @throws MagicNotAvailableException
	 *             if the correct version of ImageMagic cannot be found
	 */
	static String findMagickAndCheckVersionOrThrow() throws MagicNotAvailableException {
		String testMagickPath = Which.find(MAGICK_COMMAND_TO_CHECK_INSTALLATION);
		if (testMagickPath == null) {
			throw new MagicNotAvailableException(
					"ImageMagick not found on system path. Install ImageMagick (at least version " + REQUIRED_VERSION
							+ ") and restart.");
		}
		String magickPath = new File(testMagickPath).getParent() + File.separator + MAGICK_COMMAND;
		if (!new File(magickPath).exists()) {
			if (OS.isWindows() && new File(magickPath + ".exe").exists()) {
				/* OK; windows cmd searches for exe when resolving binary */
			} else {
				throw new MagicNotAvailableException("ImageMagick was found, but '" + MAGICK_COMMAND
						+ "' binary does not exist!");
			}
		}

		MagickVersion currentVersion = getCurrentMagickVersion(magickPath);
		if (currentVersion == null) {
			throw new MagicNotAvailableException("Unable to check ImageMagick version\n\n" + magickPath);
		}

		if (!currentVersion.isAtLeast(REQUIRED_VERSION)) {
			throw new MagicNotAvailableException("Require ImageMagick version " + REQUIRED_VERSION
					+ " or above (found version " + currentVersion + ")");
		}
		return magickPath;
	}

	private static MagickVersion getCurrentMagickVersion(String magickPath) {
		ProcessBuilder builder = new ProcessBuilder(magickPath, "-version");
		builder.redirectErrorStream(true);
		MagickVersion currentVersion = null;
		BufferedReader reader = null;
		try {
			Process process = builder.start();
			reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			String line;
			while ((line = reader.readLine()) != null) {
				if (currentVersion == null) {
					Matcher match = VERSION_PATTERN.matcher(line);
					if (match.matches()) {
						int major = Integer.parseInt(match.group(1));
						int minor = Integer.parseInt(match.group(2));
						int rev = Integer.parseInt(match.group(3));
						int patch = Integer.parseInt(match.group(4));
						currentVersion = new MagickVersion(major, minor, rev, patch);
						LOG.debug("Detected ImageMagick version {}", currentVersion);
					}
				}
				/*
				 * continue to drain I/O after a match as subprocess could block
				 * if stdout buffers fills and we wait in waitFor()
				 */
			}

			process.waitFor();
		} catch (IOException e) {
			/* ignore */
			LOG.debug("Exception finding ImageMagick version", e);
		} catch (InterruptedException e) {
			/* ignore */
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
				}
			}
		}

		return currentVersion;
	}

	public static class MagickVersion {
		public final int major, minor, rev, patch;

		public MagickVersion(int major, int minor, int rev, int patch) {
			this.major = major;
			this.minor = minor;
			this.rev = rev;
			this.patch = patch;
		}

		public boolean isAtLeast(MagickVersion other) {
			if (major < other.major) {
				return false;
			} else if (major > other.major) {
				return true;
			}
			if (minor < other.minor) {
				return false;
			} else if (minor > other.minor) {
				return true;
			}
			if (rev < other.rev) {
				return false;
			} else if (rev > other.rev) {
				return true;
			}
			if (patch < other.patch) {
				return true;
			} else if (patch > other.patch) {
				return false;
			}

			// all equal
			return true;
		}

		@Override
		public String toString() {
			return String.format("%d.%d.%d-%d", major, minor, rev, patch);
		}
	}

	public static class MagicNotAvailableException extends RuntimeException {

		private static final long serialVersionUID = 1L;

		public MagicNotAvailableException(String message) {
			super(message);
		}
	}
}