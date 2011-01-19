package com.cgoab.offline.ui.actions;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.operations.IOperationHistory;
import org.eclipse.core.commands.operations.IOperationHistoryListener;
import org.eclipse.core.commands.operations.IUndoContext;
import org.eclipse.core.commands.operations.OperationHistoryEvent;
import org.eclipse.core.commands.operations.OperationHistoryFactory;
import org.eclipse.jface.action.Action;
import org.eclipse.swt.SWT;

import com.cgoab.offline.ui.MainWindow;
import com.cgoab.offline.ui.MainWindow.ContextChangedListener;

public class RedoAction extends Action {
	private static final String name = "Redo";
	private final MainWindow application;
	private final IOperationHistory history = OperationHistoryFactory.getOperationHistory();
	private final Listener listener = new Listener();

	public RedoAction(final MainWindow application) {
		super(name);
		setAccelerator(SWT.MOD1 + 'Y');
		this.application = application;
		setEnabled(false);
		history.addOperationHistoryListener(listener);
		application.addUndoContextChangedListener(listener);
	}

	@Override
	public void run() {
		try {
			history.redo(application.getCurrentOperationContext(), null, null);
		} catch (ExecutionException e1) {
			e1.printStackTrace();
		}
	}

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
			if (history.canRedo(context)) {
				setEnabled(true);
				setText(name + " " + history.getRedoOperation(context).getLabel());
			} else {
				setEnabled(false);
				setText(name);
			}
		}
	}
}
