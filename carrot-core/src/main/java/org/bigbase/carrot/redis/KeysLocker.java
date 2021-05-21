package org.bigbase.carrot.redis;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.bigbase.carrot.Key;
import org.bigbase.carrot.KeyValue;
import org.bigbase.carrot.util.Utils;

/**
 * THis helper class performs safe group Key/KeyValue locking/unlocking
 * @author Vladimir Rodionov
 *
 */
public class KeysLocker {

  /*
   * Read-Write Lock TODO: StampedLock (Java 8)
   */
  static ReentrantReadWriteLock[] locks = new ReentrantReadWriteLock[11113];
  static {
    for (int i = 0; i < locks.length; i++) {
      locks[i] = new ReentrantReadWriteLock();
    }
  }

  /**
   * Read Lock Key
   * @param key
   */
  public static void readLock(Key key) {
    int hash = key.hashCode();
    int index = hash % locks.length;
    locks[index].readLock().lock();
  }

  /**
   * Read unlock Key
   * @param key
   */
  public static void readUnlock(Key key) {
    int hash = key.hashCode();
    int index = hash % locks.length;
    locks[index].readLock().unlock();
  }

  /**
   * Read Lock KeyValue
   * @param key
   */
  public static void readLock(KeyValue key) {
    int hash = key.hashCode();
    int index = hash % locks.length;
    locks[index].readLock().lock();
  }

  /**
   * Read unlock KeyValue
   * @param key
   */
  public static void readUnlock(KeyValue key) {
    int hash = key.hashCode();
    int index = hash % locks.length;
    locks[index].readLock().unlock();
  }

  /**
   * Write Lock Key
   * @param key
   */
  public static void writeLock(Key key) {
    int hash = key.hashCode();
    int index = hash % locks.length;
    locks[index].writeLock().lock();
  }

  /**
   * Write unlock Key
   * @param key
   */
  public static void writeUnlock(Key key) {
    int hash = key.hashCode();
    int index = hash % locks.length;
    locks[index].writeLock().unlock();
  }

  /**
   * Write Lock KeyValue
   * @param key
   */
  public static void writeLock(KeyValue key) {
    int hash = key.hashCode();
    int index = hash % locks.length;
    locks[index].writeLock().lock();
  }

  /**
   * Write unlock KeyValue
   * @param key
   */
  public static void writeUnlock(KeyValue key) {
    int hash = key.hashCode(); 
    int index = hash % locks.length;
    locks[index].writeLock().unlock();
  }

  /**
   * Read lock list of KeyValues
   * @param kvs list of KeyValues
   */
  public static void readLockAllKeyValues(List<KeyValue> kvs) {
    // Sort them first to avoid deadlocks in a future
    Collections.sort(kvs);
    for(int i=0; i < kvs.size(); i++) {
      readLock(kvs.get(i));
    }
  }
  /**
   * Read unlock list of KeyValues
   * @param kvs list of KeyValues
   */
  public static void readUnlockAllKeyValues(List<KeyValue> kvs) {
    for(int i=0; i < kvs.size(); i++) {
      readUnlock(kvs.get(i));
    }
  }
  
  /**
   * Write lock list of KeyValues
   * @param kvs list of KeyValues
   */
  public static void writeLockAllKeyValues(List<KeyValue> kvs) {
    // Sort them first to avoid deadlocks in a future
    Collections.sort(kvs);
    for(int i=0; i < kvs.size(); i++) {
      writeLock(kvs.get(i));
    }
  }
  /**
   * Write unlock list of KeyValues
   * @param kvs list of KeyValues
   */
  public static void writeUnlockAllKeyValues(List<KeyValue> kvs) {
    for(int i=0; i < kvs.size(); i++) {
      writeUnlock(kvs.get(i));
    }
  }
  
  /**
   * Write lock list of Keys
   * @param kvs list of Keys
   */
  public static void writeLockAllKeys(List<Key> kvs) {
    // Sort them first to avoid deadlocks in a future
    Collections.sort(kvs);
    for(int i=0; i < kvs.size(); i++) {
      writeLock(kvs.get(i));
    }
  }
  
  /**
   * Write unlock list of Keys
   * @param kvs list of Keys
   */
  public static void writeUnlockAllKeys(List<Key> kvs) {
    for(int i=0; i < kvs.size(); i++) {
      writeUnlock(kvs.get(i));
    }
  }
  
  /**
   * Read lock list of Keys
   * @param kvs list of Keys
   */
  public static void readLockAllKeys(List<Key> kvs) {
    // Sort them first to avoid deadlocks in a future
    Collections.sort(kvs);
    for(int i=0; i < kvs.size(); i++) {
      readLock(kvs.get(i));
    }
  }
  /**
   * Read unlock list of Keys
   * @param kvs list of Keys
   */
  public static void readUnlockAllKeys(List<Key> kvs) {
    for(int i=0; i < kvs.size(); i++) {
      readUnlock(kvs.get(i));
    }
  }
}
