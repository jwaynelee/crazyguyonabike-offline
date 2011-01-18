package com.cgoab.offline.ui;

import static com.cgoab.offline.util.StringUtils.nullToEmpty;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.commands.operations.IUndoContext;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextListener;
import org.eclipse.jface.text.TextEvent;
import org.eclipse.jface.text.TextViewer;
import org.eclipse.jface.text.TextViewerUndoManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.events.VerifyListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DateTime;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cgoab.offline.model.Journal;
import com.cgoab.offline.model.JournalAdapter;
import com.cgoab.offline.model.JournalListener;
import com.cgoab.offline.model.Page;
import com.cgoab.offline.model.Page.EditFormat;
import com.cgoab.offline.model.Page.HeadingStyle;
import com.cgoab.offline.model.UploadState;
import com.cgoab.offline.ui.JournalSelectionService.JournalSelectionListener;
import com.cgoab.offline.ui.thumbnailviewer.ThumbnailViewer;
import com.cgoab.offline.util.StringUtils;
import com.cgoab.offline.util.Utils;

public class PageEditor {

	private static final Logger LOG = LoggerFactory.getLogger(PageEditor.class);

	private DocumentUndoCache undoCache = new DocumentUndoCache(3);

	private Page currentPage;

	private Text distanceInput;

	private Text headlineInput;

	private List<Control> pageEditorWidgets;

	private TextViewer textInput;

	private Text titleInput;

	private Combo cmbFormat;

	private Combo cmbHeadingStyle;

	private Combo cmbIndent;

	private DateTime dateInput;

	private Button btnBold;

	private Button btnItalic;

	private DirtyCurrentPageListener dirtyCurrentPageListener = new DirtyCurrentPageListener();

	private ApplicationWindow application;

	private TextViewerUndoManager undoManager;

	private enum EditorState {
		DISABLED, EDITABLE, READONLY;
	}

