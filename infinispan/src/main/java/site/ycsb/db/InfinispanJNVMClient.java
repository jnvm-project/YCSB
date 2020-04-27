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
import site.ycsb.OffHeapStringByteIterator;
import site.ycsb.DB;
import site.ycsb.DBException;
import site.ycsb.Status;

import org.infinispan.Cache;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

import eu.telecomsudparis.jnvm.offheap.OffHeapString;
import eu.telecomsudparis.jnvm.util.persistent.RecoverableHashMap;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

/**
 * This is a client implementation for Infinispan 5.x.
 */
public class InfinispanJNVMClient extends DB {
  private static final Log LOGGER = LogFactory.getLog(InfinispanJNVMClient.class);


  private EmbeddedCacheManager infinispanManager;

  public InfinispanJNVMClient() {
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
    OffHeapString keyName = key.toOffHeapString();
    try {
      Map<OffHeapString, OffHeapString> row;
      Cache<OffHeapString, Map<OffHeapString, OffHeapString>> cache = infinispanManager.getCache(cacheName);
      row = cache.get(keyName);
      if (row != null) {
        result.clear();
        if (fields == null || fields.isEmpty()) {
          OffHeapStringByteIterator.putAllAsByteIterators(result, row);
        } else {
          for (ByteIterator field : fields) {
            result.put(field, new OffHeapStringByteIterator(row.get(field.toOffHeapString())));
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
    OffHeapString keyName = key.toOffHeapString();
    try {
      Cache<OffHeapString, Map<OffHeapString, OffHeapString>> cache = infinispanManager.getCache(cacheName);
      Map<OffHeapString, OffHeapString> row = cache.get(keyName);
      if (row == null) {
        row = new RecoverableHashMap<>(values.size());
        OffHeapStringByteIterator.putAllAsStrings(row, values);
        cache.put(keyName, row);
      } else {
        OffHeapStringByteIterator.putAllAsStrings(row, values);
      }

      return Status.OK;
    } catch (Exception e) {
      LOGGER.error(e);
      return Status.ERROR;
    }
  }

  public Status insert(ByteIterator table, ByteIterator key, Map<ByteIterator, ByteIterator> values) {
    String cacheName = table.toString();
    OffHeapString keyName = key.toOffHeapString();
    try {
      Map<OffHeapString, OffHeapString> row = new RecoverableHashMap<>(values.size());
      OffHeapStringByteIterator.putAllAsStrings(row, values);
      infinispanManager.getCache(cacheName).put(keyName, row);

      return Status.OK;
    } catch (Exception e) {
      LOGGER.error(e);
      return Status.ERROR;
    }
  }

  public Status delete(ByteIterator table, ByteIterator key) {
    String cacheName = table.toString();
    OffHeapString keyName = key.toOffHeapString();
    try {
      infinispanManager.getCache(cacheName).remove(keyName);
      return Status.OK;
    } catch (Exception e) {
      LOGGER.error(e);
      return Status.ERROR;
    }
  }
}
