/*
 Copyright (C) 2021-present Carrot, Inc.

 <p>This program is free software: you can redistribute it and/or modify it under the terms of the
 Server Side Public License, version 1, as published by MongoDB, Inc.

 <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 Server Side Public License for more details.

 <p>You should have received a copy of the Server Side Public License along with this program. If
 not, see <http://www.mongodb.com/licensing/server-side-public-license>.
*/
package org.bigbase.carrot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bigbase.carrot.compression.CodecFactory;
import org.bigbase.carrot.compression.CodecType;
import org.bigbase.carrot.util.Key;
import org.bigbase.carrot.util.UnsafeAccess;
import org.bigbase.carrot.util.Utils;
import org.junit.Test;
import org.junit.Ignore;

import static org.junit.Assert.*;

public class IndexBlockTest {

  private static final Logger log = LogManager.getLogger(IndexBlockTest.class);

  static {
    UnsafeAccess.debug = true;
  }

  @Test
  public void testAll() throws RetryOperationException, IOException {

    for (int i = 0; i < 10; i++) {
      log.debug("\nRun {}} \n", (i + 1));
      testPutGet();
      testPutGetWithCompressionLZ4();
      testPutGetWithCompressionLZ4HC();
      testPutGetDeleteFull();
      testPutGetDeleteFullWithCompressionLZ4();
      testPutGetDeleteFullWithCompressionLZ4HC();
      testPutGetDeletePartial();
      testPutGetDeletePartialWithCompressionLZ4();
      testPutGetDeletePartialWithCompressionLZ4HC();
      testAutomaticDataBlockMerge();
      testAutomaticDataBlockMergeWithCompressionLZ4();
      testAutomaticDataBlockMergeWithCompressionLZ4HC();
      testOverwriteSameValueSize();
      testOverwriteSameValueSizeWithCompressionLZ4();
      testOverwriteSameValueSizeWithCompressionLZ4HC();
      testOverwriteSmallerValueSize();
      testOverwriteSmallerValueSizeWithCompressionLZ4();
      testOverwriteSmallerValueSizeWithCompressionLZ4HC();
      testOverwriteLargerValueSize();
      testOverwriteLargerValueSizeWithCompressionLZ4();
      testOverwriteLargerValueSizeWithCompressionLZ4HC();
    }
    BigSortedMap.printGlobalMemoryAllocationStats();
    UnsafeAccess.mallocStats();
  }

  protected void freeKeys(ArrayList<Key> keys) {
    for (Key key : keys) {
      UnsafeAccess.free(key.address);
    }
  }

  @Ignore
  @Test
  public void testPutGet() {
    log.debug("testPutGet");
    IndexBlock ib = getIndexBlock(4096);
    ArrayList<Key> keys = fillIndexBlock(ib);
    for (Key key : keys) {
      long valuePtr = UnsafeAccess.malloc(key.length);
      long size = ib.get(key.address, key.length, valuePtr, key.length, Long.MAX_VALUE);
      assertEquals(size, key.length);
      int res = Utils.compareTo(key.address, key.length, valuePtr, key.length);
      assertEquals(0, res);
      UnsafeAccess.free(valuePtr);
    }
    ib.free();
    freeKeys(keys);
  }

  @Ignore
  @Test
  public void testPutGetWithCompressionLZ4() {
    log.debug("testPutGetWithCompressionLZ4");
    BigSortedMap.setCompressionCodec(CodecFactory.getInstance().getCodec(CodecType.LZ4));
    testPutGet();
    BigSortedMap.setCompressionCodec(CodecFactory.getInstance().getCodec(CodecType.NONE));
  }

  @Ignore
  @Test
  public void testPutGetWithCompressionLZ4HC() {
    log.debug("testPutGetWithCompressionLZ4HC");
    BigSortedMap.setCompressionCodec(CodecFactory.getInstance().getCodec(CodecType.LZ4HC));
    testPutGet();
    BigSortedMap.setCompressionCodec(CodecFactory.getInstance().getCodec(CodecType.NONE));
  }

