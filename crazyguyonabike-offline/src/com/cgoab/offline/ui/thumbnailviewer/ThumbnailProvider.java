package com.cgoab.offline.ui.thumbnailviewer;

import java.io.File;
import java.util.concurrent.Future;

import org.eclipse.swt.graphics.ImageData;

import com.cgoab.offline.util.FutureCompletionListener;
import com.cgoab.offline.util.JobListener;
import com.drew.metadata.Metadata;

/**
 * Provides the thumbnails of images for a {@link ThumbnailViewer}.
 */
public interface ThumbnailProvider {

	/**
	 * Returns a future that provides the thumbnail, either loaded from file or
	 * from some cache.
	 * 
	 * @param image
	 * @param listener
	 * @return
	 */
	public Future<Thumbnail> get(File image, FutureCompletionListener<Thumbnail> listener, Object data);

	public void addJobListener(JobListener listener);

	public void removeJobListener(JobListener listener);

	/**
	 * Hint to remove this file from any cache the provider may be using.
	 * 
	 * @param image
	 */
	public void remove(File image);

	public static class Thumbnail {
		public final Metadata meta;
		public final ImageData imageData;

		public Thumbnail(Metadata meta, ImageData image) {
			this.meta = meta;
			this.imageData = image;
		}
	}
}