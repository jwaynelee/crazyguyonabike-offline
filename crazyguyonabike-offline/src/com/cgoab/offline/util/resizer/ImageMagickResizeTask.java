package com.cgoab.offline.util.resizer;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cgoab.offline.util.FutureCompletionListener;
import com.cgoab.offline.util.ListenableCancellableTask;
import com.cgoab.offline.util.StringUtils;

public class ImageMagickResizeTask extends ListenableCancellableTask<Object> {

	private static final Logger LOG = LoggerFactory.getLogger(ImageMagickResizeTask.class);
	public static final String MAGICK_COMMAND = "convert";
	private final File source, destination;
	private final String cmdPath;
	private Process process; /* guarded by lock */
	private final Object lock = new Object();

	public ImageMagickResizeTask(String cmd, File source, File destination, FutureCompletionListener<Object> listener,
			Object data) {
		super(listener, data);
		this.cmdPath = cmd;
		this.source = source;
		this.destination = destination;
	}

	@Override
	public Object call() throws Exception {
		List<String> args = new ArrayList<String>();
		args.add(cmdPath);
		args.add(source.getAbsolutePath());
		args.add("-resize");
		args.add("1400x1400^");
		args.add("-quality");
		args.add("70");
		args.add(destination.getAbsolutePath());
		ProcessBuilder pb = new ProcessBuilder(args);
		pb.redirectErrorStream(true);

		LOG.debug("Launching process [{}]", StringUtils.join(args, " "));
		synchronized (lock) {
			// publish the process so it can be cancelled
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
		 * rarely be waiting and should return immediately (the process is
		 * done). A side affect of this is that we won't be interruptible (I/O
		 * not interrupted).
		 */
		while ((line = os.readLine()) != null) {
			LOG.debug(">> " + line);
			buff.append(line).append("\n");
		}

		LOG.debug("Waiting for process completion");
		int code = process.waitFor();
		synchronized (lock) {
			process = null;
		}
		LOG.debug("Process returned exit code [{}]", code);
		if (code != 0) {
			// raise error, showing message(s) written to stdout/stderr
			throw new MagickException("Process returned exit code " + code + ":\n\n" + buff);
		}
		return null;
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
}