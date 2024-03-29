package com.cgoab.offline.ui.actions;

import org.eclipse.jface.action.Action;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Shell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cgoab.offline.model.Journal;
import com.cgoab.offline.model.Page;
import com.cgoab.offline.ui.JournalSelectionService;
import com.cgoab.offline.ui.JournalUtils;

public class SaveAction extends Action {

	static Logger LOG = LoggerFactory.getLogger(SaveAction.class);

	private final Shell shell;

	public SaveAction(Shell shell) {
		super("&Save Journal");
		setAccelerator(SWT.MOD1 + 'S');
		this.shell = shell;
	}

	@Override
	public void run() {
		Journal journal = JournalSelectionService.getInstance().getCurrentJournal();
		if (journal != null) {
			JournalUtils.saveJournal(journal, false, shell);
		}
	}
}
