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

import lib.util.persistent.Transaction;
import lib.util.persistent.PersistentString;
import lib.util.persistent.PersistentObject;
import lib.util.persistent.ObjectPointer;
import lib.util.persistent.types.ObjectField;
import lib.util.persistent.types.ObjectType;
import lib.util.persistent.ComparableWith;
import lib.util.persistent.EquatesWith;

import java.util.Map;

/**
 * A ByteIterator that iterates through a string.
 */
public class PersistentStringByteIterator
    extends PersistentObject
    implements Comparable<PersistentStringByteIterator>, ComparableWith<PersistentString>,
               EquatesWith<PersistentString>, ByteIterator {
  private static final ObjectField<PersistentString> STR = new ObjectField<>(PersistentString.class);
  private static final ObjectType<PersistentStringByteIterator> TYPE =
      ObjectType.withFields(PersistentStringByteIterator.class, STR);

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
      out.put(entry.getKey().toPersistentStringByteIterator(), entry.getValue().toPersistentStringByteIterator());
    }
  }

  public PersistentStringByteIterator(PersistentString s) {
    super(TYPE);
    this.off = 0;
    Transaction.run(() -> {
        setObjectField(STR, s);
      }
    );
  }

  public PersistentStringByteIterator(String s) {
    this(PersistentString.make(s));
  }

  public PersistentStringByteIterator(ObjectType<? extends PersistentStringByteIterator> type) {
    super(type);
  }

  public PersistentStringByteIterator(ObjectPointer<? extends PersistentStringByteIterator> p) {
    super(p);
  }

  public PersistentString str() {
    return getObjectField(STR);
  }

  @Override
  public boolean hasNext() {
    return off < this.str().length();
  }

  @Override
  public byte nextByte() {
    byte ret = (byte) this.str().toString().charAt(off);
    off++;
    return ret;
  }

  @Override
  public long bytesLeft() {
    return this.str().length() - off;
  }

  @Override
  public void reset() {
    off = 0;
  }

  @Override
  public byte[] toArray() {
    byte[] bytes = new byte[(int) bytesLeft()];
    for (int i = 0; i < bytes.length; i++) {
      bytes[i] = (byte) this.str().toString().charAt(off + i);
    }
    off = (int) this.str().length();
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
      return this.str().toString();
    }
  }

  @Override
  public PersistentString toPersistentString() {
    if (off > 0) {
      return ByteIterator.super.toPersistentString();
    } else {
      return this.str();
    }
  }

  @Override
  public PersistentStringByteIterator toPersistentStringByteIterator() {
    return this;
  }

  @Override
  public int compareTo(PersistentStringByteIterator anotherStringByteIterator) {
    return str().compareTo(anotherStringByteIterator.str());
  }

  @Override
  public int compareWith(PersistentString anotherString) {
    return str().compareTo(anotherString);
  }

  @Override
  public int equivalentHash() {
    return str().hashCode();
  }

  @Override
  public boolean equatesWith(PersistentString anotherString) {
    return str().equals(anotherString);
  }

  @Override
  public int hashCode() {
    return this.str().hashCode();
  }

  public boolean equals(Object o) {
    if (o == null) {
      return false;
    } else if (o == this) {
      return true;
    } else if (o instanceof PersistentStringByteIterator) {
      PersistentStringByteIterator a = (PersistentStringByteIterator) o;
      return this.str().equals(a.str());
    } else if (o instanceof StringByteIterator) {
      StringByteIterator a = (StringByteIterator) o;
      return this.str().equals(a.toString());
    }
    return false;
  }

}