  @Ignore
  @Test
  public void testAutomaticDataBlockMerge() {
    log.debug("testAutomaticDataBlockMerge");
    IndexBlock ib = getIndexBlock(4096);
    ArrayList<Key> keys = fillIndexBlock(ib);
    Utils.sortKeys(keys);

    int before = ib.getNumberOfDataBlock();

    // Delete half of records

    List<Key> toDelete = keys.subList(0, keys.size() / 2);
    for (Key key : toDelete) {
      OpResult res = ib.delete(key.address, key.length, Long.MAX_VALUE);
      assertSame(res, OpResult.OK);
    }
    int after = ib.getNumberOfDataBlock();
    log.debug("Before ={} After={}", before, after);
    assertTrue(before > after);
    ib.free();
    freeKeys(keys);
  }

  @Ignore
  @Test
  public void testAutomaticDataBlockMergeWithCompressionLZ4() {
    log.debug("testAutomaticDataBlockMergeWithCompressionLZ4");
    BigSortedMap.setCompressionCodec(CodecFactory.getInstance().getCodec(CodecType.LZ4));
    testAutomaticDataBlockMerge();
    BigSortedMap.setCompressionCodec(CodecFactory.getInstance().getCodec(CodecType.NONE));
  }

  @Ignore
  @Test
  public void testAutomaticDataBlockMergeWithCompressionLZ4HC() {
    log.debug("testAutomaticDataBlockMergeWithCompressionLZ4HC");
    BigSortedMap.setCompressionCodec(CodecFactory.getInstance().getCodec(CodecType.LZ4HC));
    testAutomaticDataBlockMerge();
    BigSortedMap.setCompressionCodec(CodecFactory.getInstance().getCodec(CodecType.NONE));
  }

  @Ignore
  @Test
  public void testPutGetDeleteFull() {
    log.debug("testPutGetDeleteFull");

    IndexBlock ib = getIndexBlock(4096);
    ArrayList<Key> keys = fillIndexBlock(ib);

    for (Key key : keys) {
      long valuePtr = UnsafeAccess.malloc(key.length);

      long size = ib.get(key.address, key.length, valuePtr, key.length, Long.MAX_VALUE);
      assertEquals(size, key.length);
      int res = Utils.compareTo(key.address, key.length, valuePtr, key.length);
      assertEquals(0, res);
      UnsafeAccess.free(valuePtr);
    }

    // now delete all
    List<Key> splitRequires = new ArrayList<Key>();
    for (Key key : keys) {
      OpResult result = ib.delete(key.address, key.length, Long.MAX_VALUE);
      if (result == OpResult.SPLIT_REQUIRED) {
        splitRequires.add(key);
        continue;
      }
      assertEquals(OpResult.OK, result);
      // try again
      result = ib.delete(key.address, key.length, Long.MAX_VALUE);
      assertEquals(OpResult.NOT_FOUND, result);
    }
    // Now try get them
    for (Key key : keys) {
      long valuePtr = UnsafeAccess.malloc(key.length);

      long size = ib.get(key.address, key.length, valuePtr, key.length, Long.MAX_VALUE);
      if (splitRequires.contains(key)) {
        assertTrue(size > 0);
      } else {
        assertEquals(DataBlock.NOT_FOUND, size);
      }
      UnsafeAccess.free(valuePtr);
    }
    ib.free();
    freeKeys(keys);
  }

  @Ignore
  @Test
  public void testPutGetDeleteFullWithCompressionLZ4() {
    log.debug("testPutGetDeleteFullWithCompressionLZ4");
    BigSortedMap.setCompressionCodec(CodecFactory.getInstance().getCodec(CodecType.LZ4));
    testPutGetDeleteFull();
    BigSortedMap.setCompressionCodec(CodecFactory.getInstance().getCodec(CodecType.NONE));
  }

  @Ignore
  @Test
  public void testPutGetDeleteFullWithCompressionLZ4HC() {
    log.debug("testPutGetDeleteFullWithCompressionLZ4HC");
    BigSortedMap.setCompressionCodec(CodecFactory.getInstance().getCodec(CodecType.LZ4HC));
    testPutGetDeleteFull();
    BigSortedMap.setCompressionCodec(CodecFactory.getInstance().getCodec(CodecType.NONE));
  }

