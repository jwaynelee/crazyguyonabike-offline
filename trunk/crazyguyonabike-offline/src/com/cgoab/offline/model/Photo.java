package com.cgoab.offline.model;

import java.io.File;

import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;

import com.cgoab.offline.util.Assert;

public class Photo {

	private IDocument captionDocument;

	private File file;

	private UploadState state = UploadState.NEW;

	/* transient, not persisted */
	private File resizedPhotoFile;

	public Photo() {
	}

	public Photo(File file) {
		setFile(file);
	}

	public String getCaption() {
		return captionDocument == null ? null : captionDocument.get();
	}

	public IDocument getOrCreateCaptionDocument() {
		return captionDocument == null ? (captionDocument = new Document()) : captionDocument;
	}

	public UploadState getState() {
		return state;
	}

	public void setState(UploadState state) {
		Assert.isTrue(state != UploadState.PARTIALLY_UPLOAD);
		this.state = state;
	}

	public File getFile() {
		return file;
	}

	// public String getImageName() {
	// return imageName;
	// }

	public void setCaption(String caption) {
		getOrCreateCaptionDocument().set(caption);
	}

	public void setFile(File file) {
		this.file = file;
	}

	// public void setImageName(String imageName) {
	// this.imageName = imageName;
	// }

	@Override
	public String toString() {
		return String.format("Photo [%s]", file.getName());
	}

	@Override
	public int hashCode() {
		return file.getName().hashCode();
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

	public void setResizedPhotoFile(File resizedPhotoFile) {
		this.resizedPhotoFile = resizedPhotoFile;
	}

	public File getResizedPhoto() {
		return resizedPhotoFile;
	}
}