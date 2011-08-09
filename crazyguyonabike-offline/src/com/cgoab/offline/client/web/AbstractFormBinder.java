package com.cgoab.offline.client.web;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.cgoab.offline.util.FormFinder.FormItem;
import com.cgoab.offline.util.FormFinder.HtmlForm;

public abstract class AbstractFormBinder {

	public static String toOnOff(boolean value) {
		return value ? "1" : "0";
	}

	private final Map<String, Expected> expected = new HashMap<String, Expected>();

	private HtmlForm form;

	private final String formName;

	public AbstractFormBinder(String name) {
		this.formName = name;
	}

	/**
	 * Initializes the binder using the given HTML form, returns <tt>true</tt>
	 * if initilization was OK or encountered only warnings, <tt>false</tt> if
	 * fatal.
	 * 
	 * @param form
	 * @param errorCollector
	 * @return
	 */
	public boolean initialize(HtmlForm form, List<String> errorCollector) {
		// 1) check fields
		boolean fatal = false;
		Map<String, FormItem> remainingItems = new HashMap<String, FormItem>(form.getItems());
		for (Entry<String, Expected> e : expected.entrySet()) {
			String name = e.getKey();
			FormItem item = remainingItems.remove(name);
			if (item == null) {
				if (e.getValue().used) /* used */{
					fatal = true;
					errorCollector.add("ERROR: [" + name + "] but missing on " + formName);
				} else /* unused */{
					errorCollector.add("WARNING: [" + name + "] missing on " + formName);

				}
			} else {
				String expectedValue = e.getValue().value;
				if (expectedValue != null) {
					List<String> options = item.getOptions();
					if (options == null) {
						/* error */
						fatal = true;
						errorCollector.add("ERROR: No options for [" + name + "] on " + formName);
					} else {
						boolean found = false;
						for (String option : options) {
							if (option.equals(expectedValue)) {
								found = true;
								break;
							}
						}
						if (!found) {
							errorCollector.add("ERROR: No value [" + expectedValue + "] for [" + name + "] on "
									+ formName + " (options " + options + ")");
							fatal = true;
						}
					}
				}
			}
		}

		if (remainingItems.size() > 0) {
			for (Entry<String, FormItem> e : remainingItems.entrySet()) {
				errorCollector.add("WARNING: [" + e.getKey() + "] new on " + formName);
			}
		}

		if (!fatal) {
			/* initialized */
			this.form = form;
		}

		return !fatal;
	}

	public boolean isInitialized() {
		return form != null;
	}

	protected ParamaterBuilder newCollector() {
		if (form == null) {
			throw new IllegalStateException("Binder must be connected to a form first!");
		}
		final Map<String, Object> properties = form.newDefaultProperties();
		ParamaterBuilder c = new ParamaterBuilder() {
			private void checkDeclared(String name, Object value) {
				/* check the property was declared and exists */
				Expected e = expected.get(name);
				if (e == null) {
					throw new IllegalStateException("Field [" + name + "] not declared");
				}
				if (value != null && e.value != null && !e.value.equals(value)) {
					throw new IllegalStateException("Field [" + name + "] option [" + value + "] not declared");
				}
			}

			@Override
			public Map<String, Object> getMap() {
				return properties;
			}

			@Override
			public void put(String name, Object value) {
				checkDeclared(name, value);
				properties.put(name, value);
			}

			@Override
			public void remove(String name) {
				checkDeclared(name, null);
				properties.remove(name);
			}
		};
		return c;
	}

	protected void registerUnusedProperty(String name) {
		throwIfInitialized();
		expected.put(name, new Expected(false));
	}

	protected void registerUsedProperty(String name) {
		throwIfInitialized();
		expected.put(name, new Expected(true));
	}

	protected void registerUsedPropertyOption(String name, String value) {
		throwIfInitialized();
		expected.put(name, new Expected(value));
	}

	private void throwIfInitialized() {
		if (isInitialized()) {
			throw new IllegalStateException("Already initialized!");
		}
	}

	private static class Expected {
		boolean used;
		String value;

		public Expected(boolean used) {
			this.used = used;
		}

		public Expected(String value) {
			this.value = value;
			this.used = true;
		}
	}

	public static class InitializationErrorException extends InitializationException {
		private static final long serialVersionUID = 1L;

		public InitializationErrorException(String message) {
			super(message);
		}
	}

	public static class InitializationException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		public InitializationException(String message) {
			super(message);
		}
	}

	public static class InitializationWarningException extends InitializationException {
		private static final long serialVersionUID = 1L;

		public InitializationWarningException(String message) {
			super(message);
		}
	}

	public interface ParamaterBuilder {
		Map<String, Object> getMap();

		void put(String name, Object value);

		void remove(String string);
	}
}