  @Ignore
  @Test
  public void testPutGetDeletePartial() {
    log.debug("testPutGetDeletePartial");

    IndexBlock ib = getIndexBlock(4096);
    ArrayList<Key> keys = fillIndexBlock(ib);

    for (Key key : keys) {
      long valuePtr = UnsafeAccess.malloc(key.length);
      long size = ib.get(key.address, key.length, valuePtr, key.length, Long.MAX_VALUE);
      assertEquals(size, key.length);
      int res = Utils.compareTo(key.address, key.length, valuePtr, key.length);
      assertEquals(0, res);
      UnsafeAccess.free(valuePtr);
    }

    // now delete some
    List<Key> toDelete = keys.subList(0, keys.size() / 2);
    List<Key> splitRequires = new ArrayList<Key>();

    for (Key key : toDelete) {

      OpResult result = ib.delete(key.address, key.length, Long.MAX_VALUE);
      if (result == OpResult.SPLIT_REQUIRED) {
        splitRequires.add(key);
        continue;
      }
      assertEquals(OpResult.OK, result);
      // try again
      result = ib.delete(key.address, key.length, Long.MAX_VALUE);
      assertEquals(OpResult.NOT_FOUND, result);
    }
    // Now try get them
    for (Key key : toDelete) {
      long valuePtr = UnsafeAccess.malloc(key.length);
      long size = ib.get(key.address, key.length, valuePtr, key.length, Long.MAX_VALUE);
      if (splitRequires.contains(key)) {
        assertTrue(size > 0);
      } else {
        assertEquals(DataBlock.NOT_FOUND, size);
      }
      UnsafeAccess.free(valuePtr);
    }
    // Now get the rest
    for (Key key : keys.subList(keys.size() / 2, keys.size())) {
      long valuePtr = UnsafeAccess.malloc(key.length);
      long size = ib.get(key.address, key.length, valuePtr, key.length, Long.MAX_VALUE);
      assertEquals(size, key.length);
      int res = Utils.compareTo(key.address, key.length, valuePtr, key.length);
      assertEquals(0, res);
      UnsafeAccess.free(valuePtr);
    }

    ib.free();
    freeKeys(keys);
  }

  @Ignore
  @Test
  public void testPutGetDeletePartialWithCompressionLZ4() {
    log.debug("testPutGetDeletePartialWithCompressionLZ4");
    BigSortedMap.setCompressionCodec(CodecFactory.getInstance().getCodec(CodecType.LZ4));
    testPutGetDeletePartial();
    BigSortedMap.setCompressionCodec(CodecFactory.getInstance().getCodec(CodecType.NONE));
  }

  @Ignore
  @Test
  public void testPutGetDeletePartialWithCompressionLZ4HC() {
    log.debug("testPutGetDeletePartiallWithCompressionLZ4HC");
    BigSortedMap.setCompressionCodec(CodecFactory.getInstance().getCodec(CodecType.LZ4HC));
    testPutGetDeletePartial();
    BigSortedMap.setCompressionCodec(CodecFactory.getInstance().getCodec(CodecType.NONE));
  }

  @Ignore
  @Test
  public void testOverwriteSameValueSize() throws RetryOperationException, IOException {
    log.debug("testOverwriteSameValueSize");
    Random r = new Random();
    IndexBlock ib = getIndexBlock(4096);
    ArrayList<Key> keys = fillIndexBlock(ib);
    for (Key key : keys) {
      byte[] value = new byte[key.length];
      r.nextBytes(value);
      long valuePtr = UnsafeAccess.allocAndCopy(value, 0, value.length);
      long buf = UnsafeAccess.malloc(value.length);
      boolean res = ib.put(key.address, key.length, valuePtr, value.length, Long.MAX_VALUE, 0);
      assertTrue(res);
      long size = ib.get(key.address, key.length, buf, value.length, Long.MAX_VALUE);
      assertEquals(value.length, (int) size);
      assertEquals(0, Utils.compareTo(buf, value.length, valuePtr, value.length));
      UnsafeAccess.free(valuePtr);
      UnsafeAccess.free(buf);
    }
    scanAndVerify(ib, keys);
    ib.free();
    freeKeys(keys);
  }

