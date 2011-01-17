package com.cgoab.offline.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Assert;
import org.junit.Test;

import com.cgoab.offline.model.Page.PhotosOrder;

public class PageTest {

	private Journal journal = new Journal(new File("does-not-exist.xml"), "foo");
	private Page page = new Page(journal);

	@Test
	public void addPhotos_firesPhotosAddedListener() throws Exception {
		final List<Photo> photos = new ArrayList<Photo>();
		photos.add(new Photo(new File("a")));
		photos.add(new Photo(new File("b")));
		Mockery context = new Mockery();
		final JournalListener listener = context.mock(JournalListener.class);
		context.checking(new Expectations() {
			{
				oneOf(listener).photosAdded(photos, page);
				oneOf(listener).journalDirtyChange();
			}
		});
		journal.addJournalListener(listener);
		page.addPhotos(photos, 0);
		context.assertIsSatisfied();
	}

	@Test
	public void removePhotos_firesPhotosAddedListener() throws Exception {
		final List<Photo> photos = new ArrayList<Photo>();
		final Photo a = new Photo(new File("a"));
		photos.add(a);
		photos.add(new Photo(new File("b")));
		page.addPhotos(photos, 0);
		Mockery context = new Mockery();
		final JournalListener listener = context.mock(JournalListener.class);
		context.checking(new Expectations() {
			{
				oneOf(listener).photosRemoved(Arrays.asList(a), page);
				oneOf(listener).journalDirtyChange();
			}
		});
		journal.addJournalListener(listener);
		page.removePhotos(Arrays.asList(a));
		context.assertIsSatisfied();
	}

	@Test
	public void addPhotos_manualOrder() throws Exception {
		page.setPhotosOrder(PhotosOrder.MANUAL); // default
		List<Photo> photos = new ArrayList<Photo>();
		photos.add(new Photo(new File("C.jpg")));
		photos.add(new Photo(new File("B.jpg")));
		photos.add(new Photo(new File("A.jpg")));

		page.addPhotos(photos, 0); // ok
		Assert.assertEquals(photos, page.getPhotos());
	}

	@Test
	public void addPhotos_insert() throws Exception {
		page.setPhotosOrder(PhotosOrder.MANUAL);
		List<Photo> photos1 = new ArrayList<Photo>();
		photos1.add(new Photo(new File("C.jpg")));
		photos1.add(new Photo(new File("B.jpg")));
		photos1.add(new Photo(new File("A.jpg")));

		page.addPhotos(photos1, 0); // ok
		assertPhotos(page.getPhotos(), "C.jpg", "B.jpg", "A.jpg");

		List<Photo> photos2 = new ArrayList<Photo>();
		photos2.add(new Photo(new File("D.jpg")));
		photos2.add(new Photo(new File("E.jpg")));
		photos2.add(new Photo(new File("F.jpg")));
		page.addPhotos(photos2, 1); // insert after "C"
		assertPhotos(page.getPhotos(), "C.jpg", "D.jpg", "E.jpg", "F.jpg", "B.jpg", "A.jpg");
	}

	@Test
	public void testSetPhotosOrder() {

	}

	@Test
	public void addPhoto_duplicateFoundInBatch() throws Exception {
		// page.setPhotosOrder(PhotosOrder.NAME); // default
		List<Photo> photos1 = new ArrayList<Photo>();
		photos1.add(new Photo(new File("C.jpg")));
		photos1.add(new Photo(new File("A.jpg")));
		photos1.add(new Photo(new File("C.jpg")));

		DuplicatePhotoException ex = null;
		try {
			page.addPhotos(photos1, 0);
		} catch (DuplicatePhotoException e) {
			ex = e;
		}
		assertNotNull(ex);
		assertEquals(1, ex.getDuplicatePhotos().size());
		assertTrue(ex.getDuplicatePhotos().containsKey(photos1.get(2)));
		assertEquals(null, ex.getDuplicatePhotos().get(photos1.get(2)));
	}

