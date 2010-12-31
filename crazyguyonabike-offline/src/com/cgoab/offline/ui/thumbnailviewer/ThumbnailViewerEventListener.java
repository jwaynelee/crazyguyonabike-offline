package com.cgoab.offline.ui.thumbnailviewer;

import java.io.File;

import com.drew.metadata.Metadata;

public interface ThumbnailViewerEventListener {

	public void itemsRemoved(Object[] selection);

	public void itemsMoved(Object[] selection, int insertionPoint);

	public void itemsAdded(File[] newItems, int insertionPoint);

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
	 * @return if the image should be kept and an invalid image icon displayed.
	 */
	public boolean itemFailedToLoad(Object item, Throwable exception);

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
