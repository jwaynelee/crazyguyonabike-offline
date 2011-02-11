package com.cgoab.offline.ui.thumbnailviewer;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.swt.widgets.Display;

import com.cgoab.offline.model.Journal;
import com.cgoab.offline.util.ListenableThreadPoolExecutor;

public class CachingThumbnailProviderFactory {

	private final Display display;

	private final ExecutorService executor;

	private final String folderExtension;

	private ResizeStrategy resizer;

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
		if (journal.isUseExifThumbnail() == Boolean.TRUE) {
			service.setUseExifThumbnail(true);
		} else {
			service.setUseExifThumbnail(false);
		}
		return service;
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

	public File getOrCreateThumbnailsFolder(Journal journal) {
		File file = new File(journal.getFile().getParent() + File.separator + journal.getName() + folderExtension);
		if (!file.exists()) {
			file.mkdirs();
		}
		return file;
	}

	/**
	 * Returns the thumbnail provider assigned to the journal.
	 * 
	 * @param journal
	 * @return
	 */
	public static final CachingThumbnailProvider getProvider(Journal journal) {
		return (CachingThumbnailProvider) journal.getData(ThumbnailProvider.KEY);
	}
}