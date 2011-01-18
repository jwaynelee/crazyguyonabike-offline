package com.cgoab.offline.ui;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.commands.operations.IUndoContext;
import org.eclipse.core.commands.operations.UndoContext;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IContributionManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.action.StatusLineManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import com.cgoab.offline.client.UploadClientFactory;
import com.cgoab.offline.model.Journal;
import com.cgoab.offline.ui.actions.AddPhotosAction;
import com.cgoab.offline.ui.actions.CloseJournalAction;
import com.cgoab.offline.ui.actions.DeletePageAction;
import com.cgoab.offline.ui.actions.NewJournalAction;
import com.cgoab.offline.ui.actions.NewPageAction;
import com.cgoab.offline.ui.actions.OpenJournalAction;
import com.cgoab.offline.ui.actions.OpenPageInBrowserAction;
import com.cgoab.offline.ui.actions.PurgeResizedPhotosAction;
import com.cgoab.offline.ui.actions.PurgeThumbnailCacheAction;
import com.cgoab.offline.ui.actions.RedoAction;
import com.cgoab.offline.ui.actions.SaveAction;
import com.cgoab.offline.ui.actions.ToggleHideUploadedContent;
import com.cgoab.offline.ui.actions.ToggleResizeImagesBeforeUpload;
import com.cgoab.offline.ui.actions.ToggleUseExifThumbnailAction;
import com.cgoab.offline.ui.actions.UndoAction;
import com.cgoab.offline.ui.actions.ViewResizedPhotosAction;
import com.cgoab.offline.ui.thumbnailviewer.CachingThumbnailProviderFactory;
import com.cgoab.offline.util.Utils;
import com.cgoab.offline.util.resizer.ImageMagickResizerServiceFactory;

public class ApplicationWindow extends org.eclipse.jface.window.ApplicationWindow {

	private static final String OPENJOURNALS_PREFERENCE_PATH = "/openjournals";

	private JournalViewer journalView;

	// private ImageCache thumbnailCache;
	public Action newJournalAction, openJournalAction, closeJournalAction, saveAction, uploadAction, deletePageAction,
			addPhotosAction, openPageInBrowserAction, toggleHideUploadedContent, toggleResizePhotos,
			viewResizedPhotosFolder, purgeResizedPhotos, toggleUseExifThumbnailAction, purgeThumbnailCache,
			newPageAction;

	public static IUndoContext APPLICATION_CONTEXT = new UndoContext();

	IUndoContext currentContext = APPLICATION_CONTEXT;

	private IAction openPreferencesAction = new Action("Preferences") {
		@Override
		public void run() {
			PreferencesDialog prefs = new PreferencesDialog(getShell());
			prefs.setPreferences(preferences);
			prefs.open();
		}
	};

	// private IAction renameJournalAction = new Action("Rename") {
	// public void run() {
	// treeViewer.editElement(((IStructuredSelection)
	// treeViewer.getSelection()).getFirstElement(), 0);
	// }
	// };

	private Preferences preferences;
	private ImageMagickResizerServiceFactory resizerServiceFactory;
	private CachingThumbnailProviderFactory thumbnailProviderFactory;
	private ThumbnailView thumbnailView;

	private UploadClientFactory uploadFactory;

	public ApplicationWindow(Shell shell) {
		super(shell);
		setBlockOnOpen(true);
		addStatusLine();
		addMenuBar();
	}