  @Ignore
  @Test
  public void testOverwriteSameValueSizeWithCompressionLZ4()
      throws RetryOperationException, IOException {
    log.debug("testOverwriteSameValueSizeWithCompressionLZ4");
    BigSortedMap.setCompressionCodec(CodecFactory.getInstance().getCodec(CodecType.LZ4));
    testOverwriteSameValueSize();
    BigSortedMap.setCompressionCodec(CodecFactory.getInstance().getCodec(CodecType.NONE));
  }

  @Ignore
  @Test
  public void testOverwriteSameValueSizeWithCompressionLZ4HC()
      throws RetryOperationException, IOException {
    log.debug("testOverwriteSameValueSizeWithCompressionLZ4HC");
    BigSortedMap.setCompressionCodec(CodecFactory.getInstance().getCodec(CodecType.LZ4HC));
    testOverwriteSameValueSize();
    BigSortedMap.setCompressionCodec(CodecFactory.getInstance().getCodec(CodecType.NONE));
  }

  @Ignore
  @Test
  public void testOverwriteSmallerValueSize() throws RetryOperationException, IOException {
    log.debug("testOverwriteSmallerValueSize");
    Random r = new Random();
    IndexBlock ib = getIndexBlock(4096);
    ArrayList<Key> keys = fillIndexBlock(ib);
    for (Key key : keys) {
      byte[] value = new byte[key.length - 2];
      r.nextBytes(value);
      long valuePtr = UnsafeAccess.allocAndCopy(value, 0, value.length);
      long buf = UnsafeAccess.malloc(value.length);
      boolean res = ib.put(key.address, key.length, valuePtr, value.length, Long.MAX_VALUE, 0);
      assertTrue(res);
      long size = ib.get(key.address, key.length, buf, value.length, Long.MAX_VALUE);
      assertEquals(value.length, (int) size);
      assertEquals(0, Utils.compareTo(buf, value.length, valuePtr, value.length));
      UnsafeAccess.free(valuePtr);
      UnsafeAccess.free(buf);
    }
    scanAndVerify(ib, keys);
    ib.free();
    freeKeys(keys);
  }

  @Ignore
  @Test
  public void testOverwriteSmallerValueSizeWithCompressionLZ4()
      throws RetryOperationException, IOException {
    log.debug("testOverwriteSmallerValueSizeWithCompressionLZ4");
    BigSortedMap.setCompressionCodec(CodecFactory.getInstance().getCodec(CodecType.LZ4));
    testOverwriteSmallerValueSize();
    BigSortedMap.setCompressionCodec(CodecFactory.getInstance().getCodec(CodecType.NONE));
  }

  @Ignore
  @Test
  public void testOverwriteSmallerValueSizeWithCompressionLZ4HC()
      throws RetryOperationException, IOException {
    log.debug("testOverwriteSmallerValueSizeWithCompressionLZ4HC");
    BigSortedMap.setCompressionCodec(CodecFactory.getInstance().getCodec(CodecType.LZ4HC));
    testOverwriteSmallerValueSize();
    BigSortedMap.setCompressionCodec(CodecFactory.getInstance().getCodec(CodecType.NONE));
  }

  @Ignore
  @Test
  public void testOverwriteLargerValueSize() throws RetryOperationException, IOException {
    log.debug("testOverwriteLargerValueSize");
    Random r = new Random();
    IndexBlock ib = getIndexBlock(4096);
    ArrayList<Key> keys = fillIndexBlock(ib);
    // Delete half keys
    int toDelete = keys.size() / 2;
    for (int i = 0; i < toDelete; i++) {
      Key key = keys.remove(0);
      OpResult res = ib.delete(key.address, key.length, Long.MAX_VALUE);
      assertEquals(OpResult.OK, res);
      UnsafeAccess.free(key.address);
    }
    for (Key key : keys) {
      byte[] value = new byte[key.length + 2];
      r.nextBytes(value);
      long valuePtr = UnsafeAccess.allocAndCopy(value, 0, value.length);
      long buf = UnsafeAccess.malloc(value.length);
      boolean res = ib.put(key.address, key.length, valuePtr, value.length, Long.MAX_VALUE, 0);
      assertTrue(res);
      long size = ib.get(key.address, key.length, buf, value.length, Long.MAX_VALUE);
      assertEquals(value.length, (int) size);
      assertEquals(0, Utils.compareTo(buf, value.length, valuePtr, value.length));
      UnsafeAccess.free(valuePtr);
      UnsafeAccess.free(buf);
    }
    scanAndVerify(ib, keys);
    ib.free();
    freeKeys(keys);
  }

