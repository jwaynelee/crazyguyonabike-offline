package com.cgoab.offline.ui;

import org.eclipse.jface.action.IAction;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;

public class ActionRunner extends SelectionAdapter {

	private IAction target;

	public ActionRunner(IAction target) {
		this.target = target;
	}

	@Override
	public void widgetSelected(SelectionEvent e) {
		target.run();
	}
}