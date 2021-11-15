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

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bigbase.carrot.compression.CodecFactory;
import org.bigbase.carrot.compression.CodecType;
import org.junit.BeforeClass;
import org.junit.Test;

public class BigSortedMapPerfTest {

  private static final Logger log = LogManager.getLogger(BigSortedMapPerfTest.class);

  static BigSortedMap map;
  static long totalLoaded;
  static long totalScanned = 0;

  @BeforeClass
  public static void setUp() {
    log.debug("Set up: block = 4096; Mem=" + 10000000);

    // BigSortedMap.setCompressionCodec(CodecFactory.getInstance().getCodec(CodecType.LZ4));

    BigSortedMap.setMaxBlockSize(4096);
    map = new BigSortedMap(100000000L);
    totalLoaded = 1;
    long start = System.currentTimeMillis();
    while (true) {
      byte[] key = ("KEY" + (totalLoaded)).getBytes();
      byte[] value = ("VALUE" + (totalLoaded)).getBytes();
      boolean res = map.put(key, 0, key.length, value, 0, value.length, 0);
      if (res == false) {
        totalLoaded--;
        break;
      }
      totalLoaded++;
      if (totalLoaded % 100000 == 0) {
        log.debug(
            "Loaded " + totalLoaded + " RAM alocated=" + BigSortedMap.getGlobalAllocatedMemory());
      }
    }
    long end = System.currentTimeMillis();
    log.debug(
        "Time to load="
            + totalLoaded
            + " ="
            + (end - start)
            + "ms"
            + " RPS="
            + (totalLoaded * 1000) / (end - start));
    log.debug("Total memory=" + BigSortedMap.getGlobalAllocatedMemory());
  }

  @Test
  public void testCountRecords() throws IOException {
    log.debug("testCountRecords");
    int n = 10;
    long start = System.currentTimeMillis();
    for (int i = 0; i < n; i++) {
      log.debug("Scan Run started " + i);
      totalScanned += countRecords();
      log.debug("Scan Run finished " + i);
    }
    long end = System.currentTimeMillis();

    log.debug(totalScanned * 1000 / (end - start) + " RPS");
    assertEquals(n * totalLoaded, totalScanned);
  }

  long countRecords() throws IOException {
    BigSortedMapScanner scanner = map.getScanner(0, 0, 0, 0);
    long counter = 0;
    while (scanner.hasNext()) {
      counter++;
      scanner.next();
    }
    scanner.close();
    return counter;
  }
}
