package com.cgoab.offline.ui;

import static com.cgoab.offline.util.StringUtils.nullToEmpty;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextListener;
import org.eclipse.jface.text.TextEvent;
import org.eclipse.jface.text.TextViewer;
import org.eclipse.jface.text.TextViewerUndoManager;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ColumnViewerEditorActivationEvent;
import org.eclipse.jface.viewers.ColumnViewerEditorActivationStrategy;
import org.eclipse.jface.viewers.ICellModifier;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerEditor;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.events.MenuListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.events.TraverseEvent;
import org.eclipse.swt.events.TraverseListener;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.events.VerifyListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DateTime;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.text.undo.DocumentUndoManagerRegistry;
import org.eclipse.text.undo.IDocumentUndoManager;
import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cgoab.offline.model.Journal;
import com.cgoab.offline.model.JournalAdapter;
import com.cgoab.offline.model.JournalXmlLoader;
import com.cgoab.offline.model.Page;
import com.cgoab.offline.model.Page.EditFormat;
import com.cgoab.offline.model.Page.HeadingStyle;
import com.cgoab.offline.model.Page.PhotosOrder;
import com.cgoab.offline.model.Photo;
import com.cgoab.offline.model.UploadState;
import com.cgoab.offline.ui.JournalContentProvider.JournalHolder;
import com.cgoab.offline.ui.thumbnailviewer.CachingThumbnailProvider;
import com.cgoab.offline.ui.thumbnailviewer.CachingThumbnailProviderFactory;
import com.cgoab.offline.ui.thumbnailviewer.ThumbnailProvider;
import com.cgoab.offline.ui.thumbnailviewer.ThumbnailViewer;
import com.cgoab.offline.util.Assert;
import com.cgoab.offline.util.JobListener;
import com.cgoab.offline.util.StringUtils;
import com.cgoab.offline.util.Utils;
import com.cgoab.offline.util.resizer.ImageMagickResizeTask.MagicNotAvailableException;
import com.cgoab.offline.util.resizer.ImageMagickResizerServiceFactory;
import com.cgoab.offline.util.resizer.ResizerService;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.jpeg.JpegDirectory;

public class PageEditor {

	private static final Logger LOG = LoggerFactory.getLogger(PageEditor.class);
	private static final String OPENJOURNALS_PREFERENCE_PATH = "/openjournals";

	// private Map<Page, Document> documentCache = new HashMap<Page,
	// Document>();
	private ImageMagickResizerServiceFactory resizerServiceFactory;

	private Journal getCurrentJournal() {
		IStructuredSelection selection = (IStructuredSelection) treeViewer.getSelection();
		if (selection.size() != 1) {
			return null;
		}
		Object first = selection.getFirstElement();
		if (first instanceof Journal) {
			return (Journal) first;
		}
		if (first instanceof Page) {
			return ((Page) first).getJournal();
		}
		return null;
	}

	abstract class ActionWithJournal extends Action {

		public ActionWithJournal() {
			super();
		}

		public ActionWithJournal(String text, ImageDescriptor image) {
			super(text, image);
		}

		public ActionWithJournal(String text, int style) {
			super(text, style);
		}

		public ActionWithJournal(String text) {
			super(text);
		}

		@Override
		public final void run() {
			Journal journal = getCurrentJournal();
			if (journal == null) {
				return;
			}
			run(journal);
		}

		protected abstract void run(Journal currentJournal);
	}

	private IAction openResizedPhotosFolder = new ActionWithJournal("View resized photos") {
		{
			setEnabled(Desktop.isDesktopSupported());
		}

		public void run(Journal journal) {
			ResizerService resizer = resizerServiceFactory.getResizerFor(journal);
			if (resizer == null) {
				return;
			}
			File folder = resizer.getPhotoFolder();
			try {
				Desktop.getDesktop().open(folder);
			} catch (IOException e) {
				/* ignore */
				LOG.debug("Failed to browse folder " + folder);
			}
		}
	};

	private IAction purgeResizedPhotos = new ActionWithJournal("Purge resized photos") {
		public void run(Journal journal) {
			ResizerService service = resizerServiceFactory.getOrCreateResizerFor(journal);
			long bytes = service.purge();
			if (bytes > 0) {
				MessageBox box = new MessageBox(shell, SWT.ICON_INFORMATION | SWT.OK);
				box.setText("Photos purged");
				box.setMessage(Utils.formatBytes(bytes) + " of resized photos deleted");
				box.open();
			}
		}
	};

	private IAction purgeThumbnailCache = new Action("Purge thumbnail cache") {
		public void run() {
			Journal journal = getCurrentJournal();
			if (journal == null) {
				return;
			}
			CachingThumbnailProvider provider = thumbnailProviderFactory.getThumbnailProvider(journal);
			if (provider == null) {
				return;
			}
			// TODO progress bar?
			long bytes = provider.purge();
			if (bytes > 0) {
				MessageBox box = new MessageBox(shell, SWT.ICON_INFORMATION | SWT.OK);
				box.setText("Thumbnails purged");
				box.setMessage(Utils.formatBytes(bytes) + " of thumbnails deleted");
				box.open();
			}
		}
	};

	IAction toggleResizePhotos = new ActionWithJournal("Resize photos", Action.AS_CHECK_BOX) {
		public void run(Journal journal) {
			Boolean currentSetting = journal.isResizeImagesBeforeUpload();
			if (currentSetting == null || currentSetting != isChecked()) {
				if (isChecked()) {
					if (!registerPhotoResizer(journal, false)) {
						return;
					}
				} else {
					// first cancel all pending work
					ResizerService resizer = resizerServiceFactory.getResizerFor(journal);
					if (resizer != null) {
						resizer.cancelAll();
					}
					unregisterPhotoResizer(journal);
				}
				journal.setResizeImagesBeforeUpload(isChecked());
				journal.setDirty(true);
			}
		}
	};

	private IAction toggleUseExifThumbnailAction = new ActionWithJournal("Use EXIF thumbnail", Action.AS_CHECK_BOX) {
		@Override
		public void run(Journal journal) {
			CachingThumbnailProvider provider = thumbnailProviderFactory.getOrCreateThumbnailProvider(journal);
			provider.setUseExifThumbnail(isChecked());
			// trigger a refresh of thumbnails in viewer
			thumbViewer.refresh();
		}
	};

	private IAction addPhotosAction = new Action("Add Photos") {
		public void run() {
			if (currentPage == null) {
				return;
			}
			FileDialog dialog = getOrCreateFileDialog();
			dialog.setText("Select photos to add to page");
			dialog.setFileName("");
			dialog.open();
			String[] fileStrings = dialog.getFileNames();
			if (fileStrings.length == 0) {
				return;
			}

			// add photos to the model, manually trigger refresh of the viewer
			File[] files = new File[fileStrings.length];
			for (int i = 0; i < fileStrings.length; ++i) {
				files[i] = new File(dialog.getFilterPath() + File.separator + fileStrings[i]);
			}

			// update the model via view controller to apply the same error
			// handling for duplicates etc..
			((PhotosContentProvider) thumbViewer.getContentProvider()).addPhotosRetryIfDuplicates(files, -1);
		}
	};

