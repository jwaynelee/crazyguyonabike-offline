package com.cgoab.offline.ui.actions;

import org.eclipse.jface.action.Action;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;

import com.cgoab.offline.model.Journal;
import com.cgoab.offline.ui.JournalSelectionService;
import com.cgoab.offline.ui.JournalUtils;
import com.cgoab.offline.ui.NewJournalDialog;

public class OpenJournalAction extends Action {

	private final Shell shell;

	public OpenJournalAction(Shell shell) {
		super("&Open Journal");
		setAccelerator(SWT.MOD1 + 'O');
		this.shell = shell;
	}

	@Override
	public void run() {
		FileDialog fd = new FileDialog(shell, SWT.OPEN);
		fd.setFilterExtensions(new String[] { "*" + NewJournalDialog.EXTENSION });
		String path = fd.open();
		if (path != null) {
			Journal current = JournalSelectionService.getInstance().getCurrentJournal();
			if (current != null) {
				if (!JournalUtils.closeJournal(current, shell)) {
					return;
				}
			}
			JournalUtils.openJournal(path, false, shell);
		}
	}
}
