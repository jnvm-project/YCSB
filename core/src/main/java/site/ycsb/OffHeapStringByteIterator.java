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

import eu.telecomsudparis.jnvm.offheap.OffHeapString;
import eu.telecomsudparis.jnvm.offheap.OffHeapObject;
import eu.telecomsudparis.jnvm.offheap.OffHeap;
import eu.telecomsudparis.jnvm.offheap.MemoryBlockHandle;

import java.util.Map;

/**
 * A ByteIterator that iterates through a string.
 */
public class OffHeapStringByteIterator extends ByteIterator implements OffHeapObject {
  private static final long CLASS_ID = OffHeap.Klass.registerUserKlass(OffHeapStringByteIterator.class, 8L);
  private OffHeapString str;
  private int off;

  /**
   * Put all of the entries of one map into the other, converting
   * String values into ByteIterators.
   */
  public static void putAllAsByteIterators(Map<ByteIterator, ByteIterator> out, Map<OffHeapString, OffHeapString> in) {
    for (Map.Entry<OffHeapString, OffHeapString> entry : in.entrySet()) {
      out.put(new OffHeapStringByteIterator(entry.getKey()), new OffHeapStringByteIterator(entry.getValue()));
    }
  }

  /**
   * Put all of the entries of one map into the other, converting
   * ByteIterator values into Strings.
   */
  public static void putAllAsStrings(Map<OffHeapString, OffHeapString> out, Map<ByteIterator, ByteIterator> in) {
    for (Map.Entry<ByteIterator, ByteIterator> entry : in.entrySet()) {
      out.put(entry.getKey().toOffHeapString(), entry.getValue().toOffHeapString());
    }
  }

  public static void putAllAsOffHeapStringByteIterators(Map<OffHeapStringByteIterator, OffHeapStringByteIterator> out,
                                                        Map<ByteIterator, ByteIterator> in) {
    for (Map.Entry<ByteIterator, ByteIterator> entry : in.entrySet()) {
      out.put((OffHeapStringByteIterator) entry.getKey(), (OffHeapStringByteIterator) entry.getValue());
    }
  }

  public OffHeapStringByteIterator(OffHeapString s) {
    this.str = s;
    this.off = 0;
    OffHeap.getAllocator().blockFromOffset(str.getOffset()).setKlass(CLASS_ID);
  }

  public OffHeapStringByteIterator(String s) {
    this(new OffHeapString(s));
  }

  public OffHeapStringByteIterator(long offset) {
    this.str = new OffHeapString(offset);
    this.off = 0;
  }

  public OffHeapStringByteIterator(MemoryBlockHandle block) {
    this(block.getOffset());
  }

  @Override
  public boolean hasNext() {
    return off < str.length();
  }

  @Override
  public byte nextByte() {
    byte ret = (byte) str.charAt(off);
    off++;
    return ret;
  }

  @Override
  public long bytesLeft() {
    return str.length() - off;
  }

  @Override
  public void reset() {
    off = 0;
  }

  @Override
  public byte[] toArray() {
    byte[] bytes = new byte[(int) bytesLeft()];
    for (int i = 0; i < bytes.length; i++) {
      bytes[i] = (byte) str.charAt(off + i);
    }
    off = (int) str.length();
    return bytes;
  }

  /**
   * Specialization of general purpose toString() to avoid unnecessary
   * copies.
   * <p>
   * Creating a new StringByteIterator, then calling toString()
   * yields the original String object, and does not perform any copies
   * or String conversion operations.
   * </p>
   */
  @Override
  public String toString() {
    if (off > 0) {
      return super.toString();
    } else {
      return str.toString();
    }
  }

  @Override
  public int hashCode() {
    return this.str.hashCode();
  }

  @Override
  public OffHeapString toOffHeapString() {
    if (off > 0) {
      return super.toOffHeapString();
    } else {
      return str;
    }
  }

  public boolean equals(Object o) {
    if (o == null) {
      return false;
    } else if (o == this) {
      return true;
    } else if (o instanceof OffHeapStringByteIterator) {
      OffHeapStringByteIterator a = (OffHeapStringByteIterator) o;
      return this.str.equals(a.str);
    } else if (o instanceof StringByteIterator) {
      StringByteIterator a = (StringByteIterator) o;
      return this.str.equals(a.toString());
    }
    return false;
  }

  public long getOffset() {
    return str.getOffset();
  }
  public void attach(long offset) {
    str.attach(offset);
  }
  public void detach() {
    str.detach();
  }
  public long size() {
    return str.size();
  }
  public long classId() {
    return CLASS_ID;
  }
  public long length() {
    return str.length();
  }
  public long addressFromFieldOffset(
        long fieldOffset) {
    return str.addressFromFieldOffset(fieldOffset);
  }
  public void destroy() {
    str.destroy();
  }

}
