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
 * This is a client implementation for Infinispan 9.4.x,
 * meant for use with JNVM JPFA persistence backend,
 *
 * i.e., use weak calls paired with JNVM failure-atomic blocks,
 * instead of strong (crash-consistent) calls.
 */
public class InfinispanJPFAClient extends DB {
  private static final Log LOGGER = LogFactory.getLog(InfinispanJPFAClient.class);

  private static EmbeddedCacheManager infinispanManager;

  private static final Phaser INIT = new Phaser(1);
  private static final AtomicInteger INIT_COUNT = new AtomicInteger(0);

  public InfinispanJPFAClient() {
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
      RecoverableMap<OffHeapStringByteIterator, OffHeapStringByteIterator> row = null;
      Cache<ByteIterator, RecoverableMap<OffHeapStringByteIterator, OffHeapStringByteIterator>> cache =
          infinispanManager.getCache(cacheName);
      row = cache.get(key);
      if (row == null) {
        return Status.ERROR;
      } else {
        for (Map.Entry<ByteIterator, ByteIterator> entry : values.entrySet()) {
          OffHeapStringByteIterator eKey = entry.getKey().toOffHeapStringByteIterator();
          OffHeapStringByteIterator eVal = entry.getValue().toOffHeapStringByteIterator();

          // Use the weaker variant replaceValue (no flush/fence), because we are in a FA section
          OffHeapStringByteIterator eOldVal = row.replaceValue(eKey, eVal);

          // NOTE: this is already done in the YCSB client
          //eVal.validate();

          // Old value was created by the YCSB client, outside the transaction,
          //   we need to manually call that methods for it to be logged and freed.
          eOldVal.invalidate();
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
      OffHeap.startRecording();
      Map<OffHeapStringByteIterator, OffHeapStringByteIterator> row = new RecoverableHashMap<>(values.size());
      infinispanManager.getCache(cacheName).put(key, row);
      for (Map.Entry<ByteIterator, ByteIterator> entry : values.entrySet()) {
        OffHeapStringByteIterator eKey = entry.getKey().toOffHeapStringByteIterator();
        OffHeapStringByteIterator eVal = entry.getValue().toOffHeapStringByteIterator();
        row.put(eKey, eVal);
        // Both the key and the value were created by the YCSB client, outside the transaction,
        //   and were already validated.
        //   We need not log then, hence, not to call validate().
        //
        // TODO: Is it fair to have the YCSB client validate these beforehand,
        //   right after creating them? (More efficient than in the FA section)
        //eKey.validate();
        //eVal.validate();
      }
      // This call is optional, since “row” was created inside the transaction.
      // JNVM will ignore it and not log it.
      // Newly created objects inside transactions are already tracked
      //   and will be automically validated at the end of it.
      ((RecoverableMap) row).validate();
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
      infinispanManager.getCache(cacheName).remove(key);
      return Status.OK;
    } catch (Exception e) {
      LOGGER.error(e);
      return Status.ERROR;
    }
  }
}
