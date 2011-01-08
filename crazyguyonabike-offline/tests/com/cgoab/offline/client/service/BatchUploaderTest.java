package com.cgoab.offline.client.service;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

import org.hamcrest.Description;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.Sequence;
import org.jmock.api.Action;
import org.jmock.api.Invocation;
import org.junit.Assert;
import org.junit.Test;

import com.cgoab.offline.client.BatchUploader;
import com.cgoab.offline.client.BatchUploader.BatchUploaderListener;
import com.cgoab.offline.client.CompletionCallback;
import com.cgoab.offline.client.PhotoUploadProgressListener;
import com.cgoab.offline.client.UploadClient;
import com.cgoab.offline.model.DuplicatePhotoException;
import com.cgoab.offline.model.InvalidInsertionPointException;
import com.cgoab.offline.model.Journal;
import com.cgoab.offline.model.Page;
import com.cgoab.offline.model.PageNotEditableException;
import com.cgoab.offline.model.Photo;
import com.cgoab.offline.model.UploadState;

public class BatchUploaderTest {

	private static int id;
	private Mockery context = new Mockery();
	private Journal journal = new Journal(new File("test.xml"), "<test>");

	private static Page newPage(Journal journal) {
		return journal.createNewPage();
	}

	private static Photo newPhoto() {
		Photo photo = new Photo();
		photo.setCaption("caption");
		photo.setFile(new File("text_" + (id++) + ".jpeg"));
		photo.setState(UploadState.NEW);
		return photo;
	}

	@Test
	public void testUploadSucess() throws Exception {
		final Mockery context = new Mockery();
		final int docId = 1;
		final int pageId = 100;
		final Page page = newPage(journal);
		final Photo photo1 = newPhoto();
		final Photo photo2 = newPhoto();
		page.addPhotos(Arrays.asList(photo1, photo2), -1);

		final UploadClient client = context.mock(UploadClient.class);
		context.checking(new Expectations() {
			{
				oneOf(client).createNewPage(with(same(page)), with(any(CompletionCallback.class)));
				will(new CompletionCallbackAction<Integer>(pageId));
				oneOf(client).addPhoto(with(equal(pageId)), with(same(photo1)), with(any(CompletionCallback.class)),
						with(any(PhotoUploadProgressListener.class)));
				will(new CompletionCallbackAction<Void>(null));
				oneOf(client).addPhoto(with(equal(pageId)), with(same(photo2)), with(any(CompletionCallback.class)),
						with(any(PhotoUploadProgressListener.class)));
				will(new CompletionCallbackAction<Void>(null));
			}
		});

		final BatchUploaderListener listener = context.mock(BatchUploaderListener.class);
		context.checking(new Expectations() {
			{
				Sequence sequence = context.sequence("callback");
				oneOf(listener).beforeUploadPage(page);
				inSequence(sequence);
				oneOf(listener).afterUploadPage(page);
				inSequence(sequence);
				oneOf(listener).beforeUploadPhoto(photo1);
				inSequence(sequence);
				/* don't expect progress as mock doesn't call it! */
				oneOf(listener).afterUploadPhoto(photo1);
				inSequence(sequence);
				oneOf(listener).beforeUploadPhoto(photo2);
				inSequence(sequence);
				/* don't expect progress as mock doesn't call it! */
				oneOf(listener).afterUploadPhoto(photo2);
				inSequence(sequence);
				oneOf(listener).finished(with(equal(Arrays.asList(page))));
				inSequence(sequence);
			}
		});

		BatchUploader uploader = new BatchUploader(client);
		uploader.setPages(Arrays.asList(page));
		uploader.setListener(listener);

		Assert.assertEquals(UploadState.NEW, page.getState());
		Assert.assertEquals(UploadState.NEW, photo1.getState());
		Assert.assertEquals(UploadState.NEW, photo2.getState());
		Assert.assertEquals(Page.UNSET_SERVER_ID, page.getServerId());

		uploader.start();
		context.assertIsSatisfied();

		/* check page state correct */
		Assert.assertEquals(UploadState.UPLOADED, page.getState());
		Assert.assertEquals(pageId, page.getServerId());
		Assert.assertEquals(UploadState.UPLOADED, photo1.getState());
		Assert.assertEquals(UploadState.UPLOADED, photo2.getState());
	}

