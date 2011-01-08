package com.cgoab.offline.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;
import org.htmlcleaner.XPatherException;

/**
 * 1) download /doc/edit/page/pic and /doc/edit/page, build html-form, make sure
 * expected properties are in form, merge new ones and warn user if new/missing
 * params detected.
 */
public class FormFinder {
	private static void addAll(Object[] nodes, HtmlForm target) throws XPatherException {
		for (Object input : nodes) {
			if (input instanceof TagNode) {
				TagNode n = (TagNode) input;
				FormItem item = new FormItem();
				item.name = attributeIgnoreCase(n, "name");
				item.value = attributeIgnoreCase(n, "value");
				item.type = attributeIgnoreCase(n, "type");
				item.htmlTag = n.getName();
				if (n.getName().equalsIgnoreCase("select")) {
					// scoop up values
					for (Object o : n.evaluateXPath("//option")) {
						if (o instanceof TagNode) {
							TagNode no = (TagNode) o;
							String value = attributeIgnoreCase(no, "value");
							item.options.add(value);
							if (attributeIgnoreCase(no, "selected") != null) {
								item.value = value;
							}
						}
					}
					target.items.put(item.name, item);
				} else if (n.getName().equalsIgnoreCase("input") && item.name.equalsIgnoreCase("submit")) {
					FormItem submit = target.items.get("submit");
					if (submit != null) {
						submit.value = null;
						submit.options.add(item.value);
					} else {
						target.items.put(item.name, item);
					}
				} else {
					target.items.put(item.name, item);
				}
			}
		}
	}

	private static String attributeIgnoreCase(TagNode node, String name) {
		@SuppressWarnings("unchecked")
		Map<String, String> map = node.getAttributes();
		for (Map.Entry<String, String> s : map.entrySet()) {
			if (s.getKey().equalsIgnoreCase(name)) {
				return s.getValue();
			}
		}
		return null;
	}

	public static HtmlForm findFormWithName(TagNode root, String formName) throws XPatherException {
		Object[] forms = root.evaluateXPath("//form");
		for (Object object : forms) {
			if (object instanceof TagNode) {
				HtmlForm form = parse((TagNode) object);
				if (form.name != null && form.name.equals(formName)) {
					return form;
				}
			}
		}
		return null;
	}

	public static HtmlForm findFormWithAction(TagNode root, String action) throws XPatherException {
		Object[] forms = root.evaluateXPath("//form");
		for (Object object : forms) {
			if (object instanceof TagNode) {
				HtmlForm form = parse((TagNode) object);
				if (form.action.endsWith(action)) {
					return form;
				}
			}
		}
		return null;
	}

	public static void main(String[] args) throws Exception {
		HtmlCleaner cleaner = new HtmlCleaner();
		TagNode tag = cleaner.clean(FormFinder.class.getResourceAsStream("/EditPage.html"));
		System.out.println(FormFinder.findFormWithName(tag, "form"));

//		tag = new HtmlCleaner().clean(FormFinder.class.getResourceAsStream("/AddPhoto.html"));
//		System.out.println(findFormWithAction(tag, "doc/edit/page/pic/"));
	}

	public static HtmlForm parse(TagNode root) throws XPatherException {
		HtmlForm form = new HtmlForm();
		form.name = attributeIgnoreCase(root, "name");
		form.method = attributeIgnoreCase(root, "method");
		form.action = attributeIgnoreCase(root, "action");
		addAll(root.evaluateXPath("//input"), form);
		addAll(root.evaluateXPath("//textarea"), form);
		addAll(root.evaluateXPath("//select"), form);
		addAll(root.evaluateXPath("//button"), form);
		return form;
	}

	public static class FormItem {
		String htmlTag;
		String name;
		List<String> options = new ArrayList<String>();
		String type;
		String value;

		public String getHtmlTag() {
			return htmlTag;
		}

		public String getName() {
			return name;
		}

		public List<String> getOptions() {
			return options;
		}

		public String getType() {
			return type;
		}

		public String getValue() {
			return value;
		}

		@Override
		public String toString() {
			String s = String.format("%20s = '%s' (<%s/>", name, value == null ? "<null>" : value, htmlTag);
			if (options.size() > 0) {
				s += " " + options;
			}
			s = s + ") -- " + type;
			return s;
		}

	}

	public static class HtmlForm {
		private String action;
		private Map<String, FormItem> items = new HashMap<String, FormFinder.FormItem>();
		private String method;
		private String name;

		/**
		 * Returns name/value pairs to submit this form, defaults to those set
		 * on form and asserts each override property exists.
		 * 
		 * @param newFormProperties
		 * @return
		 */
		public Map<String, Object> newMergedProperties(Map<String, Object> overrides) {
			Map<String, Object> merged = new HashMap<String, Object>();
			overrides = new HashMap<String, Object>(overrides); /* copy */

			for (FormItem item : items.values()) {
				Object value = item.getValue();
				if (overrides.containsKey(item.name)) {
					value = overrides.remove(item.name);
				}
				merged.put(item.getName(), value);
			}

			if (overrides.size() > 0) {
				throw new IllegalArgumentException("Properties [" + overrides + "] do not exist on form");
			}

			return merged;
		}

		public String getAction() {
			return action;
		}

		public Map<String, FormItem> getItems() {
			return items;
		}

		public String getMethod() {
			return method;
		}

		public String getName() {
			return name;
		}

		@Override
		public String toString() {
			StringBuilder b = new StringBuilder("---------------------------------\n");
			b.append("form [action=");
			b.append(action).append(", method=");
			b.append(method).append(", name=");
			b.append(name).append("]\n---------------------------------\n");
			for (Entry<String, FormItem> item : items.entrySet()) {
				b.append(item.getValue()).append("\n");
			}
			return b.toString();
		}

	}
}
