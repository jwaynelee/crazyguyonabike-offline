package play;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import com.cgoab.offline.model.DuplicatePhotoException;
import com.cgoab.offline.model.InvalidInsertionPointException;
import com.cgoab.offline.model.Journal;
import com.cgoab.offline.model.JournalXmlLoader;
import com.cgoab.offline.model.Page;
import com.cgoab.offline.model.PageNotEditableException;
import com.cgoab.offline.model.Photo;

public class JournalWriterSpeedTest {
	public static final String TEST_DIR = "SpeedTest";
	public static final int NJOURNALS = 1;
	static List<Journal> journals = new ArrayList<Journal>();

	public static void main(String[] args) throws IOException, XMLStreamException, Exception {
		// create 10 journals, each with 100 pages, each page with 10 photos...
		for (int i = 0; i < NJOURNALS; i++) {
			Journal journal = new Journal(new File(TEST_DIR + File.separator + "test" + i + ".xml"), "Journal#" + i);
			for (int p = 0; p < 500; ++p) {
				Page page = new Page(journal);
				page.setText("this is a long line of text that is perhaps the longest line of text I am ever going to write but wowo isn't it very long");
				page.setDistance(10);
				page.setTitle("Page#" + p);
				page.setLocalId(p);
				for (int j = 0; j < 10; ++j) {
					Photo photo = new Photo();
					photo.setFile(new File("foo.jpg"));
					photo.setCaption("Caption for photo " + j);
					page.addPhotos(Arrays.asList(photo), -1);
				}
				journal.addPage(page);
			}
			journals.add(journal);
		}
		long start = System.currentTimeMillis();
		doTest();
		System.out.printf("%dms\n", System.currentTimeMillis() - start);
	}

	private static void doTest() throws IOException, XMLStreamException {
		for (Journal j : journals) {
			JournalXmlLoader.save(j);
		}
	}
}
