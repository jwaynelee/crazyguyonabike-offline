package com.cgoab.offline.ui.actions;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;

import com.cgoab.offline.model.Journal;
import com.cgoab.offline.ui.JournalSelectionService;
import com.cgoab.offline.ui.JournalSelectionService.JournalSelectionListener;

public class ToggleHideUploadedContent extends ActionWithCurrentJournal {

	public ToggleHideUploadedContent() {
		super("Hide uploaded", IAction.AS_CHECK_BOX);
		JournalSelectionService.getInstance().addListener(new JournalSelectionListener() {

			@Override
			public void journalClosed(Journal journal) {
				setChecked(false);
			}

			@Override
			public void journalOpened(Journal journal) {
				setChecked(journal.isHideUploadedContent());
			}

			@Override
			public void selectionChanged(Object newSelection, Object oldSelection) {
			}
		});
	}

	@Override
	public void run(Journal journal) {
		journal.setHideUploadedContent(isChecked());
	}
}
