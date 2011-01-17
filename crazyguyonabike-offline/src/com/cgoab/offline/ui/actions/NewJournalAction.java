package com.cgoab.offline.ui.actions;

import java.io.File;

import org.eclipse.jface.action.Action;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Shell;

import com.cgoab.offline.model.Journal;
import com.cgoab.offline.ui.JournalSelectionService;
import com.cgoab.offline.ui.JournalUtils;
import com.cgoab.offline.ui.NewJournalDialog;

public class NewJournalAction extends Action {

	private final Shell shell;

	public NewJournalAction(Shell shell) {
		super("New Journal");
		this.shell = shell;
	}

	public void run() {
		// 1) save current journal
		JournalSelectionService selection = JournalSelectionService.getInstance();
		Journal currentJournal = selection.getCurrentJournal();
		if (currentJournal != null && !JournalUtils.closeJournal(currentJournal, shell)) {
			return;
		}

		// 2) create new journal
		NewJournalDialog dialog = new NewJournalDialog(shell);
		if (dialog.open() != SWT.OK) {
			return;
		}
		File file = new File(dialog.getLocation());
		File parent = file.getParentFile();
		if (parent != null && !parent.exists()) {
			parent.mkdirs();
		}
		Journal newJournal = new Journal(file, dialog.getName());

		JournalUtils.saveJournal(newJournal, false, shell);
		selection.setJournal(newJournal);
	}
}
