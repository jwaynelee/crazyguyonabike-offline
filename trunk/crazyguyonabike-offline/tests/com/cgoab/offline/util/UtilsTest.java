package com.cgoab.offline.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class UtilsTest {
	@Test
	public void testFormatBytes() {
		assertEquals("5b", Utils.formatBytes(5));
		assertEquals("1020b", Utils.formatBytes(1020));
		assertEquals("1kb", Utils.formatBytes(1 * 1024));
		assertEquals("60kb", Utils.formatBytes(60 * 1024 + 50));
		assertEquals("1mb", Utils.formatBytes(1024 * 1024));
		assertEquals("1.4mb", Utils.formatBytes((1024 * 1024) + (410 * 1024)));
		assertEquals("41.8mb", Utils.formatBytes(41 * (1024 * 1024) + 789 * 1024));
		assertEquals("142mb", Utils.formatBytes(142 * (1024 * 1024)));
	}
}
