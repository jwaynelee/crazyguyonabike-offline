package com.cgoab.offline.client.web;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.XPatherException;
import org.junit.Assert;
import org.junit.Test;

import com.cgoab.offline.client.DocumentDescription;
import com.cgoab.offline.client.DocumentType;
import com.cgoab.offline.client.web.CGOABHtmlUtils;

public class CGOABHtmlScraperTest {

	File errorFile = new File("TestData/ErrorPage.htm");
	File homeFile = new File("TestData/HomePage.htm");
//	File loginFile = new File("TestData/LoginPage.htm");
	File userAccountFile = new File("TestData/UserAccountPage.htm");
	File addNewPageDateError = new File("TestData/ErrorAddNewPageDateOlderThanPrevious.htm");
	File errorAddSamePhotoName = new File("TestData/ErrorAddPhotoSameName.htm");
	HtmlCleaner cleaner = new HtmlCleaner();

	@Test
	public void getDocuments() throws Exception {
		List<DocumentDescription> documents = CGOABHtmlUtils.extractDocuments(cleaner.clean(getClass().getResourceAsStream("MyPage.htm")));
		DocumentDescription d1 = documents.get(0);
		Assert.assertEquals("TEST Journal", d1.getTitle());
		Assert.assertEquals(7953, d1.getDocumentId());
		Assert.assertEquals("Not yet published", d1.getStatus());
		Assert.assertEquals(DocumentType.JOURNAL, d1.getType());
		
		DocumentDescription d2 = documents.get(1);
		Assert.assertEquals("Ben Voyage", d2.getTitle());
		Assert.assertEquals(5221, d2.getDocumentId());
		Assert.assertEquals("Work in progress", d2.getStatus());
		Assert.assertEquals(DocumentType.JOURNAL, d2.getType());
		
		DocumentDescription d3 = documents.get(2);
		Assert.assertEquals("Dublin to Cork the Long Way", d3.getTitle());
		Assert.assertEquals(5192, d3.getDocumentId());
		Assert.assertEquals("Not yet published", d3.getStatus());
		Assert.assertEquals(DocumentType.JOURNAL, d3.getType());
	}

	@Test
	public void getDocId() {
		Assert.assertEquals(7953, CGOABHtmlUtils.getDocId("/doc/?o=RrzKj&doc_id=7953&v=14"));
	}

	@Test
	public void getPageId() {
		Assert.assertEquals(7953, CGOABHtmlUtils.getPageId("/doc/?o=RrzKj&page_id=7953&v=14"));
	}

	@Test
	public void getAddPhotoSameNameErrorMessage() throws IOException {
		Assert.assertEquals("That filename already exists! Please choose another name to save as.",
				CGOABHtmlUtils.getAddPageErrorMessage(cleaner.clean(errorAddSamePhotoName)));
	}

	@Test
	public void getAddPageOlderDateErrorMessage() throws IOException {
		Assert.assertEquals("The date you entered is earlier than the previous dated page in your journal.",
				CGOABHtmlUtils.getAddPageErrorMessage(cleaner.clean(addNewPageDateError)));
	}

	@Test
	public void getUsername_wrongPageGivesNull() throws IOException {
		Assert.assertEquals(null, CGOABHtmlUtils.getUsernameFromMyAccount(cleaner.clean(homeFile)));
	}

	@Test
	public void getUsername() throws IOException {
		Assert.assertEquals("benrowlands", CGOABHtmlUtils.getUsernameFromMyAccount(cleaner.clean(userAccountFile)));
	}

	@Test
	public void getRealname_wrongPageGivesNull() throws IOException {
		Assert.assertEquals(null, CGOABHtmlUtils.getRealnameFromMyAccount(cleaner.clean(homeFile)));
	}

	@Test
	public void getRealname() throws IOException {
		Assert.assertEquals("Ben Rowlands", CGOABHtmlUtils.getRealnameFromMyAccount(cleaner.clean(userAccountFile)));
	}

	@Test
	public void isUpdateAccountPage_homePage() throws Exception {
		Assert.assertFalse(CGOABHtmlUtils.isUpdateAccountPage(cleaner.clean(homeFile)));
	}

	@Test
	public void isUpdateAccountPage_accountPage() throws Exception {
		Assert.assertTrue(CGOABHtmlUtils.isUpdateAccountPage(cleaner.clean(userAccountFile)));
	}

	// @Test
	// public void isLoginPage_loginPage() throws Exception {
	// Assert.assertTrue(CGOABHtmlUtils.isLoginPage(cleaner.clean(loginFile)));
	// }

	@Test
	public void isLoginPage_homePage() throws Exception {
		Assert.assertFalse(CGOABHtmlUtils.isLoginPage(cleaner.clean(homeFile)));
	}

	// @Test
	// public void isErrorPage_errorPage() throws Exception {
	// Assert.assertTrue(CGOABHtmlScraper.isErrorPage(cleaner.clean(errorFile)));
	// }
	//
	// @Test
	// public void isErrorPage_homePage() throws Exception {
	// Assert.assertFalse(CGOABHtmlScraper.isErrorPage(cleaner.clean(homeFile)));
	// }
}
