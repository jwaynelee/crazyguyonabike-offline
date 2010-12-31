package com.cgoab.offline.ui.thumbnailviewer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Display;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cgoab.offline.ui.thumbnailviewer.ThumbnailProvider.Thumbnail;
import com.cgoab.offline.ui.util.SWTUtils;
import com.cgoab.offline.util.CompletedFuture;
import com.cgoab.offline.util.FutureCompletionListener;
import com.cgoab.offline.util.JobListener;
import com.cgoab.offline.util.ListenableCancellableTask;
import com.cgoab.offline.util.Utils;
import com.drew.imaging.jpeg.JpegMetadataReader;
import com.drew.imaging.jpeg.JpegProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifDirectory;

/**
 * Thumbnail provider that caches images both on disk (all thumbnails) and in
 * memory (recently accessed thumbnails).
 */
public class CachingThumbnailProvider implements ThumbnailProvider, FutureCompletionListener<Thumbnail> {

	private static Logger LOG = LoggerFactory.getLogger(ThumbnailProvider.class);

	private final File cacheDirectory;

	private final ThumbnailCache cache;

	private final ExecutorService executor;

	private final Display display;

	private final Map<File, Future<Thumbnail>> tasks = new HashMap<File, Future<Thumbnail>>();

	private final ResizeStrategy resizer;

	private final List<JobListener> listeners = new ArrayList<JobListener>();

	public CachingThumbnailProvider(ExecutorService executor, File cacheDirectory, Display display,
			ResizeStrategy resizer) {
		this.cacheDirectory = cacheDirectory;
		if (!cacheDirectory.exists()) {
			if (!cacheDirectory.mkdir()) {
				throw new IllegalStateException("Could not create thumbnail directory [" + cacheDirectory + "]");
			}
		} else if (!cacheDirectory.isDirectory()) {
			throw new IllegalArgumentException("[" + cacheDirectory + "] is not a directory");
		}
		this.executor = executor;
		this.display = display;
		this.cache = new ThumbnailCache(16 * 1024 * 1024); // 16mb
		this.resizer = resizer;
	}

	private File getNameInCache(File source) {
		String name = source.getName();
		int id = name.lastIndexOf(".");
		if (id > 0) {
			// remove ".jpeg" or ".jpg"
			name = name.substring(0, id);
		}
		return new File(cacheDirectory + File.separator + name + ".png");
	}

	public void addJobListener(JobListener listener) {
		if (!listeners.contains(listener)) {
			listeners.add(listener);
		}
	}

	public void removeJobListener(JobListener listener) {
		listeners.remove(listener);
	}

	@Override
	public Future<Thumbnail> get(File source, FutureCompletionListener<Thumbnail> listener, Object data) {
		SWTUtils.assertOnUIThread();
		LOG.debug("Request for thumbnail of [{}]", source.getAbsolutePath());

		// 0) file does not exist (or was deleted) clear cache and return an
		// exception
		if (!source.exists()) {
			LOG.debug("Source image does not exist, removing from cache(s)");
			cache.remove(source);
			File f = getNameInCache(source);
			if (f.exists()) {
				f.delete();
			}
			return new CompletedFuture<Thumbnail>(null, new FileNotFoundException(source.getAbsolutePath()));
		}

		// 1) check local cache
		Thumbnail result = cache.get(source);
		if (result != null) {
			CompletedFuture<Thumbnail> fr = new CompletedFuture<Thumbnail>(result, null);
			if (listener != null) {
				/*
				 * Call the listener inline as the future is completed.
				 */
				listener.onCompletion(fr, data);
			}
			return fr;
		}

		// 2) create a job to check file cache or create a thumbnail
		File destination = getNameInCache(source);
		GetThumbnailTask task = new GetThumbnailTask(source, destination, resizer, SWT.HIGH, display, this,
				new Object[] { source, listener, data });
		Future<Thumbnail> future = executor.submit(task);
		tasks.put(source, future);
		fireStatusChanged();
		return future;
	}

