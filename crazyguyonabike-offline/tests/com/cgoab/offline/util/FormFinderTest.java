package com.cgoab.offline.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;

import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.XPatherException;
import org.junit.Test;

import com.cgoab.offline.util.FormFinder.HtmlForm;

public class FormFinderTest {
	@Test
	public void testEditPageForm() throws XPatherException, IOException {
		HtmlForm form = FormFinder.findFormWithName(
				new HtmlCleaner().clean(getClass().getResourceAsStream("EditPage.html")), "form");
		assertNotNull(form);
		assertEquals("POST", form.getMethod());
		// TODO check fields
	}

	@Test
	public void testAddPhotoForm() throws XPatherException, IOException {
		HtmlForm form = FormFinder.findFormWithName(
				new HtmlCleaner().clean(getClass().getResourceAsStream("AddPhoto.html")), "form");
		assertNotNull(form);
		assertEquals("POST", form.getMethod());
		// TODO check fields
	}
}
