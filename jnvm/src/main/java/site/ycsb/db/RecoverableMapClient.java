/**
 * Copyright (c) 2013-2015 YCSB contributors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License. See accompanying LICENSE file.
 *
 * Submitted by Chrisjan Matser on 10/11/2010.
 */
package site.ycsb.db;

import eu.telecomsudparis.jnvm.util.persistent.RecoverableStrongHashMap;
import eu.telecomsudparis.jnvm.util.persistent.RecoverableMap;

import site.ycsb.ByteIterator;
import site.ycsb.OffHeapStringByteIterator;
import site.ycsb.DBException;
import site.ycsb.Status;

import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;
import java.util.Set;
import javax.xml.ws.Holder;

/**
 * Recoverable map client.
 *
 * See {@code jnvm/README.md} for details.
 *
 * @author Anatole Lefort
 */
public abstract class RecoverableMapClient extends AbstractMapClient {

  private static final Phaser INIT = new Phaser(1);
  private static final AtomicInteger INIT_COUNT = new AtomicInteger(0);

  abstract void buildMap();

  /**
   * Initialize any state for this DB. Called once per DB instance; there is one
   * DB instance per client thread.
   */
  @Override
  public void init() throws DBException {
    super.init();

    int curInitCount = INIT_COUNT.getAndIncrement();

    if (curInitCount > 0) {
      INIT.awaitAdvance(0);
    } else {
      if (backend == null) {
        buildMap();
      }
      INIT.arriveAndAwaitAdvance();
    }
  }

  /**
   * Cleanup any state for this DB. Called once per DB instance; there is one DB
   * instance per client thread.
   */
  @Override
  public void cleanup() throws DBException {
    int curInitCount = INIT_COUNT.decrementAndGet();
    if (curInitCount > 0) {
      INIT.awaitAdvance(1);
    } else {
      backend = null;
      INIT.arriveAndAwaitAdvance();
    }
    super.cleanup();
  }

  /**
   * Read a record from the database. Each field/value pair from the result will
   * be stored in a HashMap.
   *
   * @param table
   *          The name of the table
   * @param key
   *          The record key of the record to read.
   * @param fields
   *          The list of fields to read, or null for all of them
   * @param result
   *          A HashMap of field/value pairs for the result
   * @return Zero on success, a non-zero error code on error
   */
  @Override
  public Status read(ByteIterator table, ByteIterator key, Set<ByteIterator> fields,
      Holder<Map<ByteIterator, ByteIterator>> result) {
    Map<? extends ByteIterator, ? extends ByteIterator> row = backend.get(key);
    if(row == null) {
      return Status.ERROR;
    }
    result.value.clear();
    result.value = (Map<ByteIterator, ByteIterator>) row;

    return Status.OK;
  }

  /**
   * Update a record in the database. Any field/value pairs in the specified
   * values HashMap will be written into the record with the specified record
   * key, overwriting any existing values with the same field name.
   *
   * @param table
   *          The name of the table
   * @param key
   *          The record key of the record to write.
   * @param values
   *          A HashMap of field/value pairs to update in the record
   * @return Zero on success, a non-zero error code on error
   */
  @Override
  public Status update(ByteIterator table, ByteIterator key, Map<ByteIterator, ByteIterator> values) {
    RecoverableMap<OffHeapStringByteIterator, OffHeapStringByteIterator> row =
        (RecoverableMap<OffHeapStringByteIterator, OffHeapStringByteIterator>) backend.get(key);
    if(row == null) {
      return Status.ERROR;
    }
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

    return Status.OK;
  }

  /**
   * Insert a record in the database. Any field/value pairs in the specified
   * values HashMap will be written into the record with the specified record
   * key.
   *
   * @param table
   *          The name of the table
   * @param key
   *          The record key of the record to insert.
   * @param values
   *          A HashMap of field/value pairs to insert in the record
   * @return Zero on success, a non-zero error code on error
   */
  @Override
  public Status insert(ByteIterator table, ByteIterator key, Map<ByteIterator, ByteIterator> values) {
    // Fast (no-guarantee) inserts
    /*
    Map<? extends ByteIterator, ? extends ByteIterator> row = new RecoverableStrongHashMap<>(values.size());
    OffHeapStringByteIterator.putAllAsOffHeapStringByteIterators(
            (Map<OffHeapStringByteIterator, OffHeapStringByteIterator>) row, values);
    */

    RecoverableMap<OffHeapStringByteIterator, OffHeapStringByteIterator> row =
        new RecoverableStrongHashMap<>(values.size());
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

    backend.put(key, row);

    row.fence();
    row.validate();
    // TODO: Would an implicit flush be good enough here?
    row.writebackHeader();
    row.fence();

    return Status.OK;
  }

  /**
   * Delete a record from the database.
   *
   * @param table
   *          The name of the table
   * @param key
   *          The record key of the record to delete.
   * @return Zero on success, a non-zero error code on error
   */
  @Override
  public Status delete(ByteIterator table, ByteIterator key) {
    backend.remove(key);
    return Status.OK;
  }

}

