package photosintext;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.PaintObjectEvent;
import org.eclipse.swt.custom.PaintObjectListener;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.custom.StyledTextUtil;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DragSource;
import org.eclipse.swt.dnd.DragSourceAdapter;
import org.eclipse.swt.dnd.DragSourceEvent;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetAdapter;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.events.VerifyListener;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.GlyphMetrics;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.cgoab.offline.ui.thumbnailviewer.CachingThumbnailProvider;
import com.cgoab.offline.ui.thumbnailviewer.FitWithinResizeStrategy;

public class EmbeddedImages implements PaintObjectListener, VerifyListener, DisposeListener {

	private StyledText styledText;

	public static final String TEXT = "After the obligatory photo of the huge dump truck in Sparwood I loaded up on groceries and"
			+ " headed towards Elkford where I'd rejoin the GDMBR for the final approach to Banff. No one in the gas station had taken "
			+ "the dirt road out of Elkford (one of only 2 roads in the town!). Unsure about water supply along the road I filled up with "
			+ "4l water. At $20 the campsite was too expensive so I pushed on hoping to find a bit of land to camp. I needn't have worried. "
			+ "As soon as I was on the dirt road up to Elk pass I was into wilderness. About 5km later I set up camp on a wide open grassy area. "
			+ "The people I spoke with in the gas station had tried to scare me with stories of Grizzly bears roaming this valley. So it seemed "
			+ "prudent to hang my food from a tree!.\n\nI woke to another freezing morning but clear blue skies. The gamble had paid off.\n\nMy cold "
			+ "legs felt sluggish as I rode into the cold headwind towards Crowsnest pass. But the landscape was breathtaking. The day long rainfall in "
			+ "Pincher Creek had fallen as snow in the mountains.\n\nHuge wind farms lined the busy road to the pass. The pass was barely noticeable,"
			+ "only the change in province provided any clue I'd reached the summit.";

	Map<Integer, String> images = new HashMap<Integer, String>();

	int next;

	CachingThumbnailProvider provider;

	static String OBJECT_CODE = "\uFFFC";

