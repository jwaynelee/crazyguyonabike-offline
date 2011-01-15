package com.cgoab.offline.ui.thumbnailviewer;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.swt.graphics.ImageData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cgoab.offline.ui.thumbnailviewer.ThumbnailProvider.Thumbnail;
import com.cgoab.offline.util.Utils;

/**
 * Cache that puts a (rough) bound on how many bytes it will hold, items expired
 * in LRU order.
 * 
 * @TODO strip out ununsed meta-data?
 * @TODO take into account size of meta-data?
 */
class ThumbnailCache {

	private static final Logger LOG = LoggerFactory.getLogger(ThumbnailCache.class);

	private final int maxSizeInBytes;

	private int bytes;

	private LinkedHashMap<File, ThumbnailCache.CacheEntry> lruMap = new LinkedHashMap<File, ThumbnailCache.CacheEntry>(
			64, 0.75f, true) {
		private static final long serialVersionUID = 1L;

		@Override
		protected boolean removeEldestEntry(Map.Entry<File, ThumbnailCache.CacheEntry> eldest) {
			/* delete oldest entry if too big */
			if (bytes > maxSizeInBytes) {
				LOG.debug("Expiring eldest entry [{}] as cache size {}kb exceeded limit {}kb)",
						new Object[] { eldest.getKey(), Utils.formatBytes(bytes), Utils.formatBytes(maxSizeInBytes) });
				entryRemoved(eldest.getValue().thumb.imageData);
				/*
				 * TODO: loop() and terminate when map is under max size,
				 * currently a large entry may push us well over the limit, but
				 * we only remove a single item. Since we are caching similar
				 * sized thumbnails this probably isn't necessary.
				 */
				return true;
			}
			return false;
		}
	};

	public ThumbnailCache(int capacityInBytes) {
		this.maxSizeInBytes = capacityInBytes;
	}

	public void clear() {
		lruMap.clear();
		bytes = 0;
	}

	public ThumbnailCache.CacheEntry remove(File file) {
		LOG.debug("Removing thumnail for [{}]", file);
		ThumbnailCache.CacheEntry result = lruMap.remove(file);
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
	public void add(File file, Thumbnail thumb) {
		LOG.debug("Caching thumbnail for [{}] in [{}]", file.getName(), this);
		ThumbnailCache.CacheEntry entry = new CacheEntry(file.lastModified(), thumb);
		bytes += sizeof(thumb.imageData);
		ThumbnailCache.CacheEntry old = lruMap.put(file, entry);
		if (old != null) {
			bytes -= sizeof(old.thumb.imageData);
		}
	}

	private static int sizeof(byte[] array) {
		return array == null ? 0 : array.length;
	}

	private static int sizeof(ImageData image) {
		/* TODO include meta-data? */
		return sizeof(image.data) + sizeof(image.alphaData) + sizeof(image.maskData);
	}

	/**
	 * Retrieves the image data associated with the given file name.
	 * 
	 * Returns <tt>null</tt> if the cache has expired the item (got low on
	 * memory) or if the file was changed since it was last cached.
	 * 
	 * @param file
	 * @return
	 */
	public Thumbnail get(File file) {
		ThumbnailCache.CacheEntry entry = lruMap.get(file);
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
		final long timestamp;
		final Thumbnail thumb;

		public CacheEntry(long timestamp, Thumbnail thumb) {
			this.timestamp = timestamp;
			this.thumb = thumb;
		}
	}
}