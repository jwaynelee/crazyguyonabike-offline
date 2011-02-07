package com.cgoab.offline.ui;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;

import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ColumnViewerEditorActivationEvent;
import org.eclipse.jface.viewers.ColumnViewerEditorActivationStrategy;
import org.eclipse.jface.viewers.ICellModifier;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerEditor;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.TreeItem;

import com.cgoab.offline.model.Journal;
import com.cgoab.offline.model.Page;
import com.cgoab.offline.model.UploadState;
import com.cgoab.offline.ui.JournalSelectionService.JournalSelectionListener;

public class JournalViewer {

	private MainWindow window;

	private TreeViewer treeViewer;

	public JournalViewer(MainWindow editor) {
		this.window = editor;
		JournalSelectionService.getInstance().addListener(new JournalSelectionListener() {

			PropertyChangeListener hideUploadedChangeListener = new PropertyChangeListener() {
				@Override
				public void propertyChange(PropertyChangeEvent evt) {
					if (Journal.HIDE_UPLOADED_CONTENT.equals(evt.getPropertyName())) {
						treeViewer.refresh();
					}
				}
			};

			PropertyChangeListener updateLabelListener = new PropertyChangeListener() {
				@Override
				public void propertyChange(PropertyChangeEvent evt) {
					treeViewer.update(evt.getSource(), new String[] { evt.getPropertyName() });
				}
			};

			@Override
			public void journalClosed(Journal journal) {
				treeViewer.setInput(null);
				journal.removePropertyChangeListener(hideUploadedChangeListener);
			}

			@Override
			public void journalOpened(Journal journal) {
				/* select the last non-uploaded page else the journal */
				List<Page> pages = journal.getPages();
				Object o = journal;
				if (pages.size() > 0) {
					Page lastPage = pages.get(pages.size() - 1);
					if (lastPage.getState() != UploadState.UPLOADED) {
						o = lastPage;
					}
				}
				journal.addPropertyChangeListener(hideUploadedChangeListener);
				final Object toSelect = o;
				Display.getCurrent().asyncExec(new Runnable() {
					@Override
					public void run() {
						/*
						 * hack; a page can't be openend until all the others
						 * listeners finish.
						 */
						treeViewer.setSelection(new StructuredSelection(toSelect));
					}
				});
			}

			@Override
			public void selectionChanged(Object newSelection, Object oldSelection) {
				if (newSelection instanceof Page) {
					((Page) newSelection).addPropertyChangeListener(updateLabelListener);
				}
				if (oldSelection instanceof Page) {
					((Page) oldSelection).removePropertyChangeListener(updateLabelListener);
				}
			}
		});
	}

	public void createComponents(Composite parent) {
		treeViewer = new TreeViewer(parent);
		JournalSelectionService.getInstance().register(treeViewer);
		GridData data = new GridData(SWT.FILL, SWT.FILL, true, true);
		treeViewer.getTree().setLayoutData(data);

		TreeViewerEditor.create(treeViewer, new ColumnViewerEditorActivationStrategy(treeViewer) {
			@Override
			protected boolean isEditorActivationEvent(ColumnViewerEditorActivationEvent event) {
				return event.eventType == ColumnViewerEditorActivationEvent.PROGRAMMATIC;
			}

			@Override
			public void setEnableEditorActivationWithKeyboard(boolean enable) {
			}
		}, ColumnViewerEditorActivationEvent.PROGRAMMATIC);

		CellEditor[] editors = new CellEditor[1];
		editors[0] = new TextCellEditor(treeViewer.getTree());
		treeViewer.getTree().addKeyListener(new KeyListener() {
			@Override
			public void keyPressed(KeyEvent e) {
			}

			@Override
			public void keyReleased(KeyEvent e) {
				if (e.keyCode == SWT.DEL) {
					window.deletePageAction.run();
				}
			}
		});
		treeViewer.setColumnProperties(new String[] {});
		treeViewer.setCellEditors(editors);
		treeViewer.getTree().addFocusListener(new FocusListener() {

			@Override
			public void focusGained(FocusEvent e) {
				window.setCurrentUndoContext(MainWindow.APPLICATION_CONTEXT);
			}

			@Override
			public void focusLost(FocusEvent e) {
			}
		});
		treeViewer.addFilter(new ViewerFilter() {
			@Override
			public boolean select(Viewer viewer, Object parentElement, Object element) {
				if (element instanceof Page) {
					Page page = (Page) element;
					if (page.getJournal().isHideUploadedContent()) {
						return ((Page) element).getState() != UploadState.UPLOADED;
					}
				}
				return true;
			}
		});
		treeViewer.setCellModifier(new ICellModifier() {
			@Override
			public boolean canModify(Object element, String property) {
				return element instanceof Journal;
			}

			@Override
			public Object getValue(Object element, String property) {
				return element.toString();
			}

			@Override
			public void modify(Object element, String property, Object value) {
				TreeItem item = (TreeItem) element;
				Journal journal = (Journal) item.getData();
				String newName = value.toString();
				String oldName = journal.getName();
				if (!oldName.equals(newName)) {
					journal.setName(newName);
					journal.setDirty(true);
					treeViewer.update(journal, null);
				}
			}
		});

		treeViewer.setColumnProperties(new String[] { "name" });
		treeViewer.setContentProvider(new JournalContentProvider());
		treeViewer.setLabelProvider(new JournalTreeLabelProvider(treeViewer, parent.getShell()));
		treeViewer.getControl().setMenu(createMenu());
	}

	private Menu createMenu() {
		MenuManager menuMgr = new MenuManager();
		menuMgr.addMenuListener(new IMenuListener() {
			@Override
			public void menuAboutToShow(IMenuManager manager) {
				IStructuredSelection currentSelection = ((IStructuredSelection) treeViewer.getSelection());
				boolean hasSelection = currentSelection.size() > 0;

				manager.add(window.newJournalAction);

				if (hasSelection) {
					manager.add(window.newPageAction);
				}
				manager.add(window.openJournalAction);
				if (hasSelection) {
					manager.add(new Separator());
					manager.add(window.closeJournalAction);
					manager.add(new Separator());
					manager.add(window.saveAction);
					manager.add(new Separator());
					manager.add(window.uploadAction);
					manager.add(new Separator());
					manager.add(window.deletePageAction);
					manager.add(window.addPhotosAction);
					manager.add(window.openPageInBrowserAction);
					manager.add(new Separator());
					manager.add(window.toggleHideUploadedContent);
					manager.add(new Separator());
					manager.add(window.toggleResizePhotos);
					manager.add(window.viewResizedPhotosFolder);
					manager.add(window.purgeResizedPhotos);
					manager.add(new Separator());
					manager.add(window.toggleUseExifThumbnailAction);
					manager.add(window.purgeThumbnailCache);
				}
			}
		});
		menuMgr.setRemoveAllWhenShown(true);
		return menuMgr.createContextMenu(treeViewer.getControl());
	}

	public TreeViewer getViewer() {
		return treeViewer;
	}
}
