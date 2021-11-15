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
package org.bigbase.carrot.redis.sets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bigbase.carrot.BigSortedMap;
import org.bigbase.carrot.compression.CodecFactory;
import org.bigbase.carrot.compression.CodecType;
import org.bigbase.carrot.util.Key;
import org.bigbase.carrot.util.UnsafeAccess;
import org.bigbase.carrot.util.Utils;
import org.bigbase.carrot.util.Value;
import org.junit.Ignore;
import org.junit.Test;

public class SetScannerTest {

  private static final Logger log = LogManager.getLogger(SetScannerTest.class);

  BigSortedMap map;
  int valSize = 16;
  long n = 100000;

  static {
    // UnsafeAccess.debug = true;
  }

  private List<Value> getValues(long n) {
    List<Value> values = new ArrayList<Value>();
    Random r = new Random();
    long seed = r.nextLong();
    r.setSeed(seed);
    log.debug("VALUES SEED=" + seed);
    byte[] buf = new byte[valSize / 2];
    for (int i = 0; i < n; i++) {
      r.nextBytes(buf);
      long ptr = UnsafeAccess.malloc(valSize);
      UnsafeAccess.copy(buf, 0, ptr, buf.length);
      UnsafeAccess.copy(buf, 0, ptr + buf.length, buf.length);
      values.add(new Value(ptr, valSize));
    }
    return values;
  }

  private Key getKey() {
    long ptr = UnsafeAccess.malloc(valSize);
    byte[] buf = new byte[valSize];
    Random r = new Random();
    long seed = r.nextLong();
    r.setSeed(seed);
    log.debug("KEY SEED=" + seed);
    r.nextBytes(buf);
    UnsafeAccess.copy(buf, 0, ptr, valSize);
    return new Key(ptr, valSize);
  }

  private void setUp() {
    map = new BigSortedMap(1000000000);
  }

  // @Ignore
  @Test
  public void runAllNoCompression() throws IOException {
    BigSortedMap.setCompressionCodec(CodecFactory.getInstance().getCodec(CodecType.NONE));
    log.debug("");
    for (int i = 0; i < 1; i++) {
      log.debug("*************** RUN = " + (i + 1) + " Compression=NULL");
      allTests();
      BigSortedMap.printGlobalMemoryAllocationStats();
      UnsafeAccess.mallocStats.printStats();
    }
  }

  // @Ignore
  @Test
  public void runAllCompressionLZ4() throws IOException {
    BigSortedMap.setCompressionCodec(CodecFactory.getInstance().getCodec(CodecType.LZ4));
    log.debug("");
    for (int i = 0; i < 1; i++) {
      log.debug("*************** RUN = " + (i + 1) + " Compression=LZ4");
      allTests();
      BigSortedMap.printGlobalMemoryAllocationStats();
      UnsafeAccess.mallocStats.printStats();
    }
  }

  @Ignore
  @Test
  public void runAllCompressionLZ4HC() throws IOException {
    BigSortedMap.setCompressionCodec(CodecFactory.getInstance().getCodec(CodecType.LZ4HC));
    log.debug("");
    for (int i = 0; i < 10; i++) {
      log.debug("*************** RUN = " + (i + 1) + " Compression=LZ4HC");
      allTests();
      BigSortedMap.printGlobalMemoryAllocationStats();
      UnsafeAccess.mallocStats.printStats();
    }
  }

  private void allTests() throws IOException {
    long start = System.currentTimeMillis();
    setUp();
    testDirectScannerPerformance();
    tearDown();
    setUp();
    testReverseScannerPerformance();
    tearDown();
    setUp();
    testEdgeConditions();
    tearDown();
    setUp();
    testSingleFullScanner();
    tearDown();
    setUp();
    testSingleFullScannerReverse();
    tearDown();
    setUp();
    testSinglePartialScanner();
    tearDown();
    setUp();
    testSinglePartialScannerOpenStart();
    tearDown();
    setUp();
    testSinglePartialScannerOpenEnd();
    tearDown();
    setUp();
    testSinglePartialScannerReverse();
    tearDown();
    setUp();
    testSinglePartialScannerReverseOpenStart();
    tearDown();
    setUp();
    testSinglePartialScannerReverseOpenEnd();
    tearDown();
    long end = System.currentTimeMillis();
    log.debug("\nRUN in " + (end - start) + "ms");
  }

