package com.cgoab.offline.ui.actions;

import org.eclipse.swt.SWT;

import com.cgoab.offline.model.Journal;
import com.cgoab.offline.ui.JournalSelectionService;
import com.cgoab.offline.ui.JournalSelectionService.JournalSelectionListener;

public class NewPageAction extends ActionWithCurrentJournal {

	public NewPageAction() {
		super("&New Page");
		setAccelerator(SWT.MOD1 + 'N');
		JournalSelectionService.getInstance().addListener(new JournalSelectionListener() {
			@Override
			public void selectionChanged(Object newSelection, Object oldSelection) {
			}

			@Override
			public void journalOpened(Journal journal) {
				setEnabled(true);
			}

			@Override
			public void journalClosed(Journal journal) {
				setEnabled(false);
			}
		});
	}

	public void run(Journal journal) {
		journal.createNewPage();
	}
}
