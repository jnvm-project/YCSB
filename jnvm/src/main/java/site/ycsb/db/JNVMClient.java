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
import eu.telecomsudparis.jnvm.util.persistent.PersistentHashMap;

import site.ycsb.DBException;

/**
 * JNVM client.
 *
 * See {@code jnvm/README.md} for details.
 *
 * @author Anatole Lefort
 */
public class JNVMClient extends AbstractMapClient {

  @Override
  public void init() throws DBException {
    super.init();

    pmemPool = new PMemPool(POOL_SIZE, PMEM_FILE);
    pmemPool.open();
    backend = new PersistentHashMap<>(null, null, initialCapacity, pmemPool);
  }

  @Override
  public void cleanup() throws DBException {
    super.cleanup();
    pmemPool.close();
    pmemPool = null;
  }

}
