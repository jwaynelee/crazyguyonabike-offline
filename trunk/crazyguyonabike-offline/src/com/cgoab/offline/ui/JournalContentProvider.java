package com.cgoab.offline.ui;

import java.util.List;

import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;

import com.cgoab.offline.model.Journal;
import com.cgoab.offline.model.JournalListener;
import com.cgoab.offline.model.Page;
import com.cgoab.offline.model.Photo;

public class JournalContentProvider implements IStructuredContentProvider, ITreeContentProvider, JournalListener {

	private TreeViewer treeViewer;

	@Override
	public void dispose() {
	}

	@Override
	public Object[] getChildren(Object o) {
		if (o instanceof Journal) {
			return ((Journal) o).getPages().toArray();
		}
		return new Object[0];
	}

	@Override
	public Object[] getElements(Object parent) {
		return new Object[] { ((JournalHolder) parent).getJournal() };
	}

	@Override
	public Object getParent(Object child) {
		if (child instanceof Journal) {
			return null;
		} else {
			return ((Page) child).getJournal();
		}
	}

	@Override
	public boolean hasChildren(Object parent) {
		if (parent instanceof Journal) {
			return ((Journal) parent).getPages().size() > 0;
		}
		return false;
	}

	@Override
	public void inputChanged(Viewer v, Object oldInput, Object newInput) {
		treeViewer = (TreeViewer) v;
		if (oldInput != null) {
			((JournalHolder) oldInput).getJournal().removeListener(this);
		}
		if (newInput != null) {
			((JournalHolder) newInput).getJournal().addJournalListener(this);
		}
	}

	@Override
	public void journalDirtyChange() {
		// update to show "*" dirty marker
		JournalHolder holder = (JournalHolder) treeViewer.getInput();
		treeViewer.update(holder.getJournal(), null);
	}

	@Override
	public void pageAdded(Page page) {
		treeViewer.refresh();
		treeViewer.setSelection(new StructuredSelection(page), true);
	}

	@Override
	public void pageDeleted(Page page) {
		treeViewer.refresh();
	}

	@Override
	public void photosAdded(List<Photo> photo, Page page) {
	}

	@Override
	public void photosRemoved(List<Photo> photos, Page page) {
	}

	/**
	 * Workaround for "bug" in JFace TreeViewer
	 * https://bugs.eclipse.org/bugs/show_bug.cgi?id=9262
	 */
	public static final class JournalHolder {
		private final Journal journal;

		public JournalHolder(Journal journal) {
			this.journal = journal;
		}

		public Journal getJournal() {
			return journal;
		}
	}
}