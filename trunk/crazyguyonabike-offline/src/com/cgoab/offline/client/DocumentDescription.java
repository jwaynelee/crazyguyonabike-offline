package com.cgoab.offline.client;

/**
 * Describes a document (article or journal) held on the CGOAB server to which
 * Pages may be uploaded.
 * <p>
 * This object maps onto the listing provided by
 * <tt>www.crazyguyonabike.com/my</tt>.
 */
public class DocumentDescription {
	private final String title;
	private final int hits;
	private final int documentId;
	private final String status;
	private final DocumentType type;

	public DocumentDescription(String title, int hits, String status, int docId, DocumentType type) {
		this.title = title;
		this.hits = hits;
		this.status = status;
		this.documentId = docId;
		this.type = type;
	}

	public String getTitle() {
		return title;
	}

	public int getHits() {
		return hits;
	}

	public String getStatus() {
		return status;
	}

	public int getDocumentId() {
		return documentId;
	}

	public DocumentType getType() {
		return type;
	}

	@Override
	public String toString() {
		return type.name() + " [id=" + documentId + ", title=" + title + ", hits=" + hits + ", status=" + status + "]";
	}
}
