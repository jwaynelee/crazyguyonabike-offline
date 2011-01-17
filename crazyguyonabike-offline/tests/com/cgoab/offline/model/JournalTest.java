package com.cgoab.offline.model;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.Arrays;

import javax.sql.rowset.Joinable;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.joda.time.LocalDate;
import org.junit.Assert;
import org.junit.Test;

public class JournalTest {

	Journal journal = new Journal(null, "test");

	@Test
	public void addPage() {
		final Page page = new Page(journal);
		Mockery context = new Mockery();
		final JournalListener listener = context.mock(JournalListener.class);
		context.checking(new Expectations() {
			{
				oneOf(listener).pageAdded(page);
				oneOf(listener).journalDirtyChange();
			}
		});
		journal.addJournalListener(listener);

		journal.addPage(page);
		Assert.assertEquals(page, journal.getPages().get(0));
		context.assertIsSatisfied();
	}

	@Test
	public void createPage() {
		Mockery context = new Mockery();
		final JournalListener listener = context.mock(JournalListener.class);
		context.checking(new Expectations() {
			{
				oneOf(listener).pageAdded(with(any(Page.class)));
				oneOf(listener).journalDirtyChange();
			}
		});
		journal.addJournalListener(listener);
		Page page = journal.createNewPage();
		context.assertIsSatisfied();
		assertEquals(page, journal.getPages().get(0));
	}

	@Test
	public void createPage_inheritPreviousPageSettings() {
		final Page page = journal.createNewPage();
		page.setTitle("Day 22");
		page.setDate(new LocalDate(2011, 01, 01));
		page.setBold(true);
		page.setItalic(true);
		page.setIndent(3);

		Mockery context = new Mockery();
		final JournalListener listener = context.mock(JournalListener.class);
		context.checking(new Expectations() {
			{
				oneOf(listener).pageAdded(with(any(Page.class)));
				oneOf(listener).journalDirtyChange();
			}
		});
		journal.addJournalListener(listener);
		journal.createNewPage();
		context.assertIsSatisfied();
		Page newPage = journal.getPages().get(1);
		assertEquals("Day 23", newPage.getTitle());
		assertEquals(new LocalDate(2011, 01, 02), newPage.getDate());
		assertEquals(true, newPage.isBold());
		assertEquals(true, newPage.isItalic());
		assertEquals(3, newPage.getIndent());
	}

	@Test
	public void removePage_deletesPhotos() throws Exception {
		Page page = new Page(journal);
		journal.addPage(page);
		page.addPhotos(Arrays.asList(new Photo(new File("a.jpg"))), -1);

		/* check photos added */
		Assert.assertTrue(journal.getPhotoMap().containsKey("a.jpg"));

		/* remove the page */
		journal.removePage(page);

		/* check photos removed */
		Assert.assertFalse(journal.getPhotoMap().containsKey("a.jpg"));
	}
}
