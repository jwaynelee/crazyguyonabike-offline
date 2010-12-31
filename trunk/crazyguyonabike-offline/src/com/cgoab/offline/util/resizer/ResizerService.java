package com.cgoab.offline.util.resizer;

import java.io.File;
import java.util.List;

import com.cgoab.offline.model.Photo;
import com.cgoab.offline.util.JobListener;

public interface ResizerService {

	public abstract void addJobListener(JobListener listener);

	public abstract void removeJobListener(JobListener listener);

	/**
	 * Resizes the given files.
	 * 
	 * @param photos
	 */
	public abstract void resizeAll(List<Photo> photos);

	/**
	 * Removes the given files from the service, this includes cancelling
	 * pending resize tasks in addition to removing any resized files from the
	 * cache.
	 * 
	 * @param photos
	 */
	public abstract void removeAll(List<Photo> photos);

	public abstract int activeTasks();

	/**
	 * Cancels all outstanding resize tasks.
	 */
	public abstract void cancelAll();

	public abstract File getResizedPhotoFile(File photo);
}