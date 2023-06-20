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

import eu.telecomsudparis.jnvm.util.persistent.RecoverableHashMap;
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
 * This is a client implementation for Infinispan 9.4.x,
 * meant for use with JNVM StrongHashMap persistence backend.
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
    // Note: ISPN has no notion of next key or key/value pairs ordering at all
    //
    // Still, we could emulate a scan operation by requesting *by hand* specific keys,
    //   and cheat this with our knowledge of how the client named the keys.
    //
    // Alternatively, we could also use ISPN CacheStream API to return a sequence of key/values pairs, and do either:
    //   1- .limit(recordcount) to get whatever pairs for a N iterations (constant)
    //   OR
    //   2- .filter() to get the right sequence of pairs, altough that means always iterating over the whole cache
    // Option 1 might be preferable since it does not always iterate over the whole Cache,
    //   although it might return the same keySet on each invocation.
    //
    // Best solution might be, after all, to manually construct the matching KeySet
    //   and perform a getAll() operation on the ISPN Cache.
    return Status.OK;
  }

  public Status update(ByteIterator table, ByteIterator key, Map<ByteIterator, ByteIterator> values) {
    String cacheName = table.toString();
    try {
      RecoverableMap<OffHeapStringByteIterator, OffHeapStringByteIterator> row = null;
      Cache<ByteIterator, RecoverableMap<OffHeapStringByteIterator, OffHeapStringByteIterator>> cache =
          infinispanManager.getCache(cacheName);
      row = cache.get(key);
      // Note: row proxies are never deleted with the StrongHashMap backend,
      //   thus they can be used to guard on concurrent updates.
      synchronized(row) {
        if (row == null) {
          return Status.ERROR;
        } else {
          // Are YCSB update commands supposed to execute atomically?
          // If so, we need an atomic putAll() call instead of looping columns 1 by 1.
          // Note: Not too much of an issue, YCSB defaults issue update commands with 1 column only.
          row.fence(); // ensure generated (new) values are persisted before being inserted.
          for (Map.Entry<ByteIterator, ByteIterator> entry : values.entrySet()) {
            OffHeapStringByteIterator entryKey = entry.getKey().toOffHeapStringByteIterator();
            OffHeapStringByteIterator entryVal = entry.getValue().toOffHeapStringByteIterator();

            //Ensure new value is persisted before mapping is updated
            // NOTE: this is already done in the YCSB client
            //entryVal.validate();
            //entryVal.flush();
            //entryVal.fence();

            //Update & flush mapping
            OffHeapStringByteIterator oldVal = row.replaceValueStrong(entryKey, entryVal);

            //Ensure mapping is updated before recycling old value
            // TODO: Free old values in a single block to reduce number of fences
            oldVal.fence();
            oldVal.invalidate();
          }
        }
      }

      // Experimental thread-safe row updates using ISPN api
      //   to perform update operations atomically using a closure.
      // Performs worse than synchronized.
      /*
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
      // Warning: not only used for YCSB load
      // => need a correct implementation for workload D
      //
      //  1- make new row map and ensure that all reachable PMEM objects are flushed and validated
      //  2- put row inside StrongHashMap backend (& flush relevant mutated PMEM locations in the backend)
      //  3- fence
      //  4- validate row object
      //
      //  If step 2 is atomic, we can also consider doing (1),(4) -> fence -> (2) instead.
      //
      // Optimizations:
      //   - might want to add a switch for YCSB_load that deactivate fences,
      //     alternatively, YCSB_load could be performed with flush/fences turned off altogether if not benchmarked.

      // Fast (no-guarantee) inserts
      /*
      Map<? extends ByteIterator, ? extends ByteIterator> row = new RecoverableHashMap<>(values.size());
      OffHeapStringByteIterator.putAllAsOffHeapStringByteIterators(
          (Map<OffHeapStringByteIterator, OffHeapStringByteIterator>) row, values);
      infinispanManager.getCache(cacheName).put(key, row);
      */

      RecoverableMap<OffHeapStringByteIterator,
              OffHeapStringByteIterator> row = new RecoverableHashMap<>(values.size());
      for (Map.Entry<ByteIterator, ByteIterator> entry : values.entrySet()) {
        OffHeapStringByteIterator entryKey = entry.getKey().toOffHeapStringByteIterator();
        OffHeapStringByteIterator entryVal = entry.getValue().toOffHeapStringByteIterator();

        // NOTE: this is already done in the YCSB client
        //entryKey.validate();
        //entryVal.validate();
        // Flushing key/value pairs accounts for more than half of the total latency of insert operations
        // TODO: Is it fair to flush k/v pairs when created in the YCSB client to save time here?
        //   => does not change throughput measure, only the reported latency of insert operations
        //entryKey.flush();
        //entryVal.flush();

        row.put(entryKey, entryVal);
      }
      row.flush();

      infinispanManager.getCache(cacheName).put(key, row);

      row.fence();
      row.validate();
      // TODO: Would an implicit flush be good enough here?
      row.writebackHeader();
      row.fence();

      // Experimental node insertion - avoids creating the row if already present.
      // Useless since that's never the case in normal execution.
      // Performs worse than normal.
      /*
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
      // Not present in any workloads,
      //   do not worry too much about implementing this.
      infinispanManager.getCache(cacheName).remove(key);
      return Status.OK;
    } catch (Exception e) {
      LOGGER.error(e);
      return Status.ERROR;
    }
  }
}
