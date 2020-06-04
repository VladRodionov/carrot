package org.bigbase.carrot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.bigbase.carrot.util.Bytes;
import org.bigbase.carrot.util.Utils;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

public class BigSortedMapTest {

  static BigSortedMap map;
  static long totalLoaded;
  
  @BeforeClass 
  public static void setUp() throws IOException {
	  BigSortedMap.setMaxBlockSize(4096);
    map = new BigSortedMap(100000000);
    totalLoaded = 0;
    long start = System.currentTimeMillis();
    while(totalLoaded < 1000000) {
      totalLoaded++;
      byte[] key = ("KEY"+ (totalLoaded)).getBytes();
      byte[] value = ("VALUE"+ (totalLoaded)).getBytes();
      map.put(key, 0, key.length, value, 0, value.length, 0);
    }
    long end = System.currentTimeMillis();
    map.dumpStats();
    System.out.println("Time to load= "+ totalLoaded+" ="+(end -start)+"ms");
    long scanned = countRecords();
    System.out.println("Scanned="+ countRecords());
    System.out.println("Total memory="+BigSortedMap.getMemoryAllocated());
    System.out.println("Total   data="+BigSortedMap.getTotalDataSize());
    assertEquals(totalLoaded, scanned);
  }
    
  @Test
  public void testDeleteUndeleted() throws IOException {
    System.out.println("testDeleteUndeleted");
    List<byte[]> keys = delete(100);    
    assertEquals(totalLoaded - 100, countRecords());
    undelete(keys);
    assertEquals(totalLoaded, countRecords());

  }
  
  static long countRecords() throws IOException {
    BigSortedMapScanner scanner = map.getScanner(null, null);
    long counter = 0;
    while(scanner.hasNext()) {
//      int keySize = scanner.keySize();
//      byte[] buf = new byte[keySize];
//      scanner.key(buf,  0);
//      System.out.println(Bytes.toString(buf));
      counter++;
      scanner.next();
    }
    scanner.close();
    return counter;
  }
  
  @Test
  public void testPutGet() {   
    System.out.println("testPutGet");

    long start = System.currentTimeMillis();    
    byte[] tmp = ("VALUE"+ totalLoaded).getBytes();
    for(int i=1; i <= totalLoaded; i++) {
      byte[] key = ("KEY"+ (i)).getBytes();
      byte[] value = ("VALUE"+i).getBytes();
      //System.out.println(Bytes.toString(key));
      try {
        long size = map.get(key, 0, key.length, tmp, 0, Long.MAX_VALUE) ;
        assertEquals(value.length, (int)size);
        assertTrue(Utils.compareTo(value, 0, value.length, tmp, 0,(int) size) == 0);
      } catch(Throwable t) {
        throw t;
      }
      
    }    
    long end = System.currentTimeMillis();   
    System.out.println("Time to get "+ totalLoaded+" ="+ (end - start)+"ms");    
    
  }
  
  @Test
  public void testExists() {   
    System.out.println("testExists");
  
    for(int i=1; i <= totalLoaded; i++) {
      byte[] key = ("KEY"+ (i)).getBytes();
      boolean res = map.exists(key, 0, key.length) ;
      assertEquals(true, res);      
    }            
  }
  
  @Test
  public void testFirstKey() throws IOException {
    System.out.println("testFirstKey");

    byte[] firstKey = "KEY1".getBytes();
    byte[] secondKey = "KEY10".getBytes();
    byte[] key = map.getFirstKey();
    System.out.println(Bytes.toString(key));
    assertTrue(Utils.compareTo(key, 0, key.length, firstKey, 0, firstKey.length) == 0);
    boolean res = map.delete(firstKey, 0, firstKey.length);
    assertEquals ( true, res);
    key = map.getFirstKey();
    assertTrue(Utils.compareTo(key, 0, key.length, secondKey, 0, secondKey.length) == 0);
    
    byte[] value = "VALUE1".getBytes();
    
    res = map.put(firstKey, 0, firstKey.length, value, 0, value.length, 0);
    assertEquals(true, res);
    
  }
  