	@Test
	public void testUploadPhotoFails() throws Exception {
		final int docId = 1;
		final int pageId = 100;
		final Page page = newPage(journal);
		final Photo photo1 = newPhoto();
		final Photo photo2 = newPhoto();
		final Photo photo3 = newPhoto();
		page.addPhotos(Arrays.asList(photo1, photo2, photo3), -1);

		final Throwable exception = new Exception("MockException");

		final UploadClient client = context.mock(UploadClient.class);
		context.checking(new Expectations() {
			{
				oneOf(client).createNewPage(with(same(page)), with(any(CompletionCallback.class)));
				will(new CompletionCallbackAction<Integer>(pageId));
				oneOf(client).addPhoto(with(equal(pageId)), with(same(photo1)), with(any(CompletionCallback.class)),
						with(any(PhotoUploadProgressListener.class)));
				will(new CompletionCallbackAction<Void>(null));
				oneOf(client).addPhoto(with(equal(pageId)), with(same(photo2)), with(any(CompletionCallback.class)),
						with(any(PhotoUploadProgressListener.class)));
				will(new ErrorCallbackAction<Void>(exception));
			}
		});

		final BatchUploaderListener listener = context.mock(BatchUploaderListener.class);
		context.checking(new Expectations() {
			{
				Sequence sequence = context.sequence("callback");
				oneOf(listener).beforeUploadPage(page);
				inSequence(sequence);
				oneOf(listener).afterUploadPage(page);
				inSequence(sequence);
				oneOf(listener).beforeUploadPhoto(photo1);
				inSequence(sequence);
				/* don't expect progress as mock doesn't call it! */
				oneOf(listener).afterUploadPhoto(photo1);
				inSequence(sequence);
				oneOf(listener).beforeUploadPhoto(photo2);
				inSequence(sequence);
				/* don't expect progress as mock doesn't call it! */
				oneOf(listener).finishedWithError(with(equal(Collections.<Page> emptyList())),
						with(equal(Collections.<Page> emptyList())), with(equal(page)), with(equal(photo2)),
						with(equal(exception)));
				inSequence(sequence);
			}
		});

		BatchUploader uploader = new BatchUploader(client);
		uploader.setPages(Arrays.asList(page));
		uploader.setListener(listener);

		Assert.assertEquals(UploadState.NEW, page.getState());
		Assert.assertEquals(UploadState.NEW, photo1.getState());
		Assert.assertEquals(UploadState.NEW, photo2.getState());
		Assert.assertEquals(UploadState.NEW, photo3.getState());

		uploader.start();
		context.assertIsSatisfied();

		/* check page state correct */
		Assert.assertEquals(UploadState.PARTIALLY_UPLOAD, page.getState());
		Assert.assertEquals(pageId, page.getServerId());
		Assert.assertEquals(UploadState.UPLOADED, photo1.getState());
		Assert.assertEquals(UploadState.ERROR, photo2.getState());
		Assert.assertEquals(UploadState.NEW, photo3.getState());
	}

	@Test
	public void testUploadPartiallyUploadedPage() throws Exception {
		final int docId = 1;
		final int pageId = 100;
		final Page page = newPage(journal);
		final Photo photo1 = newPhoto();
		final Photo photo2 = newPhoto();
		page.addPhotos(Arrays.asList(photo1, photo2), -1);

		page.setState(UploadState.PARTIALLY_UPLOAD);
		page.setServerId(pageId);
		photo1.setState(UploadState.UPLOADED);
		photo2.setState(UploadState.NEW);

		final UploadClient client = context.mock(UploadClient.class);
		context.checking(new Expectations() {
			{
				oneOf(client).addPhoto(with(equal(pageId)), with(same(photo2)), with(any(CompletionCallback.class)),
						with(any(PhotoUploadProgressListener.class)));
				will(new CompletionCallbackAction<Void>(null));
			}
		});

		final BatchUploaderListener listener = context.mock(BatchUploaderListener.class);
		context.checking(new Expectations() {
			{
				Sequence sequence = context.sequence("callback");
				oneOf(listener).beforeUploadPhoto(photo2);
				inSequence(sequence);
				/* don't expect progress as mock doesn't call it! */
				oneOf(listener).afterUploadPhoto(photo2);
				inSequence(sequence);
				oneOf(listener).finished(with(equal(Arrays.asList(page))));
				inSequence(sequence);
			}
		});

		BatchUploader uploader = new BatchUploader(client);
		uploader.setPages(Arrays.asList(page));
		uploader.setListener(listener);

		Assert.assertEquals(UploadState.PARTIALLY_UPLOAD, page.getState());
		Assert.assertEquals(UploadState.UPLOADED, photo1.getState());
		Assert.assertEquals(UploadState.NEW, photo2.getState());
		Assert.assertEquals(pageId, page.getServerId());

		uploader.start();
		context.assertIsSatisfied();

		/* check page state correct */
		Assert.assertEquals(UploadState.UPLOADED, page.getState());
		Assert.assertEquals(pageId, page.getServerId());
		Assert.assertEquals(UploadState.UPLOADED, photo1.getState());
		Assert.assertEquals(UploadState.UPLOADED, photo2.getState());
	}

	static class ErrorCallbackAction<T> implements Action {
		Throwable value;

		public ErrorCallbackAction(Throwable value) {
			this.value = value;
		}

		@Override
		public void describeTo(Description description) {
			description.appendText("callback into onError()");
		}

		@Override
		public Object invoke(Invocation invocation) throws Throwable {
			for (Object o : invocation.getParametersAsArray()) {
				if (o instanceof CompletionCallback) {
					((CompletionCallback<T>) o).onError(value);
					return null;
				}
			}
			throw new IllegalArgumentException("No CompletionCallback paramater found");
		}
	}

	static class CompletionCallbackAction<T> implements Action {
		T value;

		public CompletionCallbackAction(T value) {
			this.value = value;
		}

		@Override
		public void describeTo(Description description) {
			description.appendText("callback into onCompletion");
		}

		@Override
		public Object invoke(Invocation invocation) throws Throwable {
			for (Object o : invocation.getParametersAsArray()) {
				if (o instanceof CompletionCallback) {
					((CompletionCallback<T>) o).onCompletion(value);
					return null;
				}
			}
			throw new IllegalArgumentException("No CompletionCallback paramater found");
		}
	}
}