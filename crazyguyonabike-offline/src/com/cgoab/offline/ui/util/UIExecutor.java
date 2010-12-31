package com.cgoab.offline.ui.util;

import java.util.concurrent.Executor;

import org.eclipse.swt.widgets.Display;

public class UIExecutor implements Executor {

	private final Display display;

	public UIExecutor(Display display) {
		this.display = display;
	}

	@Override
	public void execute(Runnable command) {
		if (Thread.currentThread() != display.getThread()) {
			display.asyncExec(command);
		} else {
			command.run();
		}
	}
}
