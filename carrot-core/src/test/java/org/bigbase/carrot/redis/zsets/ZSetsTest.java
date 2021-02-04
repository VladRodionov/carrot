package org.bigbase.carrot.redis.zsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.bigbase.carrot.BigSortedMap;
import org.bigbase.carrot.Key;
import org.bigbase.carrot.Value;
import org.bigbase.carrot.compression.CodecFactory;
import org.bigbase.carrot.compression.CodecType;
import org.bigbase.carrot.util.UnsafeAccess;
import org.bigbase.carrot.util.Utils;
import org.junit.Ignore;
import org.junit.Test;

public class ZSetsTest {
  BigSortedMap map;
  Key key;
  long buffer;
  int bufferSize = 64;
  int fieldSize = 16;
  long n = 10000;
  List<Value> fields;
  List<Double> scores;
  
  static {
    UnsafeAccess.debug = true;
  }
  
  private List<Value> getFields(long n) {
    List<Value> keys = new ArrayList<Value>();
    Random r = new Random();
    long seed = r.nextLong();
    r.setSeed(seed);
    System.out.println("KEYS SEED=" + seed);
    byte[] buf = new byte[fieldSize/2];
    for (int i=0; i < n; i++) {
      r.nextBytes(buf);
      long ptr = UnsafeAccess.malloc(fieldSize);
      // Make values compressible
      UnsafeAccess.copy(buf, 0, ptr, buf.length);
      UnsafeAccess.copy(buf, 0, ptr + buf.length, buf.length);
      keys.add(new Value(ptr, fieldSize));
    }
    return keys;
  }
  
  int maxScore = 10000;
  
  private List<Double> getScores(long n) {
    List<Double> scores = new ArrayList<Double>();
    Random r = new Random(1);
    for (int i = 0; i < n; i++) {
      scores.add((double)r.nextInt(maxScore));
    }
    return scores;
  }
  
  private Key getKey() {
    long ptr = UnsafeAccess.malloc(fieldSize);
    byte[] buf = new byte[fieldSize];
    Random r = new Random();
    long seed = r.nextLong();
    r.setSeed(seed);
    System.out.println("SEED=" + seed);
    r.nextBytes(buf);
    UnsafeAccess.copy(buf, 0, ptr, fieldSize);
    return key = new Key(ptr, fieldSize);
  }
  
  private void setUp() {
    map = new BigSortedMap(1000000000);
    buffer = UnsafeAccess.mallocZeroed(bufferSize); 
    fields = getFields(n);
    scores = getScores(n);
    Utils.sortKeys(fields);
    for(int i=1; i< n; i++) {
      Key prev = fields.get(i-1);
      Key cur = fields.get(i);
      int res = Utils.compareTo(prev.address, prev.length, cur.address, cur.length);
      if (res == 0) {
        System.out.println("Found duplicate");
        fail();
      }
    }
  }
  
  //@Ignore
  @Test
  public void runAllNoCompression() {
    BigSortedMap.setCompressionCodec(CodecFactory.getInstance().getCodec(CodecType.NONE));
    System.out.println();
    for (int i = 0; i < 10; i++) {
      System.out.println("*************** RUN = " + (i + 1) +" Compression=NULL");
      allTests();
      BigSortedMap.printMemoryAllocationStats();
      UnsafeAccess.mallocStats.printStats();
    }
  }
  
  //@Ignore
  @Test
  public void runAllCompressionLZ4() {
    BigSortedMap.setCompressionCodec(CodecFactory.getInstance().getCodec(CodecType.LZ4));
    System.out.println();
    for (int i = 0; i < 10; i++) {
      System.out.println("*************** RUN = " + (i + 1) +" Compression=LZ4");
      allTests();
      BigSortedMap.printMemoryAllocationStats();
      UnsafeAccess.mallocStats.printStats();
    }
  }
  
  //@Ignore
  @Test
  public void runAllCompressionLZ4HC() {
    BigSortedMap.setCompressionCodec(CodecFactory.getInstance().getCodec(CodecType.LZ4HC));
    System.out.println();
    for (int i = 0; i < 10; i++) {
      System.out.println("*************** RUN = " + (i + 1) +" Compression=LZ4HC");
      allTests();
      BigSortedMap.printMemoryAllocationStats();
      UnsafeAccess.mallocStats.printStats();
    }
  }
  