  private void loadData(Key key, List<Value> values) {
    long[] elemPtrs = new long[1];
    int[] elemSizes = new int[1];
    long count = 0;
    int n = values.size();
    for (int i = 0; i < n; i++) {
      elemPtrs[0] = values.get(i).address;
      elemSizes[0] = values.get(i).length;
      int num = Sets.SADD(map, key.address, key.length, elemPtrs, elemSizes);
      assertEquals(1, num);
      if (++count % 100000 == 0) {
        log.debug("add " + count);
      }
    }
  }

  @Ignore
  @Test
  public void testSingleFullScanner() throws IOException {

    log.debug("Test single full scanner - one key " + n + " elements");
    Key key = getKey();
    List<Value> values = getValues(n);
    List<Value> copy = copy(values);
    long start = System.currentTimeMillis();

    loadData(key, values);

    long end = System.currentTimeMillis();
    log.debug(
        "Total allocated memory ="
            + BigSortedMap.getGlobalAllocatedMemory()
            + " for "
            + n
            + " "
            + valSize
            + " byte values. Overhead="
            + ((double) BigSortedMap.getGlobalAllocatedMemory() / n - valSize)
            + " bytes per value. Time to load: "
            + (end - start)
            + "ms");

    BigSortedMap.printGlobalMemoryAllocationStats();

    assertEquals(n, Sets.SCARD(map, key.address, key.length));

    Random r = new Random();
    long seed = r.nextLong();
    r.setSeed(seed);
    log.debug("Test seed=" + seed);

    long card = 0;
    while ((card = Sets.SCARD(map, key.address, key.length)) > 0) {
      assertEquals(copy.size(), (int) card);
      /*DEBUG*/ log.debug("Set size=" + copy.size());
      deleteRandom(map, key.address, key.length, copy, r);
      SetScanner scanner = Sets.getScanner(map, key.address, key.length, false);
      int expected = copy.size();
      if (expected == 0 && scanner == null) {
        break;
      } else if (scanner == null) {
        fail("Scanner is null, but expected=" + expected);
      }
      int cc = 0;
      while (scanner.hasNext()) {
        cc++;
        scanner.next();
      }
      scanner.close();
      assertEquals(expected, cc);
    }

    assertEquals(0, (int) map.countRecords());
    assertEquals(0, (int) Sets.SCARD(map, key.address, key.length));
    Sets.DELETE(map, key.address, key.length);
    assertEquals(0, (int) Sets.SCARD(map, key.address, key.length));
    BigSortedMap.printGlobalMemoryAllocationStats();
    // Free memory
    UnsafeAccess.free(key.address);
    values.stream().forEach(x -> UnsafeAccess.free(x.address));
  }