	public PageEditor(ApplicationWindow application) {
		this.application = application;
		JournalSelectionService.getInstance().addListener(new JournalSelectionListener() {

			private JournalListener releaseFromCacheOnDeleteListener = new JournalAdapter() {
				@Override
				public void pageDeleted(Page page) {
					undoCache.release(page.getOrCreateTextDocument());
				}
			};

			@Override
			public void selectionChanged(Object newSelection, Object oldSelection) {
				displayPage(newSelection instanceof Page ? (Page) newSelection : null);
			}

			@Override
			public void journalClosed(Journal journal) {
				undoCache.clear();
				journal.removeListener(releaseFromCacheOnDeleteListener);
			}

			@Override
			public void journalOpened(Journal journal) {
				journal.addJournalListener(releaseFromCacheOnDeleteListener);
			}
		});
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

	private void copyPageToUI(Page pageToShow) {
		/* unset current page else "setText's" will dirty model */
		currentPage = null;

		LOG.debug("Binding UI to page [{}]", pageToShow);

		if (pageToShow == null) {
			// clear controls and disable...
			btnBold.setSelection(false);
			btnItalic.setSelection(false);
			titleInput.setText("");
			headlineInput.setText("");
			distanceInput.setText("");
			textInput.setDocument(new Document());
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
			distanceInput.setText(Integer.toString(pageToShow.getDistance()));
			IDocument document = pageToShow.getOrCreateTextDocument();
			undoCache.hold(document);
			textInput.setDocument(document);
		}

		// update global after update to avoid init "dirtying" page
		currentPage = pageToShow;
	}

	private void displayPage(Page pageToShow) {
		if (pageToShow == currentPage) {
			// nothing to do
			return;
		}

		copyPageToUI(pageToShow);

		if (pageToShow == null) {
			setEditorControlsState(EditorState.DISABLED);
		} else if (pageToShow.getState() == UploadState.UPLOADED) {
			setEditorControlsState(EditorState.READONLY);
		} else if (pageToShow.getState() == UploadState.PARTIALLY_UPLOAD) {
			setEditorControlsState(EditorState.READONLY);
		} else { // NEW or ERROR
			setEditorControlsState(EditorState.EDITABLE);
		}
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

	/**
	 * Binds changes control to the property on the "current page".
	 * 
	 * @TODO replace with eclipse binding framework
	 * @param c
	 * @param property
	 */
	private void bindToCurrentPage(Control c, int event, String property) {
		String name = "set" + property.substring(0, 1).toUpperCase() + property.substring(1);
		Method method = Utils.getFirstMethodWithName(name, Page.class);
		if (method == null) {
			throw new IllegalArgumentException("No method " + name);
		}
		if (method.getParameterTypes().length != 1) {
			throw new IllegalArgumentException("Setter must accept 1 argument");
		}

		c.addListener(event, new PropertyBinder(c, method) {
			@Override
			protected Object getTarget() {
				return currentPage;
			}
		});
	}

	private void bindControls() {
		bindToCurrentPage(btnBold, SWT.Selection, "bold");
		bindToCurrentPage(btnItalic, SWT.Selection, "italic");
		bindToCurrentPage(cmbIndent, SWT.Selection, "indent");
		bindToCurrentPage(cmbHeadingStyle, SWT.Selection, "headingStyle");
		bindToCurrentPage(cmbFormat, SWT.Selection, "format");
		bindToCurrentPage(titleInput, SWT.Modify, "title");
		bindToCurrentPage(headlineInput, SWT.Modify, "headline");
		bindToCurrentPage(dateInput, SWT.Selection, "date");
		bindToCurrentPage(distanceInput, SWT.Modify, "distance");
	}

	public void createControls(Composite parent) {
		Composite editorComposite = new Composite(parent, SWT.BORDER);
		editorComposite.setLayout(new GridLayout(4, false));
		editorComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

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
		// titleLabel.addFocusListener(selectCurrentPageListener);
		titleInput.setTextLimit(128);
		titleInput.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		titleInput.addModifyListener(dirtyCurrentPageListener);

		// headline
		Label headlineLabel = new Label(editorComposite, SWT.NONE);
		headlineLabel.setText("Headline:");

		headlineInput = new Text(editorComposite, SWT.SINGLE | SWT.BORDER);
		// headlineInput.addFocusListener(selectCurrentPageListener);
		headlineInput.setTextLimit(128);
		headlineInput.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		headlineInput.addModifyListener(dirtyCurrentPageListener);

		// date
		Label dateLabel = new Label(editorComposite, SWT.NONE);
		dateLabel.setText("Date:");

		dateInput = new DateTime(editorComposite, SWT.DATE | SWT.DROP_DOWN);
		// dateInput.addFocusListener(selectCurrentPageListener);
		dateInput.addSelectionListener(dirtyCurrentPageListener);
		dateInput.setLayoutData(new GridData(SWT.FILL, SWT.LEFT, true, false));

		// distance
		Label distanceLabel = new Label(editorComposite, SWT.NONE);
		distanceLabel.setText("Distance:");
		distanceInput = new Text(editorComposite, SWT.SINGLE | SWT.BORDER);
		// distanceLabel.addFocusListener(selectCurrentPageListener);
		distanceInput.setTextLimit(10);
		distanceInput.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		distanceInput.addModifyListener(dirtyCurrentPageListener);
		distanceInput.addVerifyListener(new VerifyListener() {
			@Override
			public void verifyText(VerifyEvent e) {
				for (int i = 0; i < e.text.length(); ++i) {
					if (!Character.isDigit(e.text.charAt(i))) {
						e.doit = false;
						return;
					}
				}
			}
		});

		textInput = new TextViewer(editorComposite, SWT.WRAP | SWT.BORDER | SWT.V_SCROLL);
		textInput.getTextWidget().addFocusListener(new FocusAdapter() {
			@Override
			public void focusGained(FocusEvent e) {
				IUndoContext context = undoManager.getUndoContext();
				application.setCurrentUndoContext(context);
			}
		});
		undoManager = new TextViewerUndoManager(20);
		undoManager.connect(textInput);
		// undoManager = new TextViewerUndoManager(50);
		// undoManager.connect(textInput);

		FontData fd = new FontData("Tahoma", 10, SWT.NONE);
		textInput.getTextWidget().setFont(new Font(parent.getShell().getDisplay(), fd));
		GridData data = new GridData(SWT.FILL, SWT.FILL, true, true, 4, 1);
		// 6 lines of text
		data.heightHint = textInput.getTextWidget().getLineHeight() * 6;
		GC tempGC = new GC(textInput.getTextWidget());
		int averageCharWidth = tempGC.getFontMetrics().getAverageCharWidth();
		tempGC.dispose();
		data.widthHint = averageCharWidth * 120; // 120 characters
		textInput.getTextWidget().setLayoutData(data);
		// textInput.getTextWidget().addFocusListener(selectCurrentPageListener);
		textInput.addTextListener(dirtyCurrentPageListener);
		/*
		 * Slurp up references to all the editor widgets so we can turn on/off
		 * together
		 */
		pageEditorWidgets = Arrays.asList(btnItalic, btnBold, cmbFormat, cmbIndent, cmbHeadingStyle, titleInput,
				headlineInput, distanceInput, dateInput, textInput.getTextWidget());
		setEditorControlsState(EditorState.DISABLED);
		bindControls();
	}
}