package photosintext;

import java.io.File;
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
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetAdapter;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
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
import com.cgoab.offline.ui.thumbnailviewer.LocalThumbnailTransfer;

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
		DropTarget dt = new DropTarget(styledText, DND.DROP_MOVE | DND.DROP_LINK | DND.DROP_COPY);
		dt.setTransfer(new Transfer[] { FileTransfer.getInstance(), LocalThumbnailTransfer.getInstance() });
		dt.addDropListener(new DropTargetAdapter() {
			@Override
			public void drop(DropTargetEvent e) {
				if (!FileTransfer.getInstance().isSupportedType(e.currentDataType)) {
					return;
				}
				Point control = styledText.toControl(new Point(e.x, e.y));
				int insetionOffest = StyledTextUtil.getOffsetAtPoint(styledText, control.x, control.y, new int[1],
						false);
				for (String str : (String[]) e.data) {
					// images.put(next++, str);
					// inserts.append("\uFFFC\n");
					styledText.setSelectionRange(insetionOffest, 0);
					styledText.insert("\uFFFC\n");
					try {
						addImage(new Image(composite.getDisplay(),
								provider.get(new File(str), null, null).get().imageData), insetionOffest, 1);
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				}
				System.out.println(insetionOffest);
			}
		});
	}

	public void widgetDisposed(DisposeEvent e) {
		for (StyleRange styleRange : styledText.getStyleRanges()) {
			if (styleRange instanceof ImageStyleRange) {
				ImageStyleRange imageStyleRange = (ImageStyleRange) styleRange;
				imageStyleRange.image.dispose();
			}
		}
	}

	private void addImage(Image image, int offset, int length) {
		ImageStyleRange style = new ImageStyleRange(styledText, image);
		style.start = offset;
		style.length = length;
		Rectangle rect = image.getBounds();
		style.metrics = new GlyphMetrics(rect.height + 10 + style.caption.getBounds().height, 0, rect.width);
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
			gc.drawImage(imageStyleRange.image, x, y);
			imageStyleRange.caption.setLocation(x, y + imageStyleRange.image.getBounds().height + 5);
		}
	}

	/**
	 * Text is being removed: Dispose of any images embedded in that text.
	 */
	public void verifyText(VerifyEvent e) {
		StyleRange[] ranges = styledText.getStyleRanges(e.start, (e.end - e.start), true);
		for (StyleRange styleRange : ranges) {
			if (styleRange instanceof ImageStyleRange) {
				ImageStyleRange imageStyleRange = (ImageStyleRange) styleRange;
				imageStyleRange.image.dispose();
				imageStyleRange.caption.dispose();
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

	private static class ImageStyleRange extends StyleRange {
		public Image image;
		public Text caption;

		public ImageStyleRange(StyledText parent, Image image) {
			this.image = image;
			caption = new Text(parent, SWT.SINGLE | SWT.BORDER);
			int width = image.getBounds().width;
			//caption.setLayoutData(new GridData(width, SWT.DEFAULT));
			caption.setSize(width, 20);
			caption.setText("add caption");
			//caption.pack();
		}
	}
}