package com.cgoab.offline.util;

import java.util.LinkedHashMap;
import java.util.Map;

public class LRUCache<K, V> extends LinkedHashMap<K, V> {

	private static final long serialVersionUID = 1L;
	private int capacity;

	public LRUCache(int capacity) {
		super(64, 0.75f, true);
		this.capacity = capacity;
	}

	@Override
	protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
		return size() > capacity;
	}

	public interface CacheListener<K, V> {
		/**
		 * Called when an entry is removed from the cache.
		 * 
		 * @param removed
		 */
		void entryRemoved(Map.Entry<K, V> removed);
	}
}
