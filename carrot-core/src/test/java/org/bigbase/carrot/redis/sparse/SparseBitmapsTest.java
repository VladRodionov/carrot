package org.bigbase.carrot.redis.sparse;

import static org.junit.Assert.assertEquals;

import java.util.Random;

import org.bigbase.carrot.BigSortedMap;
import org.bigbase.carrot.Key;
import org.bigbase.carrot.redis.Commons;
import org.bigbase.carrot.util.UnsafeAccess;
import org.bigbase.carrot.util.Utils;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class SparseBitmapsTest {
  BigSortedMap map;
  Key key;
  long buffer;
  int bufferSize = 64;
  int keySize = 8;
  
  static {
   // UnsafeAccess.debug = true;
  }
    
  private Key getKey() {
    long ptr = UnsafeAccess.malloc(keySize);
    byte[] buf = new byte[keySize];
    Random r = new Random();
    long seed = r.nextLong();
    r.setSeed(seed);
    System.out.println("SEED=" + seed);
    r.nextBytes(buf);
    UnsafeAccess.copy(buf, 0, ptr, keySize);
    return new Key(ptr, keySize);
  }
  
  @Before
  public void setUp() {
    map = new BigSortedMap(10000000);
    buffer = UnsafeAccess.mallocZeroed(bufferSize); 
    key = getKey();
  }
  
  @After
  public void tearDown() {
    
    UnsafeAccess.free(key.address);
    UnsafeAccess.free(buffer);
    UnsafeAccess.mallocStats.printStats();
    BigSortedMap.memoryStats();
 // Dispose
    map.dispose();
  }
  
  //@Ignore
  @Test
  public void testSetBitGetBitLoop() {
    
    long offset= 0;
    int N = 100000;
    int delta = 1000;
    long start = System.currentTimeMillis();
    for (int i = 0; i < N ; i++) {
      offset += delta ;
      int bit = SparseBitmaps.SSETBIT(map, key.address, key.length, offset, 1);
      if (bit != 0) {
        System.out.println("i="+ i +" offset =" + offset);
      }
      assertEquals(0, bit);
      bit = SparseBitmaps.SGETBIT(map, key.address, key.length, offset);
      assertEquals(1, bit);
    }
    long count = SparseBitmaps.SBITCOUNT(map, key.address, key.length, Commons.NULL_LONG, Commons.NULL_LONG);
    assertEquals(N, (int)count);

    long end  = System.currentTimeMillis();
    
    System.out.println("Time for " + N + " SetBit/GetBit/CountBits =" + (end - start) + "ms");
    
    offset= 0;
    start = System.currentTimeMillis();
    for (int i = 0; i < N ; i++) {
      offset += delta ;
      int bit = SparseBitmaps.SSETBIT(map, key.address, key.length, offset, 0);
      assertEquals(1, bit);
      bit = SparseBitmaps.SGETBIT(map, key.address, key.length, offset);
      assertEquals(0, bit);
    }
    count = SparseBitmaps.SBITCOUNT(map, key.address, key.length, Commons.NULL_LONG, Commons.NULL_LONG);
    assertEquals(0, (int)count);

    end  = System.currentTimeMillis();
    
    System.out.println("Time for " + N + " SetBit/GetBit/CountBits =" + (end - start) + "ms");
  }
  
  @Test
  public void testSparselength() {
    
    long offset= 0;
    int N = 100000;
    int delta = 1000;
    long start = System.currentTimeMillis();
    for (int i = 0; i < N ; i++) {
      offset += delta ;
      SparseBitmaps.SSETBIT(map, key.address, key.length, offset, 1);
      long count = SparseBitmaps.SBITCOUNT(map, key.address, key.length, Commons.NULL_LONG, Commons.NULL_LONG);
      assertEquals( i + 1, (int)count);
      long len = SparseBitmaps.SSTRLEN(map, key.address, key.length);
      long expectedlength = (offset / SparseBitmaps.BITS_PER_CHUNK) * SparseBitmaps.BYTES_PER_CHUNK
          + SparseBitmaps.BYTES_PER_CHUNK;
      assertEquals(expectedlength, len);
    }
    long count = SparseBitmaps.SBITCOUNT(map, key.address, key.length, Commons.NULL_LONG, Commons.NULL_LONG);
    assertEquals(N, (int)count);

    long end  = System.currentTimeMillis();
    
    System.out.println("Time for " + N + " SetBit/StrLength =" + (end - start) + "ms");
    
  }
}
