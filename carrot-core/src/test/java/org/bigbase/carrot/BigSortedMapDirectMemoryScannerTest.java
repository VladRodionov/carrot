package org.bigbase.carrot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.bigbase.carrot.util.UnsafeAccess;
import org.bigbase.carrot.util.Utils;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

public class BigSortedMapDirectMemoryScannerTest {

  static BigSortedMap map;
  static long totalLoaded;
  static long MAX_ROWS = 1000;
  @BeforeClass 
  public static void setUp() throws IOException {
    BigSortedMap.setMaxBlockSize(4096);
    map = new BigSortedMap(100000000);
    totalLoaded = 0;
    long start = System.currentTimeMillis();
    while(totalLoaded < MAX_ROWS) {
      totalLoaded++;
      load(totalLoaded);
    }
    long end = System.currentTimeMillis();
    map.dumpStats();
    System.out.println("Time to load= "+ totalLoaded+" ="+(end -start)+"ms");
    long scanned = countRecords();
    System.out.println("Scanned="+ countRecords());
    System.out.println("Total memory="+BigSortedMap.getTotalAllocatedMemory());
    System.out.println("Total   data="+BigSortedMap.getTotalBlockDataSize());
    assertEquals(totalLoaded, scanned);
  }
  
  private static boolean load(long totalLoaded) {
    byte[] key = ("KEY"+ (totalLoaded)).getBytes();
    byte[] value = ("VALUE"+ (totalLoaded)).getBytes();
    long keyPtr = UnsafeAccess.malloc(key.length);
    UnsafeAccess.copy(key, 0, keyPtr, key.length);
    long valPtr = UnsafeAccess.malloc(value.length);
    UnsafeAccess.copy(value, 0, valPtr, value.length);
    boolean result = map.put(keyPtr, key.length, valPtr, value.length, 0);
    UnsafeAccess.free(keyPtr);
    UnsafeAccess.free(valPtr);
    return result;
  }
  
   
  static long countRecords() throws IOException {
    BigSortedMapScanner scanner = map.getScanner(null, null);
    long counter = 0;
    while(scanner.hasNext()) {
      counter++;
      scanner.next();
    }
    scanner.close();
    return counter;
  }
  
  
  @Ignore
  @Test  
  public void testDirectMemoryAllRangesMapScanner() throws IOException {
    System.out.println("testDirectMemoryAllRangesMapScanner ");
    Random r = new Random();
    int startIndex = r.nextInt((int)totalLoaded);
    int stopIndex = r.nextInt((int)totalLoaded);
    
    byte[] key1 = ("KEY" + startIndex).getBytes();
    byte[] key2 = ("KEY" + stopIndex).getBytes();
    byte[] startKey, stopKey;
    if (Utils.compareTo(key1, 0, key1.length, key2, 0, key2.length) > 0) {
      startKey = key2;
      stopKey = key1;
    } else {
      startKey = key1;
      stopKey = key2;
    }
    
    long startPtr = UnsafeAccess.malloc(startKey.length);
    UnsafeAccess.copy(startKey,  0, startPtr, startKey.length);
    int startLength = startKey.length;
    long stopPtr = UnsafeAccess.malloc(stopKey.length);
    UnsafeAccess.copy(stopKey,  0, stopPtr, stopKey.length);
    int stopLength = stopKey.length;
    
    BigSortedMapDirectMemoryScanner scanner = 
        map.getScanner(0, 0, startPtr, startLength);
    long count1 = countRows(scanner);
    scanner.close();
    scanner = 
        map.getScanner(startPtr, startLength, stopPtr, stopLength);
    long count2 = countRows(scanner);
    scanner.close();
    scanner = 
        map.getScanner(stopPtr, stopLength, 0, 0);
    long count3 = countRows(scanner);
    scanner.close();
    assertEquals(totalLoaded, count1 + count2 + count3); 
    UnsafeAccess.free(startPtr);
    UnsafeAccess.free(stopPtr);
  }
  
  @Test
  public void runTests() throws IOException {
    for (int i=0; i < 10000; i++) {
      System.out.println("\nRUN "+i+"\n");
      testDirectMemoryAllRangesMapScannerReverse();
    }
  }
  
