/**
 * In-Memory High Performance Caching Engine
 * Handles TTL, Tag-based Invalidation, and Memory Safeguards
 */

class CacheEngine {
  constructor() {
    this.store = new Map();
    this.tagMap = new Map();
  }

  /**
   * Set a cached item with optional TTL (in seconds) and optional tags
   */
  set(key, value, ttlSeconds = 300, tags = []) {
    const expiresAt = ttlSeconds > 0 ? Date.now() + (ttlSeconds * 1000) : null;
    this.store.set(key, { value, expiresAt, tags });

    tags.forEach(tag => {
      if (!this.tagMap.has(tag)) {
        this.tagMap.set(tag, new Set());
      }
      this.tagMap.get(tag).add(key);
    });
  }

  /**
   * Get a cached item, returning null if expired or missing
   */
  get(key) {
    const item = this.store.get(key);
    if (!item) return null;

    if (item.expiresAt && Date.now() > item.expiresAt) {
      this.del(key);
      return null;
    }

    return item.value;
  }

  /**
   * Delete a key from cache
   */
  del(key) {
    const item = this.store.get(key);
    if (item && item.tags) {
      item.tags.forEach(tag => {
        const keysForTag = this.tagMap.get(tag);
        if (keysForTag) {
          keysForTag.delete(key);
        }
      });
    }
    this.store.delete(key);
  }

  /**
   * Invalidate all keys matching a specific tag (e.g. 'dashboard', 'statistics', 'profiles')
   */
  invalidateByTag(tag) {
    const keys = this.tagMap.get(tag);
    if (keys) {
      keys.forEach(key => this.del(key));
      this.tagMap.delete(tag);
    }
  }

  /**
   * Clear all cache entries
   */
  clear() {
    this.store.clear();
    this.tagMap.clear();
  }

  /**
   * Get cache metrics
   */
  getStats() {
    let activeKeys = 0;
    let expiredKeys = 0;
    const now = Date.now();

    for (const [key, item] of this.store.entries()) {
      if (item.expiresAt && now > item.expiresAt) {
        expiredKeys++;
      } else {
        activeKeys++;
      }
    }

    return {
      totalKeys: this.store.size,
      activeKeys,
      expiredKeys,
      tagsCount: this.tagMap.size,
    };
  }
}

module.exports = new CacheEngine();
