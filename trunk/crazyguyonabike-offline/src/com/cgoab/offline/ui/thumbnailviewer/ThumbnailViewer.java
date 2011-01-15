package com.cgoab.offline.ui.thumbnailviewer;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DragSource;
import org.eclipse.swt.dnd.DragSourceAdapter;
import org.eclipse.swt.dnd.DragSourceEvent;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetAdapter;
import org.eclipse.swt.dnd.DropTargetEffect;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.events.MenuListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

import com.cgoab.offline.ui.thumbnailviewer.ThumbnailProvider.Thumbnail;
import com.cgoab.offline.ui.util.SWTUtils;
import com.cgoab.offline.util.FutureCompletionListener;
import com.drew.metadata.Metadata;

public class ThumbnailViewer extends Canvas {

	public static final int THUMBNAIL_HEIGHT = 120;
	public static final int THUMBNAIL_WIDTH = 200;
	static final int PADDING_BETWEEN_THUMNAIL = 5;
	static final int PADDING_INSIDE = 5;
	static final int PADDING_TOP = 5;
	static final int TEXT_HEIGHT = 20;
	public static ResizeStrategy RESIZE_STRATEGY = new FitWithinResizeStrategy(new Point(THUMBNAIL_WIDTH,
			THUMBNAIL_HEIGHT));