  @Ignore
  @Test
  public void testDirectMemoryAllRangesMapScannerReverse() throws IOException {
    System.out.println("testDirectMemoryAllRangesMapScannerReverse");
    Random r = new Random();
    long seed = r.nextLong();
    r.setSeed(seed);
    System.out.println("testDirectMemoryAllRangesMapScannerReverse seed="+seed);
    int startIndex = r.nextInt((int)totalLoaded);
    int stopIndex = r.nextInt((int)totalLoaded);
    
    byte[] key1 = ("KEY" + startIndex).getBytes();
    byte[] key2 = ("KEY" + stopIndex).getBytes();
    byte[] startKey, stopKey;
    if (Utils.compareTo(key1, 0, key1.length, key2, 0, key2.length) > 0) {
      startKey = key2;
      stopKey = key1;
    } else {
      startKey = key1;
      stopKey = key2;
    }
    
    long startPtr = UnsafeAccess.malloc(startKey.length);
    UnsafeAccess.copy(startKey,  0, startPtr, startKey.length);
    int startLength = startKey.length;
    long stopPtr = UnsafeAccess.malloc(stopKey.length);
    UnsafeAccess.copy(stopKey,  0, stopPtr, stopKey.length);
    int stopLength = stopKey.length;
    
    BigSortedMapDirectMemoryScanner scanner = 
        map.getScanner(0, 0, startPtr, startLength, false);
    long count1 = countRows(scanner);
    if (scanner != null) {
      scanner.close();
    }    
    scanner = map.getScanner(0, 0, startPtr, startLength, true);
    long count11 = countRowsReverse(scanner);
    assertEquals(count1, count11);
    if ( scanner != null) {
      scanner.close();
    }
    
    scanner = 
        map.getScanner(startPtr, startLength, stopPtr, stopLength, false);
    long count2 = countRows(scanner);
    if (scanner != null) {
      scanner.close();
    }
    
    scanner = 
        map.getScanner(startPtr, startLength, stopPtr, stopLength, true);
    long count22 = countRowsReverse(scanner);
    assertEquals(count2, count22);
    if (scanner != null) {
      scanner.close();
    }
    
    scanner = 
        map.getScanner(stopPtr, stopLength, 0, 0, false);
    long count3 = countRows(scanner);
    if (scanner != null) {
      scanner.close();
    }    
    scanner = 
        map.getScanner(stopPtr, stopLength, 0, 0, true);
    long count33 = countRowsReverse(scanner);
    assertEquals(count3, count33);
    if (scanner != null) {
      scanner.close();
    }
    assertEquals(totalLoaded, count1 + count2 + count3); 
    UnsafeAccess.free(startPtr);
    UnsafeAccess.free(stopPtr);
  }  
 
  @Ignore
  @Test  
  public void testDirectMemoryFullMapScanner() throws IOException {
    System.out.println("testDirectMemoryFullMapScanner ");
    BigSortedMapDirectMemoryScanner scanner = map.getScanner(0,0,0,0);
    long start = System.currentTimeMillis();
    long count = countRows(scanner);
    long end = System.currentTimeMillis();
    System.out.println("Scanned "+ count+" in "+ (end- start)+"ms");
    assertEquals(totalLoaded, count);
    scanner.close();
  }
  
 @Ignore
  @Test  
  public void testDirectMemoryFullMapScannerReverse() throws IOException {
    System.out.println("testDirectMemoryFullMapScannerReverse ");
    BigSortedMapDirectMemoryScanner scanner = map.getScanner(0,0,0,0, true);
    long start = System.currentTimeMillis();
    long count = countRowsReverse(scanner);
    long end = System.currentTimeMillis();
    System.out.println("Scanned "+ count+" in "+ (end- start)+"ms");
    assertEquals(totalLoaded, count);
    scanner.close();
  }
  
  private List<byte[]> delete(int num) {
    Random r = new Random();
    int numDeleted = 0;
    long valPtr = UnsafeAccess.malloc(1);
    List<byte[]> list = new ArrayList<byte[]>();
    int collisions = 0;
    while (numDeleted < num) {
      int i = r.nextInt((int)totalLoaded) + 1;
      byte [] key = ("KEY"+ i).getBytes();
      long keyPtr = UnsafeAccess.malloc(key.length);
      UnsafeAccess.copy(key, 0, keyPtr, key.length);
      long len = map.get(keyPtr, key.length, valPtr, 1, Long.MAX_VALUE);
      if (len == DataBlock.NOT_FOUND) {
        collisions++;
        UnsafeAccess.free(keyPtr);
        continue;
      } else {
        boolean res = map.delete(keyPtr, key.length);
        assertTrue(res);
        numDeleted++;
        list.add(key);
        UnsafeAccess.free(keyPtr);
      }
    }
    UnsafeAccess.free(valPtr);
    System.out.println("Deleted="+ numDeleted +" collisions="+collisions);
    return list;
  }
  
