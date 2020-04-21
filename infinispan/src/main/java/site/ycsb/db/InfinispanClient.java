/**
 * Copyright (c) 2012-2016 YCSB contributors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License. See accompanying
 * LICENSE file.
 */

package site.ycsb.db;

import site.ycsb.ByteIterator;
import site.ycsb.DB;
import site.ycsb.DBException;
import site.ycsb.Status;

import org.infinispan.Cache;
import org.infinispan.atomic.AtomicMap;
import org.infinispan.atomic.AtomicMapLookup;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

/**
 * This is a client implementation for Infinispan 5.x.
 */
public class InfinispanClient extends DB {
  private static final Log LOGGER = LogFactory.getLog(InfinispanClient.class);

  // An optimisation for clustered mode
  private final boolean clustered;

  private EmbeddedCacheManager infinispanManager;

  public InfinispanClient() {
    clustered = Boolean.getBoolean("infinispan.clustered");
  }

  public void init() throws DBException {
    try {
      infinispanManager = new DefaultCacheManager("infinispan-config.xml");
      infinispanManager.getCache().start(); //Eager cache entry loading
    } catch (IOException e) {
      throw new DBException(e);
    }
  }

  public void cleanup() {
    infinispanManager.stop();
    infinispanManager = null;
  }

  public Status read(ByteIterator table, ByteIterator key, Set<ByteIterator> fields,
                     Map<ByteIterator, ByteIterator> result) {
    String cacheName = table.toString();
    try {
      Map<ByteIterator, ByteIterator> row;
      if (clustered) {
        row = AtomicMapLookup.getAtomicMap(infinispanManager.getCache(cacheName), key, false);
      } else {
        Cache<ByteIterator, Map<ByteIterator, ByteIterator>> cache = infinispanManager.getCache(cacheName);
        row = cache.get(key);
      }
      if (row != null) {
        result.clear();
        if (fields == null || fields.isEmpty()) {
          for(Map.Entry<ByteIterator, ByteIterator> value : row.entrySet()) {
            result.put(value.getKey(), value.getValue());
          }
        } else {
          for (ByteIterator field : fields) {
            result.put(field, row.get(field));
          }
        }
      }
      return Status.OK;
    } catch (Exception e) {
      LOGGER.error(e);
      return Status.ERROR;
    }
  }

  public Status scan(ByteIterator table, ByteIterator startkey, int recordcount,
      Set<ByteIterator> fields, Vector<HashMap<ByteIterator, ByteIterator>> result) {
    LOGGER.warn("Infinispan does not support scan semantics");
    return Status.OK;
  }

  public Status update(ByteIterator table, ByteIterator key, Map<ByteIterator, ByteIterator> values) {
    String cacheName = table.toString();
    try {
      if (clustered) {
        AtomicMap<ByteIterator, ByteIterator> row =
                AtomicMapLookup.getAtomicMap(infinispanManager.getCache(cacheName), key);
        for(Map.Entry<ByteIterator, ByteIterator> value : values.entrySet()) {
          row.put(value.getKey(), value.getValue());
        }
      } else {
        Cache<ByteIterator, Map<ByteIterator, ByteIterator>> cache = infinispanManager.getCache(cacheName);
        Map<ByteIterator, ByteIterator> row = cache.get(key);
        if (row == null) {
          row = new HashMap<>();
          for(Map.Entry<ByteIterator, ByteIterator> value : values.entrySet()) {
            row.put(value.getKey(), value.getValue());
          }
          cache.put(key, row);
        } else {
          for(Map.Entry<ByteIterator, ByteIterator> value : values.entrySet()) {
            row.put(value.getKey(), value.getValue());
          }
        }
      }

      return Status.OK;
    } catch (Exception e) {
      LOGGER.error(e);
      return Status.ERROR;
    }
  }

  public Status insert(ByteIterator table, ByteIterator key, Map<ByteIterator, ByteIterator> values) {
    String cacheName = table.toString();
    try {
      if (clustered) {
        AtomicMap<ByteIterator, ByteIterator> row =
                AtomicMapLookup.getAtomicMap(infinispanManager.getCache(cacheName), key);
        row.clear();
        for(Map.Entry<ByteIterator, ByteIterator> value : values.entrySet()) {
          row.put(value.getKey(), value.getValue());
        }
      } else {
        Map<ByteIterator, ByteIterator> row = new HashMap<>();
        for(Map.Entry<ByteIterator, ByteIterator> value : values.entrySet()) {
          row.put(value.getKey(), value.getValue());
        }
        infinispanManager.getCache(cacheName).put(key, row);
      }

      return Status.OK;
    } catch (Exception e) {
      LOGGER.error(e);
      return Status.ERROR;
    }
  }

  public Status delete(ByteIterator table, ByteIterator key) {
    String cacheName = table.toString();
    try {
      if (clustered) {
        AtomicMapLookup.removeAtomicMap(infinispanManager.getCache(cacheName), key);
      } else {
        infinispanManager.getCache(cacheName).remove(key);
      }
      return Status.OK;
    } catch (Exception e) {
      LOGGER.error(e);
      return Status.ERROR;
    }
  }
}