  @Test  
  public void testFullMapScanner() throws IOException {
    System.out.println("testFullMap ");
    BigSortedMapScanner scanner = map.getScanner(null, null);
    long start = System.currentTimeMillis();
    long count = 0;
    byte[] value = new byte[("VALUE"+ totalLoaded).length()];
    byte[] prev = null;
    while(scanner.hasNext()) {
      count++;
      int keySize = scanner.keySize();
      int valSize = scanner.valueSize();
      byte[] cur = new byte[keySize];
      scanner.key(cur, 0);
      scanner.value(value, 0);
      if (prev != null) {
        assertTrue (Utils.compareTo(prev, 0, prev.length, cur, 0, cur.length) < 0);
      }
      prev = cur;
      //System.out.println( new String(cur, 0, keySize));
      boolean res = scanner.next();
    }   
    long end = System.currentTimeMillis();
    System.out.println("Scanned "+ count+" in "+ (end- start)+"ms");
    assertEquals(totalLoaded, count);
    scanner.close();
  }
 
  
  private List<byte[]> delete(int num) {
    Random r = new Random();
    int numDeleted = 0;
    byte[] val = new byte[1];
    List<byte[]> list = new ArrayList<byte[]>();
    int collisions = 0;
    while (numDeleted < num) {
      int i = r.nextInt((int)totalLoaded) + 1;
      byte [] key = ("KEY"+ i).getBytes();
      long len = map.get(key, 0, key.length, val, 0, Long.MAX_VALUE);
      if (len == DataBlock.NOT_FOUND) {
        collisions++;
        continue;
      } else {
        boolean res = map.delete(key, 0, key.length);
        assertTrue(res);
        numDeleted++;
        list.add(key);
      }
    }
    System.out.println("Deleted="+ numDeleted +" collisions="+collisions);
    return list;
  }
  
  private void undelete(List<byte[]> keys) {
    for (byte[] key: keys) {
      byte[] value = ("VALUE"+ new String(key).substring(3)).getBytes();
      boolean res = map.put(key, 0, key.length, value, 0, value.length, 0);
      assertTrue(res);
    }
  }
  
  @Test
  public void testFullMapScannerWithDeletes() throws IOException {
    System.out.println("testFullMapScannerWithDeletes ");
    int toDelete = 100000;
    List<byte[]> deletedKeys = delete(toDelete);
    BigSortedMapScanner scanner = map.getScanner(null, null);
    long start = System.currentTimeMillis();
    long count = 0;
    byte[] value = new byte[("VALUE"+ totalLoaded).length()];
    byte[] prev = null;
    while(scanner.hasNext()) {
      count++;
      int keySize = scanner.keySize();
      int valSize = scanner.valueSize();
      byte[] cur = new byte[keySize];
      scanner.key(cur, 0);
      scanner.value(value, 0);
      if (prev != null) {
        assertTrue (Utils.compareTo(prev, 0, prev.length, cur, 0, cur.length) < 0);
      }
      prev = cur;
      scanner.next();
    }   
    long end = System.currentTimeMillis();
    System.out.println("Scanned "+ count+" in "+ (end- start)+"ms");
    assertEquals(totalLoaded - toDelete, count);
    undelete(deletedKeys);

  }
    
  @Test
  public void testScannerSameStartStopRow () throws IOException
  {
    System.out.println("testScannerSameStartStopRow");
    Random r = new Random();
    int startIndex = r.nextInt((int)totalLoaded);
    byte[] startKey = ("KEY" + startIndex).getBytes();

    BigSortedMapScanner scanner = map.getScanner(startKey, startKey);
    long count = countRows(scanner); 
    scanner.close();
    assertEquals(0, (int) count);
    startIndex = r.nextInt((int)totalLoaded);
    startKey = ("KEY" + startIndex).getBytes();
    scanner = map.getScanner(startKey, startKey);
    count = countRows(scanner); 
    scanner.close();
    assertEquals(0, (int) count);
  }
  

