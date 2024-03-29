/**
 * Copyright (c) 2015-2016 YCSB contributors. All rights reserved.
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

import java.util.Properties;

import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.client.hotrod.configuration.Configuration;

/**
 * Utility class to ensure only a single RemoteCacheManager is created.
 */
final class RemoteCacheManagerHolder {

  private static volatile RemoteCacheManager cacheManager = null;

  private RemoteCacheManagerHolder() {
  }

  static RemoteCacheManager getInstance(Properties props) {
    RemoteCacheManager result = cacheManager;
    if (result == null) {
      synchronized (RemoteCacheManagerHolder.class) {
        result = cacheManager;
        if (result == null) {
          ConfigurationBuilder cb = new ConfigurationBuilder().withProperties(props);
          Configuration conf = cb.build();
          result = new RemoteCacheManager(conf);
          cacheManager = new RemoteCacheManager(conf);
        }
      }
    }
    return result;
  }
}
