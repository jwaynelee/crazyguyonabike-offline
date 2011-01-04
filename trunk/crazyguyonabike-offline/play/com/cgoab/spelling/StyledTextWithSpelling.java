package com.cgoab.spelling;

import java.io.IOException;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CaretEvent;
import org.eclipse.swt.custom.CaretListener;
import org.eclipse.swt.custom.LineStyleEvent;
import org.eclipse.swt.custom.LineStyleListener;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.MenuDetectEvent;
import org.eclipse.swt.events.MenuDetectListener;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.events.MenuListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;

// TODO detect when cursor moves and re-check last suppressed edit
public class StyledTextWithSpelling implements LineStyleListener, MenuDetectListener, MenuListener {

	private Shell shell;

	private Dictionary dictionary;

	private StyledText text;

	private BreakIterator bi = BreakIterator.getWordInstance();

	private Color red;

	private List<String> suggestions = new ArrayList<String>();

	private int offset, length;

	private SupressedCheck check;

	static class SupressedCheck {
		int start;
		int end;
		int line;
	}

	private static Dictionary loadDictionary() {
		DictionaryFactory df = new DictionaryFactory();
		try {
			df.loadWordList(StyledTextWithSpelling.class.getResource("dictionary_en.ortho"));
		} catch (IOException e) {
			throw new RuntimeException("Could not load dictionary", e);
		}
		return df.create();
	}

	public StyledTextWithSpelling(Shell s) {
		shell = s;
		dictionary = loadDictionary();
		text = new StyledText(shell, SWT.WRAP);
		text.setText("A line");
		text.addLineStyleListener(this);
		text.addMenuDetectListener(this);
		text.addCaretListener(new CaretListener() {
			@Override
			public void caretMoved(CaretEvent event) {

			}
		});
		Menu menu = new Menu(text);
		menu.addMenuListener(this);
		text.setMenu(menu);
		red = shell.getDisplay().getSystemColor(SWT.COLOR_RED);
	}

	private static final void log(String fmt, Object... args) {
	//	System.out.printf(fmt + "\n", args);
	}

	@Override
	public void lineGetStyle(LineStyleEvent e) {
		StyleRange[] styles = checkSpellingOfLine(e.lineOffset, e.lineText, text.getCaretOffset() - e.lineOffset);
		e.styles = styles;
	}

	@Override
	public void menuHidden(MenuEvent e) {
	}

	@Override
	public void menuShown(MenuEvent e) {
		/* cleanup previous items */
		for (MenuItem item : text.getMenu().getItems()) {
			item.dispose();
		}

		if (suggestions.size() > 0) {
			for (final String suggestion : suggestions) {
				MenuItem item = new MenuItem(text.getMenu(), SWT.POP_UP);
				item.setText(suggestion);
				item.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						text.replaceTextRange(offset, length, suggestion);
					}
				});
			}
		}
	}

	@Override
	public void menuDetected(MenuDetectEvent e) {
		suggestions.clear();

		int offsetInDoc;
		try {
			offsetInDoc = text.getOffsetAtLocation(shell.getDisplay().map(null, text, new Point(e.x, e.y)));
		} catch (IllegalArgumentException ex) {
			// throw when we don't land on a line of text
			return;
		}
		int line = text.getLineAtOffset(offsetInDoc);
		String lineString = text.getLine(line);
		bi.setText(lineString);
		log("needle=%d, line=%s, text=%s", offsetInDoc, line, lineString);
		int startOfLine = text.getOffsetAtLine(line);
		int offsetInLine = offsetInDoc - startOfLine;
		int wordStart = bi.preceding(offsetInLine);
		int wordEnd = bi.following(offsetInLine);
		if (wordStart == BreakIterator.DONE || wordEnd == BreakIterator.DONE) {
			return;
		}
		String word = lineString.substring(wordStart, wordEnd);
		if (Character.isLetterOrDigit(word.charAt(0))) {
			offset = startOfLine + wordStart;
			length = wordEnd - wordStart;
			for (Suggestion s : dictionary.searchSuggestions(word)) {
				suggestions.add(s.getWord());
			}
		}
	}

	private boolean exists(String word, SpellCheckerOptions options, boolean firstInSentence) {
		if (dictionary.exist(word)) {
			return true;
		}

		if (!options.isCaseSensitive()) {
			if (dictionary.exist(Utils.getInvertedCapitalizion(word))) {
				return true;
			}
		} else if (firstInSentence || options.getIgnoreCapitalization() && Character.isUpperCase(word.charAt(0))) {
			// Uppercase check on starting of sentence
			String capitalizeWord = word.substring(0, 1).toLowerCase() + word.substring(1);
			if (dictionary.exist(capitalizeWord)) {
				return true;
			}
		}

		if (options.isIgnoreAllCapsWords() && Utils.isAllCapitalized(word)) {
			return true;
		}

		if (options.isIgnoreWordsWithNumbers() && Utils.isIncludeNumbers(word)) {
			return true;
		}

		return false;
	}

	private SpellCheckerOptions options = new SpellCheckerOptions();

	private StyleRange[] checkSpellingOfLine(int lineStart, String lineString, int lineEditLocation) {
		bi.setText(lineString);
		List<StyleRange> styles = new ArrayList<StyleRange>();
		int start = bi.first();
		check = null;
		for (int end = bi.next(); end != BreakIterator.DONE; start = end, end = bi.next()) {
			// ignore the current word, but schedule a check if the caret moves
			log("[%d to %d], edit location @%d", start, end, lineEditLocation);
			if (lineEditLocation <= end && lineEditLocation >= start) {
				check = new SupressedCheck();
				check.start = lineStart + start;
				check.end = lineStart + end;
				continue;
			}

			String word = lineString.substring(start, end);
			if (word.length() > 2 && Character.isLetterOrDigit(word.charAt(0))) {
				log("checking '%s' [%d to %d]", word, start, end);
				if (!exists(word, options, false)) {
					// log("does not exist");
					// manually search for a URL

					// highlight
					StyleRange r = new StyleRange();
					r.start = lineStart + start;
					r.length = end - start;
					r.underlineColor = red;
					r.underlineStyle = SWT.UNDERLINE_SQUIGGLE;
					r.underline = true;
					styles.add(r);
				}
			}
		}

		// System.out.println(styles);
		// StyleRange clear = new StyleRange();
		// clear.start = lineStart;
		// clear.length = lineString.length();
		// text.setStyleRanges(clear.start, clear.length, null, null);
		return styles.toArray(new StyleRange[0]);
	}

	public static void main(String[] args) throws IOException {
		Display d = new Display();
		Shell s = new Shell(d);
		s.setLayout(new FillLayout());
		new StyledTextWithSpelling(s);
		s.open();

		// event loop
		while (!s.isDisposed()) {
			if (!d.readAndDispatch()) {
				d.sleep();
			}
		}
		d.dispose();
	}
}