  @Ignore
  @Test
  public void testEdgeConditions() throws IOException {

    byte[] zero1 = new byte[] {0};
    byte[] zero2 = new byte[] {0, 0};
    byte[] max1 =
        new byte[] {
          (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
          (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0
        };
    byte[] max2 =
        new byte[] {
          (byte) 0xff,
          (byte) 0xff,
          (byte) 0xff,
          (byte) 0xff,
          (byte) 0xff,
          (byte) 0xff,
          (byte) 0xff,
          (byte) 0xff,
          (byte) 0xff,
          (byte) 0xff,
          (byte) 0xff,
          (byte) 0xff,
          (byte) 0xff,
          (byte) 0xff,
          (byte) 0xff,
          (byte) 0xff
        };
    long zptr1 = UnsafeAccess.allocAndCopy(zero1, 0, zero1.length);
    int zptrSize1 = zero1.length;
    long zptr2 = UnsafeAccess.allocAndCopy(zero2, 0, zero2.length);
    int zptrSize2 = zero2.length;
    long mptr1 = UnsafeAccess.allocAndCopy(max1, 0, max1.length);
    int mptrSize1 = max1.length;
    long mptr2 = UnsafeAccess.allocAndCopy(max2, 0, max2.length);
    int mptrSize2 = max2.length;

    log.debug("Test edge conditions " + n + " elements");
    Key key = getKey();
    List<Value> values = getValues(n);
    long start = System.currentTimeMillis();
    loadData(key, values);
    long end = System.currentTimeMillis();

    Utils.sortKeys(values);

    log.debug(
        "Total allocated memory ="
            + BigSortedMap.getGlobalAllocatedMemory()
            + " for "
            + n
            + " "
            + valSize
            + " byte values. Overhead="
            + ((double) BigSortedMap.getGlobalAllocatedMemory() / n - valSize)
            + " bytes per value. Time to load: "
            + (end - start)
            + "ms");

    // Direct
    SetScanner scanner =
        Sets.getScanner(
            map, key.address, key.length, zptr1, zptrSize1, zptr2, zptrSize2, false, false);
    assertTrue(scanner.hasNext() == false);
    scanner.close();

    // Reverse
    scanner =
        Sets.getScanner(
            map, key.address, key.length, zptr1, zptrSize1, zptr2, zptrSize2, false, true);
    assertTrue(scanner == null);

    // Direct
    scanner =
        Sets.getScanner(
            map, key.address, key.length, mptr1, mptrSize1, mptr2, mptrSize2, false, false);
    assertTrue(scanner.hasNext() == false);
    scanner.close();

    // Reverse
    scanner =
        Sets.getScanner(
            map, key.address, key.length, mptr1, mptrSize1, mptr2, mptrSize2, false, true);
    assertTrue(scanner == null);

    Random r = new Random();
    long seed = r.nextLong();
    r.setSeed(seed);
    log.debug("Test seed=" + seed);

    int index = r.nextInt(values.size());
    int expected = index;
    Value v = values.get(index);

    // log.debug("Expected:");

    //    for(int i=0; i < index; i++) {
    //      Value vv = values.get(i);
    //      log.debug(Bytes.toHex(vv.address, vv.length));
    //    }
    // Direct
    scanner =
        Sets.getScanner(
            map, key.address, key.length, zptr1, zptrSize1, v.address, v.length, false, false);

    if (expected == 0) {
      assertTrue(scanner.hasNext() == false);
    } else {
      assertEquals(expected, Utils.count(scanner));
    }
    scanner.close();

    // log.debug("Result:");

    // Reverse
    scanner =
        Sets.getScanner(
            map, key.address, key.length, zptr1, zptrSize1, v.address, v.length, false, true);

    if (expected == 0) {
      assertTrue(scanner == null);
    } else {
      assertEquals(expected, Utils.countReverse(scanner));
      scanner.close();
    }
    // Always close ALL scanners

    index = r.nextInt(values.size());
    expected = values.size() - index;
    v = values.get(index);
    // Direct
    scanner =
        Sets.getScanner(
            map, key.address, key.length, v.address, v.length, mptr2, mptrSize2, false, false);

    if (expected == 0) {
      assertTrue(scanner.hasNext() == false);
    } else {
      assertEquals(expected, Utils.count(scanner));
    }
    scanner.close();

    // Reverse
    scanner =
        Sets.getScanner(
            map, key.address, key.length, v.address, v.length, mptr2, mptrSize2, false, true);

    if (expected == 0) {
      assertTrue(scanner == null);
    } else {
      assertEquals(expected, Utils.countReverse(scanner));
      scanner.close();
    }

    Sets.DELETE(map, key.address, key.length);
    assertEquals(0, (int) Sets.SCARD(map, key.address, key.length));
    BigSortedMap.printGlobalMemoryAllocationStats();
    // Free memory
    UnsafeAccess.free(key.address);
    values.stream().forEach(x -> UnsafeAccess.free(x.address));
  }

  @Ignore
  @Test
  public void testSingleFullScannerReverse() throws IOException {

    log.debug("Test single full scanner reverse - one key " + n + " elements");
    Key key = getKey();
    List<Value> values = getValues(n);
    List<Value> copy = copy(values);
    long start = System.currentTimeMillis();

    loadData(key, values);
    long end = System.currentTimeMillis();
    log.debug(
        "Total allocated memory ="
            + BigSortedMap.getGlobalAllocatedMemory()
            + " for "
            + n
            + " "
            + valSize
            + " byte values. Overhead="
            + ((double) BigSortedMap.getGlobalAllocatedMemory() / n - valSize)
            + " bytes per value. Time to load: "
            + (end - start)
            + "ms");

    BigSortedMap.printGlobalMemoryAllocationStats();

    assertEquals(n, Sets.SCARD(map, key.address, key.length));

    Random r = new Random();
    long seed = r.nextLong();
    r.setSeed(seed);
    log.debug("Test seed=" + seed);

    long card = 0;
    while ((card = Sets.SCARD(map, key.address, key.length)) > 0) {
      assertEquals(copy.size(), (int) card);
      /*DEBUG*/ log.debug("Set size=" + copy.size());
      deleteRandom(map, key.address, key.length, copy, r);
      SetScanner scanner = Sets.getScanner(map, key.address, key.length, false, true);
      int expected = copy.size();
      if (scanner == null) {
        assertEquals(0, expected);
        break;
      }
      int cc = 0;
      do {
        cc++;
        if (cc % 100000 == 0) {
          log.debug(cc);
        }
      } while (scanner.previous());

      scanner.close();
      assertEquals(expected, cc);
    }

    assertEquals(0, (int) map.countRecords());
    assertEquals(0, (int) Sets.SCARD(map, key.address, key.length));
    Sets.DELETE(map, key.address, key.length);
    assertEquals(0, (int) Sets.SCARD(map, key.address, key.length));
    BigSortedMap.printGlobalMemoryAllocationStats();
    // Free memory
    UnsafeAccess.free(key.address);
    values.stream().forEach(x -> UnsafeAccess.free(x.address));
  }

  @Ignore
  @Test
  public void testSinglePartialScanner() throws IOException {

    log.debug("Test single partial scanner - one key " + n + " elements");
    Key key = getKey();
    List<Value> values = getValues(n);
    long start = System.currentTimeMillis();
    loadData(key, values);
    long end = System.currentTimeMillis();
    Utils.sortKeys(values);
    List<Value> copy = copy(values);

    log.debug(
        "Total allocated memory ="
            + BigSortedMap.getGlobalAllocatedMemory()
            + " for "
            + n
            + " "
            + valSize
            + " byte values. Overhead="
            + ((double) BigSortedMap.getGlobalAllocatedMemory() / n - valSize)
            + " bytes per value. Time to load: "
            + (end - start)
            + "ms");

    BigSortedMap.printGlobalMemoryAllocationStats();

    assertEquals(n, Sets.SCARD(map, key.address, key.length));

    Random r = new Random();
    long seed = r.nextLong();
    r.setSeed(seed);
    log.debug("Test seed=" + seed);

    long card = 0;
    while ((card = Sets.SCARD(map, key.address, key.length)) > 0) {
      assertEquals(copy.size(), (int) card);
      /*DEBUG*/ log.debug("Set size=" + copy.size());
      deleteRandom(map, key.address, key.length, copy, r);
      if (copy.size() == 0) break;
      int startIndex = r.nextInt(copy.size());
      int endIndex = r.nextInt(copy.size() - startIndex) + startIndex;

      long startPtr = copy.get(startIndex).address;
      int startSize = copy.get(startIndex).length;
      long endPtr = copy.get(endIndex).address;
      int endSize = copy.get(endIndex).length;

      int expected = (int) (endIndex - startIndex);
      SetScanner scanner =
          Sets.getScanner(
              map, key.address, key.length, startPtr, startSize, endPtr, endSize, false);
      if (scanner == null && expected == 0) {
        continue;
      } else if (scanner == null) {
        fail("Scanner is null, but expected=" + expected);
      }
      int cc = 0;
      while (scanner.hasNext()) {
        cc++;
        scanner.next();
      }
      scanner.close();
      assertEquals(expected, cc);
    }

    assertEquals(0, (int) map.countRecords());
    assertEquals(0, (int) Sets.SCARD(map, key.address, key.length));
    Sets.DELETE(map, key.address, key.length);
    assertEquals(0, (int) Sets.SCARD(map, key.address, key.length));
    BigSortedMap.printGlobalMemoryAllocationStats();
    // Free memory
    UnsafeAccess.free(key.address);
    values.stream().forEach(x -> UnsafeAccess.free(x.address));
  }

  @Ignore
  @Test
  public void testSinglePartialScannerOpenStart() throws IOException {

    log.debug("Test single partial scanner open start - one key " + n + " elements");
    Key key = getKey();
    List<Value> values = getValues(n);
    long start = System.currentTimeMillis();
    loadData(key, values);
    long end = System.currentTimeMillis();
    Utils.sortKeys(values);
    List<Value> copy = copy(values);

    log.debug(
        "Total allocated memory ="
            + BigSortedMap.getGlobalAllocatedMemory()
            + " for "
            + n
            + " "
            + valSize
            + " byte values. Overhead="
            + ((double) BigSortedMap.getGlobalAllocatedMemory() / n - valSize)
            + " bytes per value. Time to load: "
            + (end - start)
            + "ms");

    BigSortedMap.printGlobalMemoryAllocationStats();

    assertEquals(n, Sets.SCARD(map, key.address, key.length));

    Random r = new Random();
    long seed = r.nextLong();
    r.setSeed(seed);
    log.debug("Test seed=" + seed);

    long card = 0;
    while ((card = Sets.SCARD(map, key.address, key.length)) > 0) {
      assertEquals(copy.size(), (int) card);
      /*DEBUG*/ log.debug("Set size=" + copy.size());
      deleteRandom(map, key.address, key.length, copy, r);
      if (copy.size() == 0) break;
      int startIndex = 0; // r.nextInt(copy.size());
      int endIndex = r.nextInt(copy.size() - startIndex) + startIndex;

      long startPtr = 0; // copy.get(startIndex).address;
      int startSize = 0; // copy.get(startIndex).length;
      long endPtr = copy.get(endIndex).address;
      int endSize = copy.get(endIndex).length;

      int expected = (int) (endIndex - startIndex);
      SetScanner scanner =
          Sets.getScanner(
              map, key.address, key.length, startPtr, startSize, endPtr, endSize, false);
      if (scanner == null && expected == 0) {
        continue;
      } else if (scanner == null) {
        fail("Scanner is null, but expected=" + expected);
      }
      int cc = 0;
      while (scanner.hasNext()) {
        cc++;
        scanner.next();
      }
      scanner.close();
      assertEquals(expected, cc);
    }

    assertEquals(0, (int) map.countRecords());
    assertEquals(0, (int) Sets.SCARD(map, key.address, key.length));
    Sets.DELETE(map, key.address, key.length);
    assertEquals(0, (int) Sets.SCARD(map, key.address, key.length));
    BigSortedMap.printGlobalMemoryAllocationStats();
    // Free memory
    UnsafeAccess.free(key.address);
    values.stream().forEach(x -> UnsafeAccess.free(x.address));
  }

  @Ignore
  @Test
  public void testSinglePartialScannerOpenEnd() throws IOException {

    log.debug("Test single partial scanner open end - one key " + n + " elements");
    Key key = getKey();
    List<Value> values = getValues(n);
    long start = System.currentTimeMillis();
    loadData(key, values);
    long end = System.currentTimeMillis();
    Utils.sortKeys(values);
    List<Value> copy = copy(values);

    log.debug(
        "Total allocated memory ="
            + BigSortedMap.getGlobalAllocatedMemory()
            + " for "
            + n
            + " "
            + valSize
            + " byte values. Overhead="
            + ((double) BigSortedMap.getGlobalAllocatedMemory() / n - valSize)
            + " bytes per value. Time to load: "
            + (end - start)
            + "ms");

    BigSortedMap.printGlobalMemoryAllocationStats();

    assertEquals(n, Sets.SCARD(map, key.address, key.length));

    Random r = new Random();
    long seed = r.nextLong();
    r.setSeed(seed);
    log.debug("Test seed=" + seed);

    long card = 0;
    while ((card = Sets.SCARD(map, key.address, key.length)) > 0) {
      assertEquals(copy.size(), (int) card);
      /*DEBUG*/ log.debug("Set size=" + copy.size());
      deleteRandom(map, key.address, key.length, copy, r);
      if (copy.size() == 0) break;
      int startIndex = r.nextInt(copy.size());
      int endIndex = copy.size(); // .nextInt(copy.size() - startIndex) + startIndex;

      long startPtr = copy.get(startIndex).address;
      int startSize = copy.get(startIndex).length;
      long endPtr = 0; // copy.get(endIndex).address;
      int endSize = 0; // copy.get(endIndex).length;

      int expected = (int) (endIndex - startIndex);
      SetScanner scanner =
          Sets.getScanner(
              map, key.address, key.length, startPtr, startSize, endPtr, endSize, false);
      if (scanner == null && expected == 0) {
        continue;
      } else if (scanner == null) {
        fail("Scanner is null, but expected=" + expected);
      }
      int cc = 0;
      while (scanner.hasNext()) {
        cc++;
        scanner.next();
      }
      scanner.close();
      assertEquals(expected, cc);
    }

    assertEquals(0, (int) map.countRecords());
    assertEquals(0, (int) Sets.SCARD(map, key.address, key.length));
    Sets.DELETE(map, key.address, key.length);
    assertEquals(0, (int) Sets.SCARD(map, key.address, key.length));
    BigSortedMap.printGlobalMemoryAllocationStats();
    // Free memory
    UnsafeAccess.free(key.address);
    values.stream().forEach(x -> UnsafeAccess.free(x.address));
  }

  @Ignore
  @Test
  public void testSinglePartialScannerReverse() throws IOException {

    log.debug("Test single partial scanner reverse - one key " + n + " elements");
    Key key = getKey();
    List<Value> values = getValues(n);
    long start = System.currentTimeMillis();
    loadData(key, values);
    long end = System.currentTimeMillis();
    Utils.sortKeys(values);
    List<Value> copy = copy(values);

    log.debug(
        "Total allocated memory ="
            + BigSortedMap.getGlobalAllocatedMemory()
            + " for "
            + n
            + " "
            + valSize
            + " byte values. Overhead="
            + ((double) BigSortedMap.getGlobalAllocatedMemory() / n - valSize)
            + " bytes per value. Time to load: "
            + (end - start)
            + "ms");

    BigSortedMap.printGlobalMemoryAllocationStats();

    assertEquals(n, Sets.SCARD(map, key.address, key.length));

    Random r = new Random();
    long seed = r.nextLong();
    r.setSeed(seed);
    log.debug("Test seed=" + seed);

    long card = 0;
    while ((card = Sets.SCARD(map, key.address, key.length)) > 0) {
      assertEquals(copy.size(), (int) card);
      /*DEBUG*/ log.debug("Set size=" + copy.size());
      deleteRandom(map, key.address, key.length, copy, r);
      if (copy.size() == 0) break;
      int startIndex = r.nextInt(copy.size());
      int endIndex = r.nextInt(copy.size() - startIndex) + startIndex;

      long startPtr = copy.get(startIndex).address;
      int startSize = copy.get(startIndex).length;
      long endPtr = copy.get(endIndex).address;
      int endSize = copy.get(endIndex).length;

      int expected = (int) (endIndex - startIndex);

      SetScanner scanner =
          Sets.getScanner(
              map, key.address, key.length, startPtr, startSize, endPtr, endSize, false, true);
      if (scanner == null && expected == 0) {
        continue;
      } else if (scanner == null) {
        fail("Scanner is null, but expected=" + expected);
      }
      int cc = 0;
      do {
        cc++;
      } while (scanner.previous());
      scanner.close();
      assertEquals(expected, cc);
    }

    assertEquals(0, (int) map.countRecords());
    assertEquals(0, (int) Sets.SCARD(map, key.address, key.length));
    Sets.DELETE(map, key.address, key.length);
    assertEquals(0, (int) Sets.SCARD(map, key.address, key.length));
    BigSortedMap.printGlobalMemoryAllocationStats();
    // Free memory
    UnsafeAccess.free(key.address);
    values.stream().forEach(x -> UnsafeAccess.free(x.address));
  }

  @Ignore
  @Test
  public void testSinglePartialScannerReverseOpenStart() throws IOException {

    log.debug("Test single partial scanner reverse open start - one key " + n + " elements");
    Key key = getKey();
    List<Value> values = getValues(n);
    long start = System.currentTimeMillis();
    loadData(key, values);
    long end = System.currentTimeMillis();
    Utils.sortKeys(values);
    List<Value> copy = copy(values);

    log.debug(
        "Total allocated memory ="
            + BigSortedMap.getGlobalAllocatedMemory()
            + " for "
            + n
            + " "
            + valSize
            + " byte values. Overhead="
            + ((double) BigSortedMap.getGlobalAllocatedMemory() / n - valSize)
            + " bytes per value. Time to load: "
            + (end - start)
            + "ms");

    BigSortedMap.printGlobalMemoryAllocationStats();

    assertEquals(n, Sets.SCARD(map, key.address, key.length));

    Random r = new Random();
    long seed = r.nextLong();
    r.setSeed(seed);
    log.debug("Test seed=" + seed);

    long card = 0;
    while ((card = Sets.SCARD(map, key.address, key.length)) > 0) {
      assertEquals(copy.size(), (int) card);
      /*DEBUG*/ log.debug("Set size=" + copy.size());
      deleteRandom(map, key.address, key.length, copy, r);
      if (copy.size() == 0) break;
      int startIndex = 0; // r.nextInt(copy.size());
      int endIndex = r.nextInt(copy.size() - startIndex) + startIndex;

      long startPtr = 0; // copy.get(startIndex).address;
      int startSize = 0; // copy.get(startIndex).length;
      long endPtr = copy.get(endIndex).address;
      int endSize = copy.get(endIndex).length;

      int expected = (int) (endIndex - startIndex);

      SetScanner scanner =
          Sets.getScanner(
              map, key.address, key.length, startPtr, startSize, endPtr, endSize, false, true);
      if (scanner == null && expected == 0) {
        continue;
      } else if (scanner == null) {
        fail("Scanner is null, but expected=" + expected);
      }
      int cc = 0;
      do {
        cc++;
      } while (scanner.previous());
      scanner.close();
      assertEquals(expected, cc);
    }

    assertEquals(0, (int) map.countRecords());
    assertEquals(0, (int) Sets.SCARD(map, key.address, key.length));
    Sets.DELETE(map, key.address, key.length);
    assertEquals(0, (int) Sets.SCARD(map, key.address, key.length));
    BigSortedMap.printGlobalMemoryAllocationStats();
    // Free memory
    UnsafeAccess.free(key.address);
    values.stream().forEach(x -> UnsafeAccess.free(x.address));
  }

  @Ignore
  @Test
  public void testSinglePartialScannerReverseOpenEnd() throws IOException {

    log.debug("Test single partial scanner reverse open end - one key " + n + " elements");
    Key key = getKey();
    List<Value> values = getValues(n);
    long start = System.currentTimeMillis();
    loadData(key, values);
    long end = System.currentTimeMillis();
    Utils.sortKeys(values);
    List<Value> copy = copy(values);

    log.debug(
        "Total allocated memory ="
            + BigSortedMap.getGlobalAllocatedMemory()
            + " for "
            + n
            + " "
            + valSize
            + " byte values. Overhead="
            + ((double) BigSortedMap.getGlobalAllocatedMemory() / n - valSize)
            + " bytes per value. Time to load: "
            + (end - start)
            + "ms");

    BigSortedMap.printGlobalMemoryAllocationStats();

    assertEquals(n, Sets.SCARD(map, key.address, key.length));

    Random r = new Random();
    long seed = r.nextLong();
    r.setSeed(seed);
    log.debug("Test seed=" + seed);

    long card = 0;
    while ((card = Sets.SCARD(map, key.address, key.length)) > 0) {
      assertEquals(copy.size(), (int) card);
      /*DEBUG*/ log.debug("Set size=" + copy.size());
      deleteRandom(map, key.address, key.length, copy, r);
      if (copy.size() == 0) break;
      int startIndex = r.nextInt(copy.size());
      int endIndex = copy.size(); // .nextInt(copy.size() - startIndex) + startIndex;

      long startPtr = copy.get(startIndex).address;
      int startSize = copy.get(startIndex).length;
      long endPtr = 0; // copy.get(endIndex).address;
      int endSize = 0; // copy.get(endIndex).length;

      int expected = (int) (endIndex - startIndex);

      SetScanner scanner =
          Sets.getScanner(
              map, key.address, key.length, startPtr, startSize, endPtr, endSize, false, true);
      if (scanner == null && expected == 0) {
        continue;
      } else if (scanner == null) {
        fail("Scanner is null, but expected=" + expected);
      }
      int cc = 0;
      do {
        cc++;
      } while (scanner.previous());
      scanner.close();
      assertEquals(expected, cc);
    }

    assertEquals(0, (int) map.countRecords());
    assertEquals(0, (int) Sets.SCARD(map, key.address, key.length));
    Sets.DELETE(map, key.address, key.length);
    assertEquals(0, (int) Sets.SCARD(map, key.address, key.length));
    BigSortedMap.printGlobalMemoryAllocationStats();
    // Free memory
    UnsafeAccess.free(key.address);
    values.stream().forEach(x -> UnsafeAccess.free(x.address));
  }

  @Ignore
  @Test
  public void testDirectScannerPerformance() throws IOException {
    // int n = 5000000; // 5M elements
    log.debug("Test direct scanner performance " + n + " elements");
    Key key = getKey();
    List<Value> values = getValues(n);
    long start = System.currentTimeMillis();
    loadData(key, values);
    long end = System.currentTimeMillis();

    log.debug(
        "Total allocated memory ="
            + BigSortedMap.getGlobalAllocatedMemory()
            + " for "
            + n
            + " "
            + valSize
            + " byte values. Overhead="
            + ((double) BigSortedMap.getGlobalAllocatedMemory() / n - valSize)
            + " bytes per value. Time to load: "
            + (end - start)
            + "ms");

    SetScanner scanner = Sets.getScanner(map, key.address, key.length, 0, 0, 0, 0, false, false);

    start = System.currentTimeMillis();
    long count = 0;
    while (scanner.hasNext()) {
      count++;
      scanner.next();
    }
    scanner.close();
    assertEquals(count, (long) n);
    end = System.currentTimeMillis();
    log.debug("Scanned " + n + " elements in " + (end - start) + "ms");
    // Free memory
    UnsafeAccess.free(key.address);
    values.stream().forEach(x -> UnsafeAccess.free(x.address));
  }

  @Ignore
  @Test
  public void testReverseScannerPerformance() throws IOException {
    // int n = 5000000; // 5M elements
    log.debug("Test reverse scanner performance " + n + " elements");
    Key key = getKey();
    List<Value> values = getValues(n);
    long start = System.currentTimeMillis();
    loadData(key, values);
    long end = System.currentTimeMillis();

    log.debug(
        "Total allocated memory ="
            + BigSortedMap.getGlobalAllocatedMemory()
            + " for "
            + n
            + " "
            + valSize
            + " byte values. Overhead="
            + ((double) BigSortedMap.getGlobalAllocatedMemory() / n - valSize)
            + " bytes per value. Time to load: "
            + (end - start)
            + "ms");

    SetScanner scanner = Sets.getScanner(map, key.address, key.length, 0, 0, 0, 0, false, true);

    start = System.currentTimeMillis();
    long count = 0;

    do {
      count++;
    } while (scanner.previous());
    scanner.close();
    assertEquals(count, (long) n);

    end = System.currentTimeMillis();
    log.debug("Scanned (reversed) " + n + " elements in " + (end - start) + "ms");
    // Free memory
    UnsafeAccess.free(key.address);
    values.stream().forEach(x -> UnsafeAccess.free(x.address));
  }

  private <T> List<T> copy(List<T> src) {
    List<T> copy = new ArrayList<T>();
    for (T t : src) {
      copy.add(t);
    }
    return copy;
  }

  private void deleteRandom(
      BigSortedMap map, long keyPtr, int keySize, List<Value> copy, Random r) {
    int toDelete = copy.size() < 10 ? copy.size() : r.nextInt((int) copy.size() / 2);
    for (int i = 0; i < toDelete; i++) {
      int n = r.nextInt(copy.size());
      Value v = copy.remove(n);
      int count = Sets.SREM(map, keyPtr, keySize, v.address, v.length);
      assertEquals(1, count);
    }
  }

  private void tearDown() {
    // Dispose
    map.dispose();
    UnsafeAccess.mallocStats.printStats();
  }
}
