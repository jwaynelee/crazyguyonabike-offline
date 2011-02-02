package com.cgoab.offline.ui.actions;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.operations.IOperationHistory;
import org.eclipse.core.commands.operations.IOperationHistoryListener;
import org.eclipse.core.commands.operations.IUndoContext;
import org.eclipse.core.commands.operations.OperationHistoryEvent;
import org.eclipse.core.commands.operations.OperationHistoryFactory;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cgoab.offline.ui.MainWindow;
import com.cgoab.offline.ui.MainWindow.ContextChangedListener;

public class UndoAction extends Action {

	private static final Logger LOGGER = LoggerFactory.getLogger(UndoAction.class);

	private static final String name = "Undo";

	private final MainWindow application;

	private final IOperationHistory history = OperationHistoryFactory.getOperationHistory();

	private final Listener listener = new Listener();

	public UndoAction(final MainWindow application) {
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
		} catch (ExecutionException e) {
			LOGGER.error("Exception during Undo", e);
			MessageDialog.openError(null, "Undo failed", "Undo failed: " + e);
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
			if (history.canUndo(context)) {
				setEnabled(true);
				setText(name + " " + history.getUndoOperation(context).getLabel());
			} else {
				setEnabled(false);
				setText(name);
			}
		}
	}
}