package com.cgoab.offline.ui.thumbnailviewer;

import org.eclipse.swt.graphics.Point;

/**
 * Strategy used to apply different resizing algorithms to images.
 */
public interface ResizeStrategy {
	/**
	 * Returns a point representing the resized dimensions.
	 * 
	 * @param actual
	 * @return
	 */
	public Point resize(Point actual);
}
