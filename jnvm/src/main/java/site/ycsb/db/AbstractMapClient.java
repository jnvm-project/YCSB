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

import eu.telecomsudparis.jnvm.PMemPool;

import site.ycsb.ByteIterator;
import site.ycsb.DB;
import site.ycsb.DBException;
import site.ycsb.Status;
import site.ycsb.Client;

import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.Set;
import java.util.Properties;
import javax.xml.ws.Holder;

/**
 * Map abstract client.
 *
 * See {@code jnvm/README.md} for details.
 *
 * @author Anatole Lefort
 */
public abstract class AbstractMapClient extends DB {

  protected static final String PMEM_FILE="/pmem0/pMemYCSB";
  protected static final long POOL_SIZE=4*1024*1024*1024L;

  protected PMemPool pmemPool;
  protected static Map<ByteIterator,
      Map<? extends ByteIterator, ? extends ByteIterator>> backend;

  protected int initialCapacity;
  protected boolean dotransactions;
  protected boolean dopreload;
  protected int threadcount;

  /**
   * Initialize any state for this DB. Called once per DB instance; there is one
   * DB instance per client thread.
   */
  @Override
  public void init() throws DBException {
    Properties props = getProperties();
    long recordcount =
        Long.parseLong(props.getProperty(Client.RECORD_COUNT_PROPERTY, Client.DEFAULT_RECORD_COUNT));
    initialCapacity = (recordcount > Integer.MAX_VALUE) ?
        Integer.MAX_VALUE : (int) recordcount;
    dotransactions =
        Boolean.valueOf(props.getProperty(Client.DO_TRANSACTIONS_PROPERTY, String.valueOf(true)));
    dopreload =
        Boolean.valueOf(props.getProperty(Client.DO_PRELOAD_PROPERTY, String.valueOf(false)));
    threadcount =
        Integer.parseInt(props.getProperty(Client.THREAD_COUNT_PROPERTY, "1"));
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
  public Status read(ByteIterator table, ByteIterator key, Set<ByteIterator> fields,
      Holder<Map<ByteIterator, ByteIterator>> result) {
    Map<ByteIterator, ByteIterator> row = (Map<ByteIterator, ByteIterator>) backend.get(key);
    if(row == null) {
      return Status.ERROR;
    }
    result.value.clear();
    result.value = row;
/*
    if(fields == null || fields.isEmpty()) {
      //StringByteIterator.putAllAsByteIterators(result.value, row);
      result.value = row;
    } else {
      for(ByteIterator field : fields) {
        result.value.put(field, row.get(field));
      }
    }
*/
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
  public Status scan(ByteIterator table, ByteIterator startkey, int recordcount,
      Set<ByteIterator> fields, Vector<HashMap<ByteIterator, ByteIterator>> result) {
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
    Map<ByteIterator, ByteIterator> row = (Map<ByteIterator, ByteIterator>) backend.get(key);
    if(row == null) {
      return Status.ERROR;
    }
    //StringByteIterator.putAllAsStrings(row, values);
    row.putAll(values);
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
    Map<ByteIterator, ByteIterator> row = new HashMap<>();
    //StringByteIterator.putAllAsStrings(row, values);
    row.putAll(values);
    backend.put(key, row);
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
