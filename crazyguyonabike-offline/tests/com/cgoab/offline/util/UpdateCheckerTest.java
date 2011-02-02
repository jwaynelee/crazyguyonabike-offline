package com.cgoab.offline.util;

import nu.xom.Builder;
import nu.xom.Document;

import org.junit.Assert;
import org.junit.Test;

public class UpdateCheckerTest {
	@Test
	public void parse() throws Exception {
		Document xml = new Builder().build(UpdateCheckerTest.class.getResourceAsStream("downloads.atom.xml"));
		Assert.assertEquals(new Version(0, 1, 0), UpdateChecker.findLatestVersion(xml));
	}
}
