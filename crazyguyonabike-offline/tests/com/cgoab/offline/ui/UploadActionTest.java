package com.cgoab.offline.ui;

import static org.junit.Assert.*;

import org.junit.Test;

import com.cgoab.offline.model.Journal;
import com.cgoab.offline.model.Page;

public class UploadActionTest {
	@Test
	public void isContiguous() {
		Journal j = new Journal(null, "test");
		Page p1 = j.createNewPage();
		Page p2 = j.createNewPage();
		Page p3 = j.createNewPage();
		Page p4 = j.createNewPage();
		Page p5 = j.createNewPage();

		assertTrue(UploadAction.isContiguous(new Page[] { p1 }));
		assertTrue(UploadAction.isContiguous(new Page[] { p1, p2, p3 }));
		assertTrue(UploadAction.isContiguous(new Page[] { p1, p2, p3, p4, p5 }));

		assertTrue(UploadAction.isContiguous(new Page[] { p3 }));
		assertTrue(UploadAction.isContiguous(new Page[] { p2, p3, p4 }));

		assertFalse(UploadAction.isContiguous(new Page[] { p1, p5 }));
		assertFalse(UploadAction.isContiguous(new Page[] { p2, p4 }));
		assertFalse(UploadAction.isContiguous(new Page[] { p1, p3, p5 }));
		assertFalse(UploadAction.isContiguous(new Page[] { p1, p3, p4, p5 }));
	}
}
