package play;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DragSource;
import org.eclipse.swt.dnd.DragSourceAdapter;
import org.eclipse.swt.dnd.DragSourceEvent;
import org.eclipse.swt.dnd.DragSourceListener;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetEffect;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.DropTargetListener;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.ToolTip;

public class Scrolling {
	public static void main(String[] args) {
		final Display display = new Display();
		Shell shell = new Shell(display);
		shell.setLayout(new GridLayout());
		ScrolledComposite sc = new ScrolledComposite(shell, SWT.H_SCROLL);
		sc.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		sc.setAlwaysShowScrollBars(true);
		final Composite c = new Composite(sc, SWT.BORDER);
		c.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		RowLayout layout = new RowLayout();
		layout.spacing = 10;
		c.setLayout(layout);
		for (int i = 0; i < 10; ++i) {
			final Label label = new Label(c, SWT.BORDER);
			label.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseDown(MouseEvent e) {
					label.setBackground(display.getSystemColor(SWT.COLOR_BLUE));
					// label.moveAbove(c.getChildren()[0]);
					// c.layout();
				}

				@Override
				public void mouseUp(MouseEvent e) {
					label.setBackground(display.getSystemColor(SWT.COLOR_GRAY));
				}
			});
			// label.setToolTipText("Label # " + i);
			label.setText("Label:" + i);
			label.setDragDetect(true);
			setDragSource(label);
			setDropTarget(label);
			// label.setSize(100, 100);
			RowData d = new RowData(100, 100);
			label.setLayoutData(d);
		}
		c.pack();
		sc.setContent(c);
		Button button = new Button(shell, SWT.PUSH);
		button.setText("ADD");
		button.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				Label label = new Label(c, SWT.BORDER);
				label.setText("Label: ?");

				// label.setSize(100, 100);
				RowData d = new RowData(100, 100);
				label.setLayoutData(d);
				c.pack();
			}
		});
		// shell.pack();
		shell.open();

		// Set up the event loop.
		while (!shell.isDisposed()) {
			if (!display.readAndDispatch()) {
				// If no more entries in event queue
				display.sleep();
			}
		}

		display.dispose();
	}

	static Label currentLabel;

	public static void setDragSource(final Label label) {
		// Allows text to be moved only.
		int operations = DND.DROP_MOVE;
		final DragSource dragSource = new DragSource(label, operations);

		// Data should be transfered in plain text format.
		Transfer[] formats = new Transfer[] { TextTransfer.getInstance() };
		dragSource.setTransfer(formats);

		dragSource.addDragListener(new DragSourceListener() {
			public void dragStart(DragSourceEvent event) {
				// Disallows drags if text is not available.
				currentLabel = label;
				System.out.println("dragStart");
			}

			public void dragSetData(DragSourceEvent event) {
				// Provides the text data.
				System.out.println("dragSetData");

				if (TextTransfer.getInstance().isSupportedType(event.dataType)) {
					event.data = label.getText();
				}
			}

			public void dragFinished(DragSourceEvent event) {
				System.out.println("dragFinished");
			}
		});

		label.addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent e) {
				dragSource.dispose();
			}
		});
	}

	public static void setDropTarget(final Label label) {
		int operations = DND.DROP_MOVE;
		final DropTarget dropTarget = new DropTarget(label, operations);

		// Data should be transfered in plain text format.
		Transfer[] formats = new Transfer[] { TextTransfer.getInstance() };
		dropTarget.setTransfer(formats);
		dropTarget.setDropTargetEffect(new DropTargetEffect(label) {

		});
		dropTarget.addDropListener(new DropTargetListener() {
			public void dragEnter(DropTargetEvent event) {
				// Does not accept any drop if the label has text on it.
				System.out.println("dragEnter: ");
				if (currentLabel == label)
					event.detail = DND.DROP_NONE;
			}

			public void dragLeave(DropTargetEvent event) {
				System.out.println("dropLeave");
			}

			public void dragOperationChanged(DropTargetEvent event) {
			}

			public void dragOver(DropTargetEvent event) {
				event.feedback |= DND.FEEDBACK_SCROLL;
				System.out.println("dropOver");
			}

			public void drop(DropTargetEvent event) {
				System.out.println("drop");

				if (TextTransfer.getInstance().isSupportedType(event.currentDataType)) {
					currentLabel.moveAbove(label);
					currentLabel.getParent().layout();
				}
			}

			public void dropAccept(DropTargetEvent event) {
			}
		});

		label.addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent e) {
				dropTarget.dispose();
			}
		});
	}
}
