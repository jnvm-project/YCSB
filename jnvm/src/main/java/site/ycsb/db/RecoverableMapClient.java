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

import eu.telecomsudparis.jnvm.offheap.OffHeap;
import eu.telecomsudparis.jnvm.offheap.OffHeapString;
import eu.telecomsudparis.jnvm.util.persistent.RecoverableHashMap;

import site.ycsb.ByteIterator;
import site.ycsb.DBException;
import site.ycsb.Status;

import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.Set;

/**
 * Recoverable hash map client.
 *
 * See {@code jnvm/README.md} for details.
 *
 * @author Anatole Lefort
 */
public class RecoverableMapClient extends AbstractMapClient {

  private static final long RMAP_OFFSET=16;

  protected Map<OffHeapString, Map<OffHeapString, OffHeapString>> backend;
  protected Map<String, OffHeapString> columns = new HashMap<>();

  /**
   * Initialize any state for this DB. Called once per DB instance; there is one
   * DB instance per client thread.
   */
  @Override
  public void init() throws DBException {
    super.init();
    if (dotransactions) {
      backend = new RecoverableHashMap(OffHeap.baseAddr() + RMAP_OFFSET);
    } else {
      backend = new RecoverableHashMap(initialCapacity);
      System.out.println(((RecoverableHashMap)backend).getOffset());
      System.out.println(((RecoverableHashMap)backend).getOffset() - OffHeap.baseAddr());
    }
  }

  /**
   * Cleanup any state for this DB. Called once per DB instance; there is one DB
   * instance per client thread.
   */
  @Override
  public void cleanup() throws DBException {
    backend = null;
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
  public Status read(String table, String key, Set<String> fields,
      Map<String, ByteIterator> result) {
    Map<OffHeapString, OffHeapString> row = backend.get(key);
    if(row == null) {
      return Status.ERROR;
    }
    result.clear();
    if(fields == null || fields.isEmpty()) {
      for (Map.Entry<String, OffHeapString> column : columns.entrySet()) {
        OffHeapString value = row.get(column.getValue());
        if (value != null) {
          result.put(column.getKey(), new OffHeapStringByteIterator(value));
        }
      }
    } else {
      for(String field : fields) {
        result.put(field, new OffHeapStringByteIterator(row.get(field)));
      }
    }
    return Status.OK;
  }

  /**
   * Perform a range scan for a set of records in the database. Each field/value
   * pair from the result will be stored in a HashMap.
   *
   * Cassandra CQL uses "token" method for range scan which doesn't always yield
   * intuitive results.
   *
   * @param table
   *          The name of the table
   * @param startkey
   *          The record key of the first record to read.
   * @param recordcount
   *          The number of records to read
   * @param fields
   *          The list of fields to read, or null for all of them
   * @param result
   *          A Vector of HashMaps, where each HashMap is a set field/value
   *          pairs for one record
   * @return Zero on success, a non-zero error code on error
   */
  @Override
  public Status scan(String table, String startkey, int recordcount,
      Set<String> fields, Vector<HashMap<String, ByteIterator>> result) {
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
  public Status update(String table, String key, Map<String, ByteIterator> values) {
    RecoverableHashMap<OffHeapString, OffHeapString> row = (RecoverableHashMap) backend.get(key);
    if(row == null) {
      return Status.ERROR;
    }
    for (Map.Entry<String, ByteIterator> entry : values.entrySet()) {
      OffHeapString ohs = row.replace(entry.getKey(), new OffHeapString(entry.getValue().toString()));
      if (ohs != null) {
        ohs.destroy();
      }
    }
    //TODO This insertion should not be required
    //backend.put(key, row);
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
  public Status insert(String table, String key, Map<String, ByteIterator> values) {
    Map<OffHeapString, OffHeapString> row = new RecoverableHashMap<>(10);
    for (Map.Entry<String, ByteIterator> entry : values.entrySet()) {
      String kk = entry.getKey();
      OffHeapString k;
      if (columns.containsKey(kk)) {
        k = columns.get(kk);
      } else {
        k = new OffHeapString(kk);
        columns.put(kk, k);
      }
      row.put(k, new OffHeapString(entry.getValue().toString()));
    }
    backend.put(new OffHeapString(key), row);
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
  public Status delete(String table, String key) {
    backend.remove(key);
    return Status.OK;
  }

}

