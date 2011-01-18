package com.cgoab.offline.ui.actions;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.operations.IOperationHistory;
import org.eclipse.core.commands.operations.IOperationHistoryListener;
import org.eclipse.core.commands.operations.IUndoContext;
import org.eclipse.core.commands.operations.OperationHistoryEvent;
import org.eclipse.core.commands.operations.OperationHistoryFactory;
import org.eclipse.jface.action.Action;
import org.eclipse.swt.SWT;

import com.cgoab.offline.ui.ApplicationWindow;
import com.cgoab.offline.ui.ApplicationWindow.ContextChangedListener;

public class UndoAction extends Action {
	private static final String name = "Undo";
	private final IOperationHistory history = OperationHistoryFactory.getOperationHistory();
	private final ApplicationWindow application;

	private final Listener listener = new Listener();

	private class Listener implements IOperationHistoryListener, ContextChangedListener {

		@Override
		public void contextChanged(IUndoContext context) {
			updateAction();
		}

		@Override
		public void historyNotification(OperationHistoryEvent event) {
			updateAction();
		}

		private void updateAction() {
			IUndoContext context = application.getCurrentOperationContext();
			if (history.canUndo(context)) {
				setEnabled(true);
				setText(name + " " + history.getUndoOperation(context).getLabel());
			} else {
				setEnabled(false);
				setText(name);
			}
		}
	}

	public UndoAction(final ApplicationWindow application) {
		super(name);
		setAccelerator(SWT.MOD1 + 'Z');
		this.application = application;
		setEnabled(false);

		/* enable when history appears for current operation */
		history.addOperationHistoryListener(listener);
		application.addUndoContextChangedListener(listener);
	}

	@Override
	public void run() {
		try {
			history.undo(application.getCurrentOperationContext(), null, null);
		} catch (ExecutionException e1) {
			e1.printStackTrace();
		}
	}
}