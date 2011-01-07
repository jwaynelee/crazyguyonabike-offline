package com.cgoab.offline.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.cgoab.offline.model.Page.PhotosOrder;

public class PageTest {

	private Journal journal = new Journal(new File("does-not-exist.xml"), "foo");

	@Test
	public void testAddPhotosEmpty() throws Exception {
		Page page = new Page(journal);
		// page.setPhotosOrder(PhotosOrder.NAME); // default
		List<Photo> photos = new ArrayList<Photo>();
		photos.add(new Photo(new File("C.jpg")));
		photos.add(new Photo(new File("B.jpg")));
		photos.add(new Photo(new File("A.jpg")));

		page.addPhotos(photos, 0); // ok
		Assert.assertEquals(photos, page.getPhotos());
	}

	@Test
	public void testAddPhotosInsert() throws Exception {
		Page page = new Page(journal);
		// page.setPhotosOrder(PhotosOrder.NAME); // default
		List<Photo> photos1 = new ArrayList<Photo>();
		photos1.add(new Photo(new File("C.jpg")));
		photos1.add(new Photo(new File("B.jpg")));
		photos1.add(new Photo(new File("A.jpg")));

		page.addPhotos(photos1, 0); // ok
		assertEquals(photos1, page.getPhotos());

		List<Photo> photos2 = new ArrayList<Photo>();
		photos2.add(new Photo(new File("D.jpg")));
		photos2.add(new Photo(new File("E.jpg")));
		photos2.add(new Photo(new File("F.jpg")));
		page.addPhotos(photos2, 1); // insert after "C"
		assertEquals("C.jpg", page.getPhotos().get(0).getFile().getName());
		assertEquals("D.jpg", page.getPhotos().get(1).getFile().getName());
		assertEquals("E.jpg", page.getPhotos().get(2).getFile().getName());
		assertEquals("F.jpg", page.getPhotos().get(3).getFile().getName());
		assertEquals("B.jpg", page.getPhotos().get(4).getFile().getName());
		assertEquals("A.jpg", page.getPhotos().get(5).getFile().getName());
	}

	@Test
	public void testAddPhotoDuplicateSameBatch() throws Exception {
		Page page = new Page(journal);
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
	public void testAddPhotoDuplicateDifferentBatch() throws Exception {
		Page page = new Page(journal);
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

	@Test
	public void testAddPhotos_nameOrder() throws Exception {
		Page page = new Page(journal);
		page.setPhotosOrder(PhotosOrder.NAME);
		List<Photo> photos = new ArrayList<Photo>();
		photos.add(new Photo(new File("C.jpg")));
		photos.add(new Photo(new File("B.jpg")));
		photos.add(new Photo(new File("A.jpg")));

		page.addPhotos(photos, 0);
		Assert.assertEquals(photos.get(2), page.getPhotos().get(0));
		Assert.assertEquals(photos.get(1), page.getPhotos().get(1));
		Assert.assertEquals(photos.get(0), page.getPhotos().get(2));
	}

}
