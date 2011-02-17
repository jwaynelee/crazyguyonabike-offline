package photosintext;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.custom.StyledTextUtil;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTargetAdapter;
import org.eclipse.swt.dnd.DropTargetEffect;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.graphics.FontMetrics;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;

/**
 * This adapter class provides a default drag under effect (eg. select and
 * scroll) when a drag occurs over a <code>StyledText</code>.
 * 
 * <p>
 * Classes that wish to provide their own drag under effect for a
 * <code>StyledText</code> can extend this class, override the
 * <code>StyledTextDropTargetEffect.dragOver</code> method and override any
 * other applicable methods in <code>StyledTextDropTargetEffect</code> to
 * display their own drag under effect.
 * </p>
 * 
 * Subclasses that override any methods of this class should call the
 * corresponding <code>super</code> method to get the default drag under effect
 * implementation.
 * 
 * <p>
 * The feedback value is either one of the FEEDBACK constants defined in class
 * <code>DND</code> which is applicable to instances of this class, or it must
 * be built by <em>bitwise OR</em>'ing together (that is, using the
 * <code>int</code> "|" operator) two or more of those <code>DND</code> effect
 * constants.
 * </p>
 * <p>
 * <dl>
 * <dt><b>Feedback:</b></dt>
 * <dd>FEEDBACK_SELECT, FEEDBACK_SCROLL</dd>
 * </dl>
 * </p>
 * 
 * @see DropTargetAdapter
 * @see DropTargetEvent
 * @see <a href="http://www.eclipse.org/swt/">Sample code and further
 *      information</a>
 * 
 * @since 3.3
 */
public class StyledTextDropLineTargetEffect extends DropTargetEffect {
	static final int CARET_WIDTH = 2;
	static final int SCROLL_HYSTERESIS = 100; // milli seconds
	static final int SCROLL_TOLERANCE = 20; // pixels

	// model line under mouse
	// 0 == above first line
	// 1 == below first line
	// ...
	// N == below last line
	int currentLine = -1;
	long scrollBeginTime;
	int scrollY = -1;
	Listener paintListener;

	/**
	 * Creates a new <code>StyledTextDropTargetEffect</code> to handle the drag
	 * under effect on the specified <code>StyledText</code>.
	 * 
	 * @param styledText
	 *            the <code>StyledText</code> over which the user positions the
	 *            cursor to drop the data
	 */
	public StyledTextDropLineTargetEffect(StyledText styledText) {
		super(styledText);
		paintListener = new Listener() {
			public void handleEvent(Event event) {
				if (currentLine != -1) {
					StyledText text = (StyledText) getControl();
					int width = text.getSize().x;
					int y;
					if (currentLine == text.getLineCount()) {
						/* beyond the last line */
						y = text.getLocationAtOffset(text.getCharCount()).y + text.getLineHeight();
					} else {
						int offsetAtLine = text.getOffsetAtLine(currentLine);
						y = text.getLocationAtOffset(offsetAtLine).y;
					}
					event.gc.setBackground(event.display.getSystemColor(SWT.COLOR_BLACK));
					event.gc.fillRectangle(0, y, width, 1);
				}
			}
		};
	}

	/**
	 * This implementation of <code>dragEnter</code> provides a default drag
	 * under effect for the feedback specified in <code>event.feedback</code>.
	 * 
	 * For additional information see <code>DropTargetAdapter.dragEnter</code>.
	 * 
	 * Subclasses that override this method should call
	 * <code>super.dragEnter(event)</code> to get the default drag under effect
	 * implementation.
	 * 
	 * @param event
	 *            the information associated with the drag start event
	 * 
	 * @see DropTargetAdapter
	 * @see DropTargetEvent
	 */
	public void dragEnter(DropTargetEvent event) {
		currentLine = -1;
		scrollBeginTime = 0;
		scrollY = -1;
		getControl().removeListener(SWT.Paint, paintListener);
		getControl().addListener(SWT.Paint, paintListener);
	}

	/**
	 * This implementation of <code>dragLeave</code> provides a default drag
	 * under effect for the feedback specified in <code>event.feedback</code>.
	 * 
	 * For additional information see <code>DropTargetAdapter.dragLeave</code>.
	 * 
	 * Subclasses that override this method should call
	 * <code>super.dragLeave(event)</code> to get the default drag under effect
	 * implementation.
	 * 
	 * @param event
	 *            the information associated with the drag leave event
	 * 
	 * @see DropTargetAdapter
	 * @see DropTargetEvent
	 */
	public void dragLeave(DropTargetEvent event) {
		StyledText text = (StyledText) getControl();
		if (currentLine != -1) {
			refreshCaret(text, currentLine, -1);
		}
		text.removeListener(SWT.Paint, paintListener);
		scrollBeginTime = 0;
		scrollY = -1;
	}

