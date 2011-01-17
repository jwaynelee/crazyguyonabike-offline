package com.cgoab.offline.model;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.joda.time.LocalDate;
import org.junit.Test;

public class JournalXmlLoaderTest {

	@Test
	public void saveAndLoad() throws Exception {
		// build test model
		File tmpFile = File.createTempFile("journal", null);
		tmpFile.deleteOnExit();

		Journal testJournal = new Journal(tmpFile, "testJournal");
		testJournal.setUseExifThumbnail(true);
		testJournal.setResizeImagesBeforeUpload(false);
		{
			Page page1 = testJournal.createNewPage();
			page1.setTitle("Page1");
			page1.setDistance(100);
			page1.setText("HelloWorld on page 1");
			page1.setDate(new LocalDate(2001, 01, 01));
			Photo photo1 = new Photo(new File("a.jpg").getAbsoluteFile());
			photo1.setState(UploadState.UPLOADED);
			photo1.setCaption("caption 1");
			Photo photo2 = new Photo(new File("b.jpg").getAbsoluteFile());
			photo2.setState(UploadState.UPLOADED);
			photo2.setCaption("caption 2");
			page1.addPhotos(Arrays.asList(photo1, photo2), 0);
			page1.setServerId(100);
			page1.setState(UploadState.UPLOADED);
		}
		{
			Page page2 = testJournal.createNewPage();
			page2.setTitle("Page2");
			page2.setDistance(5);
			page2.setText("HelloWorld on page 2");
			page2.setDate(new LocalDate(2001, 01, 02));
			Photo photo3 = new Photo(new File("c.jpg").getAbsoluteFile());
			photo3.setState(UploadState.UPLOADED);
			photo3.setCaption("caption 3");
			Photo photo4 = new Photo(new File("d.jpg").getAbsoluteFile());
			photo4.setState(UploadState.ERROR);
			photo4.setCaption("caption 4");
			page2.addPhotos(Arrays.asList(photo3, photo4), 0);
			page2.setServerId(101);
			page2.setState(UploadState.PARTIALLY_UPLOAD);
		}
		{
			Page page3 = testJournal.createNewPage();
			page3.setTitle("Page2");
			page3.setDistance(5);
			page3.setText("HelloWorld on page 3");
			page3.setDate(new LocalDate(2001, 01, 03));
			Photo photo5 = new Photo(new File("e.jpg").getAbsoluteFile());
			photo5.setCaption("caption 5");
			Photo photo6 = new Photo(new File("f.jpg").getAbsoluteFile());
			photo6.setCaption("caption 6");
			page3.addPhotos(Arrays.asList(photo5, photo6), 0);
		}

		JournalXmlLoader.save(testJournal);
		Journal loaded = JournalXmlLoader.open(testJournal.getFile());
		JournalXmlLoader.validateJournal(loaded);

		assertEquals(testJournal.getName(), loaded.getName());
		assertEquals(testJournal.getFile(), loaded.getFile());
		assertEquals(testJournal.getPages().size(), loaded.getPages().size());
		assertEquals(testJournal.isHideUploadedContent(), loaded.isHideUploadedContent());
		assertEquals(testJournal.isUseExifThumbnail(), loaded.isUseExifThumbnail());
		assertEquals(testJournal.isResizeImagesBeforeUpload(), loaded.isResizeImagesBeforeUpload());

		/* compare model with loaded */
		for (int i = 0; i < testJournal.getPages().size(); ++i) {
			Page tp = testJournal.getPages().get(i);
			Page lp = loaded.getPages().get(i);

			assertEquals(tp.getTitle(), lp.getTitle());
			assertEquals(tp.getHeadline(), lp.getHeadline());
			assertEquals(tp.getText(), lp.getText());
			assertEquals(tp.getDistance(), lp.getDistance(), 0);
			assertEquals(tp.getDate(), lp.getDate());
			assertEquals(tp.isItalic(), lp.isItalic());
			assertEquals(tp.isBold(), lp.isBold());
			assertEquals(tp.isVisible(), lp.isVisible());
			assertEquals(tp.getIndent(), lp.getIndent());
			assertEquals(tp.getHeadingStyle(), lp.getHeadingStyle());
			assertEquals(tp.getFormat(), lp.getFormat());

			List<Photo> tphotos = tp.getPhotos();
			List<Photo> lphotos = lp.getPhotos();
			assertEquals(tphotos.size(), lphotos.size());
			for (int j = 0; j < tphotos.size(); ++j) {
				Photo tphoto = tphotos.get(j);
				Photo lphoto = lphotos.get(j);
				assertEquals(tphoto.getFile(), lphoto.getFile());
				// assertEquals(tphoto.getImageName(), lphoto.getImageName());
				assertEquals(tphoto.getCaption(), lphoto.getCaption());
			}
		}
	}
}
