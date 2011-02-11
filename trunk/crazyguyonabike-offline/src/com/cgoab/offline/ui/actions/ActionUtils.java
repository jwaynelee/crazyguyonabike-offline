package com.cgoab.offline.ui.actions;

import com.cgoab.offline.model.Page;

public class ActionUtils {
	/**
	 * Returns true if the selection is a non-empty array containing only
	 * {@link Page} objects.
	 * 
	 * @param selection
	 * @return
	 */
	public static boolean isNonEmptyPageArray(Object selection) {
		if (selection == null || !(selection instanceof Object[])) {
			return false;
		}
		Object[] items = (Object[]) selection;
		if (items.length == 0) {
			return false;
		}
		for (Object o : items) {
			if (!(o instanceof Page)) {
				return false;
			}
		}
		return true;
	}
}