  private void undelete(List<byte[]> keys) {
    for (byte[] key: keys) {
      byte[] value = ("VALUE"+ new String(key).substring(3)).getBytes();
      long keyPtr = UnsafeAccess.malloc(key.length);
      UnsafeAccess.copy(key, 0,  keyPtr, key.length);
      long valPtr = UnsafeAccess.malloc(value.length);
      UnsafeAccess.copy(value, 0, valPtr, value.length);
      boolean res = map.put(keyPtr, key.length, valPtr, value.length, 0);
      assertTrue(res);
    }
  }
  
  
  @Ignore
  @Test
  public void testDirectMemoryFullMapScannerWithDeletes() throws IOException {
    System.out.println("testDirectMemoryFullMapScannerWithDeletes ");
    int toDelete = 100000;
    List<byte[]> deletedKeys = delete(toDelete);
    BigSortedMapDirectMemoryScanner scanner = map.getScanner(0, 0, 0, 0);
    long start = System.currentTimeMillis();
    long count = 0;
    int vallen = ("VALUE" + totalLoaded).length();
    long value = UnsafeAccess.malloc(vallen);
    long prev = 0;
    int prevSize = 0;
    while(scanner.hasNext()) {
      count++;
      int keySize = scanner.keySize();
      int valSize = scanner.valueSize();
      long key = UnsafeAccess.malloc(keySize);
      scanner.key(key, keySize);
      scanner.value(value, vallen);
      if (prev != 0) {
        assertTrue (Utils.compareTo(prev, prevSize, key, keySize) < 0);
        UnsafeAccess.free(prev);
      }
      prev = key;
      prevSize = keySize;
      scanner.next();
    }   
    if (prev > 0) {
      UnsafeAccess.free(prev);
    }
    UnsafeAccess.free(value);
    
    long end = System.currentTimeMillis();
    System.out.println("Scanned "+ count+" in "+ (end- start)+"ms");
    assertEquals(totalLoaded - toDelete, count);
    undelete(deletedKeys);

  }
  
  
  @Ignore
  @Test
  public void testDirectMemoryScannerSameStartStopRow () throws IOException
  {
    System.out.println("testDirectMemoryScannerSameStartStopRow");
    Random r = new Random();
    int startIndex = r.nextInt((int)totalLoaded);
    byte[] startKey = ("KEY" + startIndex).getBytes();
    int length = startKey.length;
    long ptr = UnsafeAccess.malloc(length);
    UnsafeAccess.copy(startKey,  0, ptr, length);
    BigSortedMapDirectMemoryScanner scanner = map.getScanner(ptr, length, ptr, length);
    long count = countRows(scanner); 
    scanner.close();
    assertEquals(0, (int) count);
    
    startIndex = r.nextInt((int)totalLoaded);
    startKey = ("KEY" + startIndex).getBytes();
    length = startKey.length;
    UnsafeAccess.free(ptr);
    
    ptr = UnsafeAccess.malloc(length);
    UnsafeAccess.copy(startKey,  0, ptr, length);
    scanner = map.getScanner(ptr, length, ptr, length);    
    count = countRows(scanner); 
    scanner.close();
    UnsafeAccess.free(ptr);

    assertEquals(0, (int) count);
  }

  private long countRows(BigSortedMapDirectMemoryScanner scanner) throws IOException {
    if (scanner == null) return 0;
    long start = System.currentTimeMillis();
    long count = 0;
    long prev = 0;
    int prevLen = 0;
    int vallen = ("VALUE"+ totalLoaded).length();
    long value = UnsafeAccess.malloc(vallen);
    
    while(scanner.hasNext()) {
      count++;
      int keySize = scanner.keySize();
      long key = UnsafeAccess.malloc(keySize);
      
      scanner.key(key, keySize);
      scanner.value(value, vallen);
      if (prev != 0) {
        assertTrue (Utils.compareTo(prev,  prevLen, key, keySize) < 0);
        UnsafeAccess.free(prev);
      }
      prev = key;
      prevLen = keySize;
      scanner.next();
    }   
    if (prev != 0) {
      UnsafeAccess.free(prev);
    }
    UnsafeAccess.free(value);
    long end = System.currentTimeMillis();
    System.out.println("Scanned direct "+ count+" in "+ (end- start)+"ms");
    return count;
  }
  
  
  private long countRowsReverse(BigSortedMapDirectMemoryScanner scanner) throws IOException {
    if (scanner == null) return 0;
    long start = System.currentTimeMillis();
    long count = 0;
    long prev = 0;
    int prevLen = 0;
    int vallen = ("VALUE"+ totalLoaded).length();
    long value = UnsafeAccess.malloc(vallen);
    
    do {
      count++;
      int keySize = scanner.keySize();
      long key = UnsafeAccess.malloc(keySize);
      
      scanner.key(key, keySize);
      scanner.value(value, vallen);
      if (prev != 0) {
        assertTrue (Utils.compareTo(prev,  prevLen, key, keySize) > 0);
        UnsafeAccess.free(prev);
      }
      prev = key;
      prevLen = keySize;
    } while (scanner.previous());  
    if (prev != 0) {
      UnsafeAccess.free(prev);
    }
    UnsafeAccess.free(value);
    long end = System.currentTimeMillis();
    System.out.println("Scanned reversed "+ count+" in "+ (end- start)+"ms");
    return count;
  }

}
