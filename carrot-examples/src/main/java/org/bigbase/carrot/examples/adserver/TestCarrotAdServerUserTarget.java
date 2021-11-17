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
package org.bigbase.carrot.examples.adserver;

import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bigbase.carrot.BigSortedMap;
import org.bigbase.carrot.compression.CodecFactory;
import org.bigbase.carrot.compression.CodecType;
import org.bigbase.carrot.redis.hashes.Hashes;
import org.bigbase.carrot.redis.zsets.ZSets;
import org.bigbase.carrot.util.UnsafeAccess;
import org.bigbase.carrot.util.Utils;

/**
 * ----- Data structures to keep user targeting:
 *
 * <p>7. UserActionWords: ZSET keeps user behavior userId -> {word,score} We record user actions for
 * every ad he/she acts on in the following way: if user acts on ad, we get the list of words
 * targeted by this ad and increment score for every word in the user's ordered set. 8.
 * UserViewWords: ZSET - the same as above but only for views (this data set is much bigger than in
 * 7.) 9. UserViewAds: HASH keeps history of all ads shown to a user during last XXX minutes, hours,
 * days. 10 UserActionAds: HASH keeps history of ads user clicked on during last XX minutes, hours,
 * days.
 *
 * <p>Results:
 *
 * <p>Redis 6.0.10 = 992,291,824 Carrot no compression = 254,331,520 Carrot LZ4 compression =
 * 234,738,048 Carrot LZ4HC compression = 227,527,936
 *
 * <p>Notes:
 *
 * <p>The test uses synthetic data, which is mostly random and not compressible
 */
public class TestCarrotAdServerUserTarget {

  private static final Logger log = LogManager.getLogger(TestCarrotAdServerUserTarget.class);

  static final int MAX_ADS = 10000;
  static final int MAX_WORDS = 10000;
  static final int MAX_USERS = 1000;

  public static void main(String[] args) {
    runTestNoCompression();
    runTestCompressionLZ4();
    runTestCompressionLZ4HC();
  }

  private static void runTestNoCompression() {
    log.debug("\nTest , compression = None");
    BigSortedMap.setCompressionCodec(CodecFactory.getInstance().getCodec(CodecType.NONE));
    runTest();
  }

  private static void runTestCompressionLZ4() {
    log.debug("\nTest , compression = LZ4");
    BigSortedMap.setCompressionCodec(CodecFactory.getInstance().getCodec(CodecType.LZ4));
    runTest();
  }

  private static void runTestCompressionLZ4HC() {
    log.debug("\nTest , compression = LZ4HC");
    BigSortedMap.setCompressionCodec(CodecFactory.getInstance().getCodec(CodecType.LZ4HC));
    runTest();
  }

  private static void runTest() {
    BigSortedMap map = new BigSortedMap(1000000000L);
    doUserViewWords(map);
    doUserActionWords(map);
    doUserViewAds(map);
    doUserActionAds(map);
    long memory = BigSortedMap.getGlobalAllocatedMemory();
    log.debug("Total memory={}", memory);
    map.dispose();
  }

  private static void doUserViewWords(BigSortedMap map) {
    // SET
    log.debug("Loading UserViewWords data");
    String key = "user:viewwords:";
    Random r = new Random();
    long count = 0;
    long start = System.currentTimeMillis();
    for (int i = 0; i < MAX_USERS; i++) {
      int n = r.nextInt(MAX_WORDS);
      String k = key + i;
      long keyPtr = UnsafeAccess.allocAndCopy(k, 0, k.length());
      int keySize = k.length();
      for (int j = 0; j < n; j++) {
        String word = Utils.getRandomStr(r, 8);
        long mPtr = UnsafeAccess.allocAndCopy(word, 0, word.length());
        int mSize = word.length();
        ZSets.ZADD(
            map,
            keyPtr,
            keySize,
            new double[] {r.nextDouble()},
            new long[] {mPtr},
            new int[] {mSize},
            true);
        UnsafeAccess.free(mPtr);
        count++;
        if (count % 100000 == 0) {
          log.debug("UserViewWords :{}", count);
        }
      }
      UnsafeAccess.free(keyPtr);
    }
    long end = System.currentTimeMillis();
    log.debug("UserViewWords : loaded {} in {}ms", count, end - start);
  }