	@Test
	public void addPhotos_duplicateFoundInDifferentBatch() throws Exception {
		// page.setPhotosOrder(PhotosOrder.NAME); // default
		List<Photo> photos1 = new ArrayList<Photo>();
		photos1.add(new Photo(new File("C.jpg")));
		photos1.add(new Photo(new File("A.jpg")));
		page.addPhotos(photos1, 0);

		List<Photo> photos2 = new ArrayList<Photo>();
		photos2.add(new Photo(new File("B.jpg")));
		photos2.add(new Photo(new File("C.jpg")));

		DuplicatePhotoException ex = null;
		try {
			page.addPhotos(photos2, 0);
		} catch (DuplicatePhotoException e) {
			ex = e;
		}
		assertNotNull(ex);
		assertEquals(1, ex.getDuplicatePhotos().size());
		assertTrue(ex.getDuplicatePhotos().containsKey(photos2.get(1)));
		assertEquals(page, ex.getDuplicatePhotos().get(photos2.get(1)));
	}

	private static void assertPhotos(List<Photo> actual, String... expected) {
		Assert.assertEquals(expected.length, actual.size());
		int i = 0;
		for (Photo photo : actual) {
			if (!expected[i++].equals(photo.getFile().getName())) {
				Assert.fail("expected: <" + Arrays.toString(expected) + "> but was <" + actual + ">");
			}
		}
	}

	@Test
	public void addPhotos_nameOrder() throws Exception {
		page.setPhotosOrder(PhotosOrder.NAME);
		List<Photo> photos = new ArrayList<Photo>();
		photos.add(new Photo(new File("C.jpg")));
		photos.add(new Photo(new File("B.jpg")));
		photos.add(new Photo(new File("A.jpg")));

		page.addPhotos(photos, 0);
		assertPhotos(page.getPhotos(), "A.jpg", "B.jpg", "C.jpg");
	}

	@Test
	public void movePhoto() throws Exception {
		page.setPhotosOrder(PhotosOrder.MANUAL);
		List<Photo> photos = new ArrayList<Photo>();
		photos.add(new Photo(new File("C.jpg")));
		photos.add(new Photo(new File("B.jpg")));
		photos.add(new Photo(new File("A.jpg")));
		page.addPhotos(photos, 0);

		page.movePhotos(Arrays.asList(photos.get(2)), 0); /* A, C, B */
		page.movePhotos(Arrays.asList(photos.get(1)), 1); /* A, B, C */
		assertPhotos(page.getPhotos(), "A.jpg", "B.jpg", "C.jpg");

	}

	@Test
	public void movePhotos_changesOrder() throws Exception {
		page.setPhotosOrder(PhotosOrder.NAME);
		List<Photo> photos = new ArrayList<Photo>();
		photos.add(new Photo(new File("C.jpg")));
		photos.add(new Photo(new File("B.jpg")));
		photos.add(new Photo(new File("A.jpg")));

		page.addPhotos(photos, 0);
		assertPhotos(page.getPhotos(), "A.jpg", "B.jpg", "C.jpg");

		/* now move a photo */
		assertEquals(PhotosOrder.NAME, page.getPhotosOrder());
		page.movePhotos(Arrays.asList(photos.get(1)), 0); /* B, C, A */
		assertPhotos(page.getPhotos(), "B.jpg", "A.jpg", "C.jpg");
		assertEquals(PhotosOrder.MANUAL, page.getPhotosOrder());
	}

	@Test(expected = IllegalArgumentException.class)
	public void movePhotos_notOwnedByPage() throws Exception {
		page.setPhotosOrder(PhotosOrder.MANUAL); // default
		List<Photo> photos = new ArrayList<Photo>();
		photos.add(new Photo(new File("A.jpg")));
		photos.add(new Photo(new File("B.jpg")));
		Photo bogus = new Photo(new File("BOGUS.jpg"));
		page.movePhotos(Arrays.asList(bogus), 0);
	}

