package com.cgoab.offline.ui;

import java.io.File;

import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cgoab.offline.model.Journal;
import com.cgoab.offline.model.JournalXmlLoader;
import com.cgoab.offline.util.resizer.ResizerService;

public class JournalUtils {

	private static final Logger LOG = LoggerFactory.getLogger(JournalUtils.class);

	private static void showError(String message, Shell shell) {
		MessageBox box = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
		box.setMessage(message);
		box.open();
	}

	/**
	 * Opens a progress box and waits for completion of pending resize tasks.
	 * 
	 * @param journal
	 * @return true if the block completed
	 */
	public static boolean blockUntilPhotosResized(ResizerService service, Shell shell) {
		if (service == null || service.activeTasks() == 0) {
			return true;
		}
		// show progress monitor wait for completion
		ProgressMonitorDialog progress = new ProgressMonitorDialog(shell);
		RezierWaitTask waiter = new RezierWaitTask(service, shell.getDisplay());
		try {
			progress.run(false, true, waiter);
		} catch (Exception e) {
			/* ignore */
		}
		return !waiter.isCancelled();

	}

	/**
	 * Closes the currently open journal, takes care of prompting for save and
	 * removing any listeners to the journal.
	 * 
	 * @return true if the close succeeded, false if it was cancelled.
	 */
	public static boolean closeJournal(Journal journal, Shell shell) {
		if (journal.isDirty()) {
			if (!promptAndSaveJournal(journal, shell)) {
				return false;
			}
		}

		JournalSelectionService.getInstance().setJournal(null);
		return true;
	}

	/**
	 * Prompts the user to save the given journal.
	 * 
	 * @param journal
	 *            journal to save
	 * @return <tt>false</tt> if the operation was cancelled.
	 */
	public static boolean promptAndSaveJournal(Journal journal, Shell shell) {
		MessageBox box = new MessageBox(shell, SWT.ICON_WARNING | SWT.CANCEL | SWT.YES | SWT.NO);
		box.setText("Confirm save");
		box.setMessage("Save changes to [" + journal.getName() + "] before closing?");
		switch (box.open()) {
		case SWT.CANCEL:
			return false;
		case SWT.YES:
			saveJournal(journal, false, shell);
			break;
		case SWT.NO:
			// changes will be lost
		}
		return true;
	}

	public static boolean openJournal(String path, boolean quiet, Shell shell) {
		LOG.debug("Loading journal from [{}]", path);
		File file = new File(path);
		if (!file.exists()) {
			LOG.debug("File [{}] does not exist!", path);
			if (!quiet) {
				showError("File '" + path + "' does not exist!", shell);
			}
			return false;
		}
		Journal journal;
		try {
			journal = JournalXmlLoader.open(file);
			LOG.debug("Loaded journal [{} with {} pages] from [{}]", new Object[] { journal.getName(),
					journal.getPages().size(), path });

		} catch (Exception e) {
			LOG.warn("Failed to load journal from [" + path + "]", e);
			if (!quiet) {
				StringBuilder b = new StringBuilder();
				b.append("Failed to load file '").append(file.getName()).append("'").append(": ").append(e.toString());
				MessageBox box = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
				box.setMessage(b.toString());
				box.setText("Failed to load journal");
				box.open();
			}
			return false;
		}

		try {
			JournalXmlLoader.validateJournal(journal);
			LOG.debug("Journal [{}] is valid", journal.getName());
		} catch (AssertionError e) {
			LOG.warn("Journal [" + journal.getName() + "] is invalid", e);
			if (!quiet) {
				StringBuilder b = new StringBuilder();
				b.append("Journal '").append(journal.getName()).append("' loaded from file '").append(file.getName())
						.append("' is in an invalid state and cannot be opened.\n\n").append(e.toString());
				MessageBox box = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
				box.setMessage(b.toString());
				box.setText("Invalid journal");
				box.open();
			}
			return false;
		}

		/* load journal into UI */
		JournalSelectionService.getInstance().setJournal(journal);
		return true;
	}

	/**
	 * Saves the journal to disk, if <tt>silent</tt> failures will be suppressed
	 * else an error box will pop up.
	 * 
	 * @param journal
	 * @param silent
	 *            suppress warning dialog if file cannot be saved
	 * @return true if the file was saved, false if not.
	 */
	public static boolean saveJournal(Journal journal, boolean silent, Shell shell) {
		if (journal.getFile().exists() && journal.getLastModifiedWhenLoaded() != Journal.NEVER_SAVED_TIMESTAMP
				&& journal.getFile().lastModified() > journal.getLastModifiedWhenLoaded()) {
			MessageBox box = new MessageBox(shell, SWT.ICON_WARNING | SWT.NO | SWT.YES);
			box.setText("Confirm overwrite");
			box.setMessage("A newer version of file '" + journal.getFile().getName()
					+ "' exists, do you want to overwrite?");
			switch (box.open()) {
			case SWT.NO:
				return false;
			}
		}
		try {
			JournalXmlLoader.save(journal);
			journal.setDirty(false);
		} catch (Exception e) {
			LOG.error("Failed to save journal [" + journal.getName() + "]", e);
			if (!silent) {
				MessageBox box = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
				box.setText("Error saving journal");
				box.setMessage("Failed to save journal: " + e.toString());
				box.open();
			}
			return false;
		}
		return true;
	}
}
