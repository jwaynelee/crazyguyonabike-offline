package testutils.photos;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import testutils.TestUtils;

public class TestPhotos {

	public static File extractSmallPhoto() throws IOException {
		return extractPhoto("P1050073.jpg", TestPhotos.class);
	}

	public static File extractLargePhoto() throws IOException {
		return extractPhoto("P1190024.JPG", TestPhotos.class);
	}

	public static File extractPhoto(String name, Class<?> bias) throws IOException {
		File f = new File(TestUtils.createTempFileName(name));
		copyToTempFile(f, bias.getResourceAsStream(name));
		return f;
	}

	public static void copyToTempFile(File file, InputStream in) throws IOException {
		FileOutputStream fos = new FileOutputStream(file);
		byte[] buff = new byte[8 * 1024];
		int read;
		while ((read = in.read(buff)) != -1) {
			fos.write(buff, 0, read);
		}
		in.close();
		fos.close();
	}

}