	private static boolean hasValidExtension(String fileName) {
		String lower = fileName.toLowerCase();
		int dot = lower.lastIndexOf('.');
		if (dot > 0) {
			String extension = lower.substring(dot);
			if (extension.contains(extension)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isControlKeyOn(int stateMask) {
		return (stateMask & SWT.CONTROL) != 0;
	}

	private static boolean isShiftKeyOn(int stateMask) {
		return (stateMask & SWT.SHIFT) != 0;
	}

	private final FutureCompletionListener<Thumbnail> completionListener = new FutureCompletionListener<Thumbnail>() {
		@Override
		public void onCompletion(final Future<Thumbnail> result, final Object data) {
			/* callback already on UI thread */
			handleThumbnailReady(result, (ThumbnailHolder) data);
		}
	};

	private ThumbnailViewerContentProvider contentProvider;

	private boolean editable;

	private List<ThumbnailViewerEventListener> eventListeners = new ArrayList<ThumbnailViewerEventListener>();

	// public IStructuredSelection getSelection() {
	// throw new RuntimeException();
	// }

	private List<ViewerFilter> filters = new ArrayList<ViewerFilter>();

	private Object input;

	private Image invalidImage;

	private ThumbnailViewerLabelProvider labelProvider;

	/**
	 * Distance to the left edge of the thumbnail page (a negative value when
	 * scrolled right)
	 */
	private final Point origin = new Point(0, 0);

	/**
	 * List of selected thumbnails, items are ordered from left to right.
	 */
	private List<ThumbnailHolder> selected = new ArrayList<ThumbnailHolder>();

	private List<SelectionListener> selectionListeners = new ArrayList<SelectionListener>();

	private ThumbnailProvider thumbnailProvider;

	/**
	 * List of thumbnails currently loaded, items are ordered from left to
	 * right.
	 */
	private final List<ThumbnailHolder> thumbnails = new ArrayList<ThumbnailHolder>();

	private boolean updateSelectionOnMouseUp;

	public ThumbnailViewer(Composite parent) {
		super(parent, SWT.NO_REDRAW_RESIZE | SWT.H_SCROLL | SWT.NO_BACKGROUND | SWT.BORDER);

		configureContextMenu();

		getHorizontalBar().setEnabled(false);
		getHorizontalBar().setPageIncrement(THUMBNAIL_WIDTH + PADDING_BETWEEN_THUMNAIL);
		getHorizontalBar().addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event e) {
				int hSelection = getHorizontalBar().getSelection();
				int destX = -hSelection - origin.x;
				scroll(destX, 0, 0, 0, getClientArea().width, getClientArea().height, false);
				origin.x = -hSelection;
			}
		});

		addListener(SWT.Resize, new Listener() {
			@Override
			public void handleEvent(Event e) {
				handleResize(e);
			}
		});

		// redraw selection with active/inactive colour
		addFocusListener(new FocusListener() {
			@Override
			public void focusGained(FocusEvent e) {
				if (selected.size() > 0) {
					redraw();
				}
			}

			@Override
			public void focusLost(FocusEvent e) {
				if (selected.size() > 0) {
					redraw();
				}
			}
		});

		addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				handleKeyPressed(e);
			}
		});

		addMouseListener(new MouseListener() {
			@Override
			public void mouseDoubleClick(MouseEvent e) {
			}

			@Override
			public void mouseDown(MouseEvent e) {
				handleMouseDown(e);
			}

			@Override
			public void mouseUp(MouseEvent e) {
				handleMouseUp(e);
			}
		});

		setDragDetect(true);
		DragSource ds = new DragSource(this, DND.DROP_MOVE);
		ds.addDragListener(new DragSourceAdapter() {
			@Override
			public void dragFinished(DragSourceEvent event) {
				LocalThumbnailTransfer.getInstance().setSelectedPhoto(null);
			}

			@Override
			public void dragSetData(DragSourceEvent e) {
			}

			@Override
			public void dragStart(DragSourceEvent e) {
				if (!editable) {
					e.doit = false;
					return;
				}
				ThumbnailHolder toDrag = getThumbnailAt(e.x, e.y);
				if (selected.contains(toDrag)) {
					e.doit = true;
					e.image = toDrag.getImage();
					LocalThumbnailTransfer.getInstance().setSelectedPhoto(toDrag);

					/*
					 * linux fires mouseUp() *before* the drop is fired, so
					 * manually suppress the (re)selection event
					 */
					updateSelectionOnMouseUp = false;
				} else {
					e.doit = false;
				}
			}
		});
		ds.setTransfer(new Transfer[] { LocalThumbnailTransfer.getInstance() });
		addPaintListener(new PaintListener() {
			@Override
			public void paintControl(PaintEvent e) {
				ThumbnailViewer.this.paintControl(e.gc);
			}
		});

		/* dispose() not called recursively, listen instead */
		addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				/*
				 * Make sure the ThumbnailProvider doesn't keep creating
				 * thumbnails and updating the UI once it is disposed.
				 */
				for (Iterator<ThumbnailHolder> i = thumbnails.iterator(); i.hasNext();) {
					i.next().dispose();
					i.remove();
				}

				if (contentProvider != null) {
					contentProvider.dispose();
				}
			}
		});
	}

	public void addEventListener(ThumbnailViewerEventListener listener) {
		if (!eventListeners.contains(listener)) {
			eventListeners.add(listener);
		}
	}

	public void addFilter(ViewerFilter viewerFilter) {
		filters.add(viewerFilter);
	}

	public void addSelectionListener(SelectionListener listener) {
		if (!selectionListeners.contains(listener)) {
			selectionListeners.add(listener);
		}
	}

	// TODO optimise
	private int computeImagesWidth() {
		if (thumbnails.size() == 0) {
			return 0;
		}
		int width = PADDING_BETWEEN_THUMNAIL;
		for (ThumbnailHolder image : thumbnails) {
			// compute width with padding
			width += image.getWidth() + PADDING_BETWEEN_THUMNAIL;
		}
		return width;
	}

	@Override
	public Point computeSize(int wHint, int hHint, boolean changed) {
		return new Point(wHint, THUMBNAIL_HEIGHT + PADDING_INSIDE + PADDING_TOP + TEXT_HEIGHT
				+ getHorizontalBar().getSize().y + 5);
	}

	private void configureContextMenu() {
		Menu menu = new Menu(getShell(), SWT.POP_UP);

		final MenuItem removeItem = new MenuItem(menu, SWT.PUSH);
		removeItem.setText("Remove");
		removeItem.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				deleteCurrentSelection();
			}
		});

		final MenuItem getInfoItem = new MenuItem(menu, SWT.PUSH);
		getInfoItem.setText("Image Info");
		getInfoItem.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (selected.size() != 1) {
					return;
				}
				ThumbnailHolder holder = selected.get(0);
				try {
					Thumbnail thumb = holder.getFuture().get();
					new ExifViewer(getShell()).open(thumb.meta, holder.getFile());
				} catch (InterruptedException e1) {
					/* ignore */
				} catch (ExecutionException e1) {
					/* ignore */
				}
			}
		});
		menu.addMenuListener(new MenuListener() {
			@Override
			public void menuShown(MenuEvent e) {
				getInfoItem.setEnabled(false);
				removeItem.setEnabled(false);
				if (selected.size() == 1) {
					getInfoItem.setEnabled(true);
				}
				if (selected.size() > 0) {
					removeItem.setEnabled(true);
				}
			}

			@Override
			public void menuHidden(MenuEvent e) {

			}
		});

		setMenu(menu);
	}

	private void deleteCurrentSelection() {
		if (selected.size() > 0 && editable) {
			for (ThumbnailViewerEventListener l : eventListeners) {
				l.itemsRemoved(getSelection());
			}
			// selected.clear();
			fireSelectionListener();
		}
	}

	private ThumbnailHolder findThumbnailHolder(Object o) {
		for (ThumbnailHolder h : thumbnails) {
			if (h.getData() == o) {
				return h;
			}
		}
		return null;
	}

	// TODO use tree to speed up
	private int findThumbnailIndexAt(int xToAdd) {
		ThumbnailHolder item;

		if (thumbnails.size() == 0) {
			return -1;
		}

		// 1) test falling to left of first item
		ThumbnailHolder first = thumbnails.get(0);
		int r = first.getX() + (int) ((float) first.getWidth() / 2);
		if (xToAdd < r) {
			return 0;
		}

		// 2) test thumbs 1..end
		ThumbnailHolder previous = first;
		for (int i = 1; i < thumbnails.size(); ++i) {
			item = thumbnails.get(i);
			int limitLeft = item.getX() - (int) ((float) previous.getWidth() / 2) - PADDING_BETWEEN_THUMNAIL;
			int limitRight = item.getX() + (int) ((item.getWidth() / 2));
			if (xToAdd <= limitRight && xToAdd >= limitLeft) {
				return i;
			}
			previous = item;
		}

		// 3) must have fallen to right of final thumb
		return thumbnails.size();
	}

	private void fireSelectionListener() {
		for (SelectionListener l : selectionListeners) {
			Event e = new Event();
			e.widget = this;
			SelectionEvent selectionEvent = new SelectionEvent(e);
			l.widgetSelected(selectionEvent);
		}
	}

	public ThumbnailViewerContentProvider getContentProvider() {
		return contentProvider;
	}

	/**
	 * Exposes photo meta data which may or may not have been loaded
	 * 
	 * @param photo
	 * @return
	 */
	public Metadata getMetaData(Object photo) {
		for (ThumbnailHolder h : thumbnails) {
			if (h.getData() == photo) {
				try {
					Future<Thumbnail> future = h.getFuture();
					return future != null && future.isDone() ? h.getFuture().get().meta : null;
				} catch (InterruptedException e) {
					/* ignore */
				} catch (ExecutionException e) {
					/* ignore */
				}
			}
		}
		return null;
	}

	private Image getMissingImage() {
		if (invalidImage == null) {
			invalidImage = new Image(getDisplay(), getClass().getResourceAsStream("/icons/missing.png"));
		}
		return invalidImage;
	}

	public Object[] getSelection() {
		if (selected.isEmpty()) {
			return new Object[0];
		}
		Object[] o = new Object[selected.size()];
		for (int i = 0; i < o.length; ++i) {
			o[i] = selected.get(i).getData();
		}
		return o;
	}

	/**
	 * Returns the thumbnail at the given location, null if none.
	 * 
	 * @param x
	 *            relative to origin of the page (not canvas)
	 * @param y
	 *            relative to origin of the page (not canvas)
	 * @return
	 */
	private ThumbnailHolder getThumbnailAt(int x, int y) {
		// TODO binary search?
		int ox = -origin.x + x;
		int oy = -origin.y + y;
		for (ThumbnailHolder i : thumbnails) {
			if (i.inside(ox, oy)) {
				return i;
			}
		}
		return null;
	}

	private void handleThumbnailReady(Future<Thumbnail> future, ThumbnailHolder holder) {
		if (holder.isDisposed()) {
			return;
		}

		ExecutionException exception = null;
		Thumbnail result = null;

		try {
			result = future.get();
		} catch (InterruptedException e) {
			/* ignore */
			return;
		} catch (CancellationException e) {
			/* ignore */
			return;
		} catch (ExecutionException e) {
			exception = e;
		}

		if (result != null) {
			Image image = new Image(getDisplay(), result.imageData);
			holder.setImage(image);
		} else if (exception != null) {
			if (onInvalidImage(holder.getData(), exception)) {
				holder.setFailedToLoad(true);
			} else {
				remove(new Object[] { holder.getData() });
				return;
			}
		}

		if (isThumbnailVisible(holder)) {
			// TODO clip redraw
			redraw();
		}
	}

	private void handleDeleteKey() {
		deleteCurrentSelection();
		return;
	}

	private void handleEndOrHomeKey(KeyEvent e) {
		if (thumbnails.size() > 0) {
			int newId = e.keyCode == SWT.END ? thumbnails.size() - 1 : 0;
			ThumbnailHolder newItem = thumbnails.get(newId);
			selected.clear();
			selected.add(newItem);
			fireSelectionListener();
			scrollToDisplay(newItem);
			redraw();
		}
		return;
	}

	// select all
	private void handleCtrlAKey() {
		if (thumbnails.size() > 0) {
			selected.clear();
			selected.addAll(thumbnails);
			fireSelectionListener();
			redraw();
		}
		return;
	}

	// move (or grow selection) to the right (starting from far left if no
	// selection)
	private void handleArrowRightKey(KeyEvent e) {
		int totalThumbnails = thumbnails.size();
		ThumbnailHolder newItem = null;
		if (selected.size() == 0) {
			if (totalThumbnails == 0) {
				return;
			}
			newItem = thumbnails.get(0);
			selected.add(newItem);
		} else {
			ThumbnailHolder lastInSelection = selected.get(selected.size() - 1);
			int iLastInSelection = thumbnails.indexOf(lastInSelection);
			if (isShiftKeyOn(e.stateMask)) {
				// expand selection
				if (iLastInSelection + 1 == totalThumbnails) {
					// selection already up against edge
					return;
				}
				newItem = thumbnails.get(iLastInSelection + 1);
				selected.add(newItem);
			} else {
				if (iLastInSelection + 1 >= totalThumbnails) {
					newItem = lastInSelection;
				} else {
					newItem = thumbnails.get(iLastInSelection + 1);
				}
				selected.clear();
				selected.add(newItem);
			}
		}

		fireSelectionListener();
		scrollToDisplay(newItem);
		redraw();
	}

	// move (or grow selection) to the left (starting from far right if no
	// selection)
	private void handleArrowLeftKey(KeyEvent e) {
		int totalThumbnails = thumbnails.size();
		ThumbnailHolder newItem = null;

		// 1) if nothing selected then start from far right
		// 2) if multiple selection select one left
		// 3) if single selection select one left

		if (selected.size() == 0) {
			if (totalThumbnails == 0) {
				return; // noting we can do
			}
			newItem = thumbnails.get(totalThumbnails - 1);
			selected.add(newItem);
		} else {
			ThumbnailHolder firstInSelection = selected.get(0);
			int iFirstInSelection = thumbnails.indexOf(firstInSelection);
			if (isShiftKeyOn(e.stateMask)) {
				// expand selection
				if (iFirstInSelection == 0) {
					// selection already up against edge
					return;
				}
				newItem = thumbnails.get(iFirstInSelection - 1);
				// prepend to maintain order
				selected.add(0, newItem);
			} else {
				// clear selection, and move left
				if (iFirstInSelection == 0) {
					// end of the list - beep?
					newItem = firstInSelection;
				} else {
					newItem = thumbnails.get(iFirstInSelection - 1);
				}
				selected.clear();
				selected.add(newItem);
			}
		}

		fireSelectionListener();
		scrollToDisplay(newItem);
		redraw();
	}

	private void handleKeyPressed(KeyEvent e) {
		if (SWTUtils.getAccelerator(e) == 'A' + SWT.CONTROL) {
			handleCtrlAKey();
		} else {
			switch (e.keyCode) {
			case SWT.DEL:
				handleDeleteKey();
				break;
			case SWT.END:
				/* fall through */
			case SWT.HOME:
				handleEndOrHomeKey(e);
				break;
			case SWT.ARROW_LEFT:
				handleArrowLeftKey(e);
				break;
			case SWT.ARROW_RIGHT:
				handleArrowRightKey(e);
				break;
			}
		}
	}

	private void handleMouseDown(MouseEvent e) {
		setFocus();
		ThumbnailHolder newSelected = getThumbnailAt(e.x, e.y);

		boolean changed = false;
		boolean shiftKeyOn = isShiftKeyOn(e.stateMask);
		boolean controlKeyOn = isControlKeyOn(e.stateMask);

		if (selected.contains(newSelected) && e.button == 3) {
			// TODO remove explicit menu button check
			// no-op as this is a context menu request
		} else if (newSelected == null) {
			// clicked outside a thumbnail
			if (selected.size() == 0) {
				// nothing selected before so nothing to do
			} else {
				// something is selected, preserve if ctrl/shift on
				// else clear
				if (!shiftKeyOn && !controlKeyOn) {
					selected.clear();
					changed = true;
				}
			}
		} else {
			// 1) if control key add to selection
			// 2) if shift grow selection
			// 3) else unselect old and select new
			changed = true;
			if (controlKeyOn) {
				if (selected.remove(newSelected)) {
					// already in the list, it is now removed
				} else {
					selected.add(newSelected);
				}
			} else if (shiftKeyOn) {
				if (selected.size() > 0) {
					// add all between edge of selection and new
					int iFirstSelected = thumbnails.indexOf(selected.get(0));
					int iLastSelected = thumbnails.indexOf(selected.get(selected.size() - 1));
					int iNewSelected = thumbnails.indexOf(newSelected);
					if (iNewSelected >= iFirstSelected && iNewSelected <= iLastSelected) {
						selected.clear();
						for (int i = iFirstSelected; i <= iNewSelected; ++i) {
							selected.add(thumbnails.get(i));
						}
					} else if (iNewSelected < iFirstSelected) {
						// add to front of list
						for (int i = iFirstSelected - 1; i >= iNewSelected; --i) {
							selected.add(0, thumbnails.get(i));
						}
					} else if (iNewSelected > iLastSelected) {
						// add to end of list
						for (int i = iLastSelected + 1; i <= iNewSelected; ++i) {
							selected.add(thumbnails.get(i));
						}
					}
				}
			} else {
				// clear on mouse up, otherwise we'll destroy the selection
				// before we get to dragStart() in DND
				updateSelectionOnMouseUp = true;
				changed = false; // we've not done anything yet...
			}
		}

		if (changed) {
			fireSelectionListener();
			redraw();
		}
	}

	private void handleMouseUp(MouseEvent e) {
		// System.out.println("MouseUp");
		if (updateSelectionOnMouseUp) {
			updateSelectionOnMouseUp = false;
			selected.clear();
			ThumbnailHolder newSelected = getThumbnailAt(e.x, e.y);
			if (newSelected != null) {
				selected.add(newSelected);
			}
			fireSelectionListener();
			redraw();
		}
	}

	private void handleResize(Event e) {
		int width = computeImagesWidth();
		Rectangle client = getClientArea();
		updateScrollBars();
		int hPage = width - client.width;
		int hSelection = getHorizontalBar().getSelection();
		// int vSelection = vBar.getSelection();
		if (hSelection >= hPage) {
			if (hPage <= 0)
				hSelection = 0;
			origin.x = -hSelection;
		}
		redraw();
	}

	private void installDragAndDropTarget() {
		DropTarget dt = new DropTarget(this, DND.DROP_MOVE | DND.DROP_LINK | DND.DROP_COPY);
		dt.setTransfer(new Transfer[] { FileTransfer.getInstance(), LocalThumbnailTransfer.getInstance() });
		dt.setDropTargetEffect(new ThumbnailViewerDropEffect(this));
		dt.addDropListener(new DropTargetAdapter() {
			@Override
			public void drop(DropTargetEvent e) {
				// System.out.println("drop");
				Point offset = toControl(e.x, e.y);
				int xToAdd = -origin.x + offset.x;
				if (LocalThumbnailTransfer.getInstance().isSupportedType(e.currentDataType)) {
					ThumbnailHolder selectedPhoto = LocalThumbnailTransfer.getInstance().getSelectedPhoto();
					// guard against dropping the item back on itself (ignore)
					int right = selectedPhoto.getX() + (int) ((float) selectedPhoto.getWidth() / 2);
					int left = selectedPhoto.getX() - (int) ((float) selectedPhoto.getWidth() / 2);
					if (xToAdd > left && xToAdd < right) {
						// nothing to do
					} else {
						// on linux "mouseUp" is sent before "drop", so the
						// selection will be empty
						int insertAt = findThumbnailIndexAt(xToAdd);
						for (ThumbnailViewerEventListener l : eventListeners) {
							l.itemsMoved(getSelection(), insertAt);
						}
					}
				} else if (FileTransfer.getInstance().isSupportedType(e.currentDataType)) {
					int indexToInsertAt = findThumbnailIndexAt(xToAdd);
					indexToInsertAt = indexToInsertAt == -1 ? 0 : indexToInsertAt;
					String[] fileStrings = (String[]) e.data;
					ArrayList<File> files = new ArrayList<File>(fileStrings.length);
					for (String fileName : fileStrings) {
						if (!hasValidExtension(fileName)) {
							// TODO log
						} else {
							files.add(new File(fileName));
						}
					}
					for (ThumbnailViewerEventListener l : eventListeners) {
						l.itemsAdded(files.toArray(new File[0]), indexToInsertAt);
					}
				}
			}
		});
	}

	private boolean isThumbnailVisible(ThumbnailHolder h) {
		int leftEdge = -origin.x;
		int rightEdge = leftEdge + getClientArea().width;
		return h.getX() < rightEdge && h.getX() > leftEdge;
	}

	/**
	 * Informs the provider that one of its images is dud. The provider may
	 * choose to continue (invalid image displayed) or remove it from the list,
	 * in which case no refresh necessary.
	 * 
	 * @return
	 */
	private boolean onInvalidImage(Object image, Throwable exception) {
		for (ThumbnailViewerEventListener l : eventListeners) {
			if (!l.itemFailedToLoad(image, exception)) {
				return false;
			}
		}
		return true;
	}

	private void paintControl(GC targetGc) {
		Image buffer = new Image(getDisplay(), getBounds());
		GC bufferGC = new GC(buffer);
		bufferGC.setAntialias(SWT.ON);
		bufferGC.setBackground(targetGc.getBackground());
		bufferGC.setForeground(targetGc.getForeground());
		bufferGC.fillRectangle(buffer.getBounds());

		for (ThumbnailHolder thumb : thumbnails) {
			int left = thumb.getX() + origin.x;
			// left edge of thumbnail is beyond right edge of canvas
			if (left > getClientArea().width) {
				continue;
			}
			// right edge of thumbnail is beyond left edge of canvas
			int right = left + thumb.getWidth();
			if (right < 0) {
				continue;
			}
			// bufferGC.setAntialias(SWT.ON);
			// bufferGC.setAlpha(selected.contains(thumb) ? 127 : 255);

			// draw outline box
			bufferGC.setAlpha(255);
			bufferGC.setLineWidth(1);
			Rectangle iconBox = new Rectangle(left, PADDING_TOP, thumb.getWidth(), thumb.getHeight());

			boolean isSelected = selected.contains(thumb);

			Color oldBackgroundColour = bufferGC.getBackground();
			Color oldForegroundColour = bufferGC.getForeground();
			if (isSelected) {
				bufferGC.setBackground(getDisplay().getSystemColor(
						isFocusControl() ? SWT.COLOR_TITLE_BACKGROUND : SWT.COLOR_TITLE_INACTIVE_BACKGROUND));
				bufferGC.setForeground(getDisplay().getSystemColor(SWT.COLOR_TITLE_FOREGROUND));
				bufferGC.fillRectangle(iconBox);
			}
			String thumbText = thumb.getText();
			Point textSize = bufferGC.stringExtent(thumbText);
			int widthOfBox = thumb.getWidth();
			int spare = widthOfBox - textSize.x;
			int xOfText = 0;
			if (spare > 0) {
				xOfText = left + (int) ((float) spare / 2);
			}
			// TODO trim long file names...
			bufferGC.drawText(thumbText, xOfText, PADDING_TOP + PADDING_INSIDE + THUMBNAIL_HEIGHT + 5);
			bufferGC.setBackground(oldBackgroundColour);
			bufferGC.setForeground(oldForegroundColour);

			// draw image only if we have an image
			Image image = thumb.isFailedToLoad() ? getMissingImage() : thumb.getImage();
			if (image != null) {
				// draw image in centre of icon box
				int paddingBottom = THUMBNAIL_HEIGHT - image.getBounds().height;
				int paddingRight = THUMBNAIL_WIDTH - image.getBounds().width;

				Point imageOrigin = new Point(0, 0);
				if (paddingBottom > 0) {
					imageOrigin.y = (int) ((float) paddingBottom / 2);
				}
				if (paddingRight > 0) {
					imageOrigin.x = (int) ((float) paddingRight / 2);
				}
				bufferGC.setAlpha(thumb.getOpacity());
				bufferGC.drawImage(image, left + PADDING_INSIDE + imageOrigin.x, PADDING_TOP + PADDING_INSIDE
						+ imageOrigin.y);
				bufferGC.setAlpha(255);

				// draw overlay
				Image overlay = thumb.getOverlay();
				if (overlay != null) {
					bufferGC.drawImage(overlay, left + PADDING_INSIDE + imageOrigin.x + 5, PADDING_TOP + PADDING_INSIDE
							+ imageOrigin.y + 5);
				}
			} else {
				// still loading, will be displayed when loaded ?
				// draw a box
				Color oldFgColor = bufferGC.getForeground();
				bufferGC.setForeground(getDisplay().getSystemColor(SWT.COLOR_GRAY));
				bufferGC.setLineJoin(SWT.JOIN_ROUND);
				bufferGC.drawRectangle(left + PADDING_INSIDE, PADDING_TOP + PADDING_INSIDE, THUMBNAIL_WIDTH,
						THUMBNAIL_HEIGHT);
				bufferGC.setForeground(oldFgColor);
			}
		}

		// flush buffer
		targetGc.drawImage(buffer, 0, 0);
		buffer.dispose();
	}

	public void refresh() {

		/*
		 * resize-and-load opetions may be pending, so first dispose all the
		 * items so upon completion the result is discarded. Then cancel all
		 * outstanding operations.
		 */
		Set<Object> oldSelection = new HashSet<Object>(selected.size());
		for (Iterator<ThumbnailHolder> i = thumbnails.iterator(); i.hasNext();) {
			ThumbnailHolder th = i.next();
			if (selected.contains(th)) {
				oldSelection.add(th.getData());
			}
			th.dispose();
			i.remove();
		}
		selected.clear();

		// holds thumbnails that were previously selected
		List<ThumbnailHolder> newSelection = new ArrayList<ThumbnailHolder>();

		if (input != null) {
			Object[] newThumbs = contentProvider.getThumbnails(input);
			for (ViewerFilter filter : filters) {
				newThumbs = filter.filter(null, input, newThumbs);
			}
			// if anything is still selected then scroll to show the first item
			int x = PADDING_INSIDE;
			for (Object o : newThumbs) {
				File file = labelProvider.getImageFile(o);
				String text = labelProvider.getImageText(o);
				final ThumbnailHolder th = new ThumbnailHolder(file, text);
				th.setOpacity(labelProvider.getOpacity(o));
				th.setOverlay(labelProvider.getOverlay(o));
				th.setData(o);
				th.setX(x);
				thumbnails.add(th);
				x += th.getWidth() + PADDING_INSIDE;
				Future<Thumbnail> future = thumbnailProvider.get(th.getFile(), completionListener, th);
				th.setFuture(future);
				if (oldSelection.contains(o)) {
					newSelection.add(th);
				}
			}
		}

		if (newSelection.size() > 0) {
			scrollToDisplay(newSelection.get(0));
			selected.addAll(newSelection);
		} else {
			origin.x = 0;
			getHorizontalBar().setSelection(-origin.x);
		}

		/*
		 * there is edge cases where we fire a spurious selection event, but
		 * handler will be able to deal with that
		 */
		if (oldSelection.size() > 0) {
			fireSelectionListener();
		}
		updateScrollBars();
		redraw();
	}

	// TODO cleanup
	public void remove(Object[] removes) {

		for (Object o : removes) {
			ThumbnailHolder th = findThumbnailHolder(o);
			thumbnails.remove(th);
			selected.remove(th);
			thumbnailProvider.remove(th.getFile());
			th.dispose();
		}

		updateXValues();

		// scroll to show any remaining images
		if (thumbnails.size() > 0) {
			ThumbnailHolder last = thumbnails.get(thumbnails.size() - 1);
			int rightEdgeOfLastThumb = last.getX() + last.getWidth();
			int offsetToRightEdge = rightEdgeOfLastThumb + origin.x;
			if (offsetToRightEdge < getClientArea().width) {
				// push last item to right
				int newXOrigin = origin.x + getClientArea().width + (-offsetToRightEdge);
				origin.x = Math.min(0, newXOrigin);
				getHorizontalBar().setSelection(rightEdgeOfLastThumb);
			}
		}

		updateScrollBars();
		redraw();
	}

	public void removeEventListener(ThumbnailViewerEventListener listener) {
		eventListeners.remove(listener);
	}

	public void removeSelectionListener(SelectionListener listener) {
		selected.remove(listener);
	}

	/**
	 * Scrolls to show the given item
	 * 
	 * @param item
	 */
	private void scrollToDisplay(ThumbnailHolder item) {
		// check if new selection needs us to scroll
		int originToLeftEdge = origin.x + item.getX();
		if (originToLeftEdge < 0) {
			// need to shift right to make above positive
			origin.x += -originToLeftEdge + PADDING_BETWEEN_THUMNAIL;
		}

		// compute the x value of canvas right edge
		int xOfRightEdge = -origin.x + getClientArea().width;
		int distanceToThumbRightEdge = item.getX() + item.getWidth() - xOfRightEdge;
		if (distanceToThumbRightEdge > 0) {
			origin.x -= (distanceToThumbRightEdge + PADDING_BETWEEN_THUMNAIL);
		}

		getHorizontalBar().setSelection(-origin.x);
	}

	public void setContentProvider(ThumbnailViewerContentProvider contentProvider) {
		this.contentProvider = contentProvider;
	}

	/**
	 * When not editable then can't delete or drag-n-drop onto target
	 * 
	 * @param newEditableState
	 */
	public void setEditable(boolean newEditableState) {
		if (newEditableState != this.editable) {
			// dispose DND target
			DropTarget oldDropTarget = (DropTarget) getData(DND.DROP_TARGET_KEY);
			if (oldDropTarget != null) {
				oldDropTarget.dispose();
			}
			if (newEditableState) {
				installDragAndDropTarget();
			} else {
				new DropTarget(this, DND.DROP_NONE);
			}

			this.editable = newEditableState;
		}
	}

	public void setInput(Object newInput) {
		Object old = input;
		contentProvider.inputChanges(this, old, newInput);
		this.input = newInput;
		refresh();
	}

	public void setLabelProvider(ThumbnailViewerLabelProvider labelProvider) {
		this.labelProvider = labelProvider;
	}

	public void setSelection(StructuredSelection newSelection, boolean reveal) {
		// clear current selection
		selected.clear();

		// maintain constraint that 'selected' is ordered L to R
		if (newSelection != null) {
			List<Object> toSelect = newSelection.toList();
			for (ThumbnailHolder h : thumbnails) {
				if (toSelect.contains(h.getData())) {
					selected.add(h);
				}
			}
		}

		if (selected.size() > 0 && reveal) {
			scrollToDisplay(selected.get(0));
		}
		fireSelectionListener();
		redraw();
	}

	public void setThumbnailProvider(ThumbnailProvider thumbnailProvider) {
		this.thumbnailProvider = thumbnailProvider;
	}

	// called on resize or when image added/removed
	private void updateScrollBars() {
		int width = computeImagesWidth();
		if (width == 0) {
			getHorizontalBar().setSelection(0);
			getHorizontalBar().setEnabled(false);
		} else {
			Rectangle client = getClientArea();
			if (width < client.width) {
				getHorizontalBar().setEnabled(false);
			} else {
				getHorizontalBar().setEnabled(true);
				getHorizontalBar().setMaximum(width);
				getHorizontalBar().setThumb(Math.min(width, client.width));
			}
		}
	}

	private void updateXValues() {
		int x = PADDING_BETWEEN_THUMNAIL;
		for (ThumbnailHolder t : thumbnails) {
			t.setX(x);
			x += t.getWidth() + PADDING_BETWEEN_THUMNAIL;
		}
	}

	private class ThumbnailViewerDropEffect extends DropTargetEffect implements PaintListener {

		private static final int CARET_MARGIN = 1;
		private static final int CARET_WIDTH = PADDING_BETWEEN_THUMNAIL - 2 * CARET_MARGIN;
		private static final int SCROLL_AMOUNT = (int) ((float) THUMBNAIL_WIDTH / 3);
		private static final int SCROLL_BOUNDARY = (int) ((float) THUMBNAIL_WIDTH / 3); // pixels
		private static final int SCROLL_HYSTERESIS = 100; // milli seconds
		private static final int UNSELECTED_INDEX = -1;

		/**
		 * Identifies the thumbnail before which we will insert (from
		 * 0..thumbnails.size())
		 */
		private int insertionIndex = UNSELECTED_INDEX;

		private long scrollBeginTime = 0;

		public ThumbnailViewerDropEffect(Control control) {
			super(control);
		}

		@Override
		public void dragEnter(DropTargetEvent event) {
			insertionIndex = UNSELECTED_INDEX;
			scrollBeginTime = 0;
			((ThumbnailViewer) getControl()).addPaintListener(this);
		}

		@Override
		public void dragLeave(DropTargetEvent event) {
			redrawCaret(insertionIndex, UNSELECTED_INDEX);
			scrollBeginTime = 0;
			((ThumbnailViewer) getControl()).removePaintListener(this);
		}

		@Override
		public void dragOver(DropTargetEvent event) {
			if (thumbnails.size() == 0) {
				return;
			}
			Point pt = toControl(event.x, event.y);
			boolean needsRedraw = false;

			// check if we are near an edge and sufficient time has elapsed
			// since we last scrolled
			boolean closeToLeftEdge = pt.x < SCROLL_BOUNDARY;
			boolean closeToRightEdge = pt.x > getClientArea().width - SCROLL_BOUNDARY;

			// are we in the "scroll" window
			if (closeToLeftEdge || closeToRightEdge) {
				// is it time to run?
				if (scrollBeginTime == 0) {
					// schedule the time when the first scroll will occur
					scrollBeginTime = System.currentTimeMillis() + SCROLL_HYSTERESIS;
				} else if (System.currentTimeMillis() > scrollBeginTime) {
					// perform the scroll
					if (closeToLeftEdge) {
						origin.x += SCROLL_AMOUNT;
						if (origin.x > 0) {
							origin.x = 0;
						}
						getHorizontalBar().setSelection(-origin.x);
						needsRedraw = true;
					} else if (closeToRightEdge) {
						int totalWidth = computeImagesWidth();
						origin.x -= SCROLL_AMOUNT;
						int rightEdge = totalWidth + origin.x;
						if (rightEdge < getClientArea().width) {
							origin.x = Math.min(0, -totalWidth + getClientArea().width);
						}
						// System.out.println("new.origin.x" + origin.x);
						getHorizontalBar().setSelection(-origin.x);
						needsRedraw = true;
					}
					// schedule the next scroll...
					scrollBeginTime = System.currentTimeMillis() + SCROLL_HYSTERESIS;
				}
			} else {
				// not within bounds anymore
				scrollBeginTime = 0;
			}

			int newIndex = findThumbnailIndexAt(-origin.x + pt.x);
			int oldIndex = insertionIndex;
			insertionIndex = newIndex;

			if (!needsRedraw) {
				if (newIndex != oldIndex) {
					redrawCaret(oldIndex, newIndex);
				}
			} else {
				redraw();
			}
		}

		private Point getLeftEdgeOfCarret(int index) {
			if (index == thumbnails.size()) {
				ThumbnailHolder t = thumbnails.get(thumbnails.size() - 1);
				return new Point(origin.x + t.getX() + t.getWidth() + CARET_MARGIN, t.getHeight());
			} else {
				ThumbnailHolder t = thumbnails.get(index);
				return new Point(origin.x + t.getX() - PADDING_BETWEEN_THUMNAIL + CARET_MARGIN, t.getHeight());
			}
		}

		@Override
		public void paintControl(PaintEvent e) {
			if (insertionIndex > UNSELECTED_INDEX) {
				GC gc = e.gc;
				// draw wedge
				Point p = getLeftEdgeOfCarret(insertionIndex);
				Rectangle wedge = new Rectangle(p.x, PADDING_TOP, CARET_WIDTH, p.y);
				Color oldBackgroundColour = gc.getBackground();
				Color oldForegroundColour = gc.getForeground();
				gc.setBackground(getDisplay().getSystemColor(SWT.COLOR_TITLE_BACKGROUND));
				gc.setForeground(getDisplay().getSystemColor(SWT.COLOR_TITLE_FOREGROUND));
				gc.fillRectangle(wedge);
				gc.setBackground(oldBackgroundColour);
				gc.setForeground(oldForegroundColour);
			}
		}

		private void redrawCaret(int oldIndex, int newIndex) {
			if (oldIndex != -1) {
				Point p = getLeftEdgeOfCarret(oldIndex);
				redraw(p.x, PADDING_TOP, CARET_WIDTH, p.y, false);
			}
			if (newIndex != -1) {
				Point p = getLeftEdgeOfCarret(newIndex);
				redraw(p.x, PADDING_TOP, CARET_WIDTH, p.y, false);
			}
		}
	}

	public ThumbnailProvider getThumbnailProvider() {
		return thumbnailProvider;
	}
}