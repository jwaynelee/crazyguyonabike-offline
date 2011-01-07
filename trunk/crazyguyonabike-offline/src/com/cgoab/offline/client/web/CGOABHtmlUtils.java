package com.cgoab.offline.client.web;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.htmlcleaner.ContentToken;
import org.htmlcleaner.TagNode;
import org.htmlcleaner.XPatherException;

import com.cgoab.offline.client.DocumentDescription;
import com.cgoab.offline.client.DocumentType;

/**
 * Utilities to extract data from CGOAB html pages.
 */
public class CGOABHtmlUtils {

	private static final Pattern DOC_ID_PATTERN = Pattern.compile(".*doc_id=(\\d+).*");
	private static final Pattern PAGE_ID_PATTERN = Pattern.compile(".*page_id=(\\d+).*");

	private static String clean(String s) {
		s = s.replace("&nbsp;", " ");
		// s = StringEscapeUtils.unescapeHtml(s);
		return s.trim();
	}

	public static List<DocumentDescription> getDocuments(TagNode root) throws XPatherException {
		Object[] table = root.evaluateXPath("/body/table[5]/tbody/tr/td/table/tbody/tr");
		List<DocumentDescription> journals = new ArrayList<DocumentDescription>();
		// tr[1] is table header, tr[2] onwards are journal listings...
		for (int i = 1; i < table.length; ++i) {
			journals.add(createJournal((TagNode) table[i]));
		}
		return journals;
	}

	private static DocumentDescription createJournal(TagNode node) throws XPatherException {
		Object[] e = node.evaluateXPath("td");
		String typeString = clean(((TagNode) e[0]).getText().toString()).toUpperCase();
		TagNode anchorNode = ((TagNode) e[1]).getElementsByName("a", true)[0];
		String url = anchorNode.getAttributeByName("href");
		int id = CGOABHtmlUtils.getDocId(url);
		String title = clean(anchorNode.getText().toString());
		String status = clean(((TagNode) e[3]).getText().toString());
		String rawCount = clean(((TagNode) e[4]).getText().toString());
		rawCount = rawCount.replace(",", "");
		int hits = Integer.parseInt(rawCount);
		return new DocumentDescription(title, hits, status, id, DocumentType.valueOf(typeString));
	}

	public static int getPageId(String url) {
		Matcher match = PAGE_ID_PATTERN.matcher(url);
		if (!match.matches()) {
			throw new IllegalStateException("Could not find page_id in url '" + url + "'");
		}
		return Integer.parseInt(match.group(1));
	}

	public static int getDocId(String url) {
		Matcher match = DOC_ID_PATTERN.matcher(url);
		if (!match.matches()) {
			throw new IllegalStateException("Could not find page_id in url '" + url + "'");
		}
		return Integer.parseInt(match.group(1));
	}

	// TODO this is very fragile
	public static String getAddPageErrorMessage(TagNode root) {
		Object[] matches = matches(root, "//p['There were problems']");
		if (matches.length == 0) {
			return null;
		}
		matches = matches(((TagNode) matches[0]).getParent(), "//li/font[@color='red']");
		if (matches.length == 0) {
			return null;
		}

		String s = null;
		for (Object o : ((TagNode) matches[0]).getChildren()) {
			if (o instanceof ContentToken) {
				s = ((ContentToken) o).getContent().trim();
				break;
			}
		}

		return s;
	}

	public static String getUsernameFromMyAccount(TagNode root) {
		Object[] res = matches(root, "//input[@name='username']");
		if (res.length == 0) {
			return null;
		}
		return ((TagNode) res[0]).getAttributeByName("value");
	}

	public static String getRealnameFromMyAccount(TagNode root) {
		Object[] f = matches(root, "//input[@name='firstname']");
		if (f.length == 0) {
			return null;
		}
		String first = ((TagNode) f[0]).getAttributeByName("value");

		Object[] l = matches(root, "//input[@name='lastname']");
		if (l.length == 0) {
			return null;
		}
		String last = ((TagNode) l[0]).getAttributeByName("value");

		return first + " " + last;
	}

	private static Object[] EMPTY = new Object[0];

	private static Object[] matches(TagNode root, String string) {
		try {
			if (root == null) {
				return EMPTY;
			}
			return root.evaluateXPath(string);
		} catch (XPatherException e) {
			throw new RuntimeException(e);
		}
	}

	public static boolean isLoginPage(TagNode root) {
		try {
			return root.evaluateXPath("//h1['Login (or']").length > 0;
		} catch (XPatherException e) {
			throw new RuntimeException(e);
		}
	}

	// public static boolean isErrorPage(TagNode root) {
	// try {
	// return root.evaluateXPath("//h1['Error: Forbidden']").length > 0;
	// } catch (XPatherException e) {
	// throw new RuntimeException(e);
	// }
	// }

	public static boolean isUpdateAccountPage(TagNode root) {
		return matches(root, "//h1['Update Account']").length > 0;
	}
}
