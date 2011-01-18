package com.cgoab.offline.ui;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextListener;
import org.eclipse.jface.text.TextEvent;
import org.eclipse.jface.text.TextViewer;
import org.eclipse.jface.text.TextViewerUndoManager;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.events.TraverseEvent;
import org.eclipse.swt.events.TraverseListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cgoab.offline.model.DuplicatePhotoException;
import com.cgoab.offline.model.InvalidInsertionPointException;
import com.cgoab.offline.model.Journal;
import com.cgoab.offline.model.JournalAdapter;
import com.cgoab.offline.model.JournalListener;
import com.cgoab.offline.model.Page;
import com.cgoab.offline.model.Page.PhotosOrder;
import com.cgoab.offline.model.PageNotEditableException;
import com.cgoab.offline.model.Photo;
import com.cgoab.offline.model.UploadState;
import com.cgoab.offline.ui.JournalSelectionService.JournalSelectionListener;
import com.cgoab.offline.ui.thumbnailviewer.CachingThumbnailProvider;
import com.cgoab.offline.ui.thumbnailviewer.CachingThumbnailProviderFactory;
import com.cgoab.offline.ui.thumbnailviewer.ThumbnailProvider;
import com.cgoab.offline.ui.thumbnailviewer.ThumbnailViewer;
import com.cgoab.offline.util.Assert;
import com.cgoab.offline.util.JobListener;
import com.cgoab.offline.util.Utils;
import com.cgoab.offline.util.resizer.ImageMagickResizeTask.MagicNotAvailableException;
import com.cgoab.offline.util.resizer.ImageMagickResizerServiceFactory;
import com.cgoab.offline.util.resizer.ResizerService;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifDirectory;
import com.drew.metadata.jpeg.JpegDirectory;

public class ThumbnailView {
	private static final Collection<String> extensions;
	private static final Logger LOG = LoggerFactory.getLogger(ThumbnailView.class);
	private static final String RESIZE_LISTENER_KEY = "resize_listener_key";
	static {
		extensions = new HashSet<String>();
		extensions.add(".jpg");
		extensions.add(".jpeg");
		extensions.add(".gif");
	}

	private static boolean isValidImage(String name) {
		name = name.toLowerCase();
		for (String e : extensions) {
			if (name.endsWith(e)) {
				return true;
			}
		}
		return false;
	}

	private Button btnSortByName;
	private Button btnSortManual;
	private Listener btnSortSelectionListener;
	private TextViewer captionText;
	private TextViewerUndoManager undoManager = new TextViewerUndoManager(20);

	/**
	 * Cache a few undo stacks maintained, otherwise caption history will be
	 * lost when another image is selected (and new document bound).
	 */
	private DocumentUndoCache undoCache = new DocumentUndoCache(3);

	private Page currentPage;

	Map<PhotosOrder, Button> orderByButtonMap = new HashMap<Page.PhotosOrder, Button>();

	PropertyChangeListener photoOrderListener = new PropertyChangeListener() {
		@Override
		public void propertyChange(PropertyChangeEvent evt) {
			if (Page.PHOTOS_ORDER.equals(evt.getPropertyName())) {
				selectOrderByButton((PhotosOrder) evt.getNewValue());
			}
		}
	};

	private ImageMagickResizerServiceFactory resizerServiceFactory;

	private Shell shell;

	// TODO stash in a generica journal data map?
	private StatusUpdater statusListener;

	private CachingThumbnailProviderFactory thumbnailFactory;

	private ThumbnailViewer thumbViewer;
	private ApplicationWindow application;

