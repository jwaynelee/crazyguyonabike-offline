package com.cgoab.offline.util.resizer;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.swt.widgets.Display;

import com.cgoab.offline.model.Journal;
import com.cgoab.offline.util.ListenableThreadPoolExecutor;
import com.cgoab.offline.util.resizer.ImageMagickResizeTask.MagicNotAvailableException;

/**
 * An asynchronous photo resizing service that saves resized photos in a local
 * folder.
 * <p>
 * This implementation invokes a new ImageMagick sub-process per resize
 * operation. As such ImageMagik version 6.3.8-3 must be installed prior to
 * running this or {@link #createResizerFor(Journal)} will fail with
 * {@link MagicNotAvailableException}.
 */
public class ImageMagickResizerServiceFactory {

	private final Display display;

	private final ExecutorService executor;

	private final String folderExtension;

	private String magickPath;

	public ImageMagickResizerServiceFactory(Display display, String folderExtension) {
		this.display = display;
		/* MIN_PRIORITY allows OS to preempt resize tasks with a thumbnail task */
		this.executor = ListenableThreadPoolExecutor
				.newOptimalSizedExecutorService("ImageResizer", Thread.MIN_PRIORITY);
		this.folderExtension = folderExtension;
	}

	public ResizerService createResizerFor(Journal source) throws MagicNotAvailableException {
		String cmdPath = getOrInitializeMagickPath();
		File photoFolder = getOrCreatePhotoFolder(source);
		ResizerService tracker = new ImageMagikResizerService(photoFolder, executor, display, cmdPath);
		source.setData(ResizerService.KEY, tracker);
		return tracker;
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

	private String getOrInitializeMagickPath() throws MagicNotAvailableException {
		if (magickPath == null) {
			magickPath = ImageMagickResizeTask.findMagickAndCheckVersionOrThrow();
		}
		return magickPath;
	}
}
