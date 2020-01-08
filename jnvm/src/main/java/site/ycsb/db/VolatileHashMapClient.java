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

import eu.telecomsudparis.jnvm.util.persistent.PersistentHashMap;

import site.ycsb.DBException;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

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

    if (pmemPool.getSize() > 0) {
      try {
        byte[] sbytes = new byte[(int) pmemPool.BLOCK_SIZE];
        pmemPool.get(sbytes, SIZE);
        Integer size = (Integer) PersistentHashMap.toObject(sbytes);

        byte[] vmbytes = new byte[size];
        pmemPool.get(vmbytes, VMAP);
        backend = (ConcurrentHashMap<String, Map<String, String>>) PersistentHashMap.toObject(vmbytes);
      } catch(Exception e) {
        throw new DBException(e);
      }
    } else {
      backend = new ConcurrentHashMap<>();
    }
  }

  @Override
  public void cleanup() throws DBException {
    try {
      byte[] vmbytes = PersistentHashMap.toByteArray(backend);
      byte[] sbytes = PersistentHashMap.toByteArray(vmbytes.length);
      pmemPool.put(sbytes, SIZE);
      pmemPool.put(vmbytes, VMAP);
    } catch(Exception e) {
      throw new DBException(e);
    }

    super.cleanup();
  }

}
