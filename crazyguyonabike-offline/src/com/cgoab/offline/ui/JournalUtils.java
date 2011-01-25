package com.cgoab.offline.ui;

import java.io.File;
import java.io.IOException;

import nu.xom.ParsingException;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.MessageDialogWithToggle;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Shell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cgoab.offline.model.Journal;
import com.cgoab.offline.model.JournalXmlLoader;
import com.cgoab.offline.util.resizer.ResizerService;

public class JournalUtils {

	private static final Logger LOG = LoggerFactory.getLogger(JournalUtils.class);

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
	 * Closes journal, takes care of prompting for save and removing any
	 * listeners to the journal.
	 * 
	 * @return true if the close succeeded, false if it was cancelled.
	 */
	public static boolean closeJournal(Journal journal, Shell shell) {
		if (journal.isDirty()) {
			if (!promptAndSaveJournal(journal, shell)) {
				return false;
			}
		}

		/* release the lock */
		// FileLock lock = (FileLock) journal.getData(FileLock.class.getName());
		// Assert.notNull(lock);
		JournalSelectionService.getInstance().setJournal(null);
		return true;
	}

	// private static void releaseLock(FileLock lock) {
	// if (lock != null && lock.isValid()) {
	// try {
	// lock.release();
	// } catch (IOException e) {
	// /* ignore */
	// LOG.warn("Ignoring exception releasing lock", e);
	// }
	// }
	// }

	private static void errorParsingFile(Shell shell, Throwable exception, File file, boolean quiet) {
		LOG.warn("Failed to load journal from [" + file.getAbsolutePath() + "]", exception);
		if (!quiet) {
			StringBuilder msg = new StringBuilder();
			msg.append("Failed to load file '").append(file.getName()).append("'").append(": ")
					.append(exception.toString());
			MessageDialog.openError(shell, "Failed to load Journal", msg.toString());
		}
	}

	public static boolean openJournal(String path, boolean quiet, Shell shell) {
		LOG.debug("Loading journal from [{}]", path);
		File file = new File(path);
		if (!file.exists()) {
			LOG.debug("File [{}] does not exist!", path);
			if (!quiet) {
				MessageDialog.openError(shell, "File not found", "File [" + path + "] does not exist!");
			}
			return false;
		}
		Journal journal;

		// /* attempt to lock the file */
		// RandomAccessFile rof;
		// try {
		// rof = new RandomAccessFile(file, "rw");
		// } catch (IOException e) {
		// throw new IllegalStateException(e);
		// }
		// FileLock lock = null;
		// try {
		// lock = rof.getChannel().tryLock();
		// } catch (IOException e) {
		// LOG.error("Failed to lock file [" + file + "]", e);
		// }
		//
		// if (lock == null) {
		// MessageDialog.openError(shell, "Cannot lock file", "Journal file [" +
		// file.getAbsolutePath()
		// + "] cannot be locked, check it is not openened by another process");
		// return false;
		// }

		try {
			journal = JournalXmlLoader.open(file);
			LOG.debug("Loaded journal [{} with {} pages] from [{}]", new Object[] { journal.getName(),
					journal.getPages().size(), path });
		} catch (ParsingException e) {
			errorParsingFile(shell, e, file, quiet);
			return false;
		} catch (IOException e) {
			errorParsingFile(shell, e, file, quiet);
			return false;
		}

		try {
			JournalXmlLoader.validateJournal(journal);
			LOG.debug("Journal [{}] is valid", journal.getName());
		} catch (AssertionError e) {
			LOG.warn("Journal [" + journal.getName() + "] is invalid", e);
			if (!quiet) {
				StringBuilder msg = new StringBuilder();
				msg.append("Journal '").append(journal.getName()).append("' loaded from file '").append(file.getName())
						.append("' is in an invalid state and cannot be opened.\n\n").append(e.toString());
				MessageDialog.openError(shell, "Invalid journal", msg.toString());
			}
			return false;
		}

		/* load journal into UI */
		JournalSelectionService.getInstance().setJournal(journal);
		return true;
	}

	/**
	 * Prompts the user to save the given journal.
	 * 
	 * @param journal
	 *            journal to save
	 * @return <tt>false</tt> if the operation was cancelled.
	 */
	private static boolean promptAndSaveJournal(Journal journal, Shell shell) {
		MessageDialog box = new MessageDialog(shell, "Confirm save", null, "Save changes to [" + journal.getName()
				+ "] before closing?", MessageDialog.QUESTION_WITH_CANCEL, new String[] { IDialogConstants.YES_LABEL,
				IDialogConstants.NO_LABEL, IDialogConstants.CANCEL_LABEL }, 0);
		switch (box.open()) {
		case 0: /* yes */
			saveJournal(journal, false, shell);
			break;
		case 1: /* no */
			// changes will be lost
			break;
		case 2: /* cancel button */
			/* fall through */
		case SWT.DEFAULT: /* esc or window close */
			return false;
		}
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
			if (!MessageDialog.openQuestion(shell, "Confirm overwrite", "A newer version of file '"
					+ journal.getFile().getName() + "' exists, do you want to overwrite?")) {
				return false;
			}
		}
		try {
			JournalXmlLoader.save(journal);
			journal.setDirty(false);
		} catch (Exception e) {
			LOG.error("Failed to save journal [" + journal.getName() + "]", e);
			if (!silent) {
				MessageDialog.openError(shell, "Error saving journal", "Failed to save journal: " + e.toString());
			}
			return false;
		}
		return true;
	}
}
