/**
 * Copyright (c) 2010-2016 Yahoo! Inc., 2017 YCSB contributors All rights reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License. See accompanying
 * LICENSE file.
 */

package site.ycsb;

import eu.telecomsudparis.jnvm.offheap.OffHeap;
import eu.telecomsudparis.jnvm.offheap.MemoryBlockHandle;
import eu.telecomsudparis.jnvm.offheap.OffHeapCachedString;

/**
 * A ByteIterator that iterates through a string.
 */
public class OffHeapCachedStringByteIterator extends OffHeapStringByteIterator implements Comparable<ByteIterator> {
  private static final long CLASS_ID = OffHeap.Klass.registerUserKlass(OffHeapCachedStringByteIterator.class);


  protected OffHeapCachedStringByteIterator(OffHeapCachedString s) {
    super(s);
  }

  public OffHeapCachedStringByteIterator(String s) {
    this(new OffHeapCachedString(s));
    OffHeap.getAllocator().blockFromOffset(super.str.getOffset()).setKlass(CLASS_ID);
  }

  public OffHeapCachedStringByteIterator(long offset) {
    this(new OffHeapCachedString(offset));
  }

  public OffHeapCachedStringByteIterator(MemoryBlockHandle block) {
    this(block.getOffset());
  }
  public OffHeapCachedStringByteIterator(Void v, long offset) {
    this(offset);
  }

  @Override
  public OffHeapCachedStringByteIterator toOffHeapCachedStringByteIterator() {
    return this;
  }

  @Override
  public long classId() {
    return CLASS_ID;
  }

}