	private void fireStatusChanged() {
		for (JobListener listener : listeners) {
			listener.update(tasks.size());
		}
	}

	@Override
	public void onCompletion(final Future<Thumbnail> result, final Object data) {
		display.asyncExec(new Runnable() {
			@Override
			public void run() {
				/*
				 * Run callback on UI thread to avoid synchronisation of
				 * internal data structures.
				 */

				Object[] da = (Object[]) data;
				File source = (File) da[0];

				// 1) update internal state
				tasks.remove(source);
				fireStatusChanged();

				// 2) cache thumbnail
				try {
					Thumbnail thumb = result.get();
					cache.add(source, thumb.imageData, thumb.meta);
				} catch (InterruptedException e) {
					/* can't happen, future is already done */
				} catch (CancellationException e) {
					/* ignore */
				} catch (ExecutionException e) {
					/* ignore, client listener will take care of logging */
				}

				// 3) invoke client listener
				FutureCompletionListener<Thumbnail> realListener = (FutureCompletionListener<Thumbnail>) da[1];
				Object realData = da[2];
				if (realListener != null) {
					realListener.onCompletion(result, realData);
				}
			}
		});
	}

	// private void put(Object name, ImageData data) {
	// ImageLoader saver = new ImageLoader();
	// saver.data = new ImageData[] { data };
	// File image = createFileName(extractSourceFileName(name));
	// saver.save(image.getAbsolutePath(), SWT.IMAGE_JPEG);
	// }

	public void remove(File source) {
		SWTUtils.assertOnUIThread();

		// 1) cancel pending resize job
		Future<Thumbnail> future = tasks.remove(source);
		if (future != null && !future.isDone()) {
			future.cancel(true);
		}

		// 2) remove from cache
		cache.remove(source);

		// 3) delete file
		File cachedFile = getNameInCache(source);
		if (cachedFile.exists()) {
			if (!cachedFile.delete()) {
				LOG.info("Failed to delete thumbnail {}", cachedFile);
			}
		}
	}

	private class GetThumbnailTask extends ListenableCancellableTask<Thumbnail> {
		private File destination;
		private File source;
		private ResizeStrategy resizer;
		private int interpolationLevel = SWT.HIGH;
		private Display display;
		private long timeTaken;

		public GetThumbnailTask(File source, File destination, ResizeStrategy resizer, int interpolationLevel,
				Display display, FutureCompletionListener<Thumbnail> listener, Object data) {
			super(listener, data);
			this.destination = destination;
			this.source = source;
			this.resizer = resizer;
			this.interpolationLevel = interpolationLevel;
			this.display = display;
		}

		@Override
		public final Thumbnail call() throws Exception {
			long start = System.currentTimeMillis();
			LOG.info("Executing Task {}", this);
			try {
				return doCall();
			} catch (Exception e) {
				/* log else we won't see the exception until get() */
				LOG.info("Task failed", e);
				throw e;
			} finally {
				timeTaken = System.currentTimeMillis() - start;
				LOG.info("Task finished in {}ms", timeTaken);
			}
		}

		private Metadata loadMeta() {
			try {
				return JpegMetadataReader.readMetadata(source);
			} catch (JpegProcessingException e) {
				LOG.warn("Failed to load meta-data for [" + source.getName() + "]", e);
			}
			return null;
		}