  @Ignore
  @Test
  public void testOverwriteLargerValueSizeWithCompressionLZ4()
      throws RetryOperationException, IOException {
    log.debug("testOverwriteLargerValueSizeWithCompressionLZ4");
    BigSortedMap.setCompressionCodec(CodecFactory.getInstance().getCodec(CodecType.LZ4));
    testOverwriteLargerValueSize();
    BigSortedMap.setCompressionCodec(CodecFactory.getInstance().getCodec(CodecType.NONE));
  }

  @Ignore
  @Test
  public void testOverwriteLargerValueSizeWithCompressionLZ4HC()
      throws RetryOperationException, IOException {
    log.debug("testOverwriteLargerValueSizeWithCompressionLZ4HC");
    BigSortedMap.setCompressionCodec(CodecFactory.getInstance().getCodec(CodecType.LZ4HC));
    testOverwriteLargerValueSize();
    BigSortedMap.setCompressionCodec(CodecFactory.getInstance().getCodec(CodecType.NONE));
  }

  protected void scanAndVerify(IndexBlock b, List<Key> keys)
      throws RetryOperationException, IOException {
    long buffer = 0;
    long tmp = 0;
    int prevLength = 0;
    IndexBlockScanner is = IndexBlockScanner.getScanner(b, 0, 0, 0, 0, Long.MAX_VALUE);
    DataBlockScanner bs = null;
    int count = 0;
    while ((bs = is.nextBlockScanner()) != null) {
      while (bs.hasNext()) {
        int len = bs.keySize();
        buffer = UnsafeAccess.malloc(len);
        bs.key(buffer, len);

        boolean result = contains(buffer, len, keys);
        assertTrue(result);
        bs.next();
        count++;
        if (count > 1) {
          // compare
          int res = Utils.compareTo(tmp, prevLength, buffer, len);
          assertTrue(res < 0);
          UnsafeAccess.free(tmp);
        }
        tmp = buffer;
        prevLength = len;
      }
      bs.close();
    }
    UnsafeAccess.free(tmp);
    is.close();
    assertEquals(keys.size(), count);
  }

  private boolean contains(long key, int size, List<Key> keys) {
    for (Key k : keys) {
      if (Utils.compareTo(k.address, k.length, key, size) == 0) {
        return true;
      }
    }
    return false;
  }

  protected IndexBlock getIndexBlock(int size) {
    IndexBlock ib = new IndexBlock(null, size);
    ib.setFirstIndexBlock();
    return ib;
  }

  protected ArrayList<Key> fillIndexBlock(IndexBlock b) throws RetryOperationException {
    ArrayList<Key> keys = new ArrayList<Key>();
    Random r = new Random();
    long seed = r.nextLong();
    r.setSeed(seed);
    log.debug("Fill seed={}", seed);
    int kvSize = 32;
    boolean result = true;
    while (result) {
      byte[] key = new byte[kvSize];
      r.nextBytes(key);
      long ptr = UnsafeAccess.malloc(kvSize);
      UnsafeAccess.copy(key, 0, ptr, kvSize);
      result = b.put(ptr, kvSize, ptr, kvSize, 0, 0);
      if (result) {
        keys.add(new Key(ptr, kvSize));
      } else {
        UnsafeAccess.free(ptr);
      }
    }
    log.debug(
        "Number of data blocks={} index block data size ={} num records={}",
        b.getNumberOfDataBlock(),
        b.getDataInBlockSize(),
        keys.size());
    return keys;
  }
}