	// @Test(expected = InvalidInsertionPointException.class)
	// public void testInvalidInsertionPoint_outOfRange() throws Exception {
	// page.addPhotos(Arrays.asList(new Photo(new File("foo.jpg"))), 10);
	// }

	@Test(expected = InvalidInsertionPointException.class)
	public void addPhotos_InvalidInsertionPoint_beforeUploadedPhoto() throws DuplicatePhotoException,
			InvalidInsertionPointException, PageNotEditableException {
		Photo p1 = new Photo(new File("p1"));
		Photo p2 = new Photo(new File("p2"));
		Photo p3 = new Photo(new File("p3"));
		page.addPhotos(Arrays.asList(p1, p2, p3), 0);
		p1.setState(UploadState.UPLOADED);

		page.addPhotos(Arrays.asList(new Photo(new File("p0"))), 0); /* throw */
	}

	@Test(expected = InvalidInsertionPointException.class)
	public void movePhotos_invalidInsertionPoint_beforeUploadedPhoto() throws DuplicatePhotoException,
			InvalidInsertionPointException, PageNotEditableException {
		Photo p1 = new Photo(new File("p1"));
		Photo p2 = new Photo(new File("p2"));
		Photo p3 = new Photo(new File("p3"));
		page.addPhotos(Arrays.asList(p1, p2, p3), 0);
		p1.setState(UploadState.UPLOADED);

		page.movePhotos(Arrays.asList(p2), 0); /* throw */
	}

	@Test
	public void movePhotos_afterUploadedPhoto() throws DuplicatePhotoException, InvalidInsertionPointException,
			PageNotEditableException {
		Photo p1 = new Photo(new File("p1"));
		Photo p2 = new Photo(new File("p2"));
		Photo p3 = new Photo(new File("p3"));
		page.addPhotos(Arrays.asList(p1, p2, p3), 0);
		p1.setState(UploadState.UPLOADED);

		page.movePhotos(Arrays.asList(p2), 3); /* ok */
		assertPhotos(page.getPhotos(), "p1", "p3", "p2");
	}

	@Test(expected = PageNotEditableException.class)
	public void addPhotos_throwsIfUploaded() throws Exception {
		page.setState(UploadState.UPLOADED);
		page.addPhotos(Arrays.asList(new Photo(new File("foo.jpg"))), 0);
	}

	@Test(expected = PageNotEditableException.class)
	public void movePhotos_throwIfUploaded() throws Exception {
		Photo photo = new Photo(new File("foo.jpg"));
		page.addPhotos(Arrays.asList(photo), 0);
		page.setState(UploadState.UPLOADED);
		page.movePhotos(Arrays.asList(photo), 0);
	}

	@Test(expected = PageNotEditableException.class)
	public void removePhotos_throwIfUploaded() throws Exception {
		Photo photo = new Photo(new File("foo.jpg"));
		page.addPhotos(Arrays.asList(photo), 0);
		page.setState(UploadState.UPLOADED);
		page.removePhotos(Arrays.asList(photo));
	}

	@Test
	public void removePhotos() throws Exception {
		Photo p1 = new Photo(new File("1"));
		Photo p2 = new Photo(new File("2"));
		Photo p3 = new Photo(new File("3"));
		page.addPhotos(Arrays.asList(p1, p2, p3), 0);
		assertPhotos(page.getPhotos(), "1", "2", "3");
		page.removePhotos(Arrays.asList(p1, p3));
		assertPhotos(page.getPhotos(), "2");
	}

	@Test(expected = IllegalArgumentException.class)
	public void removePhotos_notBelongingToPage() throws Exception {
		Photo p1 = new Photo(new File("1"));
		Photo p2 = new Photo(new File("2"));
		Photo p3 = new Photo(new File("3"));
		page.addPhotos(Arrays.asList(p1, p2), 0);
		assertPhotos(page.getPhotos(), "1", "2");
		page.removePhotos(Arrays.asList(p1, p3)); /* throw */
	}
}
