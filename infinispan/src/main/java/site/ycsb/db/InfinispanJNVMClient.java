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

import eu.telecomsudparis.jnvm.util.persistent.RecoverableStrongHashMap;
import eu.telecomsudparis.jnvm.util.persistent.RecoverableMap;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import javax.xml.ws.Holder;

import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This is a client implementation for Infinispan 5.x.
 */
public class InfinispanJNVMClient extends DB {
  private static final Log LOGGER = LogFactory.getLog(InfinispanJNVMClient.class);

  private static EmbeddedCacheManager infinispanManager;

  private static final Phaser INIT = new Phaser(1);
  private static final AtomicInteger INIT_COUNT = new AtomicInteger(0);

  public InfinispanJNVMClient() {
  }

  public void init() throws DBException {
    try {
      int curInitCount = INIT_COUNT.getAndIncrement();
      if (curInitCount > 0) {
        INIT.awaitAdvance(0);
      } else {
        infinispanManager = new DefaultCacheManager("infinispan-jnvm-config.xml");
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
      Map<ByteIterator, ByteIterator> row = null;
      Cache<ByteIterator, Map<ByteIterator, ByteIterator>> cache = infinispanManager.getCache(cacheName);
      row = cache.get(key);
      if (row == null) {
        return Status.ERROR;
      }
      result.value = row;

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
      RecoverableMap<OffHeapStringByteIterator, OffHeapStringByteIterator> row = null;
      Cache<ByteIterator, RecoverableMap<OffHeapStringByteIterator, OffHeapStringByteIterator>> cache =
          infinispanManager.getCache(cacheName);
      row = cache.get(key);
      synchronized(row) {
        if (row == null) {
          return Status.ERROR;
        } else {
          //OffHeapStringByteIterator.putAllAsOffHeapStringByteIterators(row, values);
          for (Map.Entry<ByteIterator, ByteIterator> entry : values.entrySet()) {
            OffHeapStringByteIterator oldVal =
                row.replaceValue(entry.getKey().toOffHeapStringByteIterator(),
                            entry.getValue().toOffHeapStringByteIterator());
            oldVal.invalidate();
          }
        }
      }

/*
      //Experimental thread-safe row updates using ISPN api to guard row
      //update closure. Performs worse than synchronized.
      cache.compute(key, (k, v) -> {
          Map<OffHeapStringByteIterator, OffHeapStringByteIterator> row = v;
          if (row == null) {
            return null;
          } else {
            for (Map.Entry<ByteIterator, ByteIterator> entry : values.entrySet()) {
              OffHeapStringByteIterator oldVal =
                  row.replace(entry.getKey().toOffHeapStringByteIterator(),
                              entry.getValue().toOffHeapStringByteIterator());
              oldVal.invalidate();
            }
          }
          return v;
        });
      cache.merge(key, values, (v1, v2) -> {
          //Map<? extends ByteIterator, ? extends ByteIterator> row = v1;
          Map<? super OffHeapStringByteIterator, ? super OffHeapStringByteIterator> row = v1;
          if (row == null) {
            System.exit(1);
            row = new RecoverableStrongHashMap<>(v2.size());
          }
          for (Map.Entry<ByteIterator, ByteIterator> entry : v2.entrySet()) {
            OffHeapStringByteIterator oldVal = (OffHeapStringByteIterator)
                row.replace(entry.getKey().toOffHeapStringByteIterator(),
                            entry.getValue().toOffHeapStringByteIterator());
            oldVal.invalidate();
          }
          return (Map<ByteIterator, ByteIterator>) row;
        });
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
      Map<? extends ByteIterator, ? extends ByteIterator> row = new RecoverableStrongHashMap<>(values.size());
      OffHeapStringByteIterator.putAllAsOffHeapStringByteIterators(
          (Map<OffHeapStringByteIterator, OffHeapStringByteIterator>) row, values);
      infinispanManager.getCache(cacheName).put(key, row);

/*
      //Experimental node insertion - avoids creating the row if already
      //present. Useless since that's never the case in normal execution.
      //Performs worse than normal.
      Cache<ByteIterator, Map<ByteIterator, ByteIterator>> cache =
          infinispanManager.getCache(cacheName);
      cache.computeIfAbsent(key, (k) -> {
          Map<? extends ByteIterator, ? extends ByteIterator> row =
              new RecoverableStrongHashMap<>(values.size());
          OffHeapStringByteIterator.putAllAsOffHeapStringByteIterators((Map<OffHeapStringByteIterator,
              OffHeapStringByteIterator>) row, values);
          return (Map<ByteIterator, ByteIterator>) row;
        });
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
      infinispanManager.getCache(cacheName).remove(key);
      return Status.OK;
    } catch (Exception e) {
      LOGGER.error(e);
      return Status.ERROR;
    }
  }
}
