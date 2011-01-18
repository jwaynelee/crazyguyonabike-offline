package com.cgoab.offline.ui.actions;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.operations.IOperationHistory;
import org.eclipse.core.commands.operations.IOperationHistoryListener;
import org.eclipse.core.commands.operations.IUndoContext;
import org.eclipse.core.commands.operations.OperationHistoryEvent;
import org.eclipse.core.commands.operations.OperationHistoryFactory;
import org.eclipse.core.internal.commands.operations.GlobalUndoContext;
import org.eclipse.jface.action.Action;
import org.eclipse.swt.SWT;

import com.cgoab.offline.ui.ApplicationWindow;
import com.cgoab.offline.ui.ApplicationWindow.ContextChangedListener;

public class UndoAction extends Action {
	private static final GlobalUndoContext GLOBAL_UNDO_CONTEXT = new GlobalUndoContext();
	private final IOperationHistory history = OperationHistoryFactory.getOperationHistory();
	private final ApplicationWindow application;
	private static final String name = "Undo";

	public UndoAction(final ApplicationWindow application) {
		super(name);
		setAccelerator(SWT.MOD1 + 'Z');
		this.application = application;
		setEnabled(false);
		history.addOperationHistoryListener(new IOperationHistoryListener() {
			@Override
			public void historyNotification(OperationHistoryEvent event) {
				IUndoContext context = GLOBAL_UNDO_CONTEXT;// application.getCurrentOperationContext();
				if (history.canUndo(context)) {
					setEnabled(true);
					setText(name + " " + history.getUndoOperation(context).getLabel());
				} else {
					setEnabled(false);
					setText(name);
				}
			}
		});
	}

	@Override
	public void run() {
		try {
			history.undo(GLOBAL_UNDO_CONTEXT, null, null);// application.getCurrentOperationContext(),
			// null, null);
		} catch (ExecutionException e1) {
			e1.printStackTrace();
		}
	}
}
