package com.cgoab.offline.client;

/**
 * Describes a document (article or journal) held on the CGOAB server to which
 * Pages may be uploaded.
 * <p>
 * This object maps onto the listing provided by
 * <tt>www.crazyguyonabike.com/my</tt>.
 */
public class DocumentDescription {

	private final int documentId;
	private final int hits;
	private final String status;
	private final String title;
	private final DocumentType type;

	// /**
	// * Ranks in order
	// *
	// * <ul>
	// * <li>Not yet published
	// * <li>X days until start
	// * <li>X of Y days
	// * <li>Work in progress
	// * <li>Completed X
	// * </ul>
	// *
	// * If same then returns order of document ID
	// *
	// */
	// public static class DocumentDescriptionComparator implements
	// Comparator<DocumentDescription> {
	// @Override
	// public int compare(DocumentDescription o1, DocumentDescription o2) {
	//
	// return 0;
	// }
	// }

	public DocumentDescription(String title, int hits, String status, int docId, DocumentType type) {
		this.title = title;
		this.hits = hits;
		this.status = status;
		this.documentId = docId;
		this.type = type;
	}

	public int getDocumentId() {
		return documentId;
	}

	public int getHits() {
		return hits;
	}

	public String getStatus() {
		return status;
	}

	public String getTitle() {
		return title;
	}

	public DocumentType getType() {
		return type;
	}

	@Override
	public String toString() {
		return type.name() + " [id=" + documentId + ", title=" + title + ", hits=" + hits + ", status=" + status + "]";
	}
}