	/**
	 * This implementation of <code>dragOver</code> provides a default drag
	 * under effect for the feedback specified in <code>event.feedback</code>.
	 * 
	 * For additional information see <code>DropTargetAdapter.dragOver</code>.
	 * 
	 * Subclasses that override this method should call
	 * <code>super.dragOver(event)</code> to get the default drag under effect
	 * implementation.
	 * 
	 * @param event
	 *            the information associated with the drag over event
	 * 
	 * @see DropTargetAdapter
	 * @see DropTargetEvent
	 * @see DND#FEEDBACK_SELECT
	 * @see DND#FEEDBACK_SCROLL
	 */
	public void dragOver(DropTargetEvent event) {
		int effect = event.feedback;
		StyledText text = (StyledText) getControl();

		Point pt = text.getDisplay().map(null, text, event.x, event.y);
		if ((effect & DND.FEEDBACK_SCROLL) == 0) {
			scrollBeginTime = 0;
			scrollY = -1;
		} else {
			if (text.getCharCount() == 0) {
				scrollBeginTime = 0;
				scrollY = -1;
			} else {
				if (scrollY != -1 && scrollBeginTime != 0 && (pt.y >= scrollY && pt.y <= (scrollY + SCROLL_TOLERANCE))) {
					if (System.currentTimeMillis() >= scrollBeginTime) {
						Rectangle area = text.getClientArea();
						GC gc = new GC(text);
						FontMetrics fm = gc.getFontMetrics();
						gc.dispose();
						int charWidth = fm.getAverageCharWidth();
						int scrollAmount = 10 * charWidth;
						if (pt.x < area.x + 3 * charWidth) {
							int leftPixel = text.getHorizontalPixel();
							text.setHorizontalPixel(leftPixel - scrollAmount);
						}
						if (pt.x > area.width - 3 * charWidth) {
							int leftPixel = text.getHorizontalPixel();
							text.setHorizontalPixel(leftPixel + scrollAmount);
						}
						int lineHeight = text.getLineHeight();
						if (pt.y < area.y + lineHeight) {
							int topPixel = text.getTopPixel();
							text.setTopPixel(topPixel - lineHeight);
						}
						if (pt.y > area.height - lineHeight) {
							int topPixel = text.getTopPixel();
							text.setTopPixel(topPixel + lineHeight);
						}
						scrollBeginTime = 0;
						scrollY = -1;
					}
				} else {
					scrollBeginTime = System.currentTimeMillis() + SCROLL_HYSTERESIS;
					scrollY = pt.y;
				}
			}
		}

		if ((effect & DND.FEEDBACK_SELECT) != 0) {
			int newLine = getLineAtDrop(text, pt);
			if (newLine != currentLine) {
				refreshCaret(text, 0, 1);
				currentLine = newLine;
			}
		}
	}

	static int getLineAtDrop(StyledText text, Point p) {
		int[] trailing = new int[1];
		int offsetAtPoint = StyledTextUtil.getOffsetAtPoint(text, p.x, p.y, trailing, false);
		int modelLine = text.getLineAtOffset(offsetAtPoint);

		/* computer if position.y is above or below centre line */
		int lineStart = text.getOffsetAtLine(modelLine);
		int top = text.getLocationAtOffset(lineStart).y;
		int bottom = text.getLocationAtOffset(lineStart + text.getLine(modelLine).length()).y + text.getLineHeight();
		float middle = top + ((float) bottom - (float) top) / 2;
		return p.y > middle ? modelLine + 1 : modelLine;
	}

	void refreshCaret(StyledText text, int oldOffset, int newOffset) {
		if (oldOffset != newOffset) {
			// if (oldOffset != -1) {
			// Point oldPos = text.getLocationAtOffset(oldOffset);
			// int oldHeight = text.getLineHeight(oldOffset);
			// text.redraw(oldPos.x, oldPos.y, CARET_WIDTH, oldHeight, false);
			// }
			// if (newOffset != -1) {
			// Point newPos = text.getLocationAtOffset(newOffset);
			// int newHeight = text.getLineHeight(newOffset);
			// text.redraw(newPos.x, newPos.y, CARET_WIDTH, newHeight, false);
			// }
			text.redraw();
		}
	}

	/**
	 * This implementation of <code>dropAccept</code> provides a default drag
	 * under effect for the feedback specified in <code>event.feedback</code>.
	 * 
	 * For additional information see <code>DropTargetAdapter.dropAccept</code>.
	 * 
	 * Subclasses that override this method should call
	 * <code>super.dropAccept(event)</code> to get the default drag under effect
	 * implementation.
	 * 
	 * @param event
	 *            the information associated with the drop accept event
	 * 
	 * @see DropTargetAdapter
	 * @see DropTargetEvent
	 */
	public void dropAccept(DropTargetEvent event) {
		if (currentLine != -1) {
			StyledText text = (StyledText) getControl();
			// text.setSelection(text.getOffsetAtLine(currentLine));
			currentLine = -1;
		}
	}
}
