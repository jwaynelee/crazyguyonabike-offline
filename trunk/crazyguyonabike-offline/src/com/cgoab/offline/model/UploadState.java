package com.cgoab.offline.model;

public enum UploadState {
	/**
	 * New page or photo, no attempt made to upload it.
	 */
	NEW,

	/**
	 * Only applies to pages, the page uploaded but one of its photos failed
	 */
	PARTIALLY_UPLOAD,

	/**
	 * An error was encountered uploading this page or photo (in which case
	 * the page will be marked {@link UploadState#PARTIAL_UPLOAD}.
	 */
	ERROR,

	/**
	 * Successfully uploaded
	 */
	UPLOADED
}