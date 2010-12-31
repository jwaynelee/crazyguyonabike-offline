package com.cgoab.offline.model;

import java.util.List;

/**
 * Listener for specific changes that occur to a journal.
 */
public interface JournalListener {

	public void journalDirtyChange();

	public void pageAdded(Page page);

	public void pageDeleted(Page page);

	/**
	 * Called when one or more photos are added to a page in this journal.
	 * 
	 * @param photo
	 * @param page
	 */
	public void photosAdded(List<Photo> photo, Page page);

	/**
	 * Called when one or more photos are removed from a page in this journal.
	 * 
	 * @param photos
	 * @param page
	 */
	public void photosRemoved(List<Photo> photos, Page page);
}