  @Ignore
  @Test
  public void loopNext() throws IOException {
    for (int i=0; i < 100; i++) {
      testAllScannerStartStopRow();
    }
  }
  
  @Test
  public void testAllScannerStartStopRow() throws IOException {
    System.out.println("testAllScannerStartStopRow ");
    Random r = new Random();
    int startIndex = r.nextInt((int)totalLoaded);
    int stopIndex = r.nextInt((int)totalLoaded - startIndex) + startIndex;
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
    System.out.println("Start="+ Bytes.toString(startKey) + " stop="+ Bytes.toString(stopKey));
    BigSortedMapScanner scanner = map.getScanner(null, startKey);
    long count1 = countRows(scanner); 
    scanner.close();
    scanner = map.getScanner(startKey, stopKey);
    long count2 = countRows(scanner);
    scanner.close();
    scanner = map.getScanner(stopKey, null);
    long count3 = countRows(scanner);
    scanner.close();
    System.out.println("Total scanned="+(count1 + count2+count3));
    assertEquals(totalLoaded, count1 + count2 + count3);

  }

  private long countRows(BigSortedMapScanner scanner) throws IOException {
    long start = System.currentTimeMillis();
    long count = 0;
    byte[] value = new byte[("VALUE"+ totalLoaded).length()];
    byte[] prev = null;
    while(scanner.hasNext()) {
      count++;
      int keySize = scanner.keySize();
      byte[] cur = new byte[keySize];
      scanner.key(cur, 0);
      scanner.value(value, 0);
      if (prev != null) {
        assertTrue (Utils.compareTo(prev, 0, prev.length, cur, 0, cur.length) < 0);
      }
      prev = cur;
      //System.out.println( new String(cur, 0, keySize));
      scanner.next();
    }   
    long end = System.currentTimeMillis();
    System.out.println("Scanned "+ count+" in "+ (end- start)+"ms");
    return count;
  }
  
  @Test
  public void testSequentialInsert() {
    System.out.println("testSequentialInsert");
    BigSortedMap.setMaxBlockSize(4096);

    BigSortedMap map = new BigSortedMap(1000);
    int counter = 0;
    while(true) {
      byte[] key = nextKeySeq(counter);
      byte[] value = nextValueSeq(counter);
      if(map.put(key, 0, key.length, value, 0, value.length, 0)) {
        counter++;
      } else {
        counter--;
        break;
      }
    }
    System.out.println("SEQ: Inserted "+counter+" kvs");
  }
  
  @Test
  public void testNonSequentialInsert() {
    System.out.println("testNonSequentialInsert");
    BigSortedMap.setMaxBlockSize(4096);
    BigSortedMap map = new BigSortedMap(1000);
    int counter = 0;
    while(true) {
      byte[] key = nextKey(counter);
      byte[] value = nextValue(counter);
      if(map.put(key, 0, key.length, value, 0, value.length, 0)) {
        counter++;
      } else {
        counter--;
        break;
      }
    }
    System.out.println("NON-SEQ: Inserted "+counter+" kvs");
  }
  
  private byte[] nextKeySeq (long n) {
    String s = format(n , 6);
    return ("KEY"+s).getBytes();
  }
  
  private byte[] nextValueSeq(long n) {
    String s = format(n , 6);
    return ("VALUE"+s).getBytes();
  }
  
  private byte[] nextKey(long n) {
    String s = formatReverse(n, 6);
    return ("KEY" + s).getBytes();
  }
  
  private byte[] nextValue(long n) {
    String s = formatReverse(n, 6);
    return ("VALUE"+s).getBytes();
  }
  
  private String format (long n, int pos) {
    String s = Long.toString(n);
    int len = s.length();
    for (int k=0; k < pos - len; k++) {
      s = "0" + s;
    }
    
    return s;
  }
  
  private String formatReverse (long n, int pos) {
    String s = Long.toString(n);
    int len = s.length();

    for (int k=0; k < pos - len; k++) {
      s = s + "0";
    }
    
    return s;
  }
}
