package com.cgoab.offline.ui;

import static org.junit.Assert.assertEquals;

import org.eclipse.swt.graphics.Point;
import org.junit.Test;

import com.cgoab.offline.ui.thumbnailviewer.FitWithinResizeStrategy;
import com.cgoab.offline.ui.thumbnailviewer.ResizeStrategy;

public class FitWithinResizeStrategyTest {

	@Test
	public void testScaleToFitWithinSquare() {
		ResizeStrategy resizer = new FitWithinResizeStrategy(new Point(100, 100));

		// smaller
		assertEquals(new Point(50, 50), resizer.resize(new Point(50, 50)));

		// 1:1 aspect ratio
		assertEquals(new Point(100, 100), resizer.resize(new Point(100, 100)));
		assertEquals(new Point(100, 100), resizer.resize(new Point(501, 501)));

		// width > height
		assertEquals(new Point(100, 50), resizer.resize(new Point(2010, 1005)));
		assertEquals(new Point(100, 10), resizer.resize(new Point(2000, 200)));

		// heigh > width
		assertEquals(new Point(50, 100), resizer.resize(new Point(1005, 2010)));
		assertEquals(new Point(10, 100), resizer.resize(new Point(200, 2000)));
	}

	@Test
	public void testScaleToFitWithinRectangle() {

		ResizeStrategy resizer = new FitWithinResizeStrategy(new Point(150, 100));

		// smaller & one in one out
		assertEquals(new Point(10, 99), resizer.resize(new Point(10, 99)));
		assertEquals(new Point(10, 100), resizer.resize(new Point(50, 500)));
		// test rounding 19.8 -> 20
		assertEquals(new Point(20, 100), resizer.resize(new Point(99, 500)));

		// 1:1 aspect ratio
		assertEquals(new Point(150, 100), resizer.resize(new Point(150, 100)));
		assertEquals(new Point(150, 100), resizer.resize(new Point(1500, 1000)));

		// width > height
		assertEquals(new Point(150, 75), resizer.resize(new Point(2010, 1005)));
		assertEquals(new Point(150, 15), resizer.resize(new Point(2000, 200)));

		// heigh > width
		assertEquals(new Point(50, 100), resizer.resize(new Point(1005, 2010)));
		assertEquals(new Point(10, 100), resizer.resize(new Point(200, 2000)));
	}

}
