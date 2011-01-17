package com.cgoab.offline.ui.actions;

import org.eclipse.swt.widgets.Shell;

import com.cgoab.offline.model.Journal;
import com.cgoab.offline.ui.JournalUtils;

public class CloseJournalAction extends ActionWithCurrentJournal {

	private final Shell shell;

	public CloseJournalAction(Shell shell) {
		super("Close Journal");
		this.shell = shell;
	}

	@Override
	protected void run(Journal currentJournal) {
		JournalUtils.closeJournal(currentJournal, shell);
	}
}
