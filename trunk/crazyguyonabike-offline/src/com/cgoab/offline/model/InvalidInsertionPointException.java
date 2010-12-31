package com.cgoab.offline.model;

/**
 * Thrown when an attempt is made to insert a photo at an invalid location
 * (usually before the last uploaded photo).
 */
public class InvalidInsertionPointException extends ModelException {
	private static final long serialVersionUID = 1L;
}
