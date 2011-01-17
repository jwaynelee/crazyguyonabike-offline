package testdata.photos;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import com.cgoab.offline.util.Assert;

public class TestPhotos {
	public static File getPhotoAsTempFile() throws IOException {
		String image = "P1190024.JPG";
		File source = File.createTempFile("photo", null);
		copyToTempFile(source, TestPhotos.class.getResourceAsStream(image));
		return source;
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

	public static File createTempFileName(File source) {
		String tmpDir = System.getProperty("java.io.tmpdir");
		Assert.notNull(tmpDir);
		File f = new File(tmpDir + File.separator + System.currentTimeMillis() + "_" + source.getName());
		Assert.isTrue(!f.exists());
		return f;
	}
}
