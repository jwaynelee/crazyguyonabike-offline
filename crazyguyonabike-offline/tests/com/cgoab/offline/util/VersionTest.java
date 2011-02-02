package com.cgoab.offline.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VersionTest {

	@Test
	public void parse() {
		assertEquals(new Version(0), Version.parse("0"));
		assertEquals(new Version(0, 0), Version.parse("0.0"));
		assertEquals(new Version(0, 0, 0), Version.parse("0.0.0"));
		assertEquals(new Version(0, 0, 0, 0), Version.parse("0.0.0.0"));
		assertEquals(new Version(1, 2, 3, 99), Version.parse("1.2.3.99"));
	}

	@Test
	public void isNewerThanOrEqual() {
		/* equal */
		assertTrue(new Version(1).isGreaterThanOrEqual(new Version(1)));
		assertTrue(new Version(1, 0).isGreaterThanOrEqual(new Version(1, 0)));
		assertTrue(new Version(1, 0, 0).isGreaterThanOrEqual(new Version(1, 0, 0)));
		assertTrue(new Version(1, 0, 0, 0).isGreaterThanOrEqual(new Version(1, 0, 0, 0)));

		/* true */
		assertTrue(new Version(1).isGreaterThanOrEqual(new Version(1, 0)));
		assertTrue(new Version(1).isGreaterThanOrEqual(new Version(1, 1, 1)));
		assertTrue(new Version(1).isGreaterThanOrEqual(new Version(1, 1, 1, 1)));

		assertTrue(new Version(1, 1, 1, 1).isGreaterThanOrEqual(new Version(1)));
		assertTrue(new Version(1, 1, 0, 0).isGreaterThanOrEqual(new Version(1, 0, 9, 9)));

		/* false */
		assertFalse(new Version(1, 0, 0, 0).isGreaterThanOrEqual(new Version(1, 0, 0, 1)));
		assertFalse(new Version(1, 0, 0, 0).isGreaterThanOrEqual(new Version(1, 1)));
	}

	@Test
	public void isOlderThan() {
		assertTrue(new Version(1).isLessThan(new Version(2)));
		assertTrue(new Version(1).isLessThan(new Version(2, 0, 9, 11)));
		assertFalse(new Version(1).isLessThan(new Version(0, 9, 9, 11)));
	}
}
