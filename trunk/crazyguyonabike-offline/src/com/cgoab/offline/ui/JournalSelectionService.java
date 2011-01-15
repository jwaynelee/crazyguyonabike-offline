package com.cgoab.offline.ui;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;

import com.cgoab.offline.model.Journal;
import com.cgoab.offline.model.Page;
import com.cgoab.offline.ui.JournalContentProvider.JournalHolder;
import com.cgoab.offline.util.Assert;

public class JournalSelectionService {

	private static JournalSelectionService instance = new JournalSelectionService();

	public static JournalSelectionService getInstance() {
		return instance;
	}

	private List<JournalSelectionListener> listeners = new ArrayList<JournalSelectionService.JournalSelectionListener>();
	private Object current;
	private TreeViewer viewer;
	private ISelectionChangedListener listener = new ISelectionChangedListener() {

		@Override
		public void selectionChanged(SelectionChangedEvent event) {
			IStructuredSelection s = (IStructuredSelection) event.getSelection();

			Object old = current;

			if (s.size() == 1) {
				current = s.getFirstElement();
			} else {
				current = null;
			}

			for (JournalSelectionListener l : listeners) {
				l.selectionChanged(current, old);
			}
		}
	};

	public void register(TreeViewer viewer) {
		viewer.addSelectionChangedListener(listener);
		this.viewer = viewer;
	}

	public void unregister(ISelectionProvider provider) {
		provider.removeSelectionChangedListener(listener);
	}

	public Page getSelectedPage() {
		return current instanceof Page ? (Page) current : null;
	}

	public Journal getSelectedJournal() {
		return current instanceof Journal ? (Journal) current : null;
	}

	public Journal getCurrentJournal() {
		JournalHolder input = (JournalHolder) viewer.getInput();
		return input == null ? null : input.getJournal();
	}

	public void addListener(JournalSelectionListener listener) {
		listeners.add(listener);
	}

	public interface JournalSelectionListener {
		public void selectionChanged(Object newSelection, Object oldSelection);

		public void journalOpened(Journal journal);

		public void journalClosed(Journal journal);
	}

	public void setPage(Page page) {
		viewer.setSelection(new StructuredSelection(page), true);
	}

	public void setJournal(Journal newJournal) {
		JournalHolder ph = (JournalHolder) viewer.getInput();
		Journal previous = ph == null ? null : ph.getJournal();
		if (previous != null) {
			Assert.isTrue(newJournal == null);
		} else {
			/* ignore */
		}
		viewer.setInput(newJournal == null ? null : new JournalHolder(newJournal));
		for (JournalSelectionListener listener : listeners) {
			if (newJournal == null) {
				listener.journalClosed(previous);
			} else {
				listener.journalOpened(newJournal);
			}
		}
	}
}
