package com.cgoab.offline.ui.thumbnailviewer;

import static java.lang.Math.round;

import org.eclipse.swt.graphics.Point;

/**
 * Traditional resize strategy that fits both dimensions within the bounds.
 * 
 * Aspect ratio is preserved and no enlargement is done.
 */
public class FitWithinResizeStrategy implements ResizeStrategy {
	private Point bounds;

	public FitWithinResizeStrategy(Point bounds) {
		this.bounds = bounds;
	}

	public Point resize(Point origional) {
		float scaleX = (float) bounds.x / origional.x;
		float scaleY = (float) bounds.y / origional.y;

		// if (scaleX >= 1 && scaleY >= 1) {
		// return origional; // fits already
		// }

		if (scaleX * origional.y < bounds.y) {
			return new Point(round(scaleX * origional.x), round(scaleX * origional.y));
		} else {
			return new Point(round(scaleY * origional.x), round(scaleY * origional.y));
		}
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " [" + bounds.x + "x" + bounds.y + "]";
	}
}