  private static void doUserActionWords(BigSortedMap map) {
    // SET
    log.debug("Loading UserActionWords data");
    String key = "user:actionwords:";
    Random r = new Random();
    long count = 0;
    long start = System.currentTimeMillis();
    for (int i = 0; i < MAX_USERS; i++) {
      int n = r.nextInt(MAX_WORDS / 100);
      String k = key + i;
      long keyPtr = UnsafeAccess.allocAndCopy(k, 0, k.length());
      int keySize = k.length();
      for (int j = 0; j < n; j++) {
        String word = Utils.getRandomStr(r, 8);
        long mPtr = UnsafeAccess.allocAndCopy(word, 0, word.length());
        int mSize = word.length();
        ZSets.ZADD(
            map,
            keyPtr,
            keySize,
            new double[] {r.nextDouble()},
            new long[] {mPtr},
            new int[] {mSize},
            true);
        UnsafeAccess.free(mPtr);
        count++;
        if (count % 100000 == 0) {
          log.debug("UserActionWords :{}", count);
        }
      }
      UnsafeAccess.free(keyPtr);
    }
    long end = System.currentTimeMillis();
    log.debug("UserActionWords : loaded " + count + " in " + (end - start) + "ms");
  }

  private static void doUserViewAds(BigSortedMap map) {
    log.debug("Loading User View Ads data");
    String key = "user:viewads:";
    Random r = new Random();
    long start = System.currentTimeMillis();
    long count = 0;
    for (int i = 0; i < MAX_USERS; i++) {
      int n = r.nextInt(MAX_ADS);
      String k = key + i;
      long keyPtr = UnsafeAccess.allocAndCopy(k, 0, k.length());
      int keySize = k.length();
      for (int j = 0; j < n; j++) {
        count++;
        long mPtr = UnsafeAccess.malloc(Utils.SIZEOF_INT);
        int mSize = Utils.SIZEOF_INT;
        UnsafeAccess.putInt(mPtr, MAX_ADS - j);
        int views = r.nextInt(100);
        long vPtr = UnsafeAccess.malloc(Utils.SIZEOF_INT);
        int vSize = Utils.SIZEOF_INT;
        UnsafeAccess.putInt(vPtr, views);

        Hashes.HSET(map, keyPtr, keySize, mPtr, mSize, vPtr, vSize);
        UnsafeAccess.free(vPtr);
        UnsafeAccess.free(mPtr);
        if (count % 100000 == 0) {
          log.debug("UserViewAds :{}", count);
        }
      }
      UnsafeAccess.free(keyPtr);
    }

    long end = System.currentTimeMillis();
    log.debug("UserViewAds : loaded {} in {}ms", count, end - start);
  }

  private static void doUserActionAds(BigSortedMap map) {
    log.debug("Loading User Action Ads data");
    String key = "user:actionads:";
    Random r = new Random();
    long start = System.currentTimeMillis();
    long count = 0;
    for (int i = 0; i < MAX_USERS; i++) {
      int n = r.nextInt(MAX_ADS / 100);
      String k = key + i;
      long keyPtr = UnsafeAccess.allocAndCopy(k, 0, k.length());
      int keySize = k.length();
      for (int j = 0; j < n; j++) {
        count++;
        long mPtr = UnsafeAccess.malloc(Utils.SIZEOF_INT);
        int mSize = Utils.SIZEOF_INT;
        UnsafeAccess.putInt(mPtr, MAX_ADS - j);
        int views = r.nextInt(100);
        long vPtr = UnsafeAccess.malloc(Utils.SIZEOF_INT);
        int vSize = Utils.SIZEOF_INT;
        UnsafeAccess.putInt(vPtr, views);

        Hashes.HSET(map, keyPtr, keySize, mPtr, mSize, vPtr, vSize);
        UnsafeAccess.free(vPtr);
        UnsafeAccess.free(mPtr);
        if (count % 100000 == 0) {
          log.debug("UserActionAds :{}", count);
        }
      }
      UnsafeAccess.free(keyPtr);
    }

    long end = System.currentTimeMillis();
    log.debug("UserActionAds : loaded {} in {}ms", count, end - start);
  }
}
