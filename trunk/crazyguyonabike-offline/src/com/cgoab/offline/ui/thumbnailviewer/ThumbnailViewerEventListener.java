package com.cgoab.offline.ui.thumbnailviewer;

import java.io.File;

public interface ThumbnailViewerEventListener {

	/**
	 * Called when an image cannot be loaded (file missing, invalid format etc).
	 * 
	 * By returning <tt>true</tt> the model item that provided the image will be
	 * kept but an invalid image icon will be displayed. Returning
	 * <tt>false</tt> will cause the image to be removed from the list (and thus
	 * the model should update itself as appropriate).
	 * 
	 * @param item
	 * @param exception
	 * @return if the image should be kept and an invalid image icon displayed,
	 *         false if it should be discarded.
	 */
	public boolean itemFailedToLoad(Object item, Throwable exception);

	public void itemsAdded(File[] newItems, int insertionPoint);

	public void itemsMoved(Object[] selection, int insertionPoint);

	public void itemsRemoved(Object[] selection);

	// /**
	// * Called on completion of a thumbnail load (either from cache or disk)
	// with
	// * the photo meta data (EXIF header).
	// *
	// * @param data
	// * @param meta
	// */
	// public void itemMetaDataAvailable(Object data, Metadata meta);
}
