package com.cgoab.offline.util.resizer;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.swt.widgets.Display;

import com.cgoab.offline.model.Journal;
import com.cgoab.offline.util.ListenableThreadPoolExecutor;
import com.cgoab.offline.util.resizer.ImageMagickResizeTask.MagicNotAvailableException;

/**
 * An asynchronous photo resizing service that saves resized photos in a local
 * folder
 * <p>
 * This implementation invokes a new ImageMagick sub-process per resize
 * operation. As such ImageMagik version 6.3.8-3 must be installed prior to
 * running this or {@link #getOrCreateResizerFor(Journal)} will fail with
 * {@link MagicNotAvailableException}.
 */
public class ImageMagickResizerServiceFactory {

	private final Map<Object, ResizerService> trackers = new HashMap<Object, ResizerService>();

	private final Display display;

	private final ExecutorService executor;

	private String magickPath;

	private final String folderExtension;

	public ImageMagickResizerServiceFactory(Display display, String folderExtension) {
		this.display = display;
		this.executor = ListenableThreadPoolExecutor
				.newOptimalSizedExecutorService("ImageResizer", Thread.MIN_PRIORITY);
		this.folderExtension = folderExtension;
	}

	public ResizerService getOrCreateResizerFor(Journal source) throws MagicNotAvailableException {
		ResizerService tracker = trackers.get(source);
		if (tracker == null) {
			String cmdPath = getOrInitializeMagickPath();
			File photoFolder = getOrCreatePhotoFolder(source);
			tracker = new ImageMagikResizerService(photoFolder, executor, display, cmdPath);
			source.setData(ResizerService.KEY, tracker);
		}
		return tracker;
	}

	private String getOrInitializeMagickPath() throws MagicNotAvailableException {
		if (magickPath == null) {
			magickPath = ImageMagickResizeTask.findMagickAndCheckVersionOrThrow();
		}
		return magickPath;
	}

	private File getOrCreatePhotoFolder(Journal source) {
		File folder = new File(source.getFile().getParent() + File.separator + source.getName() + folderExtension);
		if (!folder.exists()) {
			folder.mkdirs();
		}
		return folder;
	}

	// public static class UnableToCreateResizerException extends
	// RuntimeException {
	//
	// private static final long serialVersionUID = 1L;
	//
	// public UnableToCreateResizerException(String message, Throwable cause) {
	// super(message, cause);
	// }
	//
	// public UnableToCreateResizerException(String message) {
	// super(message);
	// }
	// }

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
