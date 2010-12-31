package com.cgoab.offline.model;

import java.util.Map;

/**
 * Thrown when an attempt is made to add a photo that already exists in the page
 * or journal.
 */
public class DuplicatePhotoException extends ModelException {
	private static final long serialVersionUID = 1L;

	private final Map<Photo, Page> duplicates;

	public DuplicatePhotoException(Map<Photo, Page> duplicates) {
		this.duplicates = duplicates;
	}

	public Map<Photo, Page> getDuplicatePhotos() {
		return duplicates;
	}
}
