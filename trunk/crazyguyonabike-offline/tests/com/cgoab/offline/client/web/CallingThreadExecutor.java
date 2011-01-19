package com.cgoab.offline.client.web;

import java.util.concurrent.Executor;

/**
 * Executor that runs work on calling thread, handy for testing when outside of
 * UI (no need for callbacks on UI thread).
 */
public class CallingThreadExecutor implements Executor {
	@Override
	public void execute(Runnable command) {
		command.run();
	}
}