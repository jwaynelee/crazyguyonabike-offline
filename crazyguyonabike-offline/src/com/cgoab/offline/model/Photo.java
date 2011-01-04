package com.cgoab.offline.model;

import java.io.File;

import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;

import com.cgoab.offline.util.Assert;

public class Photo implements Cloneable {

	private IDocument captionDocument;

	private File file;

	private UploadState state = UploadState.NEW;

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
	public Photo clone() {
		try {
			return (Photo) super.clone();
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	@Override
	public String toString() {
		return String.format("Photo [%s]", file.getName());
	}

	/**
	 * Equality defined by file name of the photo.
	 */
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Photo) {
			return file.getName().equals(((Photo) obj).getFile().getName());
		}
		return false;
	}
}