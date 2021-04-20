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
import eu.telecomsudparis.jnvm.offheap.OffHeapCachedString;
import lib.util.persistent.PersistentString;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.Iterator;

/**
 * YCSB-specific buffer class.  ByteIterators are designed to support
 * efficient field generation, and to allow backend drivers that can stream
 * fields (instead of materializing them in RAM) to do so.
 * <p>
 * YCSB originially used String objects to represent field values.  This led to
 * two performance issues.
 * </p><p>
 * First, it leads to unnecessary conversions between UTF-16 and UTF-8, both
 * during field generation, and when passing data to byte-based backend
 * drivers.
 * </p><p>
 * Second, Java strings are represented internally using UTF-16, and are
 * built by appending to a growable array type (StringBuilder or
 * StringBuffer), then calling a toString() method.  This leads to a 4x memory
 * overhead as field values are being built, which prevented YCSB from
 * driving large object stores.
 * </p>
 * The StringByteIterator class contains a number of convenience methods for
 * backend drivers that convert between Map&lt;String,String&gt; and
 * Map&lt;String,ByteBuffer&gt;.
 *
 */
public interface ByteIterator extends Iterator<Byte> {
//public interface ByteIterator extends Iterator<Byte>, Comparable<ByteIterator> {

  @Override
  boolean hasNext();

  @Override
  default Byte next() {
    throw new UnsupportedOperationException();
  }

  byte nextByte();

  /** @return byte offset immediately after the last valid byte */
  default int nextBuf(byte[] buf, int bufOff) {
    int sz = bufOff;
    while (sz < buf.length && hasNext()) {
      buf[sz] = nextByte();
      sz++;
    }
    return sz;
  }

  long bytesLeft();

  @Override
  default void remove() {
    throw new UnsupportedOperationException();
  }

  /** Resets the iterator so that it can be consumed again. Not all
   * implementations support this call.
   * @throws UnsupportedOperationException if the implementation hasn't implemented
   * the method.
   */
  default void reset() {
    throw new UnsupportedOperationException();
  }

  /** Consumes remaining contents of this object, and returns them as a string. */
  default String toString1() {
    Charset cset = Charset.forName("UTF-8");
    CharBuffer cb = cset.decode(ByteBuffer.wrap(this.toArray()));
    return cb.toString();
  }

  /** Consumes remaining contents of this object, and returns them as a byte array. */
  default byte[] toArray() {
    long left = bytesLeft();
    if (left != (int) left) {
      throw new ArrayIndexOutOfBoundsException("Too much data to fit in one array!");
    }
    byte[] ret = new byte[(int) left];
    for (int i = 0; i < ret.length; i++) {
      ret[i] = nextByte();
    }
    return ret;
  }

/*
  //Not working
  default int compareTo(ByteIterator anotherByteIterator) {
    long len1 = this.bytesLeft();
    long len2 = anotherByteIterator.bytesLeft();
    long lim = Math.min(len1, len2);

    long k = 0;
    while (k < lim) {
      char c1 = (char) this.nextByte();
      char c2 = (char) anotherByteIterator.nextByte();
      if (c1 != c2) {
        return c1 - c2;
      }
      k++;
    }
    return (int)(len1 - len2);
  }
*/

/*
  //Interface default can't override Object method
  default boolean equals(Object o) {
    if (o == null) {
      return false;
    } else if (o == this) {
      return true;
    } else if (o instanceof ByteIterator) {
      ByteIterator a = (ByteIterator) o;
      long len1 = this.bytesLeft();
      long len2 = a.bytesLeft();
      if (len1 != len2) {
        return false;
      }
      int k = 0;
      while (k<len1) {
        byte b1 = this.nextByte();
        byte b2 = a.nextByte();
        if (b1 != b2) {
          return false;
        }
        k++;
      }
    }
  }
*/

  /** Consumes remaining contents of this object, and returns them as an offheap string. */
  default OffHeapString toOffHeapString() {
    //return new OffHeapString(this.toString());
    throw new UnsupportedOperationException("Not implemented");
  }

  default OffHeapCachedString toOffHeapCachedString() {
    //return new OffHeapCachedString(this.toString());
    throw new UnsupportedOperationException("Not implemented");
  }

  default OffHeapStringByteIterator toOffHeapStringByteIterator() {
    //return new OffHeapStringByteIterator(toOffHeapString());
    throw new UnsupportedOperationException("Not implemented");
  }

  default OffHeapCachedStringByteIterator toOffHeapCachedStringByteIterator() {
    //return new OffHeapCachedStringByteIterator(toOffHeapCachedString());
    throw new UnsupportedOperationException("Not implemented");
  }

  /** Consumes remaining contents of this object, and returns them as an pcj string. */
  default PersistentString toPersistentString() {
    //return new PersistentString(this.toString());
    throw new UnsupportedOperationException("Not implemented");
  }

  default PersistentStringByteIterator toPersistentStringByteIterator() {
    //return new PersistentStringByteIterator(toPersistentString());
    throw new UnsupportedOperationException("Not implemented");
  }

  default StringByteIterator toStringByteIterator() {
    //return new PersistentStringByteIterator(toPersistentString());
    throw new UnsupportedOperationException("Not implemented");
  }

}
