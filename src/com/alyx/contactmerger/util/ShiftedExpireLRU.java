package com.alyx.contactmerger.util;

import java.util.HashMap;
import java.util.LinkedHashMap;

import com.alyx.contactmerger.contacts.ContactDataMapper;

/**
 * An LRU cache for method calls that expires entries based on their time-to-live (TTL).
 * The expiration is calculated using the timestamp and hashcode to avoid cache stampedes.
 */
public class ShiftedExpireLRU extends LinkedHashMap<ContactDataMapper.MethodCall, Object> {
    private final int capacity;
    private final long ttl; // Time-to-live (TTL) for cache entries
    private final HashMap<ContactDataMapper.MethodCall, ContactDataMapper.MethodCall> keyNormalization;

    /**
     * Constructor to initialize the LRU cache with TTL and capacity.
     *
     * @param ttl      Time-to-live in milliseconds for cache entries
     * @param capacity Maximum number of entries in the cache
     */
    public ShiftedExpireLRU(long ttl, int capacity) {
        super(capacity, 0.75f, true); // LRU access order
        this.ttl = ttl;
        this.capacity = capacity;
        keyNormalization = new HashMap<>(capacity * 2); // Ensure enough capacity for normalization map
    }

    /**
     * Adds an entry to the cache and updates key normalization.
     *
     * @param key   The key of the cache entry (MethodCall)
     * @param value The value of the cache entry
     * @return The previous value associated with the key, or null if there was no mapping
     */
    @Override
    public synchronized Object put(ContactDataMapper.MethodCall key, Object value) {
        keyNormalization.put(key, key); // Normalize the key
        return super.put(key, value);
    }

    /**
     * Retrieves an entry from the cache, checking expiration and key normalization.
     *
     * @param key The key to retrieve the value for
     * @return The value associated with the key, or null if expired or not found
     */
    @Override
    public synchronized Object get(Object key) {
        if (!(key instanceof ContactDataMapper.MethodCall)) {
            return null;
        }

        ContactDataMapper.MethodCall ikey = (ContactDataMapper.MethodCall) key;
        ContactDataMapper.MethodCall k = keyNormalization.get(key);

        if (k == null) {
            keyNormalization.put(ikey, ikey);
            k = ikey;
        }

        // Check expiration: if the entry is expired, return null
        long expirationTime = k.created + ttl;
        if (expirationTime < System.currentTimeMillis()) {
            keyNormalization.remove(k); // Remove from normalization map if expired
            remove(k); // Optionally remove from main cache as well
            return null;
        }

        return super.get(key); // Return the value if not expired
    }

    /**
     * Removes the eldest entry when the cache exceeds its capacity.
     *
     * @param eldest The eldest entry in the cache
     * @return true if the eldest entry should be removed, false otherwise
     */
    @Override
    protected boolean removeEldestEntry(Entry<ContactDataMapper.MethodCall, Object> eldest) {
        return size() > capacity; // Ensure we don't exceed the cache capacity
    }
}