	private Button btnBold;

	private Button btnHideUploaded;

	private Button btnItalic;

	private Button btnSortByDate;

	private Button btnSortByName;

	private Button btnSortManual;

	private Listener btnSortSelectionListener;

	private TextViewer captionText;

	private Combo cmbFormat;

	private Combo cmbHeadingStyle;

	private Combo cmbIndent;

	private Page currentPage;

	private DateTime dateInput;

	private IAction deletePageAction = new Action("Delete Page") {
		public void run() {
			List<Page> pagesToDelete = new ArrayList<Page>();
			if (treeViewer.getSelection() instanceof IStructuredSelection) {
				IStructuredSelection s = (IStructuredSelection) treeViewer.getSelection();
				for (Object o : s.toList()) {
					if (o instanceof Page) {
						pagesToDelete.add((Page) o);
					}
				}
			}

			if (pagesToDelete.size() == 0) {
				return;
			}

			MessageBox confirm = new MessageBox(shell, SWT.CANCEL | SWT.OK | SWT.ICON_QUESTION);
			StringBuilder str = new StringBuilder("Are you sure you want to delete the following page(s):\n");
			for (Page p : pagesToDelete) {
				str.append("   '").append(p.getTitle()).append(":").append(p.getHeadline()).append("'\n");
			}
			confirm.setText("Confirm delete");
			confirm.setMessage(str.toString());
			if (confirm.open() == SWT.OK) {
				Object previousPageOfFirstDeletedPage = null;
				for (Page toDelete : pagesToDelete) {
					Journal journal = toDelete.getJournal();
					List<Page> pages = journal.getPages();
					int id = pages.indexOf(toDelete);
					journal.removePage(toDelete);
					if (previousPageOfFirstDeletedPage == null) {
						previousPageOfFirstDeletedPage = pages.size() == 0 ? toDelete.getJournal() : pages.get(Math
								.min(pages.size() - 1, id));
					}
				}
				treeViewer.setSelection(new StructuredSelection(previousPageOfFirstDeletedPage));
			}
		}
	};

	private DirtyCurrentPageListener dirtyCurrentPageListener = new DirtyCurrentPageListener();

	private Text distanceInput;

	FileDialog fileDialog;

	private Text headlineInput;

	private IAction newJournalAction = new Action("New Journal") {
		public void run() {
			// 1) save current journal
			if (!closeCurrentJournal()) {
				return;
			}

			// 2) create new journal
			NewJournalDialog dialog = new NewJournalDialog(shell);
			if (dialog.open() != SWT.OK) {
				return;
			}
			File file = new File(dialog.getLocation());
			File parent = file.getParentFile();
			if (parent != null && !parent.exists()) {
				parent.mkdirs();
			}
			Journal newJournal = new Journal(file, dialog.getName());
			saveJournal(newJournal, false);
			bindJournal(newJournal);
		}
	};

	private IAction newPageAction = new Action("New Page") {
		public void run() {
			// save current page
			Object i = ((IStructuredSelection) treeViewer.getSelection()).getFirstElement();
			Page page = null;
			Journal journal = null;
			if (i instanceof Journal) {
				journal = (Journal) i;
			} else if (i instanceof Page) {
				journal = ((Page) i).getJournal();
			} else {
				return;
			}

			page = journal.createNewPage();
			if (page != null) {
				treeViewer.setExpandedState(page.getJournal(), true);
				treeViewer.setSelection(new StructuredSelection(page), true);
				// give focus to first item in edit window
				titleInput.setFocus();
			}
		};
	};

	private IAction openJournalAction = new Action("Open Journal") {
		public void run() {
			FileDialog fd = new FileDialog(shell, SWT.OPEN);
			fd.setFilterExtensions(new String[] { "*" + NewJournalDialog.EXTENSION });
			String path = fd.open();
			if (path != null) {
				if (closeCurrentJournal()) {
					openJournal(path, false);
				}
			}
		}
	};

	private IAction openPreferencesAction = new Action("Preferences") {
		public void run() {
			PreferencesDialog prefs = new PreferencesDialog(shell);
			prefs.setPreferences(preferences);
			prefs.open();
		}
	};

	// current page in the UI, maybe null
	Map<PhotosOrder, Button> orderByButtonMap = new HashMap<Page.PhotosOrder, Button>();

	private List<Control> pageEditorWidgets;

	private Preferences preferences;

	private IAction closeJournalAction = new Action("Close Journal") {
		public void run() {
			closeCurrentJournal();
		}
	};

	private IAction renameJournalAction = new Action("Rename") {
		public void run() {
			treeViewer.editElement(((IStructuredSelection) treeViewer.getSelection()).getFirstElement(), 0);
		}
	};

	private IAction saveAction = new Action("Save Journal") {

		public void run() {
			Journal journal = currentPage == null ? null : currentPage.getJournal();
			Object first = ((IStructuredSelection) treeViewer.getSelection()).getFirstElement();
			if (first != null && (first instanceof Journal)) {
				journal = (Journal) first;
			}

			if (journal == null) {
				return;
			}

			saveJournal(journal, false);
		}
	};

	private FocusListener selectCurrentPageListener = new FocusListener() {

		@Override
		public void focusGained(FocusEvent e) {
			// make sure tree viewer showing correct item
			if (currentPage != null) {
				treeViewer.setSelection(new StructuredSelection(currentPage));
			}
		}

		@Override
		public void focusLost(FocusEvent e) {
		}
	};

	private Shell shell;

	private Label statusBar;

	private TextViewer textInput;

	private ThumbnailViewer thumbViewer;
	private Text titleInput;
	private TreeViewer treeViewer;
	private UploadAction uploadAction;

	private TextViewerUndoManager undoManager;

	private StatusUpdater statusListener;
	private CachingThumbnailProviderFactory thumbnailProviderFactory;

	public PageEditor(Shell shell) {
		this.shell = shell;
		shell.setText("CGOAB Offline");
	}

	public void afterUpload(Page errorPage, Photo errorPhoto) {
		treeViewer.refresh();

		if (errorPage == null) {
			displayPage(null);
		} else {
			// selection event triggers refresh of thumnail viewer
			treeViewer.setSelection(new StructuredSelection(errorPage), true);

			if (errorPage == currentPage) {
				// HACK: force refresh of thumbnail viewer
				thumbViewer.refresh();
			}

			if (errorPhoto != null) {
				thumbViewer.setSelection(new StructuredSelection(errorPhoto), true);
			}
		}
	}

	// private ImageCache thumbnailCache;