	public ThumbnailView(final ApplicationWindow application,
			final ImageMagickResizerServiceFactory resizerServiceFactory,
			CachingThumbnailProviderFactory thumbnailProvider) {
		this.resizerServiceFactory = resizerServiceFactory;
		this.thumbnailFactory = thumbnailProvider;
		this.application = application;
		JournalSelectionService.getInstance().addListener(new JournalSelectionListener() {

			PropertyChangeListener journalPropertyListener = new PropertyChangeListener() {
				@Override
				public void propertyChange(PropertyChangeEvent evt) {
					if (Journal.USE_EXIF_THUMBNAIL.equals(evt.getPropertyName())) {
						Journal journal = (Journal) evt.getSource();
						CachingThumbnailProvider provider = ((CachingThumbnailProvider) journal
								.getData(ThumbnailProvider.KEY));
						provider.setUseExifThumbnail((Boolean) evt.getNewValue());
						thumbViewer.refresh();
					} else if (Journal.HIDE_UPLOADED_CONTENT.equals(evt.getPropertyName())) {
						thumbViewer.refresh();
					}
				}
			};

			@Override
			public void journalClosed(Journal journal) {
				/* make sure history is cleared */
				undoCache.clear();
				CachingThumbnailProvider provider = (CachingThumbnailProvider) journal.getData(ThumbnailProvider.KEY);
				provider.removeJobListener(statusListener.thumnailListener);
				provider.close();

				ResizerService resizer = (ResizerService) journal.getData(ResizerService.KEY);
				if (resizer != null) {
					if (!JournalUtils.blockUntilPhotosResized(resizer, shell)) {
						/* cancel outstanding resize tasks if canceled */
						resizer.cancelAll();
					}
					unregisterResizer(journal);
				}
			}

			@Override
			public void journalOpened(Journal journal) {
				if (journal.isResizeImagesBeforeUpload() == Boolean.TRUE) {
					registerResizer(journal, true);
				} else {
					/* null = ask, false = disabled */
				}

				CachingThumbnailProvider provider = thumbnailFactory.createThumbnailProvider(journal);
				provider.addJobListener(statusListener.thumnailListener);
				thumbViewer.setThumbnailProvider(provider);
				journal.addPropertyChangeListener(journalPropertyListener);
			}

			@Override
			public void selectionChanged(Object newSelection, Object oldSelection) {
				displayPage((Page) (newSelection instanceof Page ? newSelection : null));
			}
		});
	}

