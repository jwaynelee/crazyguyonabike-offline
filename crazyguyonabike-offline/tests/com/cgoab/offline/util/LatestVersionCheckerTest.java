package com.cgoab.offline.util;

import nu.xom.Builder;
import nu.xom.Document;

import org.junit.Assert;
import org.junit.Test;

public class LatestVersionCheckerTest {
	@Test
	public void parse() throws Exception {
		Document xml = new Builder().build(LatestVersionCheckerTest.class.getResourceAsStream("downloads.atom.xml"));
		Assert.assertEquals(new Version(0, 1, 0), LatestVersionChecker.extractLatestVersion(xml));
	}
}
