package com.cgoab.offline.ui.actions;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.resource.ImageDescriptor;

import com.cgoab.offline.model.Journal;
import com.cgoab.offline.ui.JournalSelectionService;

abstract class ActionWithCurrentJournal extends Action {

	public ActionWithCurrentJournal() {
		super();
	}

	public ActionWithCurrentJournal(String text) {
		super(text);
	}

	public ActionWithCurrentJournal(String text, ImageDescriptor image) {
		super(text, image);
	}

	public ActionWithCurrentJournal(String text, int style) {
		super(text, style);
	}

	@Override
	public final void run() {
		Journal journal = JournalSelectionService.getInstance().getCurrentJournal();
		if (journal != null) {
			run(journal);
		}
	}

	protected abstract void run(Journal currentJournal);
}