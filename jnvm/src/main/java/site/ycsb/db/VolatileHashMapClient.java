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

import java.util.concurrent.ConcurrentHashMap;
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

  private static final int SIZE=0;
  private static final int VMAP=1;

  @Override
  public void init() throws DBException {
    super.init();

    if (dotransactions) {
      try {
        FileInputStream fis = new FileInputStream(PMEM_FILE);
        ObjectInputStream ois = new ObjectInputStream(fis);
        backend = (ConcurrentHashMap<String, Map<String, String>>) ois.readObject();
        ois.close();
        fis.close();
      } catch(Exception e) {
        throw new DBException(e);
      }
    } else {
      backend = new ConcurrentHashMap<>(initialCapacity);
    }
  }

  @Override
  public void cleanup() throws DBException {
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

    super.cleanup();
  }

}
