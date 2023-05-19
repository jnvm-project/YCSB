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

import eu.telecomsudparis.jnvm.util.persistent.AutoPersistMap;
import eu.telecomsudparis.jnvm.offheap.OffHeap;

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
 * meant to be used with JNVM AutoPersist-like HashMap backend
 *
 *   In comparison to other JNVM backends,
 *   this should be run without OffHeapStringByteIterators,
 *   but regular StringByteIterators.
 */
public class InfinispanAutoPersistClient extends DB {
  private static final Log LOGGER = LogFactory.getLog(InfinispanAutoPersistClient.class);

  private static EmbeddedCacheManager infinispanManager;

  private static final Phaser INIT = new Phaser(1);
  private static final AtomicInteger INIT_COUNT = new AtomicInteger(0);

  public InfinispanAutoPersistClient() {
  }

  public void init() throws DBException {
    try {
      int curInitCount = INIT_COUNT.getAndIncrement();
      if (curInitCount > 0) {
        INIT.awaitAdvance(0);
      } else {
        infinispanManager = new DefaultCacheManager("infinispan-autopersist-config.xml");
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
      OffHeap.startRecording();
      Map<ByteIterator, ByteIterator> row = null;
      Cache<ByteIterator, Map<ByteIterator, ByteIterator>> cache = infinispanManager.getCache(cacheName);
      row = cache.get(key);
      if (row == null) {
        return Status.ERROR;
      }
      result.value = row;
      OffHeap.stopRecording();

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
      OffHeap.startRecording();
      AutoPersistMap<OffHeapStringByteIterator, OffHeapStringByteIterator,
              ByteIterator, ByteIterator> row = null;
      Cache<ByteIterator, AutoPersistMap<OffHeapStringByteIterator, OffHeapStringByteIterator,
              ByteIterator, ByteIterator>> cache =
          infinispanManager.getCache(cacheName);
      row = cache.get(key);
      if (row == null) {
        return Status.ERROR;
      } else {
        for (Map.Entry<ByteIterator, ByteIterator> entry : values.entrySet()) {
          // This special call takes care of turning Convertible<> volatile objects into their
          // off-heap conterparts in a crash-consistent way whilst inserting them into the row map.
          row.putConvert(entry.getKey(), entry.getValue());
        }
      }
      OffHeap.stopRecording();

      return Status.OK;
    } catch (Exception e) {
      LOGGER.error(e);
      return Status.ERROR;
    }
  }

  public Status insert(ByteIterator table, ByteIterator key, Map<ByteIterator, ByteIterator> values) {
    String cacheName = table.toString();
    try {
      // Must use a transaction here as well for YCSB workload d
      OffHeap.startRecording();

      // AutoPersist-like Map is itself a Convertible<>,
      //   so it can be conveniently instanciated crash-consistently from its own volatile counterpart.
      // The underlying AutoPersist-like algorithm will do
      //   the (in-depth) migration of all reachable objects on-the-fly,
      //   since the whole sub-graph is required to implement Convertible<>.
      AutoPersistMap<OffHeapStringByteIterator, OffHeapStringByteIterator,
                     ByteIterator, ByteIterator> row =
          new AutoPersistMap<>(values);

      infinispanManager.getCache(cacheName).put(key, row);
      OffHeap.stopRecording();

      return Status.OK;
    } catch (Exception e) {
      LOGGER.error(e);
      return Status.ERROR;
    }
  }

  public Status delete(ByteIterator table, ByteIterator key) {
    String cacheName = table.toString();
    try {
      OffHeap.startRecording();
      infinispanManager.getCache(cacheName).remove(key);
      OffHeap.stopRecording();
      return Status.OK;
    } catch (Exception e) {
      LOGGER.error(e);
      return Status.ERROR;
    }
  }
}
