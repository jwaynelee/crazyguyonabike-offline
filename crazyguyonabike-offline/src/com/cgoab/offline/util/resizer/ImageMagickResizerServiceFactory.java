package com.cgoab.offline.util.resizer;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.swt.widgets.Display;

import com.cgoab.offline.model.Journal;
import com.cgoab.offline.ui.PreferenceUtils;
import com.cgoab.offline.util.ListenableThreadPoolExecutor;
import com.cgoab.offline.util.StringUtils;
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

	static final int DEFAULT_SIZE = 1000; /* 1000 x 1000 */

	static final int DEFAULT_JPEG_QUALITY = 70;

	private final Display display;

	private final ExecutorService executor;

	private final String folderExtension;

	public ImageMagickResizerServiceFactory(Display display, String folderExtension) {
		this.display = display;
		/* MIN_PRIORITY allows OS to preempt resize tasks with a thumbnail task */
		this.executor = ListenableThreadPoolExecutor
				.newOptimalSizedExecutorService("ImageResizer", Thread.MIN_PRIORITY);
		this.folderExtension = folderExtension;

		/* configure default preferences */
		PreferenceUtils.getStore().setDefault(PreferenceUtils.RESIZE_QUALITY, DEFAULT_JPEG_QUALITY);
		PreferenceUtils.getStore().setDefault(PreferenceUtils.RESIZE_DIMENSIONS, DEFAULT_SIZE);
		try {
			PreferenceUtils.getStore().setDefault(PreferenceUtils.MAGICK_PATH,
					new File(ImageMagickResizeTask.findMagickAndCheckVersionOrThrow()).getParent());
		} catch (MagicNotAvailableException e) {
			/* ignore */
		}

	}

	public ResizerService createResizerFor(Journal source) throws MagicNotAvailableException {
		String cmdPath = getOrInitializeMagickPath();
		File photoFolder = getOrCreatePhotoFolder(source);
		int quality = PreferenceUtils.getStore().getInt(PreferenceUtils.RESIZE_QUALITY);
		int size = PreferenceUtils.getStore().getInt(PreferenceUtils.RESIZE_DIMENSIONS);
		ResizerService tracker = new ImageMagickResizerService(size, quality, photoFolder, executor, display, cmdPath);
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

	private String getOrInitializeMagickPath() throws MagicNotAvailableException {
		String magickPath = PreferenceUtils.getStore().getString(PreferenceUtils.MAGICK_PATH);
		if (StringUtils.isEmpty(magickPath)) {
			magickPath = ImageMagickResizeTask.findMagickAndCheckVersionOrThrow();
			PreferenceUtils.getStore().setValue(PreferenceUtils.MAGICK_PATH, magickPath);
		}
		return magickPath;
	}
}