  private void allTests() {
    setUp();
    testAddGetScore();
    tearDown();
    setUp();
    testAddRemove();
    tearDown();
  }
  
  @Ignore
  @Test
  public void testAddGetScore () {
    System.out.println("Test ZSet Add Get Score");
    Key key = getKey();
    long[] elemPtrs = new long[1];
    int[] elemSizes = new int[1];
    double[] scores = new double[1];
    long start = System.currentTimeMillis();
    
    for (int i = 0; i < n; i++) {
      elemPtrs[0] = fields.get(i).address;
      elemSizes[0] = fields.get(i).length;
      scores[0] = this.scores.get(i);
      long num = ZSets.ZADD(map, key.address, key.length, scores, elemPtrs, elemSizes, true);
      assertEquals(1, (int)num);
      if ((i+1) % 10000 == 0) {
        System.out.println(i+1);
      }
    }
    long end = System.currentTimeMillis();
    System.out.println("Total allocated memory ="+ BigSortedMap.getTotalAllocatedMemory() 
    + " for "+ n + " " + (fieldSize + Utils.SIZEOF_DOUBLE) + " byte values. Overhead="+ 
        ((double)BigSortedMap.getTotalAllocatedMemory()/n - (fieldSize + Utils.SIZEOF_DOUBLE))+
    " bytes per value. Time to load: "+(end -start)+"ms");
    
    BigSortedMap.printMemoryAllocationStats();

    
    assertEquals(n, ZSets.ZCARD(map, key.address, key.length));
    start = System.currentTimeMillis();
    for (int i =0; i < n; i++) {
      Double res = ZSets.ZSCORE(map, key.address, key.length, fields.get(i).address, fields.get(i).length);
      assertEquals(this.scores.get(i), res);
    }
    end = System.currentTimeMillis();
    System.out.println(" Time for " + n+ " ZSCORE="+(end -start)+"ms");
    BigSortedMap.memoryStats();
    ZSets.DELETE(map, key.address, key.length);
    assertEquals(0, (int)ZSets.ZCARD(map, key.address, key.length));
 
  }
  
  @Ignore
  @Test
  public void testAddRemove() {
    System.out.println("Test ZSet Add Get Score");
    Key key = getKey();
    long[] elemPtrs = new long[1];
    int[] elemSizes = new int[1];
    double[] scores = new double[1];
    long start = System.currentTimeMillis();
    for (int i = 0; i < n; i++) {
      elemPtrs[0] = fields.get(i).address;
      elemSizes[0] = fields.get(i).length;
      scores[0] = this.scores.get(i);
      long num = ZSets.ZADD(map, key.address, key.length, scores, elemPtrs, elemSizes, true);
      assertEquals(1, (int)num);
      if ((i+1) % 10000 == 0) {
        System.out.println(i+1);
      }
    }
    long end = System.currentTimeMillis();
    System.out.println("Total allocated memory ="+ BigSortedMap.getTotalAllocatedMemory() 
    + " for "+ n + " " + (fieldSize + Utils.SIZEOF_DOUBLE) + " byte values. Overhead="+ 
        ((double)BigSortedMap.getTotalAllocatedMemory()/n - (fieldSize + Utils.SIZEOF_DOUBLE))+
    " bytes per value. Time to load: "+(end -start)+"ms");
    
    BigSortedMap.printMemoryAllocationStats();
    
    assertEquals(n, ZSets.ZCARD(map, key.address, key.length));
//    start = System.currentTimeMillis();
//    for (int i =0; i < n; i++) {
//      elemPtrs[0] = values.get(i).address;
//      elemSizes[0] = values.get(i).length;
//      long n = ZSets.ZREM(map, key.address, key.length, elemPtrs, elemSizes);
//      assertEquals(1, (int) n);
//    }
//    end = System.currentTimeMillis();
//    System.out.println("Time for " + n+ " ZREM="+(end -start)+"ms");
    ZSets.DELETE(map, key.address, key.length);
    assertEquals(0, (int)ZSets.ZCARD(map, key.address, key.length));
 
  }
  
  private void tearDown() {
    // Dispose
    map.dispose();
    UnsafeAccess.free(key.address);
    for (Key k: fields) {
      UnsafeAccess.free(k.address);
    }
    UnsafeAccess.free(buffer);
    UnsafeAccess.mallocStats.printStats();
    BigSortedMap.memoryStats();
  }
}
