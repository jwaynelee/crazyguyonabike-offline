package org.eclipse.swt.custom;

/**
 * Exposes some internal methods
 */
public class StyledTextUtil {
	public static int getOffsetAtPoint(StyledText text, int x, int y, int[] trailing, boolean inTextOnly) {
		return text.getOffsetAtPoint(x, y, trailing, inTextOnly);
	}
}
