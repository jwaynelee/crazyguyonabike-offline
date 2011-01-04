package play;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ExtendedModifyEvent;
import org.eclipse.swt.custom.ExtendedModifyListener;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import play.UndoManager.StyledTextEdit;

public class UndoRedoExample {

	private static Text undoinfo;
	private static StyledText text;
	private static Text redoinfo;
	private static UndoManager manager = new UndoManager();

	private static void modify(ExtendedModifyEvent e) {
		if (manager.isInRedo() || manager.isInUndo()) {
			return;
		}

		// find the text that was added...
		String addedText = "";
		if (e.length > 0) {
			addedText = text.getText(e.start, e.start + e.length - 1);
		}
		manager.addChange(new StyledTextEdit(text, e.start, e.length, e.replacedText, addedText, e.time));
	}

	private static void updateInfo() {
		undoinfo.setText(manager.toString());
	}

	public static void main(String[] args) {
		final Display d = new Display();
		Shell s = new Shell(d);
		s.setLayout(new GridLayout(2, true));
		d.addFilter(SWT.KeyDown, new Listener() {

			@Override
			public void handleEvent(Event arg0) {
				switch (arg0.keyCode) {
				case SWT.F1:
					manager.undo();
					break;
				case SWT.F2:
					manager.redo();
					break;
				default:
					break;
				}
			}
		});
		undoinfo = new Text(s, SWT.READ_ONLY | SWT.BORDER);
		undoinfo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

		redoinfo = new Text(s, SWT.READ_ONLY | SWT.BORDER);
		redoinfo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

		// final Text textBox = new Text(s, SWT.SINGLE | SWT.BORDER);
		// textBox.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false,
		// 2, 1));
		// textBox.addModifyListener(new ModifyListener() {
		// String previous = "";
		//
		// @Override
		// public void modifyText(ModifyEvent arg0) {
		// manager.addChange(new TextEdit(textBox,
		// previous,textBox.getSelection()));
		// previous = textBox.getText();
		// }
		// });

		text = new StyledText(s, SWT.NONE);
		text.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));
		text.addExtendedModifyListener(new ExtendedModifyListener() {
			@Override
			public void modifyText(ExtendedModifyEvent e) {
				modify(e);
			}
		});

		s.open();

		// loop
		s.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent arg0) {
				d.dispose();
			}
		});
		while (!d.isDisposed()) {
			if (!d.readAndDispatch()) {
				d.sleep();
			}
		}
	}
}
