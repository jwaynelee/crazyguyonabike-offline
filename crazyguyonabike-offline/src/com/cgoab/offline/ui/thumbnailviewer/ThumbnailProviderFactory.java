package com.cgoab.offline.ui.thumbnailviewer;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.swt.widgets.Display;

import com.cgoab.offline.model.Journal;
import com.cgoab.offline.util.ListenableThreadPoolExecutor;

public class ThumbnailProviderFactory {

	private final Map<Journal, ThumbnailProvider> providers = new HashMap<Journal, ThumbnailProvider>();

	private final ExecutorService executor;

	private final Display display;

	private ResizeStrategy resizer;

	public ThumbnailProviderFactory(Display display, ResizeStrategy resizer) {
		this.executor = ListenableThreadPoolExecutor.newOptimalSizedExecutorService("ThumbnailProvider",
				Thread.NORM_PRIORITY);
		this.display = display;
		this.resizer = resizer;
	}

	public ThumbnailProvider getThumbnailProvider(Journal journal) {
		return providers.get(journal);
	}

	public ThumbnailProvider getOrCreateThumbnailProvider(Journal journal) {
		ThumbnailProvider service = providers.get(journal);
		if (service == null) {
			File thumbFolder = getOrCreateThumbnailsFolder(journal);
			service = new CachingThumbnailProvider(executor, thumbFolder, display, resizer);
			providers.put(journal, service);
		}
		return service;
	}

	public File getOrCreateThumbnailsFolder(Journal journal) {
		File file = new File(journal.getFile().getParent() + File.separator + journal.getName() + "_thumbnails");
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
