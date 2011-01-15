package com.cgoab.offline.model;

import java.io.File;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

public class JournalTest {
	@Test
	public void deletePhotos() throws Exception {
		Journal journal = new Journal(null, "test");
		Page page1 = new Page(journal);
		journal.addPage(page1);
		page1.addPhotos(Arrays.asList(new Photo(new File("a.jpg"))), -1);

		/* check photos added */
		Assert.assertTrue(journal.getPhotoMap().containsKey("a.jpg"));

		/* remove the page */
		journal.removePage(page1);

		/* check photos removed */
		Assert.assertFalse(journal.getPhotoMap().containsKey("a.jpg"));
	}
}
