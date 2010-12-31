package com.cgoab.offline.util.resizer;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.swt.widgets.Display;

import com.cgoab.offline.model.Journal;
import com.cgoab.offline.ui.thumbnailviewer.ResizeStrategy;
import com.cgoab.offline.util.ListenableThreadPoolExecutor;

public class ResizerServiceFactory {

	private final Map<Object, ResizerService> trackers = new HashMap<Object, ResizerService>();

	private final Display display;

	private final ExecutorService executor;

	public ResizerServiceFactory(Display display) {
		this.display = display;
		executor = ListenableThreadPoolExecutor.newOptimalSizedExecutorService("ImageResizer", Thread.MIN_PRIORITY);
	}

	public ResizerService getResizerFor(Journal journal) {
		return trackers.get(journal);
	}

	public ResizerService getOrCreateResizerFor(Journal source) throws UnableToCreateResizerException {
		ResizerService tracker = trackers.get(source);
		if (tracker == null) {
			File photoFolder = getOrCreatePhotoFolder(source);
			tracker = new ImageMagikResizerService(photoFolder, executor, display);
			trackers.put(source, tracker);
		}
		return tracker;
	}

	private File getOrCreatePhotoFolder(Journal source) {
		File folder = new File(source.getFile().getParent() + File.separator + source.getName() + "_images");
		if (!folder.exists()) {
			folder.mkdirs();
		}
		return folder;
	}

	public static class UnableToCreateResizerException extends RuntimeException {

		private static final long serialVersionUID = 1L;

		public UnableToCreateResizerException(String message, Throwable cause) {
			super(message, cause);
		}

		public UnableToCreateResizerException(String message) {
			super(message);
		}
	}

	public void dispose() {
		executor.shutdown();
		try {
			executor.awaitTermination(5, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			/* ignore */
		} finally {
			executor.shutdownNow();
		}
	}
}
