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

import site.ycsb.DBException;
import site.ycsb.ByteIterator;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;

/**
 * Volatile HashMap client.
 *
 * See {@code jnvm/README.md} for details.
 *
 * @author Anatole Lefort
 */
public class VolatileHashMapClient extends AbstractMapClient {

  private static final Phaser INIT = new Phaser(1);
  private static final AtomicInteger INIT_COUNT = new AtomicInteger(0);

  @Override
  public void init() throws DBException {
    super.init();

    int curInitCount = INIT_COUNT.getAndIncrement();

    if (curInitCount > 0) {
      INIT.awaitAdvance(0);
    } else {
      if (dotransactions) {
        try {
          FileInputStream fis = new FileInputStream(PMEM_FILE);
          ObjectInputStream ois = new ObjectInputStream(fis);
          backend = (Map<ByteIterator, Map<ByteIterator, ByteIterator>>) ois.readObject();
          ois.close();
          fis.close();
        } catch(Exception e) {
          throw new DBException(e);
        }
      } else {
        backend = new ConcurrentHashMap<>(initialCapacity);
      }
      INIT.arriveAndAwaitAdvance();
    }
  }

  @Override
  public void cleanup() throws DBException {
    int curInitCount = INIT_COUNT.decrementAndGet();
    if (curInitCount > 0) {
      INIT.awaitAdvance(1);
    } else {
      if (!dotransactions) {
        try {
          FileOutputStream fos = new FileOutputStream(PMEM_FILE);
          ObjectOutputStream oos = new ObjectOutputStream(fos);
          oos.writeObject(backend);
          oos.close();
          fos.close();
        } catch(Exception e) {
          throw new DBException(e);
        }
      }
      INIT.arriveAndAwaitAdvance();
    }
    super.cleanup();
  }

}
