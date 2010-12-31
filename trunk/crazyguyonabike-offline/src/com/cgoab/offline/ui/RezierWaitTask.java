package com.cgoab.offline.ui;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.swt.widgets.Display;

import com.cgoab.offline.model.Journal;
import com.cgoab.offline.ui.util.SWTUtils;
import com.cgoab.offline.util.JobListener;
import com.cgoab.offline.util.resizer.ResizerService;

public class RezierWaitTask implements IRunnableWithProgress, JobListener {

	private final ResizerService service;
	private final Display display;
	private final Journal journal;
	private IProgressMonitor monitor;
	private boolean complete;
	private boolean cancelled;

	// number of tasks left to complete (used to track how many just completed)
	private int tasks;

	public RezierWaitTask(ResizerService service, Display display, Journal journal) {
		this.service = service;
		this.display = display;
		this.journal = journal;
	}

	@Override
	public void update(int remainingJobs) {
		if (remainingJobs == 0) {
			complete = true;
		} else {
			int completed = tasks - remainingJobs; // how many just completed
			tasks = remainingJobs;
			monitor.worked(completed);
		}
	}

	/**
	 * Returns true if the request was cancelled.
	 * 
	 * @return
	 */
	public boolean isCancelled() {
		return cancelled;
	}

	@Override
	public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
		SWTUtils.assertOnUIThread(); // this is designed to run on UI thread
		tasks = service.activeTasks();
		monitor.beginTask(
				"Waiting for image resizer to finish resizing photos in journal [" + journal.getName() + "]", tasks);
		service.addJobListener(this);
		this.monitor = monitor;
		try {
			// run event loop, updates come via callback
			while (!monitor.isCanceled() && !complete) {
				if (!display.readAndDispatch()) {
					display.sleep();
				}
			}
			cancelled = monitor.isCanceled();
		} finally {
			service.removeJobListener(this);
		}
	}
}
