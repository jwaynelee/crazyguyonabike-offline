package com.cgoab.offline.util.resizer;

import java.io.File;
import java.util.List;

import com.cgoab.offline.model.Photo;
import com.cgoab.offline.util.JobListener;

/**
 * Service to resize photos.
 */
public interface ResizerService {

	public static final String KEY = ResizerService.class.getName();

	/**
	 * Returns the number of active (running & pending) resize tasks
	 * 
	 * @return
	 */
	public abstract int activeTasks();

	public abstract void addJobListener(JobListener listener);

	/**
	 * Cancels all outstanding resize tasks.
	 */
	public abstract void cancelAll();

	public abstract File getPhotoFolder();

	/**
	 * Returns the file path to the resized photo, or <tt>null</tt> if it does
	 * not exist.
	 * 
	 * @param photo
	 *            a file holding the resized photo or null.
	 * @return
	 */
	public abstract File getResizedPhotoFile(File photo);

	public long purge();

	/**
	 * Removes the given files from the service, this includes cancelling
	 * pending resize tasks in addition to removing any resized files from the
	 * cache.
	 * 
	 * @param photos
	 */
	public abstract void removeAll(List<Photo> photos);

	public abstract void removeJobListener(JobListener listener);

	/**
	 * Resizes the list of photos, if a photo in the list was already resized
	 * and the source file has not changed then nothing will be done.
	 * 
	 * @param photos
	 */
	public abstract void resizeAll(List<Photo> photos);
}