	/**
	 * Utility to attempt to add photos to the current page and retry with
	 * duplicates removed if there is an exception.
	 * 
	 * @param files
	 * @param insertionPoint
	 */
	public void addPhotosRetryIfDuplicates(File[] files, int insertionPoint) {
		List<Photo> photos = new ArrayList<Photo>(files.length);
		for (File f : files) {
			if (isValidImage(f.getName())) {
				photos.add(new Photo(f));
			} else {
				/* ignore */
			}
		}

		try {
			currentPage.addPhotos(photos, insertionPoint);
		} catch (InvalidInsertionPointException e) {
			return;
		} catch (PageNotEditableException e) {
			return;
		} catch (DuplicatePhotoException e) {
			StringBuilder b = new StringBuilder("The following photos are already added to this journal:\n\n");
			int i = 0;
			Map<Photo, Page> duplicates = e.getDuplicatePhotos();
			for (Iterator<Entry<Photo, Page>> it = duplicates.entrySet().iterator(); it.hasNext();) {
				Entry<Photo, Page> next = it.next();
				if (i++ == 10) {
					b.append("  ").append("... ").append(duplicates.size() - i).append(" more\n");
					break;
				}
				b.append("  ").append(next.getKey().getFile().getName());
				if (next.getValue() == null) {
					b.append("\n");
				} else if (next.getValue() == currentPage) {
					b.append(" (this page)\n");
				} else {
					b.append(" (page " + next.getValue().getTitle() + ")\n");
				}
			}
			b.append("\nIf you need to attach a duplicate photo, copy to a new file first.");
			int nonDuplicatePhotos = photos.size() - duplicates.size();
			int style = SWT.ICON_WARNING;
			if (nonDuplicatePhotos > 0) {
				b.append("Do you want to continue with the duplicates removed?");
				style |= SWT.YES | SWT.NO;
			} else {
				style |= SWT.OK;
			}
			MessageBox msg = new MessageBox(shell, style);
			msg.setText("Duplicate image(s) detected");
			msg.setMessage(b.toString());
			if (msg.open() != SWT.YES) {
				return;
			}
			photos.removeAll(duplicates.keySet());
			try {
				currentPage.addPhotos(photos, insertionPoint);
			} catch (DuplicatePhotoException e1) {
				return; /* can't happen! */
			} catch (InvalidInsertionPointException e1) {
				return; /* ignore */
			} catch (PageNotEditableException e1) {
				return; /* ignore */
			}
		}

		/* before refresh, ask if EXIF thumbnails should be used */
		Journal journal = currentPage.getJournal();
		if (journal.isUseExifThumbnail() == null) {
			MessageBox box = new MessageBox(shell, SWT.ICON_QUESTION | SWT.YES | SWT.NO);
			box.setText("Use embedded thumnails?");
			box.setMessage("Do you want to use embedded JPEG thumbnails if available?\n\n"
					+ "Embedded thumnbails load quicker but may be of poor quality. If you"
					+ " don't use them a new thumbnail will be created for each photo, "
					+ "providing crisper thumbnails but taking longer to load.");
			/* TODO this will trigger a refresh, supress if slow? */
			journal.setUseExifThumbnail(box.open() == SWT.YES);
		}

		thumbViewer.refresh();
		thumbViewer.setSelection(new StructuredSelection(photos), true);

		Boolean resize = journal.isResizeImagesBeforeUpload();
		if (resize == null) {
			MessageBox box = new MessageBox(shell, SWT.ICON_QUESTION | SWT.YES | SWT.NO | SWT.CANCEL);
			box.setText("Resize photos?");
			box.setMessage("Do you want photos added to this journal to be resized before they are uploaded to reduce upload time?");
			switch (box.open()) {
			case SWT.YES:
				resize = Boolean.TRUE;
				break;
			case SWT.NO:
				resize = Boolean.FALSE;
				break;
			}

			if (resize != null) {
				if (resize == Boolean.TRUE) {
					if (!registerResizer(journal, false)) {
						return;
					}
				}
				journal.setResizeImagesBeforeUpload(resize);
			}
		}
	}