	public EmbeddedImages(Composite parent) {
		final ExecutorService executor = Executors.newSingleThreadExecutor();
		provider = new CachingThumbnailProvider(executor, null, parent.getShell().getDisplay(),
				new FitWithinResizeStrategy(new Point(300, 200)));
		provider.setUseExifThumbnail(false);
		parent.getDisplay().addListener(SWT.Dispose, new Listener() {
			@Override
			public void handleEvent(Event event) {
				executor.shutdown();
			}
		});
		final Composite composite = new Composite(parent, SWT.NONE);
		composite.setLayout(new FillLayout());
		styledText = new StyledText(composite, SWT.WRAP | SWT.BORDER | SWT.V_SCROLL);
		styledText.setText(TEXT);
		// insertImages();
		styledText.setLayout(new GridLayout());
		styledText.addVerifyListener(this);
		styledText.addPaintObjectListener(this);
		styledText.addDisposeListener(this);
		styledText.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseDown(MouseEvent e) {
				int offset = StyledTextUtil.getOffsetAtPoint(styledText, e.x, e.y, new int[2], false);
				try {
					if (OBJECT_CODE.equals(styledText.getTextRange(offset, 1))) {
						styledText.setSelectionRange(offset, 1);
					}
				} catch (Exception ex) {
					System.out.println("ERROR: offset = " + offset);
				}
			}
		});

		DropTarget dropTarget = new DropTarget(styledText, DND.DROP_MOVE | DND.DROP_LINK | DND.DROP_COPY);
		dropTarget.setDropTargetEffect(new StyledTextDropLineTargetEffect(styledText));
		dropTarget.setTransfer(new Transfer[] { FileTransfer.getInstance(), MyTransfer.getInstance() });
		dropTarget.addDropListener(new DropTargetAdapter() {
			@Override
			public void drop(DropTargetEvent e) {
				Point p = styledText.toControl(new Point(e.x, e.y));

				/* find the line we'll drop onto */
				int lineAtDrop = StyledTextDropLineTargetEffect.getLineAtDrop(styledText, p);
				int offsetOfLine = lineAtDrop == styledText.getLineCount() ? styledText.getCharCount() : styledText
						.getOffsetAtLine(lineAtDrop);
				log("line=", lineAtDrop, "offset=", offsetOfLine, "charCount=", styledText.getCharCount());
				if (MyTransfer.getInstance().isSupportedType(e.currentDataType)) {
					/* local transfer */
					Data data = (Data) MyTransfer.getInstance().getSelectedPhoto();
					if (data != null) {
						try {
							supressDispose = true;
							// TODO eat new-line
							styledText.replaceTextRange(data.offset, 1, "");
							styledText.setSelectionRange(offsetOfLine, 0);
							styledText.insert(OBJECT_CODE + "\n");
							addImage(data.image, offsetOfLine, 1, data.caption);
						} finally {
							supressDispose = false;
						}
					}
					return;
				} else if (!FileTransfer.getInstance().isSupportedType(e.currentDataType)) {
					return;
				} else {
					for (String str : (String[]) e.data) {
						styledText.setSelectionRange(offsetOfLine, 0);
						styledText.insert(OBJECT_CODE + "\n");
						try {
							Image image = new Image(composite.getDisplay(), provider.get(new File(str), null, null)
									.get().imageData);
							addImage(image, offsetOfLine, 1, null);
						} catch (Exception ex) {
							ex.printStackTrace();
						}
					}
				}
			}
		});
		DragSource source = new DragSource(styledText, DND.DROP_MOVE);
		source.setTransfer(new Transfer[] { photosintext.MyTransfer.getInstance() });
		source.addDragListener(new DragSourceAdapter() {
			@Override
			public void dragStart(DragSourceEvent event) {
				int offset = styledText.getOffsetAtLocation(new Point(event.x, event.y));

				// if (styledText.getSelectionCount() == 1) {
				if (OBJECT_CODE.equals(styledText.getTextRange(offset, 1))) {
					StyleRange style = styledText.getStyleRangeAtOffset(offset);
					if (style instanceof ImageStyleRange) {
						ImageStyleRange range = (ImageStyleRange) style;
						photosintext.MyTransfer.getInstance().setSelectedPhoto(
								new Data(range.image, range.caption, offset));
						event.doit = true;
						System.out.println("dragStart()");
						return;
					}
				}
				// }
				event.doit = false;
			}

			@Override
			public void dragFinished(DragSourceEvent event) {
				photosintext.MyTransfer.getInstance().setSelectedPhoto(null);
			}
		});
	}

	static class Data {
		Image image;
		Text caption;
		int offset;

		public Data(Image image, Text caption, int offset) {
			this.image = image;
			this.caption = caption;
			this.offset = offset;
		}
	}

	public void widgetDisposed(DisposeEvent e) {
		for (StyleRange styleRange : styledText.getStyleRanges()) {
			if (styleRange instanceof ImageStyleRange) {
				ImageStyleRange imageStyleRange = (ImageStyleRange) styleRange;
				imageStyleRange.image.dispose();
			}
		}
	}

	static void log(Object... args) {
		System.out.print(Thread.currentThread().getStackTrace()[1].getMethodName());
		System.out.println(" " + Arrays.toString(args));
	}

	private void addImage(Image image, int offset, int length, Text caption) {
		log(offset, length, styledText.getCharCount());
		ImageStyleRange style;
		if (caption == null) {
			style = new ImageStyleRange(styledText, image);
		} else {
			style = new ImageStyleRange(styledText, image, caption);
		}
		style.start = offset;
		style.length = length;
		Rectangle rect = image.getBounds();
		style.metrics = new GlyphMetrics(rect.height + 10 + style.caption.getBounds().height, 0, rect.width + 10);
		styledText.setStyleRange(style);
	}

	/**
	 * Are there embedded images in the current style range? If so, paint them.
	 */
	public void paintObject(PaintObjectEvent event) {
		GC gc = event.gc;
		if (event.style instanceof ImageStyleRange) {
			ImageStyleRange imageStyleRange = (ImageStyleRange) event.style;
			int x = event.x;
			int y = event.y + event.ascent - imageStyleRange.metrics.ascent;
			gc.drawImage(imageStyleRange.image, x + 10, y);
			imageStyleRange.caption.setLocation(x + 10, y + imageStyleRange.image.getBounds().height + 5);
		}
	}

	/**
	 * Text is being removed: Dispose of any images embedded in that text.
	 */
	public void verifyText(VerifyEvent e) {
		StyleRange[] ranges = styledText.getStyleRanges(e.start, (e.end - e.start), true);
		for (StyleRange styleRange : ranges) {
			if (styleRange instanceof ImageStyleRange && !supressDispose) {
				ImageStyleRange imageStyleRange = (ImageStyleRange) styleRange;
				imageStyleRange.image.dispose();
				imageStyleRange.caption.dispose();
				System.out.println("!-disposed-!");
			}
		}
	}

	// private void insertImages() {
	// Display display = this.styledText.getDisplay();
	// Image[] images = new Image[] {
	// display.getSystemImage(SWT.ICON_INFORMATION),
	// display.getSystemImage(SWT.ICON_QUESTION), };
	// int lastOffset = 0;
	// for (int i = 0; i < images.length; i++) {
	// int offset = TEXT.indexOf("\uFFFC", lastOffset);
	// addImage(images[i], offset);
	// lastOffset = offset + 1;
	// }
	// }

	public static void main(String[] args) {
		final Display display = new Display();
		final Shell shell = new Shell(display);
		shell.setLayout(new FillLayout());

		new EmbeddedImages(shell);
		shell.setSize(600, 800);
		shell.open();
		while (!shell.isDisposed()) {
			if (!display.readAndDispatch())
				display.sleep();
		}
		display.dispose();
	}

	private static boolean supressDispose;

	private static class ImageStyleRange extends StyleRange {
		public Image image;
		public Text caption;

		public ImageStyleRange(StyledText parent, Image image) {
			this(parent, image, new Text(parent, SWT.MULTI | SWT.WRAP | SWT.BORDER), true);
		}

		public ImageStyleRange(final StyledText parent, Image image, final Text caption) {
			this(parent, image, caption, false);
		}

		ImageStyleRange(final StyledText parent, Image image, final Text caption, boolean defaultText) {
			this.image = image;
			this.caption = caption;
			Rectangle rectangle = image.getBounds();
			final int height = rectangle.height;
			final int width = rectangle.width;
			// caption.setLayoutData(new GridData(width, SWT.DEFAULT));
			caption.setSize(width, 20);
			if (defaultText) {
				caption.setBackground(parent.getShell().getDisplay().getSystemColor(SWT.COLOR_YELLOW));
				caption.setText("add caption");
			}
			caption.addListener(SWT.Modify, new Listener() {
				int old = -1;

				@Override
				public void handleEvent(Event event) {
					Point newSize = caption.computeSize(width, SWT.DEFAULT);
					if (newSize.y != old) {
						caption.setSize(width, newSize.y);
						metrics.ascent = height + 10 + caption.getBounds().height;
						parent.redraw();
						old = newSize.y;
					}
				}
			});
		}
	}
}