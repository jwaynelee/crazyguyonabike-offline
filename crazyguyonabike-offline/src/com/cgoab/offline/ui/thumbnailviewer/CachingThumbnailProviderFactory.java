package com.cgoab.offline.ui.thumbnailviewer;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.swt.widgets.Display;

import com.cgoab.offline.model.Journal;
import com.cgoab.offline.util.ListenableThreadPoolExecutor;

public class CachingThumbnailProviderFactory {

	private final ExecutorService executor;

	private final Display display;

	private ResizeStrategy resizer;

	private final String folderExtension;

	public CachingThumbnailProviderFactory(Display display, ResizeStrategy resizer, String folderExtension) {
		this.executor = ListenableThreadPoolExecutor.newOptimalSizedExecutorService("ThumbnailProvider",
				Thread.NORM_PRIORITY);
		this.display = display;
		this.resizer = resizer;
		this.folderExtension = folderExtension;
	}

	public CachingThumbnailProvider createThumbnailProvider(Journal journal) {
		File thumbFolder = getOrCreateThumbnailsFolder(journal);
		CachingThumbnailProvider service = new CachingThumbnailProvider(executor, thumbFolder, display, resizer);
		journal.setData(ThumbnailProvider.KEY, service);
		return service;
	}

	public File getOrCreateThumbnailsFolder(Journal journal) {
		File file = new File(journal.getFile().getParent() + File.separator + journal.getName() + folderExtension);
		if (!file.exists()) {
			file.mkdirs();
		}
		return file;
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