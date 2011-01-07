package com.cgoab.offline.model;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import nu.xom.ParsingException;
import nu.xom.ValidityException;

import org.joda.time.LocalDate;
import org.junit.Test;

import com.cgoab.offline.model.Page.EditFormat;
import com.cgoab.offline.model.Page.HeadingStyle;

public class JournalXmlLoaderTest {

	@Test
	public void testSaveThenLoad() throws Exception {
		// build test model
		Journal tj = new Journal(new File("testfile.xml"), "testJournal");
		Page p1 = newPage(tj, 100);
		Photo photo1 = new Photo();
		photo1.setFile(new File("C:\\foo.jpg"));
		photo1.setCaption("Photo 1 caption");
		p1.addPhotos(Arrays.asList(photo1), -1);
		Photo photo2 = new Photo();
		photo2.setFile(new File("C:\\bar.jpg"));
		photo2.setCaption("Photo 2 caption");
		p1.addPhotos(Arrays.asList(photo2), -1);
		Page p2 = newPage(tj, 200);
		tj.addPage(p1);
		tj.addPage(p2);

		JournalXmlLoader.save(tj);
		Journal lj = JournalXmlLoader.open(tj.getFile());

		assertEquals(tj.getName(), lj.getName());
		assertEquals(tj.getFile(), lj.getFile());
		assertEquals(tj.getPages().size(), lj.getPages().size());

		for (int i = 0; i < tj.getPages().size(); ++i) {
			Page tp = tj.getPages().get(i);
			Page lp = lj.getPages().get(i);

			assertEquals(tp.getLocalId(), lp.getLocalId());
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

	static Page newPage(Journal j, int id) {
		Page p = new Page(j);
		p.setLocalId(id);
		p.setTitle("TITLE");
		p.setHeadline("HEADLINE");
		p.setText("Some text with\nnewlines and <b>html</b> <a href=\"foo\">tags</a>");
		p.setDistance(100);
		p.setDate(new LocalDate(2000, 1, 1));
		p.setItalic(true);
		p.setBold(false);
		p.setHeadingStyle(HeadingStyle.MEDIUM);
		p.setFormat(EditFormat.AUTO);
		p.setIndent(3);
		return p;
	}
}