	private void bindControls() {
		btnSortSelectionListener = new Listener() {
			@Override
			public void handleEvent(Event event) {
				if (currentPage != null) {
					PhotosOrder order = null;
					if (event.widget == btnSortByName) {
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
		btnSortByName.addListener(SWT.Selection, btnSortSelectionListener);
	}

	public void bindModelToUI(Page pageToShow) {
		Page oldPage = currentPage;
		currentPage = null;

		if (oldPage != null) {
			oldPage.removePropertyChangeListener(photoOrderListener);
		}

		if (pageToShow == null) {
			/* clear controls and disable... */
			thumbViewer.setInput(null);
			thumbViewer.setEnabled(false);
		} else {
			captionText.setDocument(new Document());
			selectOrderByButton(orderByButtonMap.get(pageToShow.getPhotosOrder()));
			thumbViewer.setInput(pageToShow);
			pageToShow.addPropertyChangeListener(photoOrderListener);
			thumbViewer.setEnabled(true);
			thumbViewer.setEditable(pageToShow.getState() != UploadState.UPLOADED);
		}

		// update global after update to avoid init "dirtying" page
		currentPage = pageToShow;

		/* HACK: removes previous page photo count in status */
		statusListener.updateStatusBar();
	}

	public void createComponents(Composite parent) {
		shell = parent.getShell();
		thumbViewer = new ThumbnailViewer(parent);
		// thumbViewer.setCache(thumbnailCache);
		thumbViewer.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));
		thumbViewer.setContentProvider(new PhotosContentProvider(shell, this));
		thumbViewer.setLabelProvider(new PhotosLabelProvider());
		thumbViewer.addFilter(new ViewerFilter() {
			@Override
			public boolean select(Viewer viewer, Object parentElement, Object element) {
				if (((Page) parentElement).getJournal().isHideUploadedContent()) {
					Photo p = (Photo) element;
					return p.getState() != UploadState.UPLOADED;
				}
				return true;
			}
		});
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

		Composite captionComposite = new Composite(parent, SWT.NONE);
		captionComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		captionComposite.setLayout(new GridLayout(4, false));

		Label captionLabel = new Label(captionComposite, SWT.NONE);
		captionLabel.setText("Caption: ");
		captionLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
		captionText = new TextViewer(captionComposite, SWT.MULTI | SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
		// captionText.setSize(SWT.DEFAULT, 50);
		undoManager.connect(captionText);
		captionText.getControl().setEnabled(false);
		FontData fd = new FontData("Tahoma", 10, SWT.NONE);
		captionText.getControl().setFont(new Font(parent.getDisplay(), fd));
		GridData gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
		gridData.minimumHeight = captionText.getTextWidget().getLineHeight();
		captionText.getControl().setLayoutData(gridData);
		captionText.addTextListener(new ITextListener() {
			@Override
			public void textChanged(TextEvent event) {
				if (currentPage != null) {
					currentPage.getJournal().setDirty(true);
				}
			}
		});

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
		// btnSortByDate = new Button(sortGroup, SWT.RADIO);
		// btnSortByDate.setText("date");
		btnSortByName = new Button(sortGroup, SWT.RADIO);
		btnSortByName.setText("name");
		btnSortManual = new Button(sortGroup, SWT.RADIO);
		btnSortManual.setText("manual");
		sortGroup.setLayoutData(new GridData(SWT.RIGHT, SWT.TOP, false, false));
		// orderByButtonMap.put(PhotosOrder.DATE, btnSortByDate);
		orderByButtonMap.put(PhotosOrder.NAME, btnSortByName);
		orderByButtonMap.put(PhotosOrder.MANUAL, btnSortManual);

		bindControls();
	}

	protected void displayPage(Page pageToShow) {
		bindModelToUI(pageToShow);
	}

	public ImageMagickResizerServiceFactory getResizerServiceFactory() {
		return resizerServiceFactory;
	}

	PhotosOrder getSortOrderFromButton(Button b) {
		for (Entry<PhotosOrder, Button> e : orderByButtonMap.entrySet()) {
			if (e.getValue() == b) {
				return e.getKey();
			}
		}
		throw new IllegalStateException();
	}

	public CachingThumbnailProviderFactory getThumbnailProvider() {
		return thumbnailFactory;
	}

	public ThumbnailViewer getViewer() {
		return thumbViewer;
	}

	private ResizerService createResizer(Journal journal, boolean quiet) {
		try {
			return resizerServiceFactory.createResizerFor(journal);
		} catch (MagicNotAvailableException e) {
			LOG.info("Failed to start photo reszier", e);
			if (!quiet) {
				MessageBox error = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
				error.setText("Error");
				error.setMessage("Failed to start photo resizer:\n\n" + e.getMessage());
				error.open();
			}
			return null;
		}
	}

	public boolean registerResizer(Journal journal, boolean quiet) {
		Assert.isNull(journal.getData(RESIZE_LISTENER_KEY));
		Assert.isNull(journal.getData(ResizerService.KEY));

		/* use existing resizer if set */
		final ResizerService resizer = createResizer(journal, quiet);

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
			resizer.resizeAll(photos);
		}

		// 3) listen for addition & removal of photos from journal
		JournalListener resizeListener = new JournalAdapter() {
			@Override
			public void photosAdded(List<Photo> photos, Page page) {
				resizer.resizeAll(photos);
			}

			public void photosRemoved(List<Photo> photos, Page page) {
				resizer.removeAll(photos); // TODO remove from cache?
			}
		};
		resizer.addJobListener(statusListener.resizeListener);
		journal.addJournalListener(resizeListener);
		journal.setData(RESIZE_LISTENER_KEY, resizeListener);
		return true;
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

	public void unregisterResizer(Journal journal) {
		ResizerService resizer = (ResizerService) journal.getData(ResizerService.KEY);
		if (resizer != null) {
			resizer.removeJobListener(statusListener.resizeListener);
			JournalListener listener = (JournalListener) journal.getData(RESIZE_LISTENER_KEY);
			journal.removeListener(listener);
			resizer.cancelAll(); /* don't wait */
			journal.removeData(RESIZE_LISTENER_KEY);
			journal.removeData(ResizerService.KEY);
		}
	}

	interface StatusBarUpdater {
		void setStatus(String value);
	}

	/**
	 * Updates status bar with thumbnail viewer details.
	 */
	class StatusUpdater implements SelectionListener {

		private final String LISTENER_KEY = FocusListener.class.getName();

		private int remainingImageJobs, remainingThumbnailJobs;

		public JobListener resizeListener = new JobListener() {
			@Override
			public void update(int remainingJobs) {
				remainingImageJobs = remainingJobs;
				updateStatusBar();
			}
		};

		private Object[] selected;

		public JobListener thumnailListener = new JobListener() {

			@Override
			public void update(int remainingJobs) {
				remainingThumbnailJobs = remainingJobs;
				updateStatusBar();
			}
		};

		public StatusUpdater() {
		}

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
			if (meta.containsDirectory(JpegDirectory.class)) {
				try {
					JpegDirectory jpeg = (JpegDirectory) meta.getDirectory(JpegDirectory.class);
					return jpeg.getImageWidth() + " x " + jpeg.getImageHeight();
				} catch (Exception e) {
					/* ignore */
				}
			}
			return "";
		}

		private String getPhotoDateTime(Metadata meta) {
			if (meta.containsDirectory(ExifDirectory.class)) {
				try {
					ExifDirectory exifDirectory = (ExifDirectory) meta.getDirectory(ExifDirectory.class);
					Date d = exifDirectory.getDate(ExifDirectory.TAG_DATETIME_ORIGINAL);
					return DateFormat.getDateTimeInstance().format(d);
				} catch (Exception e) {
					/* ignore */
				}
			}
			return "";
		}

		void updateStatusBar() {
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
						if (meta != null) {
							String takenOn = getPhotoDateTime(meta);
							if (!takenOn.isEmpty()) {
								b.append(" : taken on ").append(takenOn).append(" ");
							}
						}
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

			application.getStatusLineManager().setMessage(b.toString());
		}

		@Override
		public void widgetDefaultSelected(SelectionEvent e) {
		}

		@Override
		public void widgetSelected(SelectionEvent e) {
			selected = thumbViewer.getSelection();

			FocusListener oldListener = (FocusListener) captionText.getData(LISTENER_KEY);
			if (oldListener != null) {
				captionText.getTextWidget().removeFocusListener(oldListener);
			}

			/* disable current page so we don't dirty the journal */
			Page p = currentPage;
			try {
				currentPage = null;
				if (selected.length == 1) {
					/* enable and bind photo comment */
					Photo currentPhoto = (Photo) selected[0];

					/* read-only after upload */
					if (currentPhoto.getState() == UploadState.UPLOADED) {
						captionText.setEditable(false);
					} else {
						captionText.setEditable(true);
					}
					captionText.getTextWidget().setEnabled(true);
					IDocument document = currentPhoto.getOrCreateCaptionDocument();
					undoCache.hold(document);
					captionText.setDocument(document);
					FocusListener listener = new FocusAdapter() {
						@Override
						public void focusGained(FocusEvent e) {
							application.setCurrentUndoContext(undoManager.getUndoContext());
						}
					};
					captionText.getTextWidget().addFocusListener(listener);
					captionText.setData(LISTENER_KEY, listener);
				} else {
					// 0 or > 1
					captionText.setDocument(new Document(""));
					captionText.getTextWidget().setEnabled(false);
					application.setCurrentUndoContext(ApplicationWindow.APPLICATION_CONTEXT);
				}
			} finally {
				currentPage = p;
			}

			updateStatusBar();
		}
	}
}