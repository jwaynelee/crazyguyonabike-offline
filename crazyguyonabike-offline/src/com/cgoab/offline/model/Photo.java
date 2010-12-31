package com.cgoab.offline.model;

import java.io.File;

import com.cgoab.offline.util.Assert;

public class Photo implements Cloneable {

	private String caption;

	private File file;

	// private String imageName;

	private UploadState state = UploadState.NEW;

	public String getCaption() {
		return caption;
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
		this.caption = caption;
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
}