		protected ImageData createThumbnail(int rotate) {
			Image thumbnail;
			LOG.debug("Loading image [{}]", source.getName());
			Image image = new Image(display, source.getAbsolutePath());
			Point current = new Point(image.getBounds().width, image.getBounds().height);
			LOG.debug("Loaded [{}] size = {}", source.getName(), current.x + "x" + current.y);

			// if the image will be resized, compute the size of the box as if
			// it were rotated
			Point thumbSize;
			if (rotate == SWT.RIGHT || rotate == SWT.LEFT) {
				thumbSize = swapXAndY(resizer.resize(swapXAndY(current)));
			} else {
				thumbSize = resizer.resize(current);
			}
			LOG.debug("Resizing image [{}] to {}", source.getName(), thumbSize.x + "x" + thumbSize.y);
			thumbnail = new Image(display, thumbSize.x, thumbSize.y);
			GC gc = new GC(thumbnail);
			gc.setAntialias(SWT.ON);
			gc.setInterpolation(interpolationLevel);
			gc.drawImage(image, 0, 0, current.x, current.y, 0, 0, thumbSize.x, thumbSize.y);
			image.dispose();
			ImageData data = rotate == SWT.NONE ? thumbnail.getImageData() : SWTUtils.rotate(thumbnail.getImageData(),
					rotate);
			thumbnail.dispose();
			gc.dispose();
			return data;
		}

		private Point swapXAndY(Point p) {
			return new Point(p.y, p.x);
		}

		private int detectRotation(Metadata meta) {
			if (meta == null) {
				return SWT.NONE;
			}

			ExifDirectory exif = (ExifDirectory) meta.getDirectory(ExifDirectory.class);
			Object o = exif.getObject(ExifDirectory.TAG_ORIENTATION);
			if (o != null && o instanceof Integer) {
				// from http://sylvana.net/jpegcrop/exif_orientation.html
				switch ((Integer) o) {
				case 1: /* 0 */
					return SWT.NONE;
				case 3: /* 180 */
					LOG.debug("Rotating image [{}] by 180 degrees", source.getName());
					return SWT.DOWN;
				case 8: /* 90 */
					LOG.debug("Rotating image [{}] by 90 degrees", source.getName());
					return SWT.LEFT;
				case 6: /* 270 */
					LOG.debug("Rotating image [{}] by 270 degrees", source.getName());
					return SWT.RIGHT;
				}
			}

			return SWT.NONE;
		}

		private ImageData loadImage(File file) {
			try {
				return new ImageData(destination.getAbsolutePath());
			} catch (SWTException e) {
				// failed to load, continue to (re)create the thumbnail
				LOG.info("Failed to load image [" + file.getName() + "]", e);
				return null;
			}
		}

		public Thumbnail doCall() {
			// 1) check cache, if file can be loaded we are done
			if (destination.exists() && destination.lastModified() >= source.lastModified()) {
				// load from file
				ImageData image = loadImage(destination);
				if (image != null) {
					// TODO cache meta in cache directory?
					return new Thumbnail(loadMeta(), image);
				}
			}

			// 2) create a new thumbnail
			Metadata meta = loadMeta();
			int rotation = detectRotation(meta);
			ImageData data = createThumbnail(rotation);
			Thumbnail result = new Thumbnail(meta, data);

			// 3) add a task to save the thumbnail to disk
			//
			// A race is created. If the "save" task does not complete
			// before another request is made to load the same thumbnail we'll
			// recreate it all over and add another save task.
			executor.submit(new SaveThumbnailTask(data, destination));
			return result;
		}

		@Override
		public String toString() {
			return String.format("Loading or create thumbnail for [%s]", source.getAbsolutePath());
		}
	}

	/**
	 * Saves the thumbnail data to disk, this is done as a separate task so the
	 * UI gets the thumbnail as soon as possible and the less important task of
	 * saving to disk is deferred.
	 */
	private static class SaveThumbnailTask implements Callable<File> {

		ImageData data;
		File destination;

		public SaveThumbnailTask(ImageData data, File destination) {
			this.data = data;
			this.destination = destination;
		}

