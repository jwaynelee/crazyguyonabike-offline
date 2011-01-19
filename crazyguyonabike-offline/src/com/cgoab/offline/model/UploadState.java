package com.cgoab.offline.model;

public enum UploadState {
	/**
	 * An error was encountered uploading this page or photo (in which case the
	 * page will be marked {@link UploadState#PARTIAL_UPLOAD}.
	 */
	ERROR,

	/**
	 * New page or photo, no attempt made to upload it.
	 */
	NEW,

	/**
	 * Only applies to pages, the page uploaded but one of its photos failed
	 */
	PARTIALLY_UPLOAD,

	/**
	 * Successfully uploaded
	 */
	UPLOADED
}