	private void bindControls() {
		bindToCurrentPage(btnBold, SWT.Selection, "bold", true);
		bindToCurrentPage(btnItalic, SWT.Selection, "italic", true);
		bindToCurrentPage(cmbIndent, SWT.Selection, "indent", true);
		bindToCurrentPage(cmbHeadingStyle, SWT.Selection, "headingStyle", true);
		bindToCurrentPage(cmbFormat, SWT.Selection, "format", false);
		bindToCurrentPage(titleInput, SWT.Modify, "title", true);
		bindToCurrentPage(headlineInput, SWT.Modify, "headline", true);
		bindToCurrentPage(dateInput, SWT.FocusOut, "date", false);
		bindToCurrentPage(distanceInput, SWT.FocusOut, "distance", false);
		// bindToCurrentPage(textInput.getControl(), SWT.FocusOut, "text",
		// false);
		// bindToCurrentPhoto(captionText.getControl(), SWT.FocusOut,
		// "caption");

		btnSortSelectionListener = new Listener() {
			@Override
			public void handleEvent(Event event) {
				if (currentPage != null) {
					PhotosOrder order = null;
					if (event.widget == btnSortByDate) {
						if (btnSortByDate.getSelection()) {
							order = PhotosOrder.DATE;
						}
					} else if (event.widget == btnSortByName) {
						if (btnSortByName.getSelection()) {
							order = PhotosOrder.NAME;
						}
					} else if (event.widget == btnSortManual) {
						if (btnSortManual.getSelection()) {
							order = PhotosOrder.MANUAL;
						}
					} else {
						throw new IllegalStateException();
					}

					// order is null for the deselection event
					if (order != null && order != currentPage.getPhotosOrder()) {
						currentPage.setPhotosOrder(order);
						thumbViewer.refresh(); // requests sorted input
					}
				}
			}
		};
		btnSortManual.addListener(SWT.Selection, btnSortSelectionListener);
		btnSortByDate.addListener(SWT.Selection, btnSortSelectionListener);
		btnSortByName.addListener(SWT.Selection, btnSortSelectionListener);
	}

	private void bindModelToUI(Page pageToShow) {

		// unset current page first, otherwise "setText" calls will
		// dirty the model
		currentPage = null;

		LOG.debug("Binding UI to page [{}]", pageToShow);

		if (pageToShow == null) {
			// clear controls and disable...
			btnBold.setSelection(false);
			btnItalic.setSelection(false);
			titleInput.setText("");
			headlineInput.setText("");
			distanceInput.setText("");
			textInput.setDocument(null);
			captionText.setDocument(null);
			selectOrderByButton((Button) null);
		} else {
			btnBold.setSelection(pageToShow.isBold());
			btnItalic.setSelection(pageToShow.isItalic());
			cmbIndent.select(cmbIndent.indexOf(Integer.toString(pageToShow.getIndent())));
			cmbHeadingStyle
					.select(cmbHeadingStyle.indexOf(StringUtils.capitalise(pageToShow.getHeadingStyle().name())));
			cmbFormat.select(cmbFormat.indexOf(StringUtils.capitalise(pageToShow.getFormat().name())));
			String title = nullToEmpty(pageToShow.getTitle());
			titleInput.setText(title);
			titleInput.setSelection(0, title.length() + 1);
			headlineInput.setText(pageToShow == null ? "" : StringUtils.nullToEmpty(pageToShow.getHeadline()));
			LocalDate date = pageToShow.getDate();
			dateInput.setDate(date.getYear(), date.getMonthOfYear() - 1, date.getDayOfMonth());

			// TODO stick a limit on caching, close when no longer used.
			IDocument document = pageToShow.getOrCreateTextDocument();
			DocumentUndoManagerRegistry.connect(document);
			IDocumentUndoManager m = DocumentUndoManagerRegistry.getDocumentUndoManager(document);
			m.connect(this);
			textInput.setDocument(document);
			captionText.setDocument(null); // no photo is selected by default
			selectOrderByButton(orderByButtonMap.get(pageToShow.getPhotosOrder()));
		}

		// update global after update to avoid init "dirtying" page
		currentPage = pageToShow;

		if (pageToShow == null) {
			thumbViewer.setInput(null);
		} else {
			thumbViewer.setInput(pageToShow);
		}
		statusListener.updateStatusBar(); // HACK to avoid leaving old page
											// photo count in status
	}

	/**
	 * Binds changes control to the property on the "current page".
	 * 
	 * @param c
	 * @param property
	 */
	private void bindToCurrentPage(Control c, int callback, String property, boolean refreshCurrentPage) {
		Method method;
		try {
			String name = "set" + property.substring(0, 1).toUpperCase() + property.substring(1);
			method = Utils.getFirstMethodWithName(name, Page.class);
			if (method == null) {
				throw new IllegalArgumentException("No method " + name);
			}
			if (method.getParameterTypes().length != 1) {
				throw new IllegalArgumentException("Setter must accept 1 argument");
			}

			c.addListener(callback, new MyBinder(c, method, refreshCurrentPage) {
				@Override
				protected Object getTarget() {
					return currentPage;
				}
			});
		} catch (SecurityException e) {
			e.printStackTrace();
		}
	}

	private void createActions(Shell shell) {
		uploadAction = new UploadAction(shell, this);
		uploadAction.setThumbnailFactory(thumbnailProviderFactory);
		uploadAction.setResizerFactory(resizerServiceFactory);
		uploadAction.setPreferences(preferences);
	}

