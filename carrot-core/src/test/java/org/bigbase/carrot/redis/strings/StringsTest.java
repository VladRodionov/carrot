package org.bigbase.carrot.redis.strings;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.bigbase.carrot.BigSortedMap;
import org.bigbase.carrot.Key;
import org.bigbase.carrot.KeyValue;
import org.bigbase.carrot.redis.MutationOptions;
import org.bigbase.carrot.util.UnsafeAccess;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class StringsTest {
  BigSortedMap map;
  Key key;
  long buffer;
  int bufferSize = 512;
  long n = 1000000;
  List<KeyValue> keyValues;
  
  static {
   // UnsafeAccess.debug = true;
  }
  
  private List<KeyValue> getKeyValues(long n) {
    List<KeyValue> keyValues = new ArrayList<KeyValue>();
    for (int i=0; i < n; i++) {
      // key
      byte[] key = ("user:"+i).getBytes();
      long keyPtr = UnsafeAccess.malloc(key.length);
      int keySize = key.length;
      UnsafeAccess.copy(key, 0, keyPtr, keySize);
      
      // value
      FakeUserSession session = FakeUserSession.newSession(i);
      //*DEBUG*/ System.out.println(session);
      byte[] value = session.toString().getBytes();
      int valueSize = value.length;
      long valuePtr = UnsafeAccess.malloc(valueSize);
      UnsafeAccess.copy(value, 0, valuePtr, valueSize);
      keyValues.add(new KeyValue(keyPtr, keySize, valuePtr, valueSize));
    }
    return keyValues;
  }
  

  
  @Before
  public void setUp() {
    map = new BigSortedMap(1000000000);
    buffer = UnsafeAccess.mallocZeroed(bufferSize); 
    keyValues = getKeyValues(n);
  }
  
  @Test
  public void testSetGet () {
    System.out.println("Test Strings Set/Get ");
 
    long start = System.currentTimeMillis();
    long totalSize = 0;
    for (int i = 0; i < n; i++) {
      KeyValue kv = keyValues.get(i);
      totalSize += kv.keySize + kv.valueSize;
      boolean result = Strings.SET(map, kv.keyPtr, kv.keySize, kv.valuePtr, kv.valueSize, 0, 
        MutationOptions.NONE, true);
      assertEquals(true, result);
      if (i % 1000000 == 0) {
        System.out.println(i);
      }
    }
    long end = System.currentTimeMillis();
    System.out.println("Total allocated memory ="+ BigSortedMap.getTotalAllocatedMemory() 
    + " for "+ n + " " + (totalSize) + " byte values. Overhead="+ 
        ((double)BigSortedMap.getTotalAllocatedMemory() - totalSize)/n +
    " bytes per key-value. Time to load: "+(end -start)+"ms");
    start = System.currentTimeMillis();
    for (int i =0; i < n; i++) {
      KeyValue kv = keyValues.get(i);
      long valueSize = Strings.GET(map, kv.keyPtr, kv.keySize, buffer, bufferSize);
      assertEquals(kv.valueSize, (int)valueSize);
    }
    end = System.currentTimeMillis();
    System.out.println("Time GET ="+(end -start)+"ms");
    BigSortedMap.memoryStats();
   
 
  }
  
  @Test
  public void testSetRemove() {
    System.out.println("Test Strings Set/Remove ");
    
    long start = System.currentTimeMillis();
    long totalSize = 0;
    for (int i = 0; i < n; i++) {
      KeyValue kv = keyValues.get(i);
      totalSize += kv.keySize + kv.valueSize;
      boolean result = Strings.SET(map, kv.keyPtr, kv.keySize, kv.valuePtr, kv.valueSize, 0, 
        MutationOptions.NONE, true);
      assertEquals(true, result);
      if (i % 1000000 == 0) {
        System.out.println(i);
      }
    }
    long end = System.currentTimeMillis();
    System.out.println("Total allocated memory ="+ BigSortedMap.getTotalAllocatedMemory() 
    + " for "+ n + " " + (totalSize) + " byte values. Overhead="+ 
        ((double)BigSortedMap.getTotalAllocatedMemory() - totalSize)/n +
    " bytes per key-value. Time to load: "+(end -start)+"ms");
    start = System.currentTimeMillis();
    for (int i =0; i < n; i++) {
      KeyValue kv = keyValues.get(i);
      boolean result = Strings.DELETE(map, kv.keyPtr, kv.keySize);
      assertEquals(true, result);
    }
    end = System.currentTimeMillis();
    System.out.println("Time DELETE ="+(end -start)+"ms");
    BigSortedMap.memoryStats();
  }
  
  @After
  public void tearDown() {
    // Dispose
    map.dispose();
    for (KeyValue k: keyValues) {
      UnsafeAccess.free(k.keyPtr);
      UnsafeAccess.free(k.valuePtr);
    }
    UnsafeAccess.mallocStats.printStats();
    BigSortedMap.memoryStats();
  }
}


class FakeUserSession {
  
  static final String[] ATTRIBUTES = new String[] {
      "attr1", "attr2", "attr3", "attr4", "attr5", 
      "attr6", "attr7", "attr8", "attr9", "attr10"
  };
  
  Properties props = new Properties();
  
  FakeUserSession(Properties p){
    this.props = p;
  }
  
  static FakeUserSession newSession(int i) {
    Properties p = new Properties();
    for (String attr: ATTRIBUTES) {
      p.put(attr, attr + ":value:"+ i);
    }
    return new FakeUserSession(p);
  }
  
  public String toString() {
    return props.toString();
  }
  
}