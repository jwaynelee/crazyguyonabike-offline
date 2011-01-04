package play;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Text;

public class UndoManager {

	private final List<UndoRedoStatusListener> listeners = new ArrayList<UndoRedoStatusListener>();
	private final Stack<ChangeHolder> undo = new Stack<ChangeHolder>();
	private final Stack<ChangeHolder> redo = new Stack<ChangeHolder>();
	private boolean inRedo, inUndo;

	public void redo() {
		assert inUndo = false;
		assert inRedo = false;
		if (!redo.isEmpty()) {
			try {
				inRedo = true;
				ChangeHolder c = redo.pop();
				c.pin = true;
				c.change.redo();
				undo.push(c);
			} finally {
				inRedo = false;
			}
			fireRedoListener();
		}
	}

	public void undo() {
		assert inUndo = false;
		assert inRedo = false;
		if (!undo.isEmpty()) {
			try {
				inUndo = true;
				ChangeHolder c = undo.pop();
				c.pin = true;
				c.change.undo();
				redo.push(c);
			} finally {
				inUndo = false;
			}
			fireUndoListener();
		}
	}

	public boolean isInRedo() {
		return inRedo;
	}

	public boolean isInUndo() {
		return inUndo;
	}

	public void addChange(Change change) {
		// usually we'll be adding to the undo stack, but if this edit
		// occured whilst we were "undoing" then it'll fall onto the redo stack
		// if (inUndo) {
		// redo.push(new ChangeHolder(change, true));
		// fireRedoListener();
		// } else if (inRedo) {
		// undo.push(new ChangeHolder(change, true));
		// fireUndoListener();
		// } else {
		// clear redo stack
		redo.clear();
		if (undo.isEmpty()) {
			undo.push(new ChangeHolder(change, false));
		} else {
			// attempt to merge with previous change
			ChangeHolder previous = undo.peek();
			if (previous.pin || !previous.change.merge(change)) {
				undo.push(new ChangeHolder(change, false));
			}
		}
		fireRedoListener();
		fireUndoListener();
		// }
	}

	private void fireUndoListener() {
		boolean available = !undo.isEmpty();
		for (UndoRedoStatusListener l : new ArrayList<UndoRedoStatusListener>(listeners)) {
			l.undoChange(available);
		}
	}

	private void fireRedoListener() {
		boolean available = !redo.isEmpty();
		for (UndoRedoStatusListener l : new ArrayList<UndoRedoStatusListener>(listeners)) {
			l.redoChange(available);
		}
	}

	public interface Change {
		void undo();

		void redo();

		boolean merge(Change change);
	}

	private static class ChangeHolder {
		Change change;
		boolean pin;

		public ChangeHolder(Change change, boolean pin) {
			this.change = change;
			this.pin = pin;
		}
	}

	public void addStatusListener(UndoRedoStatusListener listener) {
		listeners.add(listener);
	}

	// public static class TextEdit implements Change {
	// private Text text;
	// private String previous;
	// private Point selection;
	//
	// public TextEdit(Text text, String previous, Point selection) {
	// this.text = text;
	// this.previous = previous;
	// this.selection = selection;
	// System.out.println(selection);
	// }
	//
	// @Override
	// public void apply() {
	// text.setText(previous);
	// }
	//
	// @Override
	// public boolean merge(Change change) {
	// return false;
	// }
	// }

	public static class StyledTextEdit implements Change {
		// ms between edits
		private static final int PAUSE_THRESHOLD = 12000;
		private StyledText text;
		private int start, length;
		private String replacedText;
		private String addedText;
		private int time;
		private boolean undoAllowed = true;

		public StyledTextEdit(StyledText text, int start, int length, String replacedText, String addedText, int time) {
			this.text = text;
			this.start = start;
			this.length = length;
			this.replacedText = replacedText;
			this.time = time;
			this.addedText = addedText;
		}

		@Override
		public boolean merge(Change change) {
			if (!(change instanceof StyledTextEdit)) {
				return false;
			}

			StyledTextEdit newChange = (StyledTextEdit) change;
			if (newChange.text != text) {
				// not the same widget
				return false;
			}

			boolean intime = newChange.time - time < PAUSE_THRESHOLD;

			// 1) adding text to end of previous change
			if (intime) {
				if (length > 0 && "".equals(newChange.replacedText)) {
					if (start + length == newChange.start) {
						// merge
						addedText += newChange.addedText;
						length += newChange.length;
						time = newChange.time;
						return true;
					}
				}
				// 2) deleting
				if (length == 0 && newChange.length == 0) {
					if (newChange.start + newChange.replacedText.length() == start) {
						replacedText = newChange.replacedText + replacedText;
						start = newChange.start;
						time = newChange.time;
						return true;
					}
				}
			}

			return false;
		}

		@Override
		public void undo() {
			assert undoAllowed;
			undoAllowed = false;
			text.replaceTextRange(start, length, replacedText);
			text.setCaretOffset(computeOffset());
		}

		public void redo() {
			assert !undoAllowed;
			undoAllowed = true;
			text.replaceTextRange(start, 0, addedText);
			text.setCaretOffset(computeOffset());
		}

		private int computeOffset() {
			if ("".equals(replacedText)) {
				// deletion
				return start;// + edit.length;
			} else {
				// addition
				return start + replacedText.length();
			}
		}
	}

	public interface UndoRedoStatusListener {
		public void undoChange(boolean available);

		public void redoChange(boolean available);
	}
}