	private void createControls(final Shell shell) {
		GridLayout layout = new GridLayout(1, false);
		shell.setLayout(layout);
		SashForm sash = new SashForm(shell, SWT.HORIZONTAL);
		sash.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		createTreeViewer(sash);
		createEditorUI(sash);
		sash.setWeights(new int[] { 1, 3 });

		createThumbnailViewer(shell);

		// status & progress bar
		statusBar = new Label(shell, SWT.NONE);
		statusBar.setText("");
		statusBar.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));
	}

	private void createEditorUI(Composite parent) {
		Composite editorComposite = new Composite(parent, SWT.BORDER);
		editorComposite.setLayout(new GridLayout(4, false));
		GridData data = new GridData();
		data.horizontalAlignment = SWT.FILL;
		data.verticalAlignment = SWT.FILL;
		data.grabExcessHorizontalSpace = true;
		data.grabExcessVerticalSpace = true;
		editorComposite.setLayoutData(data);

		// style
		Label styleLabel = new Label(editorComposite, SWT.NONE);
		styleLabel.setText("Style:");
		Composite styleComposite = new Composite(editorComposite, SWT.NONE);
		styleComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
		RowLayout styleLayout = new RowLayout();
		styleLayout.center = true;
		styleLayout.spacing = 6;
		styleLayout.wrap = false;
		styleComposite.setLayout(styleLayout);
		new Label(styleComposite, SWT.NONE).setText("italic");
		btnItalic = new Button(styleComposite, SWT.CHECK);
		btnItalic.addSelectionListener(dirtyCurrentPageListener);
		// btnItalic.setText("italic");
		new Label(styleComposite, SWT.NONE).setText("bold");
		btnBold = new Button(styleComposite, SWT.CHECK);
		btnBold.addSelectionListener(dirtyCurrentPageListener);
		// btnBold.setText("bold");
		new Label(styleComposite, SWT.NONE).setText("indent");
		cmbIndent = new Combo(styleComposite, SWT.SINGLE | SWT.V_SCROLL | SWT.READ_ONLY | SWT.BORDER);
		for (int i = Page.INDENT_MIN; i <= Page.INDENT_MAX; ++i) {
			cmbIndent.add(Integer.toString(i));
		}
		cmbIndent.select(0);
		cmbIndent.addModifyListener(dirtyCurrentPageListener);

		new Label(styleComposite, SWT.NONE).setText("heading");
		cmbHeadingStyle = new Combo(styleComposite, SWT.SINGLE | SWT.V_SCROLL | SWT.READ_ONLY | SWT.BORDER);
		cmbHeadingStyle.add(StringUtils.capitalise(HeadingStyle.SMALL.name()));
		cmbHeadingStyle.add(StringUtils.capitalise(HeadingStyle.MEDIUM.name()));
		cmbHeadingStyle.add(StringUtils.capitalise(HeadingStyle.LARGE.name()));
		cmbHeadingStyle.select(0);
		cmbHeadingStyle.addModifyListener(dirtyCurrentPageListener);

		new Label(styleComposite, SWT.NONE).setText("format");
		cmbFormat = new Combo(styleComposite, SWT.SINGLE | SWT.V_SCROLL | SWT.READ_ONLY | SWT.BORDER);
		cmbFormat.add(StringUtils.capitalise(EditFormat.AUTO.name()));
		cmbFormat.add(StringUtils.capitalise(EditFormat.LIST.name()));
		cmbFormat.add(StringUtils.capitalise(EditFormat.MANUAL.name()));
		cmbFormat.select(0);
		cmbFormat.addModifyListener(dirtyCurrentPageListener);

		// title
		Label titleLabel = new Label(editorComposite, SWT.NONE);
		titleLabel.setText("Title:");
		titleInput = new Text(editorComposite, SWT.SINGLE | SWT.BORDER);
		titleLabel.addFocusListener(selectCurrentPageListener);
		titleInput.setTextLimit(128);
		titleInput.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		titleInput.addModifyListener(dirtyCurrentPageListener);

		// headline
		Label headlineLabel = new Label(editorComposite, SWT.NONE);
		headlineLabel.setText("Headline:");

		headlineInput = new Text(editorComposite, SWT.SINGLE | SWT.BORDER);
		headlineInput.addFocusListener(selectCurrentPageListener);
		headlineInput.setTextLimit(128);
		headlineInput.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		headlineInput.addModifyListener(dirtyCurrentPageListener);

		// date
		Label dateLabel = new Label(editorComposite, SWT.NONE);
		dateLabel.setText("Date:");

		dateInput = new DateTime(editorComposite, SWT.DATE | SWT.DROP_DOWN);
		dateInput.addFocusListener(selectCurrentPageListener);
		dateInput.addSelectionListener(dirtyCurrentPageListener);
		dateInput.setLayoutData(new GridData(SWT.FILL, SWT.LEFT, true, false));

		// distance
		Label distanceLabel = new Label(editorComposite, SWT.NONE);
		distanceLabel.setText("Distance:");
		distanceInput = new Text(editorComposite, SWT.SINGLE | SWT.BORDER);
		distanceLabel.addFocusListener(selectCurrentPageListener);
		distanceInput.setTextLimit(10);
		distanceInput.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		distanceInput.addModifyListener(dirtyCurrentPageListener);
		distanceInput.addVerifyListener(new VerifyListener() {
			@Override
			public void verifyText(VerifyEvent e) {
				String newTxt = distanceInput.getText().substring(0, e.start) + e.text
						+ distanceInput.getText().substring(e.start);
				try {
					Float.parseFloat(newTxt);
				} catch (Exception ex) {
					e.doit = false;
				}
			}
		});

		textInput = new TextViewer(editorComposite, SWT.WRAP | SWT.BORDER | SWT.V_SCROLL);
		undoManager = new TextViewerUndoManager(50);
		undoManager.connect(textInput);

		FontData fd = new FontData("Tahoma", 10, SWT.NONE);
		textInput.getTextWidget().setFont(new Font(shell.getDisplay(), fd));
		data = new GridData(SWT.FILL, SWT.FILL, true, true, 4, 1);
		// 8 lines of text
		data.heightHint = textInput.getTextWidget().getLineHeight() * 8;
		GC tempGC = new GC(textInput.getTextWidget());
		int averageCharWidth = tempGC.getFontMetrics().getAverageCharWidth();
		tempGC.dispose();
		data.widthHint = averageCharWidth * 120; // 120 characters
		textInput.getTextWidget().setLayoutData(data);
		textInput.getTextWidget().addFocusListener(selectCurrentPageListener);
		textInput.addTextListener(dirtyCurrentPageListener);

		// configure text editor DND
		// textInput.setDragDetect(true);
		// DropTarget dt = new DropTarget(textInput, DND.DROP_MOVE);
		// dt.setDropTargetEffect(new StyledTextDropTargetEffect(textInput));
		// dt.addDropListener(new DropTargetAdapter() {
		// @Override
		// public void drop(DropTargetEvent e) {
		// Point pt = shell.getDisplay().map(null, textInput, e.x, e.y);
		// // pt = textInput.toControl(pt);
		// int[] trailing = new int[1];
		// int offset = StyledTextUtil.getOffsetAtPoint(textInput, pt.x, pt.y,
		// trailing, false);
		// // TODO hack?!!!
		// String imgTag = "###" +
		// LocalThumbnailTransfer.getInstance().getSelectedPhoto().getFile().getName();
		// int lineAtOffset = textInput.getLineAtOffset(offset);
		// System.out.println();
		// if (!StringUtils.isEmpty(textInput.getLine(lineAtOffset))) {
		// imgTag = Text.DELIMITER + imgTag + Text.DELIMITER;
		// }
		// if (offset == textInput.getCharCount()) {
		// textInput.append(imgTag);
		// } else {
		// textInput.replaceTextRange(offset, 0, imgTag);
		// }
		// }
		// });
		// dt.setTransfer(new Transfer[] { LocalThumbnailTransfer.getInstance()
		// });
	}

	private void createMenu(final Shell shell) {
		Menu menuBar = new Menu(shell, SWT.BAR);
		MenuItem fileMenuHeader = new MenuItem(menuBar, SWT.CASCADE);
		fileMenuHeader.setText("&File");
		Menu fileMenu = new Menu(shell, SWT.DROP_DOWN);
		fileMenuHeader.setMenu(fileMenu);

		MenuItem newJournalItem = new MenuItem(fileMenu, SWT.PUSH);
		newJournalItem.setText("&New Journal\tCtrl+Shift+N");
		newJournalItem.setAccelerator(SWT.CTRL + SWT.SHIFT + 'N');
		newJournalItem.addSelectionListener(new ActionRunner(newJournalAction));

		MenuItem newPageItem = new MenuItem(fileMenu, SWT.PUSH);
		newPageItem.setText("&New Page\tCtrl+N");
		newPageItem.setAccelerator(SWT.CTRL + 'N');
		newPageItem.addSelectionListener(new ActionRunner(newPageAction));

		new MenuItem(fileMenu, SWT.SEPARATOR);

		MenuItem fileOpenItem = new MenuItem(fileMenu, SWT.PUSH);
		fileOpenItem.setText("&Open\tCtrl+O");
		fileOpenItem.setAccelerator(SWT.CTRL + 'O');
		fileOpenItem.addSelectionListener(new ActionRunner(openJournalAction));

		new MenuItem(fileMenu, SWT.SEPARATOR);

		MenuItem fileSaveItem = new MenuItem(fileMenu, SWT.PUSH);
		fileSaveItem.setText("&Save\tCtrl+S");
		fileSaveItem.setAccelerator(SWT.CTRL + 'S');
		fileSaveItem.addSelectionListener(new ActionRunner(saveAction));

		new MenuItem(fileMenu, SWT.SEPARATOR);

		MenuItem openPreferences = new MenuItem(fileMenu, SWT.PUSH);
		openPreferences.setText("&Preferences");
		openPreferences.addSelectionListener(new ActionRunner(openPreferencesAction));

		new MenuItem(fileMenu, SWT.SEPARATOR);

		MenuItem fileExitItem = new MenuItem(fileMenu, SWT.PUSH);
		fileExitItem.setText("E&xit");
		fileExitItem.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				shell.close();
			}
		});

		MenuItem editMenuHeader = new MenuItem(menuBar, SWT.CASCADE);
		editMenuHeader.setText("&Edit");
		Menu editMenu = new Menu(shell, SWT.DROP_DOWN);
		editMenuHeader.setMenu(editMenu);

		final MenuItem undo = new MenuItem(editMenu, SWT.DROP_DOWN);
		undo.setText("Undo\tCtrl+Z");
		undo.setAccelerator(SWT.CTRL + 'Z');
		undo.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				undoManager.undo();
			}
		});

		final MenuItem redo = new MenuItem(editMenu, SWT.DROP_DOWN);
		redo.setText("Redo\tCtrl+Y");
		redo.setAccelerator(SWT.CTRL + 'Y');
		redo.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				undoManager.redo();
			}
		});

		editMenu.addMenuListener(new MenuListener() {

			@Override
			public void menuHidden(MenuEvent e) {
			}

			@Override
			public void menuShown(MenuEvent e) {
				undo.setEnabled(undoManager.undoable());
				redo.setEnabled(undoManager.redoable());
			}
		});

		shell.setMenuBar(menuBar);
	}

	private void createThumbnailViewer(Shell shell) {
		thumbViewer = new ThumbnailViewer(shell);
		// thumbViewer.setCache(thumbnailCache);
		thumbViewer.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));
		thumbViewer.setContentProvider(new PhotosContentProvider(this));
		thumbViewer.setLabelProvider(new PhotosLabelProvider());
		thumbViewer.addFilter(new ViewerFilter() {
			@Override
			public boolean select(Viewer viewer, Object parentElement, Object element) {
				if (isHideUploadedPhotosAndPages()) {
					Photo p = (Photo) element;
					return p.getState() != UploadState.UPLOADED;
				}
				return true;
			}
		});
		thumbViewer.addFocusListener(selectCurrentPageListener);
		statusListener = new StatusUpdater();
		thumbViewer.addSelectionListener(statusListener);
		thumbViewer.addTraverseListener(new TraverseListener() {
			@Override
			public void keyTraversed(TraverseEvent e) {
				switch (e.detail) {
				/* Do tab group traversal */
				case SWT.TRAVERSE_ESCAPE:
				case SWT.TRAVERSE_RETURN:
				case SWT.TRAVERSE_TAB_NEXT:
				case SWT.TRAVERSE_TAB_PREVIOUS:
				case SWT.TRAVERSE_PAGE_NEXT:
				case SWT.TRAVERSE_PAGE_PREVIOUS:
					e.doit = true;
					break;
				}
			}
		});
		// thumbnails.addErrorListener();

		Composite captionComposite = new Composite(shell, SWT.NONE);
		captionComposite.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));
		captionComposite.setLayout(new GridLayout(4, false));

		Label captionLabel = new Label(captionComposite, SWT.NONE);
		captionLabel.setText("Caption: ");
		captionLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
		captionText = new TextViewer(captionComposite, SWT.MULTI | SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
		// captionText.setSize(SWT.DEFAULT, 50);
		captionText.getControl().setEnabled(false);
		FontData fd = new FontData("Tahoma", 10, SWT.NONE);
		captionText.getControl().setFont(new Font(shell.getDisplay(), fd));
		GridData gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
		// GC tempGC = new GC(captionText.getTextWidget());
		// int averageCharWidth = tempGC.getFontMetrics().getAverageCharWidth();
		// tempGC.dispose();
		gridData.minimumHeight = captionText.getTextWidget().getLineHeight();
		captionText.getControl().setLayoutData(gridData);
		captionText.addTextListener(dirtyCurrentPageListener);
		captionText.getControl().addFocusListener(selectCurrentPageListener);

		// force space
		gridData = new GridData();
		gridData.widthHint = 6;
		new Label(captionComposite, SWT.NONE).setLayoutData(gridData);

		Group sortGroup = new Group(captionComposite, SWT.RADIO);
		sortGroup.setText("sort by");
		RowLayout layout2 = new RowLayout();
		layout2.marginHeight = 3;
		layout2.marginWidth = 5;
		layout2.spacing = 5;
		layout2.pack = true;
		sortGroup.setLayout(layout2);
		btnSortByDate = new Button(sortGroup, SWT.RADIO);
		btnSortByDate.setText("date");
		btnSortByName = new Button(sortGroup, SWT.RADIO);
		btnSortByName.setText("name");
		btnSortManual = new Button(sortGroup, SWT.RADIO);
		btnSortManual.setText("manual");
		sortGroup.setLayoutData(new GridData(SWT.RIGHT, SWT.TOP, false, false));
		orderByButtonMap.put(PhotosOrder.DATE, btnSortByDate);
		orderByButtonMap.put(PhotosOrder.NAME, btnSortByName);
		orderByButtonMap.put(PhotosOrder.MANUAL, btnSortManual);
	}

	private void createTreeViewer(Composite parent) {
		Composite treeComposite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.marginHeight = 0;
		layout.verticalSpacing = 0;
		treeComposite.setLayout(layout);
		GridData data = new GridData(SWT.FILL, SWT.FILL, true, true);
		treeComposite.setLayoutData(data);

		treeViewer = new TreeViewer(treeComposite);
		data = new GridData(SWT.FILL, SWT.FILL, true, true);
		treeViewer.getTree().setLayoutData(data);
		Group filterGroup = new Group(treeComposite, SWT.SHADOW_IN);
		filterGroup.setLayout(new FillLayout());
		btnHideUploaded = new Button(filterGroup, SWT.CHECK | SWT.WRAP);
		btnHideUploaded.setText("hide uploaded photos && pages");
		btnHideUploaded.setSelection(true);
		btnHideUploaded.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				treeViewer.refresh();
				thumbViewer.refresh();
			}
		});
		filterGroup.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

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
		treeViewer.setColumnProperties(new String[] {});
		treeViewer.setCellEditors(editors);

		treeViewer.addFilter(new ViewerFilter() {
			@Override
			public boolean select(Viewer viewer, Object parentElement, Object element) {
				if (element instanceof Page) {
					// filters uploaded pages
					if (isHideUploadedPhotosAndPages()) {
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

		treeViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			@Override
			public void selectionChanged(SelectionChangedEvent event) {
				Page oldPage = currentPage;

				if (treeViewer.getSelection() instanceof IStructuredSelection) {
					IStructuredSelection s = (IStructuredSelection) treeViewer.getSelection();
					if (s.size() == 0) {
						// no-op
					} else if (s.size() == 1) {
						Object e = s.getFirstElement();
						if (e instanceof Page) {
							displayPage((Page) e);
						} else {
							displayPage(null);
						}
					} else {
						// multiple pages
						displayPage(null);
					}
				}

				// force update of labels
				if (oldPage != null) {
					treeViewer.update(oldPage, null);
				}
			}
		});

		treeViewer.getTree().addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.keyCode == SWT.DEL) {
					deletePageAction.run();
				}
			}
		});

		treeViewer.setColumnProperties(new String[] { "name" });
		treeViewer.setContentProvider(new JournalContentProvider());
		treeViewer.setLabelProvider(new JournalTreeLabelProvider(treeViewer, shell));
		final OpenPageInBrowserAction openPageInBrowserAction = new OpenPageInBrowserAction(treeViewer, shell);
		MenuManager menuMgr = new MenuManager();
		menuMgr.addMenuListener(new IMenuListener() {

			@Override
			public void menuAboutToShow(IMenuManager manager) {
				// add static menu items

				manager.add(openJournalAction);
				manager.add(newJournalAction);
				manager.add(newPageAction);
				manager.add(new Separator());
				manager.add(toggleResizePhotos);
				manager.add(openResizedPhotosFolder);
				manager.add(purgeResizedPhotos);
				manager.add(new Separator());
				manager.add(toggleUseExifThumbnailAction);
				manager.add(purgeThumbnailCache);
				manager.add(new Separator());

				if (treeViewer.getSelection().isEmpty()) {
					return;
				}
				if (treeViewer.getSelection() instanceof IStructuredSelection) {
					IStructuredSelection i = (IStructuredSelection) treeViewer.getSelection();
					final Object firstElement = i.getFirstElement();
					if (firstElement instanceof Journal) {
						manager.add(saveAction);
						manager.add(closeJournalAction);
						manager.add(renameJournalAction);
						manager.add(uploadAction);
					}
					if (firstElement instanceof Page) {
						manager.add(deletePageAction);
						manager.add(addPhotosAction);
						manager.add(openPageInBrowserAction);
					}
				}
			}
		});
		menuMgr.setRemoveAllWhenShown(true);
		treeViewer.getControl().setMenu(menuMgr.createContextMenu(treeViewer.getControl()));
	}

	private void displayPage(Page pageToShow) {
		if (pageToShow == currentPage) {
			// nothing to do
			return;
		}

		bindModelToUI(pageToShow);

		if (pageToShow == null) {
			setEditorControlsState(EditorState.DISABLED);
		} else if (pageToShow.getState() == UploadState.UPLOADED) {
			setEditorControlsState(EditorState.READONLY);
		} else if (pageToShow.getState() == UploadState.PARTIALLY_UPLOAD) {
			setEditorControlsState(EditorState.READONLY);
			thumbViewer.setEditable(true); // allow photos to be
											// removed/added...
		} else { // NEW or ERROR
			setEditorControlsState(EditorState.EDITABLE);
		}

		// caption text is enabled/disabled when thumbnails are selected,
		// default to off
		// captionText.getControl().setEnabled(false);
	}

	/**
	 * Saves the journal to disk, if <tt>silent</tt> failures will be
	 * suppressed.
	 * 
	 * @param journal
	 * @param silent
	 *            suppress warning dialog if file cannot be saved
	 * @return true if the file was saved, false if not.
	 */
	boolean saveJournal(Journal journal, boolean silent) {
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

	private FileDialog getOrCreateFileDialog() {
		if (fileDialog == null) {
			fileDialog = new FileDialog(shell, SWT.OPEN | SWT.MULTI);
		}
		return fileDialog;
	}

	public Object getSelectedJournalOrPage() {
		return ((IStructuredSelection) treeViewer.getSelection()).getFirstElement();
	}

	public Shell getShell() {
		return shell;
	}

	PhotosOrder getSortOrderFromButton(Button b) {
		for (Entry<PhotosOrder, Button> e : orderByButtonMap.entrySet()) {
			if (e.getValue() == b) {
				return e.getKey();
			}
		}
		throw new IllegalStateException();
	}

	public boolean isHideUploadedPhotosAndPages() {
		return btnHideUploaded.getSelection();
	}

	/**
	 * Closes the currently open journal, takes care of prompting for save and
	 * removing any listeners to the journal.
	 * 
	 * @return true if the close succeeded, false if it was cancelled.
	 */
	private boolean closeCurrentJournal() {
		JournalHolder holder = (JournalHolder) treeViewer.getInput();
		if (holder == null) {
			return true;
		}
		Journal journal = holder.getJournal();
		if (journal.isDirty()) {
			if (!promptAndSaveJournal(journal)) {
				return false;
			}
		}

		treeViewer.setInput(null);

		// unregister thumbnail factory
		CachingThumbnailProvider thumbnailProvider = thumbnailProviderFactory.getThumbnailProvider(journal);
		if (thumbnailProvider != null) {
			thumbnailProvider.removeJobListener(statusListener.thumnailListener);
			thumbnailProvider.close();
		}

		// wait for completion of photo resize tasks...

		if (blockUntilPhotosResized(journal)) {
			// cancel outstanding resize tasks on cancel
			resizerServiceFactory.getResizerFor(journal).cancelAll();
		}

		unregisterPhotoResizer(journal);
		return true;
	}

	/**
	 * Opens a progress box to wait for completion of any pending resize tasks.
	 * 
	 * @param journal
	 * @return true if the wait was cancelled
	 */
	boolean blockUntilPhotosResized(Journal journal) {
		ResizerService resizer = resizerServiceFactory.getResizerFor(journal);
		if (resizer != null && resizer.activeTasks() > 0) {
			// show progress monitor wait for completion
			ProgressMonitorDialog progress = new ProgressMonitorDialog(shell);
			RezierWaitTask waiter = new RezierWaitTask(resizer, shell.getDisplay(), journal);
			try {
				progress.run(false, true, waiter);
			} catch (Exception e) {
				/* ignore */
			}
			return waiter.isCancelled();
		}
		return false;
	}

	private boolean openJournal(String path, boolean quiet) {
		LOG.debug("Loading journal from [{}]", path);
		File file = new File(path);
		if (!file.exists()) {
			LOG.debug("File [{}] does not exist!", path);
			if (!quiet) {
				showError("File '" + path + "' does not exist!");
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

		// finally load journal into UI
		bindJournal(journal);
		return true;
	}

	private void bindJournal(Journal journal) {
		// wire up image resizer service
		if (journal.isResizeImagesBeforeUpload() == Boolean.TRUE) {
			registerPhotoResizer(journal, true);
		}
		CachingThumbnailProvider provider = thumbnailProviderFactory.getOrCreateThumbnailProvider(journal);
		toggleUseExifThumbnailAction.setChecked(provider.isUseExifThumbnail());
		toggleResizePhotos.setChecked(journal.isResizeImagesBeforeUpload() == Boolean.TRUE);
		provider.addJobListener(statusListener.thumnailListener);
		thumbViewer.setThumbnailProvider(provider);

		treeViewer.setInput(new JournalHolder(journal));

		// s the last page non uploaded page, or the journal
		List<Page> pages = journal.getPages();
		Object newSelection = journal;
		if (pages.size() > 0) {
			Page lastPage = pages.get(pages.size() - 1);
			if (lastPage.getState() != UploadState.UPLOADED) {
				newSelection = lastPage;
			}
		}

		treeViewer.setSelection(new StructuredSelection(newSelection));
	}

	private boolean onClose() {
		JournalHolder holder = (JournalHolder) treeViewer.getInput();
		if (holder != null) {
			Journal journal = holder.getJournal();
			preferences.setValue(OPENJOURNALS_PREFERENCE_PATH, journal.getFile().getAbsolutePath());
			preferences.save();
			if (!closeCurrentJournal()) {
				return false;
			}
		}
		return true;
	}

	public void open() {
		registerCloseListener(shell);
		createControls(shell);
		bindControls();
		createActions(shell);
		createMenu(shell);

		/*
		 * Slurp up references to all the editor widgets so we can turn on/off
		 * together
		 */
		pageEditorWidgets = Arrays.asList(btnItalic, btnBold, cmbFormat, cmbIndent, cmbHeadingStyle, titleInput,
				headlineInput, distanceInput, dateInput, textInput.getTextWidget(), thumbViewer, btnSortByDate,
				btnSortByName, btnSortManual);

		setEditorControlsState(EditorState.DISABLED);

		shell.pack();
		shell.open();

		// TODO load on background thread?
		String journalToOpen = preferences.getValue(OPENJOURNALS_PREFERENCE_PATH);
		if (journalToOpen != null && !openJournal(journalToOpen, true)) {
			// update preferences if last journal failed to load
			preferences.removeValue(OPENJOURNALS_PREFERENCE_PATH);
			preferences.save();
		}

		while (!shell.isDisposed()) {
			if (!shell.getDisplay().readAndDispatch()) {
				shell.getDisplay().sleep();
			}
		}
	}

	/**
	 * Prompts the user to save the given journal.
	 * 
	 * @param journal
	 *            journal to save
	 * @return <tt>false</tt> if the operation was cancelled.
	 */
	private boolean promptAndSaveJournal(Journal journal) {
		MessageBox box = new MessageBox(shell, SWT.ICON_WARNING | SWT.CANCEL | SWT.YES | SWT.NO);
		box.setText("Confirm save");
		box.setMessage("Save changes to [" + journal.getName() + "] before closing?");
		switch (box.open()) {
		case SWT.CANCEL:
			return false;
		case SWT.YES:
			saveJournal(journal, false);
			break;
		case SWT.NO:
			// changes will be lost
		}
		return true;
	}

	private void registerCloseListener(Shell shell) {
		shell.addListener(SWT.Close, new Listener() {
			@Override
			public void handleEvent(Event event) {
				if (!onClose()) {
					event.doit = false;
				}
			}
		});
	}

	void unregisterPhotoResizer(Journal journal) {
		ResizerService resizer = resizerServiceFactory.getResizerFor(journal);
		if (resizer == null) {
			return;
		}
		resizer.removeJobListener(statusListener.resizeListener);
		journal.removeListener(resizeListener);
		resizeListener = null;
	}

	// TODO stash in a generica journal data map?
	private JournalAdapter resizeListener;

	/**
	 * Registers a listener to resize photos when they are added journal.
	 * 
	 * @param journal
	 * @param b
	 */
	boolean registerPhotoResizer(Journal journal, boolean quiet) {
		final ResizerService imageService;
		try {
			imageService = resizerServiceFactory.getOrCreateResizerFor(journal);
		} catch (MagicNotAvailableException e) {
			LOG.info("Failed to start photo reszier", e);
			if (!quiet) {
				MessageBox error = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
				error.setText("Error");
				error.setMessage("Failed to start photo resizer:\n\n" + e.getMessage());
				error.open();
			}
			return false;
		}

		// 1) update status bar when resizes occur
		imageService.addJobListener(statusListener.resizeListener);

		// 2) resize all photos already in the journal
		for (Page page : journal.getPages()) {
			// skip uploaded pages
			UploadState pageState = page.getState();
			if (pageState == UploadState.UPLOADED) {
				continue;
			}

			List<Photo> photos = new ArrayList<Photo>(page.getPhotos());
			if (pageState != UploadState.NEW) {
				// filter out uploaded photos
				for (Iterator<Photo> i = photos.iterator(); i.hasNext();) {
					if (i.next().getState() == UploadState.UPLOADED) {
						i.remove();
					}
				}
			}
			imageService.resizeAll(photos);
		}

		// 3) listen for addition & removal of photos from journal
		Assert.isNull(resizeListener);
		resizeListener = new JournalAdapter() {
			@Override
			public void photosAdded(List<Photo> photos, Page page) {
				imageService.resizeAll(photos);
			}

			public void photosRemoved(List<Photo> photos, Page page) {
				imageService.removeAll(photos); // TODO remove from cache?
			}
		};
		journal.addListener(resizeListener);
		return true;
	}

	/**
	 * Runs a bit of code (usually a UI updates) with the current page set to
	 * null, thus disabling any binding updates from occuring.
	 * 
	 * @param work
	 */
	void runOutsideOfBinding(Runnable work) {
		Page page = currentPage;
		try {
			currentPage = null;
			work.run();
		} finally {
			currentPage = page;
		}
	}

	void selectOrderByButton(Button orderButton) {
		for (Button btn : orderByButtonMap.values()) {
			btn.setSelection(btn == orderButton);
		}

		// fake a "select" event
		Event fakeEvent = new Event();
		fakeEvent.widget = orderButton;
		btnSortSelectionListener.handleEvent(fakeEvent);
	}

	// hack to avoid SWT not unselecting radio buttons when done programatically
	void selectOrderByButton(PhotosOrder order) {
		selectOrderByButton(orderByButtonMap.get(order));
	}

	private void setEditableState(Control control, boolean editable) {
		if (control instanceof StyledText) {
			((StyledText) control).setEditable(editable);
		} else if (control instanceof Text) {
			((Text) control).setEditable(editable);
		} else if (control instanceof ThumbnailViewer) {
			((ThumbnailViewer) control).setEditable(editable);
		} else {
			// default??
			control.setEnabled(editable);
		}
	}

	private void setEditorControlsState(EditorState state) {
		for (Control control : pageEditorWidgets) {
			if (state == EditorState.READONLY) {
				control.setEnabled(true);
				// run this 2nd as only option may be to disable to get readonly
				setEditableState(control, false);
			} else if (state == EditorState.DISABLED) {
				control.setEnabled(false);
			} else if (state == EditorState.EDITABLE) {
				control.setEnabled(true);
				setEditableState(control, true);
			} else {
				throw new IllegalArgumentException();
			}
		}
	}

	public void setPreferences(Preferences preferences) {
		this.preferences = preferences;
	}

	public void setResizerServiceFactory(ImageMagickResizerServiceFactory resizerServiceFactory) {
		this.resizerServiceFactory = resizerServiceFactory;
	}

	private void showError(String message) {
		MessageBox box = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
		box.setMessage(message);
		box.open();
	}

	private class DirtyCurrentPageListener implements ModifyListener, SelectionListener, ITextListener {

		@Override
		public void modifyText(ModifyEvent e) {
			setDirty();
		}

		public void setDirty() {
			if (currentPage != null) {
				Journal journal = currentPage.getJournal();
				if (!journal.isDirty()) {
					journal.setDirty(true);
				}
			}
		}

		@Override
		public void textChanged(TextEvent event) {
			setDirty();
		}

		@Override
		public void widgetDefaultSelected(SelectionEvent e) {
			setDirty();
		}

		@Override
		public void widgetSelected(SelectionEvent e) {
			setDirty();
		}
	}

	private enum EditorState {
		DISABLED, EDITABLE, READONLY;
	}

	private abstract class MyBinder implements Listener {

		Control c;
		Method property;
		private boolean updateCurrentPage;

		public MyBinder(Control c, Method m, boolean updateCurrentPage) {
			this.c = c;
			this.property = m;
			this.updateCurrentPage = updateCurrentPage;
		}

		private Object convert(String value, Class<?> target) {
			if (target == String.class) {
				return value;
			}

			if (target.isEnum()) {
				return Enum.valueOf((Class<? extends Enum>) target, value.toUpperCase());
			} else if (target.isPrimitive()) {
				boolean empty = value == null || "".equals(value.trim());
				if (target == int.class) {
					return empty ? 0 : Integer.valueOf(value);
				}
				if (target == float.class) {
					return empty ? 0 : Float.valueOf(value);
				}
				if (target == long.class) {
					return empty ? 0 : Long.valueOf(value);
				}
				if (target == double.class) {
					return empty ? 0 : Double.valueOf(value);
				}
			}
			throw new IllegalArgumentException("Cannot convert to " + target);
		}

		protected abstract Object getTarget();

		@Override
		public void handleEvent(Event event) {
			Object target = getTarget();
			if (target == null) {
				return;
			}
			// copy value from control to model
			try {
				if (c instanceof Text) {
					String value = ((Text) c).getText();
					property.invoke(target, convert(value, property.getParameterTypes()[0]));
				} else if (c instanceof Button) {
					property.invoke(target, ((Button) c).getSelection());
				} else if (c instanceof Combo) {
					property.invoke(target, convert(((Combo) c).getText(), property.getParameterTypes()[0]));
				} else if (c instanceof DateTime) {
					DateTime dt = (DateTime) c;
					property.invoke(target, new LocalDate(dt.getYear(), dt.getMonth() + 1, dt.getDay()));
				} else {
					throw new IllegalArgumentException("Cannot bind control " + c.getClass().getName());
				}
			} catch (Exception e1) {
				e1.printStackTrace();
			}

			// hack to reflect changes in label
			if (updateCurrentPage && currentPage != null) {
				treeViewer.update(currentPage, null);
			}
		}
	}

	/**
	 * Updates the status bar when a selection is made in th
	 * 
	 * @author ben
	 */
	private class StatusUpdater implements SelectionListener {

		private int remainingImageJobs, remainingThumbnailJobs;

		private Object[] selected;

		public JobListener resizeListener = new JobListener() {

			@Override
			public void update(int remainingJobs) {
				remainingImageJobs = remainingJobs;
				updateStatusBar();
			}
		};

		public JobListener thumnailListener = new JobListener() {

			@Override
			public void update(int remainingJobs) {
				remainingThumbnailJobs = remainingJobs;
				updateStatusBar();
			}
		};

		// contains the last time the status update actually ran
		// private final AtomicLong lastUpdate = new AtomicLong();

		// private void updateStatus(boolean delayUpdates) {
		// long timeSinceLastUpdate = System.currentTimeMillis() -
		// lastUpdate.get();
		// if (delayUpdates && timeSinceLastUpdate < 200) {
		// // update done in the last 200ms so delay the next update
		// long interval = 200 - timeSinceLastUpdate;
		// shell.getDisplay().timerExec((int) interval, this);
		// } else {
		// shell.getDisplay().timerExec(0, this);
		// }
		// }

		private String getDimensions(Metadata meta) {
			JpegDirectory jpeg = (JpegDirectory) meta.getDirectory(JpegDirectory.class);
			if (jpeg != null) {
				try {
					return jpeg.getImageWidth() + " x " + jpeg.getImageHeight();
				} catch (MetadataException e) {
				}
			}
			return "";
		}

		private void updateStatusBar() {
			// lastUpdate.set(System.currentTimeMillis());
			StringBuffer b = new StringBuffer();
			if (currentPage != null) {
				b.append(currentPage.getPhotos().size());
				b.append(" photos");
				if (selected != null && selected.length > 0) {
					b.append(", (").append(selected.length).append(" selected)");
					if (selected.length == 1) {
						Photo photo = (Photo) selected[0];
						b.append(": ").append(photo.getFile().getAbsolutePath());
						Metadata meta = thumbViewer.getMetaData(photo);
						if (meta != null) {
							b.append(" : ").append(getDimensions(meta));
						}
						b.append(" (");
						b.append(Utils.formatBytes(photo.getFile().length()));
						b.append(") ");
					}
				}
			}

			if (remainingThumbnailJobs > 0) {
				if (b.length() > 0) {
					b.append(" : ");
				}
				b.append(remainingThumbnailJobs).append(" thumbnails left to create");
			}

			if (remainingImageJobs > 0) {
				if (b.length() > 0) {
					b.append(" : ");
				}
				b.append(remainingImageJobs).append(" photos left to resize");
			}

			statusBar.setText(b.toString());
		}

		@Override
		public void widgetDefaultSelected(SelectionEvent e) {
		}

		@Override
		public void widgetSelected(SelectionEvent e) {
			selected = thumbViewer.getSelection();

			// disable current page so we don't dirty the journal
			Page p = currentPage;
			try {
				currentPage = null;
				if (selected.length == 1) {
					// load new comment
					Photo currentPhoto = (Photo) selected[0];

					// allow selection & copy from the caption after upload
					if (currentPhoto.getState() == UploadState.UPLOADED) {
						captionText.setEditable(false);
					} else {
						captionText.setEditable(true);
					}
					captionText.getTextWidget().setEnabled(true);
					captionText.setDocument(currentPhoto.getOrCreateCaptionDocument());
				} else {
					// 0 or > 1
					captionText.setDocument(new Document(""));
					captionText.getTextWidget().setEnabled(false);
				}
			} finally {
				currentPage = p;
			}

			updateStatusBar();
		}
	}

	public void setThumbnailProviderFactory(CachingThumbnailProviderFactory thumbnailFactory) {
		thumbnailProviderFactory = thumbnailFactory;
	}

	TreeViewer getJournalTreeViewer() {
		return treeViewer;
	}
}