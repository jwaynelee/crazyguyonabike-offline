package play;

import java.io.File;
import java.io.IOException;

import nu.xom.ParsingException;
import nu.xom.ValidityException;

import com.cgoab.offline.model.JournalXmlLoader;

public class JournalReaderSpeedTest {
	public static void main(String[] args) throws ValidityException, ParsingException, IOException {
		long start = System.currentTimeMillis();
		for (int i = 0; i < JournalWriterSpeedTest.NJOURNALS; ++i) {
			File f = new File(JournalWriterSpeedTest.TEST_DIR + File.separator + "test" + i + ".xml");
			JournalXmlLoader.open(f);
		}
		System.out.printf("%dms to load\n", System.currentTimeMillis() - start);
	}
}
