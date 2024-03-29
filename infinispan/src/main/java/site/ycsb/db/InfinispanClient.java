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
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import javax.xml.ws.Holder;

import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This is a client implementation for Infinispan 9.4.X,
 * meant for use with ISPN file persistence backend
 *
 * We replaced Cache<String, Map<String, String>>
 *   with Cache<ByteIterator, Map<ByteIterator, ByteIterator>>
 * to match and be consistent with other backends.
 */
public class InfinispanClient extends DB {
  private static final Log LOGGER = LogFactory.getLog(InfinispanClient.class);

  private static EmbeddedCacheManager infinispanManager;

  private static final Phaser INIT = new Phaser(1);
  private static final AtomicInteger INIT_COUNT = new AtomicInteger(0);

  public InfinispanClient() {
  }

  public void init() throws DBException {
    try {
      int curInitCount = INIT_COUNT.getAndIncrement();
      if (curInitCount > 0) {
        INIT.awaitAdvance(0);
      } else {
        infinispanManager = new DefaultCacheManager("infinispan-config.xml");
        infinispanManager.getCache().start(); //Eager cache entry loading
        INIT.arriveAndAwaitAdvance();
      }
    } catch (IOException e) {
      throw new DBException(e);
    }
  }

  public void cleanup() {
    int curInitCount = INIT_COUNT.decrementAndGet();
    if (curInitCount > 0) {
      INIT.awaitAdvance(1);
    } else {
      infinispanManager.stop();
      infinispanManager = null;
      INIT.arriveAndAwaitAdvance();
    }
  }

  public Status read(ByteIterator table, ByteIterator key, Set<ByteIterator> fields,
                     Holder<Map<ByteIterator, ByteIterator>> result) {
    String cacheName = table.toString();
    try {
      Map<ByteIterator, ByteIterator> row;
      Cache<ByteIterator, Map<ByteIterator, ByteIterator>> cache = infinispanManager.getCache(cacheName);
      row = cache.get(key);

      /*
      Map<String, String> row;
      Cache<String, Map<String, String>> cache = infinispanManager.getCache(cacheName);
      row = cache.get(key.toString());
      */

      if (row == null) {
        return Status.ERROR;
      }
      result.value = row;

      // Do not bother returning only selected fields, always return all of them.
      /*
      if (row != null) {
        result.value.clear();
        if (fields == null || fields.isEmpty()) {
          //result.putAll(row);
          StringByteIterator.putAllAsByteIterators(result.value, row);
        } else {
          for (ByteIterator field : fields) {
            //result.put(field, row.get(field));
            result.value.put(field, new StringByteIterator(row.get(field.toString())));
          }
        }
      }
      */
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
      Cache<ByteIterator, Map<ByteIterator, ByteIterator>> cache = infinispanManager.getCache(cacheName);
      Map<ByteIterator, ByteIterator> row = cache.get(key);

      /*
      Cache<String, Map<String, String>> cache = infinispanManager.getCache(cacheName);
      Map<String, String> row = cache.get(key.toString());
      */

      if (row == null) {
        //row = new HashMap<>();
        return Status.ERROR; //Should be found, no silent fail
      }
      row.putAll(values);
      cache.put(key, row); //always put back into the store, for the persistent layer to properly work

      /*
      StringByteIterator.putAllAsStrings(row, values);
      cache.put(key.toString(), row); //always put back into the store, for the persistent layer to properly work
      */

      return Status.OK;
    } catch (Exception e) {
      LOGGER.error(e);
      return Status.ERROR;
    }
  }

  public Status insert(ByteIterator table, ByteIterator key, Map<ByteIterator, ByteIterator> values) {
    String cacheName = table.toString();
    try {
      Map<ByteIterator, ByteIterator> row = new HashMap<>();
      row.putAll(values);
      infinispanManager.getCache(cacheName).put(key, row);

      /*
      Map<String, String> row = new HashMap<>();
      StringByteIterator.putAllAsStrings(row, values);
      infinispanManager.getCache(cacheName).put(key.toString(), row);
      */

      return Status.OK;
    } catch (Exception e) {
      LOGGER.error(e);
      return Status.ERROR;
    }
  }

  public Status delete(ByteIterator table, ByteIterator key) {
    String cacheName = table.toString();
    try {
      //infinispanManager.getCache(cacheName).remove(key.toString());

      infinispanManager.getCache(cacheName).remove(key);
      return Status.OK;
    } catch (Exception e) {
      LOGGER.error(e);
      return Status.ERROR;
    }
  }
}
