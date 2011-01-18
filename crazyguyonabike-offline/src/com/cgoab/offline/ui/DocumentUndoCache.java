package com.cgoab.offline.ui;

import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.jface.text.IDocument;
import org.eclipse.text.undo.DocumentUndoManagerRegistry;
import org.eclipse.text.undo.IDocumentUndoManager;

import com.cgoab.offline.util.LRUCache;
import com.cgoab.offline.util.LRUCache.CacheListener;

public class DocumentUndoCache {

	private Map<IDocument, IDocumentUndoManager> cache;

	public DocumentUndoCache(int size) {
		cache = new LRUCache<IDocument, IDocumentUndoManager>(size,
				new CacheListener<IDocument, IDocumentUndoManager>() {
					@Override
					public void entryRemoved(Entry<IDocument, IDocumentUndoManager> removed) {
						release(removed.getKey(), removed.getValue());
					}
				});
	}

	public void clear() {
		for (Entry<IDocument, IDocumentUndoManager> e : cache.entrySet()) {
			release(e.getKey(), e.getValue());
		}
		cache.clear();
	}

	public void hold(IDocument document) {
		IDocumentUndoManager mgr = cache.get(document);
		if (mgr != null) {
			return; /* already connected */
		}
		DocumentUndoManagerRegistry.connect(document);
		mgr = DocumentUndoManagerRegistry.getDocumentUndoManager(document);
		mgr.connect(this);
		cache.put(document, mgr);
	}

	public void release(IDocument document) {
		IDocumentUndoManager mgr = cache.remove(document);
		if (mgr == null) {
			return;
		}
		release(document, mgr);
	}

	private void release(IDocument document, IDocumentUndoManager mgr) {
		DocumentUndoManagerRegistry.disconnect(document);
		mgr.disconnect(this);
	}
}