	@Override
	protected boolean showTopSeperator() {
		return false;
	}

	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		String name = Utils.getNameString(getClass());
		String version = Utils.getVersionString(getClass());
		name = name == null ? "?" : name;
		version = version == null ? "?" : version;
		shell.setText(name + " : " + version);
	}

	private void createActions() {
		Shell shell = getShell();
		newJournalAction = new NewJournalAction(shell);
		newPageAction = new NewPageAction();
		openJournalAction = new OpenJournalAction(shell);
		closeJournalAction = new CloseJournalAction(shell);
		saveAction = new SaveAction(shell);
		deletePageAction = new DeletePageAction(shell, journalView.getViewer());
		addPhotosAction = new AddPhotosAction(shell, thumbnailView);
		openPageInBrowserAction = new OpenPageInBrowserAction(shell);
		toggleHideUploadedContent = new ToggleHideUploadedContent();
		toggleResizePhotos = new ToggleResizeImagesBeforeUpload(thumbnailView);
		viewResizedPhotosFolder = new ViewResizedPhotosAction();
		purgeResizedPhotos = new PurgeResizedPhotosAction(shell);
		toggleUseExifThumbnailAction = new ToggleUseExifThumbnailAction();
		purgeThumbnailCache = new PurgeThumbnailCacheAction(shell);
		UploadAction uploadAction = new UploadAction(shell, thumbnailView.getViewer(), journalView.getViewer());
		uploadAction.setUploadFactory(uploadFactory);
		uploadAction.setPreferences(preferences);
		this.uploadAction = uploadAction;

		fileMenuMgr.add(newJournalAction);
		fileMenuMgr.add(newPageAction);
		fileMenuMgr.add(openJournalAction);
		fileMenuMgr.add(new Separator());
		fileMenuMgr.add(closeJournalAction);
		fileMenuMgr.add(new Separator());
		fileMenuMgr.add(saveAction);
		fileMenuMgr.add(new Separator());
		fileMenuMgr.add(openPreferencesAction);
		fileMenuMgr.add(new Separator());
		fileMenuMgr.add(new Action("Exit") {
			@Override
			public void run() {
				getShell().close();
			}
		});

		editMenuMgr.add(new UndoAction(this));
		editMenuMgr.add(new RedoAction(this));

		/* rebuilt menu(s) */
		IContributionManager root = fileMenuMgr.getParent();
		root.update(true);
	}

	private void createControls(final Composite parent) {
		SashForm sashV = new SashForm(parent, SWT.VERTICAL);
		SashForm sashH = new SashForm(sashV, SWT.HORIZONTAL);
		sashH.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		sashV.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		journalView = new JournalViewer(this);
		journalView.createComponents(sashH);

		PageEditor pageEditor;
		pageEditor = new PageEditor(this);
		pageEditor.createControls(sashH);
		Composite thumbViewerGroup = new Composite(sashV, SWT.NONE);
		thumbViewerGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		thumbViewerGroup.setLayout(new GridLayout());
		thumbnailView = new ThumbnailView(this, resizerServiceFactory, thumbnailProviderFactory);
		thumbnailView.createComponents(thumbViewerGroup);
		sashV.setWeights(new int[] { 10, 9 });
		sashH.setWeights(new int[] { 1, 3 });
	}

	MenuManager fileMenuMgr;

	@Override
	protected StatusLineManager getStatusLineManager() {
		return super.getStatusLineManager();
	}

	MenuManager editMenuMgr;

	@Override
	protected MenuManager createMenuManager() {
		/* actions not created yet so just make top level menus */
		MenuManager root = new MenuManager();
		fileMenuMgr = new MenuManager("File");
		editMenuMgr = new MenuManager("Edit");
		root.add(fileMenuMgr);
		root.add(editMenuMgr);
		return root;
	}

	@Override
	protected void handleShellCloseEvent() {
		Journal journal = JournalSelectionService.getInstance().getCurrentJournal();
		if (journal != null) {
			preferences.setValue(OPENJOURNALS_PREFERENCE_PATH, journal.getFile().getAbsolutePath());
			preferences.save();
			if (!JournalUtils.closeJournal(journal, getShell())) {
				return; /* abort close */
			}
		}
		super.handleShellCloseEvent();
	}

	@Override
	protected Control createContents(Composite parent) {
		createControls(parent);
		createActions();

		/* TODO load on background thread? */
		String journalToOpen = preferences.getValue(OPENJOURNALS_PREFERENCE_PATH);
		if (journalToOpen != null && !JournalUtils.openJournal(journalToOpen, true, getShell())) {
			// update preferences if last journal failed to load
			preferences.removeValue(OPENJOURNALS_PREFERENCE_PATH);
			preferences.save();
		}
		return parent;
	}

	public void setPreferences(Preferences preferences) {
		this.preferences = preferences;
	}

	public void setResizerServiceFactory(ImageMagickResizerServiceFactory resizerServiceFactory) {
		this.resizerServiceFactory = resizerServiceFactory;
	}

	public void setThumbnailProviderFactory(CachingThumbnailProviderFactory thumbnailFactory) {
		thumbnailProviderFactory = thumbnailFactory;
	}

	public void setUploadFactory(UploadClientFactory uploadFactory) {
		this.uploadFactory = uploadFactory;
	}

	public void setCurrentUndoContext(IUndoContext newContext) {
		if (newContext == null) {
			currentContext = APPLICATION_CONTEXT;
		} else {
			currentContext = newContext;
		}
		for (ContextChangedListener listener : listeners) {
			listener.contextChanged(currentContext);
		}
	}

	public void addUndoContextChangedListener(ContextChangedListener listener) {
		listeners.add(listener);
	}

	private List<ContextChangedListener> listeners = new ArrayList<ApplicationWindow.ContextChangedListener>();

	public IUndoContext getCurrentOperationContext() {
		return currentContext;
	}

	public interface ContextChangedListener {
		public void contextChanged(IUndoContext context);
	}
}