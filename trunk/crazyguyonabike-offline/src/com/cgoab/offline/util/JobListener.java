package com.cgoab.offline.util;

/**
 * Reports changes in the number of pending jobs in some worker based system.
 */
public interface JobListener {
	/**
	 * Called when the number of remaining jobs changes.
	 * 
	 * @param remainingJobs
	 */
	public void update(int remainingJobs);
}