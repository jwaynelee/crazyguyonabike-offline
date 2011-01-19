package com.cgoab.offline.model;

import java.io.File;

import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;

import com.cgoab.offline.util.Assert;

public class Photo {

	private IDocument captionDocument;

	private File file;

	/* transient, not persisted */
	private File resizedPhotoFile;

	private UploadState state = UploadState.NEW;

	public Photo() {
	}

	public Photo(File file) {
		setFile(file);
	}

	/**
	 * Equality defined by local file name of the photo.
	 */
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Photo) {
			return file.getName().equals(((Photo) obj).getFile().getName());
		}
		return false;
	}

	public String getCaption() {
		return captionDocument == null ? null : captionDocument.get();
	}

	public File getFile() {
		return file;
	}

	public IDocument getOrCreateCaptionDocument() {
		return captionDocument == null ? (captionDocument = new Document()) : captionDocument;
	}

	public File getResizedPhoto() {
		return resizedPhotoFile;
	}

	// public String getImageName() {
	// return imageName;
	// }

	public UploadState getState() {
		return state;
	}

	@Override
	public int hashCode() {
		return file.getName().hashCode();
	}

	// public void setImageName(String imageName) {
	// this.imageName = imageName;
	// }

	public void setCaption(String caption) {
		getOrCreateCaptionDocument().set(caption);
	}

	public void setFile(File file) {
		this.file = file;
	}

	public void setResizedPhotoFile(File resizedPhotoFile) {
		this.resizedPhotoFile = resizedPhotoFile;
	}

	public void setState(UploadState state) {
		Assert.isTrue(state != UploadState.PARTIALLY_UPLOAD);
		this.state = state;
	}

	@Override
	public String toString() {
		return String.format("Photo [%s]", file.getName());
	}
}