		@Override
		public File call() throws Exception {
			FileOutputStream fos = null;
			try {
				fos = new FileOutputStream(destination);
				ImageLoader loader = new ImageLoader();
				loader.data = new ImageData[] { data };
				LOG.debug("Writing thumbnail to [{}]", destination.getAbsolutePath());
				loader.save(fos, SWT.IMAGE_PNG);
			} finally {
				try {
					if (fos != null) {
						fos.close();
					}
				} catch (IOException e) {
				}
			}
			return destination;
		}

		@Override
		public String toString() {
			return String.format("Save thumbnail to [%s]", destination.getName());
		}
	}

	private static class ThumbnailCache {

		private static final Logger LOG = LoggerFactory.getLogger(ThumbnailCache.class);

		private final int maxSizeInBytes;

		private int bytes;

		private LinkedHashMap<File, CacheEntry> lruMap = new LinkedHashMap<File, CacheEntry>(64, 0.75f, true) {
			private static final long serialVersionUID = 1L;

			@Override
			protected boolean removeEldestEntry(Map.Entry<File, CacheEntry> eldest) {
				// delete oldest entry
				if (bytes > maxSizeInBytes) {
					LOG.debug("Expiring eldest entry [{}] as cache size {}kb exceeded limit {}kb)", new Object[] {
							eldest.getKey(), Utils.formatBytes(bytes), Utils.formatBytes(maxSizeInBytes) });
					entryRemoved(eldest.getValue().thumb.imageData);
					return true;
				}
				return false;
			}
		};

		public ThumbnailCache(int capacityInBytes) {
			this.maxSizeInBytes = capacityInBytes;
		}

		public CacheEntry remove(File file) {
			LOG.debug("Removing thumnail for [{}]", file);
			CacheEntry result = lruMap.remove(file);
			if (result != null) {
				entryRemoved(result.thumb.imageData);
			}
			return result;
		}

		private void entryRemoved(ImageData data) {
			bytes -= sizeof(data);
		}

		/**
		 * Saves the image data in the cache.
		 * 
		 * @param file
		 * @param image
		 */
		public void add(File file, ImageData image, Metadata meta) {
			CacheEntry entry = new CacheEntry();
			// entry.ref= new SoftReference<ImageAndMetaDataPair>(new
			// ImageAndMetaDataPair(image, meta));
			LOG.debug("Caching thumbnail for [{}] in [{}]", file.getName(), this);
			entry.thumb = new Thumbnail(meta, image);
			entry.timestamp = file.lastModified();
			bytes += sizeof(image);
			CacheEntry old = lruMap.put(file, entry);
			if (old != null) {
				bytes -= sizeof(old.thumb.imageData);
			}
		}

		private static int sizeof(byte[] array) {
			return array == null ? 0 : array.length;
		}

		private static int sizeof(ImageData image) {
			return sizeof(image.data) + sizeof(image.alphaData) + sizeof(image.maskData);
		}

		/**
		 * Retrieves the image data associated with the given file name.
		 * 
		 * Null will be returned if the cache has expired the item (low memory)
		 * or if the file was changed since it was last cached.
		 * 
		 * @param file
		 * @return
		 */
		public Thumbnail get(File file) {
			CacheEntry entry = lruMap.get(file);
			if (entry != null) {
				Thumbnail thumb = entry.thumb;
				if (entry.timestamp == file.lastModified()) {
					LOG.debug("Cache hit for [{}]", file.getName());
					return thumb;
				} else {
					LOG.debug("Removed cache entry for [{}] as timestamps don't match", file.getName());
					entryRemoved(entry.thumb.imageData);
					lruMap.remove(file);
					return null;
				}
			} else {
				LOG.debug("Cache miss for [{}]", file.getName());
				return null;
			}
		}

		@Override
		public String toString() {
			return String.format("%s - %s (of %s) used for %d images", getClass().getSimpleName(),
					Utils.formatBytes(bytes), Utils.formatBytes(maxSizeInBytes), lruMap.size());
		}

		private static class CacheEntry {
			long timestamp;
			Thumbnail thumb;
		}
	}
}