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

import lib.util.persistent.PersistentString;

import java.util.Map;

/**
 * A ByteIterator that iterates through a string.
 */
public class PersistentStringByteIterator implements ByteIterator {
  private PersistentString str;
  private int off;

  /**
   * Put all of the entries of one map into the other, converting
   * String values into ByteIterators.
   */
  public static void putAllAsByteIterators(Map<ByteIterator, ByteIterator> out,
                                           Map<PersistentString, PersistentString> in) {
    for (Map.Entry<PersistentString, PersistentString> entry : in.entrySet()) {
      out.put(new PersistentStringByteIterator(entry.getKey()), new PersistentStringByteIterator(entry.getValue()));
    }
  }

  /**
   * Put all of the entries of one map into the other, converting
   * ByteIterator values into Strings.
   */
  public static void putAllAsStrings(Map<PersistentString, PersistentString> out,
                                     Map<ByteIterator, ByteIterator> in) {
    for (Map.Entry<ByteIterator, ByteIterator> entry : in.entrySet()) {
      out.put(entry.getKey().toPersistentString(), entry.getValue().toPersistentString());
    }
  }

  public static void putAllAsPersistentStringByteIterators(Map<PersistentStringByteIterator,
                                                           PersistentStringByteIterator> out,
                                                           Map<ByteIterator, ByteIterator> in) {
    for (Map.Entry<ByteIterator, ByteIterator> entry : in.entrySet()) {
      out.put((PersistentStringByteIterator) entry.getKey(), (PersistentStringByteIterator) entry.getValue());
    }
  }

  public PersistentStringByteIterator(PersistentString s) {
    this.str = s;
    this.off = 0;
  }

  public PersistentStringByteIterator(String s) {
    this(PersistentString.make(s));
  }

  @Override
  public boolean hasNext() {
    return off < str.length();
  }

  @Override
  public byte nextByte() {
    byte ret = (byte) str.toString().charAt(off);
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
      bytes[i] = (byte) str.toString().charAt(off + i);
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
      return ByteIterator.super.toString1();
    } else {
      return str.toString();
    }
  }

  @Override
  public int hashCode() {
    return this.str.hashCode();
  }

  @Override
  public PersistentString toPersistentString() {
    if (off > 0) {
      return ByteIterator.super.toPersistentString();
    } else {
      return str;
    }
  }

  public boolean equals(Object o) {
    if (o == null) {
      return false;
    } else if (o == this) {
      return true;
    } else if (o instanceof PersistentStringByteIterator) {
      PersistentStringByteIterator a = (PersistentStringByteIterator) o;
      return this.str.equals(a.str);
    } else if (o instanceof StringByteIterator) {
      StringByteIterator a = (StringByteIterator) o;
      return this.str.equals(a.toString());
    }
    return false;
  }

}
