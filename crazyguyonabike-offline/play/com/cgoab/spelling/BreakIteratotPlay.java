package com.cgoab.spelling;

import java.text.BreakIterator;

public class BreakIteratotPlay {

	public static void main(String[] args) {
		extractWords("hello world. This is a url http://www.foo isn't it nice.\n\nAnd here is another.",
				BreakIterator.getSentenceInstance());
	}

	static void extractWords(String target, BreakIterator wordIterator) {

		wordIterator.setText(target);
		int start = wordIterator.first();
		int end = wordIterator.next();

		while (end != BreakIterator.DONE) {
			String word = target.substring(start, end);
			if (Character.isLetterOrDigit(word.charAt(0))) {
				System.out.println("[" + word + "]");
			}
			start = end;
			end = wordIterator.next();
		}
	}
}
