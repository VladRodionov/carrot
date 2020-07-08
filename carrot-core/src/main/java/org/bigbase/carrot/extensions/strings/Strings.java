package org.bigbase.carrot.extensions.strings;

import static org.bigbase.carrot.extensions.Commons.KEY_SIZE;

import java.util.List;

import org.bigbase.carrot.BigSortedMap;
import org.bigbase.carrot.Key;
import org.bigbase.carrot.KeyValue;
import org.bigbase.carrot.extensions.DataType;
import org.bigbase.carrot.extensions.KeysLocker;
import org.bigbase.carrot.extensions.MutationOptions;
import org.bigbase.carrot.util.UnsafeAccess;
import org.bigbase.carrot.util.Utils;

/**
 * Supports String operations, found in Redis
 * @author Vladimir Rodionov
 *
 */
public class Strings {

  private static ThreadLocal<Long> keyArena = new ThreadLocal<Long>() {
    @Override
    protected Long initialValue() {
      return UnsafeAccess.malloc(512);
    }
  };
  
  private static ThreadLocal<Integer> keyArenaSize = new ThreadLocal<Integer>() {
    @Override
    protected Integer initialValue() {
      return 512;
    }
  };
  
  static ThreadLocal<Long> valueArena = new ThreadLocal<Long>() {
    @Override
    protected Long initialValue() {
      return UnsafeAccess.malloc(512);
    }
  };
  
  static ThreadLocal<Integer> valueArenaSize = new ThreadLocal<Integer>() {
    @Override
    protected Integer initialValue() {
      return 512;
    }
  };
  
  static ThreadLocal<Key> key = new ThreadLocal<Key>() {
    @Override
    protected Key initialValue() {
      return new Key(0,0);
    }
  };
  
  /**
   * Thread local updates String Append
   */
  private static ThreadLocal<StringAppend> stringAppend = new ThreadLocal<StringAppend>() {
    @Override
    protected StringAppend initialValue() {
      return new StringAppend();
    } 
  };
  
  /**
   * Thread local updates String Bitcount
   */
  private static ThreadLocal<StringBitCount> stringBitcount = new ThreadLocal<StringBitCount>() {
    @Override
    protected StringBitCount initialValue() {
      return new StringBitCount();
    } 
  };
  
  /**
   * Thread local updates String Getbit
   */
  private static ThreadLocal<StringGetBit> stringGetbit = new ThreadLocal<StringGetBit>() {
    @Override
    protected StringGetBit initialValue() {
      return new StringGetBit();
    } 
  };
  
  /**
   * Thread local updates String SetBit
   */
  private static ThreadLocal<StringSetBit> stringSetbit = new ThreadLocal<StringSetBit>() {
    @Override
    protected StringSetBit initialValue() {
      return new StringSetBit();
    } 
  };
  
  /**
   * Thread local updates String Length
   */
  private static ThreadLocal<StringLength> stringLength = new ThreadLocal<StringLength>() {
    @Override
    protected StringLength initialValue() {
      return new StringLength();
    } 
  };
  
  /**
   * Thread local updates String GetRange
   */
  private static ThreadLocal<StringGetRange> stringGetrange = new ThreadLocal<StringGetRange>() {
    @Override
    protected StringGetRange initialValue() {
      return new StringGetRange();
    } 
  };
  
  /**
   * Thread local updates String GetSet
   */
  private static ThreadLocal<StringGetSet> stringGetset = new ThreadLocal<StringGetSet>() {
    @Override
    protected StringGetSet initialValue() {
      return new StringGetSet();
    } 
  };
  
  /**
   * Thread local updates String GetSet
   */
  private static ThreadLocal<StringSet> stringSet = new ThreadLocal<StringSet>() {
    @Override
    protected StringSet initialValue() {
      return new StringSet();
    } 
  };
  
  /**
   * Thread local updates String SetRange
   */
  private static ThreadLocal<StringSetRange> stringSetrange = new ThreadLocal<StringSetRange>() {
    @Override
    protected StringSetRange initialValue() {
      return new StringSetRange();
    } 
  };
  
  /**
   * Thread local updates String BitPos
   */
  private static ThreadLocal<StringBitPos> stringBitpos = new ThreadLocal<StringBitPos>() {
    @Override
    protected StringBitPos initialValue() {
      return new StringBitPos();
    } 
  };
  /**
   * Checks key arena size
   * @param required size
   */
  
  static void checkKeyArena (int required) {
    int size = keyArenaSize.get();
    if (size >= required ) {
      return;
    }
    long ptr = UnsafeAccess.realloc(keyArena.get(), required);
    keyArena.set(ptr);
    keyArenaSize.set(required);
  }
  
  /**
   * Checks value arena size
   * @param required size
   */
  static void checkValueArena (int required) {
    int size = valueArenaSize.get();
    if (size >= required) {
      return;
    }
    long ptr = UnsafeAccess.realloc(valueArena.get(), required);
    valueArena.set(ptr);
    valueArenaSize.set(required);
  }
  /**
   * Build key for String. It uses thread local key arena 
   * TODO: data type prefix
   * @param keyPtr original key address
   * @param keySize original key size
   * @param fieldPtr field address
   * @param fieldSize field size
   * @return new key size 
   */
    
   
  private static int buildKey( long keyPtr, int keySize) {
    checkKeyArena(keySize + KEY_SIZE + Utils.SIZEOF_BYTE);
    long arena = keyArena.get();
    int kSize = KEY_SIZE + keySize + Utils.SIZEOF_BYTE;
    UnsafeAccess.putByte(arena, (byte)DataType.STRING.ordinal());
    UnsafeAccess.putInt(arena + Utils.SIZEOF_BYTE, keySize);
    UnsafeAccess.copy(keyPtr, arena + KEY_SIZE + Utils.SIZEOF_BYTE, keySize);
    return kSize;
  }
  /**
   * If key already exists and is a string, this command appends the value at the end of the string. 
   * If key does not exist it is created and set as an empty string, 
   * so APPEND will be similar to SET in this special case.
   * @param map sorted map storage
   * @param keyPtr key address
   * @param keySize key size
   * @param valuePtr value address
   * @param valueSize value size
   * @return the length of the string after the append operation (Integer).
   */
  public static int append(BigSortedMap map, long keyPtr, int keySize, long valuePtr, int valueSize) {
    
    Key kk = key.get();
    kk.address = keyPtr;
    kk.size = keySize;
    
    try {
      KeysLocker.writeLock(kk);
      int kSize = buildKey(keyPtr, keySize);
      StringAppend append = stringAppend.get();
      append.reset();
      append.setKeyAddress(keyArena.get());
      append.setKeySize(kSize);
      append.setAppendValue(valuePtr, valueSize);
      // version?
      boolean result = map.execute(append);
      if (result) {
        return append.getSizeAfterAppend();
      } else {
        return -1;
      }
    } finally {
      KeysLocker.writeUnlock(kk);
    }
  }
  
  /**
   * Count the number of set bits (population counting) in a string.
   * By default all the bytes contained in the string are examined. 
   * It is possible to specify the counting operation only in an interval 
   * passing the additional arguments start and end.
   * Like for the GETRANGE command start and end can contain negative values 
   * in order to index bytes starting from the end of the string, where -1 is 
   * the last byte, -2 is the penultimate, and so forth.
   * Non-existent keys are treated as empty strings, so the command will return zero.
   * @param map ordered map
   * @param keyPtr key address
   * @param keySize key size
   * @param start start offset(inclusive)
   * @param end end offset (inclusive), if Common.NULL_LONG - unspecified
   * @return number of bits set or 0, if key does not exists
   */
  
  public static long bitCount(BigSortedMap map, long keyPtr, int keySize, long start, long end) {
    Key kk = key.get();
    kk.address = keyPtr;
    kk.size = keySize;
    try {
      KeysLocker.readLock(kk);
      int kSize = buildKey(keyPtr, keySize);
      StringBitCount bitcount = stringBitcount.get();
      bitcount.reset();
      bitcount.setKeyAddress(keyArena.get());
      bitcount.setKeySize(kSize);
      bitcount.setStartEnd(start, end);
      map.execute(bitcount);
      return bitcount.getBitCount();
    } finally {
      KeysLocker.readUnlock(kk);
    }
  }
  
  /**
   * Increments the number stored at key by increment. If the key does not exist, 
   * it is set to 0 before performing the operation. An error is returned if the key 
   * contains a value of the wrong type or contains a string that can not 
   * be represented as integer. This operation is limited to 64 bit signed integers.
   * @param map sorted map
   * @param keyPtr key address
   * @param keySize key size
   * @param value increment value
   * @return value after increment
   */
  public static long incrementBy(BigSortedMap map, long keyPtr, int keySize, long value) {
    return 0;
  }
  

  /**
   * Increments the number stored at key by one. If the key does not exist, it is set to 0 
   * before performing the operation. An error is returned if the key contains a value of 
   * the wrong type or contains a string that can not be represented as integer. This operation 
   * is limited to 64 bit signed integers.
   * Note: this is a string operation because Redis does not have a dedicated integer type. 
   * The string stored at the key is interpreted as a base-10 64 bit signed integer to execute the operation.
   * Redis stores integers in their integer representation, so for string values that actually hold an integer, 
   * there is no overhead for storing the string representation of the integer.
   * @param map sorted map
   * @param keyPtr key address
   * @param keySize key size
   * @return value of the key after increment
   */
  public static long increment(BigSortedMap map, long keyPtr, int keySize) {
    return incrementBy(map, keyPtr, keySize, 1);
  }
  /**
   * Decrements the number stored at key by one. If the key does not exist, it is set to 0 
   * before performing the operation. An error is returned if the key contains a value of 
   * the wrong type or contains a string that can not be represented as integer. This operation 
   * is limited to 64 bit signed integers.
   * @param map ordered map
   * @param keyPtr key address
   * @param keySize key size
   * @return key value after decrement
   */
  public static long decrement(BigSortedMap map, long keyPtr, int keySize) {
    return 0;
  }
  
  /**
   * Decrements the number stored at key by a given value. If the key does not exist, it is set to 0 
   * before performing the operation. An error is returned if the key contains a value of 
   * the wrong type or contains a string that can not be represented as integer. This operation 
   * is limited to 64 bit signed integers.
   * @param map ordered map
   * @param keyPtr key address
   * @param keySize key size
   * @param value value to decrement by
   * @return key value after decrement
   */
  public static long decrementBy(BigSortedMap map, long keyPtr, int keySize, long value) {
    return 0;
  }
  
  /**
   * Increment the string representing a floating point number stored at key by the specified increment. 
   * By using a negative increment value, the result is that the value stored at the key is decremented 
   * (by the obvious properties of addition). If the key does not exist, it is set to 0 before performing 
   * the operation. An error is returned if one of the following conditions occur:
   *  1. The key contains a value of the wrong type (not a string).
   *  2. The current key content or the specified increment are not parsable as a double precision 
   *  floating point number.
   * If the command is successful the new incremented value is stored as the new value of the key (replacing 
   * the old one), and returned to the caller as a string.
   * Both the value already contained in the string key and the increment argument can be optionally provided
   * in exponential notation, however the value computed after the increment is stored consistently in the 
   * same format, that is, an integer number followed (if needed) by a dot, and a variable number of digits 
   * representing the decimal part of the number. Trailing zeroes are always removed.
   * The precision of the output is fixed at 17 digits after the decimal point regardless of the actual 
   * internal precision of the computation.
   * @param map sorted map
   * @param keyPtr key address
   * @param keySize key size
   * @param value value to increment by
   * @return key value after increment
   */
  public static double incrementByFloat(BigSortedMap map, long keyPtr, int keySize, double value) {
    return 0;
  }
  
  /**
   * Get the value of key. If the key does not exist the special value nil is returned. 
   * An error is returned if the value stored at key is not a string, 
   * because GET only handles string values.
   * @param map sorted map
   * @param keyPtr key address
   * @param keyLength key length
   * @param valueBuf value buffer
   * @param valueBufLength value buffer size
   * @return size of a value, or -1 if not found. if size is greater than valueBufLength,
   *         the call must be repeated with appropriately sized value buffer
   */
  public static long get(BigSortedMap map, long keyPtr, int keyLength, long valueBuf,
      int valueBufLength) {
    Key kk = key.get();
    kk.address = keyPtr;
    kk.size = keyLength;
    try {
      KeysLocker.readLock(kk);
      int kLength = buildKey(keyPtr, keyLength);
      long kPtr = keyArena.get();
      return map.get(kPtr, kLength, valueBuf, valueBufLength, Long.MAX_VALUE);
    } finally {
      KeysLocker.readUnlock(kk);

    }
  }
  
  /**
   * Returns the bit value at offset in the string value stored at key.
   * When offset is beyond the string length, the string is assumed to be a 
   * contiguous space with 0 bits. When key does not exist it is assumed to be 
   * an empty string, so offset is always out of range and the value is also assumed 
   * to be a contiguous space with 0 bits.
   * @param map ordered map
   * @param keyPtr key address
   * @param keySize key length
   * @param offset offset to lookup bit
   * @return 1 or 0
   */
  public static int getBit(BigSortedMap map, long keyPtr, int keySize, long offset) {
    Key kk = key.get();
    kk.address = keyPtr;
    kk.size = keySize;
    try {
      KeysLocker.readLock(kk);
      int kSize = buildKey(keyPtr, keySize);
      StringGetBit getbit = stringGetbit.get();
      getbit.reset();
      getbit.setKeyAddress(keyArena.get());
      getbit.setKeySize(kSize);
      getbit.setOffset(offset);
      map.execute(getbit);
      return getbit.getBit();
    } finally {
      KeysLocker.readUnlock(kk);
    }
  }
  
  /**
   * Sets or clears the bit at offset in the string value stored at key.
   * The bit is either set or cleared depending on value, which can be either 0 or 1.
   * When key does not exist, a new string value is created. The string is grown to make 
   * sure it can hold a bit at offset. The offset argument is required to be greater than or 
   * equal to 0, and smaller than 232 (this limits bitmaps to 512MB). When the string at key 
   * is grown, added bits are set to 0. Actually we have higher limit - 2GB per value
   * @param map sorted map
   * @param keyPtr key address
   * @param keySize key size
   * @param offset offset to set bit at
   * @param bit bit value (0 or 1)
   * @return old bit value (0 if did not exists)
   */
  public static int setBit(BigSortedMap map, long keyPtr, int keySize, long offset, int bit) {
    Key kk = key.get();
    kk.address = keyPtr;
    kk.size = keySize;
    try {
      KeysLocker.writeLock(kk);
      int kSize = buildKey(keyPtr, keySize);
      StringSetBit setbit = stringSetbit.get();
      setbit.reset();
      setbit.setKeyAddress(keyArena.get());
      setbit.setKeySize(kSize);
      setbit.setOffset(offset);
      setbit.setBit(bit);
      map.execute(setbit);
      return setbit.getOldBit();
    } finally {
      KeysLocker.writeUnlock(kk);
    }
  }
  
  /**
   * Returns the length of the string value stored at key. 
   * An error is returned when key holds a non-string value.
   * @param map sorted map
   * @param keyPtr key 
   * @param keySize
   * @return size of a value or 0 if does not exists
   */
  public static int strLength(BigSortedMap map, long keyPtr, int keySize) {
    Key kk = key.get();
    kk.address = keyPtr;
    kk.size = keySize;
    try {
      KeysLocker.readLock(kk);
      int kSize = buildKey(keyPtr, keySize);
      StringLength strlen = stringLength.get();
      strlen.reset();
      strlen.setKeyAddress(keyArena.get());
      strlen.setKeySize(kSize);
      map.execute(strlen);
      return strlen.getLength();
    } finally {
      KeysLocker.readUnlock(kk);
    }
  }
  /**
   * Returns the substring of the string value stored at key, determined by the offsets 
   * start and end (both are inclusive). Negative offsets can be used in order to provide 
   * an offset starting from the end of the string. So -1 means the last character, 
   * -2 the penultimate and so forth.
   * The function handles out of range requests by limiting the resulting range to the actual length of the string.
   * @param map
   * @param keyPtr
   * @param keySize
   * @param start
   * @param end
   * @return size of a range, or -1, if key does not exists,
   *  if size > buferSize, the call must be repeated with appropriately sized buffer
   */
  public static int getRange(BigSortedMap map, long keyPtr, int keySize , int start, int end, 
      long bufferPtr, int bufferSize) {
    Key kk = key.get();
    kk.address = keyPtr;
    kk.size = keySize;
    try {
      KeysLocker.readLock(kk);
      int kSize = buildKey(keyPtr, keySize);
      StringGetRange getrange = stringGetrange.get();
      getrange.reset();
      getrange.setKeyAddress(keyArena.get());
      getrange.setKeySize(kSize);
      getrange.setFromTo(start, end);
      getrange.setBuffer(bufferPtr, bufferSize);
      map.execute(getrange);
      return getrange.getRangeLength();
    } finally {
      KeysLocker.readUnlock(kk);
    }
  }
  
  /**
   * Atomically sets key to value and returns the old value stored at key. 
   * Returns an error when key exists but does not hold a string value.
   * @param map ordered map
   * @param keyPtr key address
   * @param keySize key size
   * @param valuePtr value address
   * @param valueSize value size
   * @param bufferPtr buffer address to copy old version to
   * @param bufferSize buffer size
   * @return size of an old value, -1 if did not existed. If size > bufferSize, 
   *         the call must be repeated
   *         with appropriately sized buffer
   */
  public static int getSet(BigSortedMap map, long keyPtr, int keySize, long valuePtr, int valueSize,
      long bufferPtr, int bufferSize) 
  {
    Key kk = key.get();
    kk.address = keyPtr;
    kk.size = keySize;
    try {
      KeysLocker.writeLock(kk);
      int kSize = buildKey(keyPtr, keySize);
      StringGetSet getset = stringGetset.get();
      getset.reset();
      getset.setKeyAddress(keyArena.get());
      getset.setKeySize(kSize);
      getset.setBuffer(bufferPtr, bufferSize);
      getset.setValue(valuePtr, valueSize);
      map.execute(getset);
      return getset.getPreviousVersionLength();
    } finally {
      KeysLocker.writeUnlock(kk);
    }
  }

  /**
   * 
   * Set key to hold the string value. If key already holds a value, it is overwritten, 
   * regardless of its type. Any previous time to live associated with the key is discarded
   * on successful SET operation (if keepTTL == false).
   * This call covers the following commands: SET, SETNX, SETEX, PSETEX
   * @param map sorted map storage
   * @param keyPtr key address
   * @param keySize key size
   * @param valuePtr value address
   * @param valueSize value size
   * @param expire expiration time (0 - does not expire)
   * @param opts mutation options (NONE, NX, XX) 
   * 
   * @param keepTTL keep current TTL
   * @return true on success, false - otherwise
   */
  public static boolean set(BigSortedMap map, long keyPtr, int keySize, long valuePtr,
      int valueSize, long expire, MutationOptions opts, boolean keepTTL) {
    Key kk = key.get();
    kk.address = keyPtr;
    kk.size = keySize;
    try {
      KeysLocker.writeLock(kk);
      int kSize = buildKey(keyPtr, keySize);
      StringSet set = stringSet.get();
      set.reset();
      set.setKeyAddress(keyArena.get());
      set.setKeySize(kSize);
      set.setValue(valuePtr, valueSize);
      set.setKeepTTL(keepTTL);
      set.setMutationOptions(opts);
      boolean result = map.execute(set);
      return result;
    } finally {
      KeysLocker.writeUnlock(kk);
    }
  }

  /**
   * Set key to hold string value if key does not exist. In that case, it is equal 
   * to SET. When key already holds a value, no operation is performed. 
   * SETNX is short for "SET if Not eXists".
   * Return value
   *  Integer reply, specifically:
   * 1 if the key was set
   * 0 if the key was not set
   * @param map sorted map storage
   * @param keyPtr key address
   * @param keySize key size
   * @param valuePtr value address
   * @param valueSize value size
   * @return true on success, false - otherwise
   */
  public static boolean setnx (BigSortedMap map, long keyPtr, int keySize, long valuePtr,  int valueSize) {
    return set(map, keyPtr, keySize, valuePtr, valueSize, 0, MutationOptions.NX, false);
  }
  
  
  /**
   * Set key to hold the string value and set key to timeout after a given number of seconds. 
   * This command is equivalent to executing the following commands:
   *   SET mykey value
   *   EXPIRE mykey seconds
   * SETEX is atomic, and can be reproduced by using the previous two commands inside an MULTI / EXEC block. 
   * It is provided as a faster alternative to the given sequence of operations, because this operation is 
   * very common when Redis is used as a cache.
   *   An error is returned when seconds is invalid.
   * @param map sorted map storage
   * @param keyPtr key address
   * @param keySize key size
   * @param valuePtr value address
   * @param valueSize value size
   * @param expire expiration time
   * @return true, false
   */
  public static boolean setex (BigSortedMap map, long keyPtr, int keySize, long valuePtr,  int valueSize,
      long expire) {
    return set(map, keyPtr, keySize, valuePtr, valueSize, expire, MutationOptions.NONE, false);
  }  
  
  /**
   * Set key to hold the string value and set key to timeout after a given number of milliseconds. 
   * This command is equivalent to executing the following commands:
   *   SET mykey value
   *   EXPIRE mykey seconds
   * SETEX is atomic, and can be reproduced by using the previous two commands inside an MULTI / EXEC block. 
   * It is provided as a faster alternative to the given sequence of operations, because this operation is 
   * very common when Redis is used as a cache.
   *   An error is returned when seconds is invalid.
   * @param map sorted map storage
   * @param keyPtr key address
   * @param keySize key size
   * @param valuePtr value address
   * @param valueSize value size
   * @param expire expiration time
   * @return true, false
   */
  public static boolean psetex (BigSortedMap map, long keyPtr, int keySize, long valuePtr,  int valueSize,
      long expire) {
    return set(map, keyPtr, keySize, valuePtr, valueSize, expire, MutationOptions.NONE, false);
  }  
  
  /**
   * Overwrites part of the string stored at key, starting at the specified offset, 
   * for the entire length of value. If the offset is larger than the current length of the string at key, 
   * the string is padded with zero-bytes to make offset fit. Non-existing keys are considered as empty 
   * strings, so this command will make sure it holds a string large enough to be able to set value at offset.
   * Note that the maximum offset that you can set is 229 -1 (536870911), as Redis Strings are limited
   * to 512 megabytes. If you need to grow beyond this size, you can use multiple keys.
   * @param map sorted map storage
   * @param keyPtr key address
   * @param keySize key size
   * @param offset offset to set value
   * @param valuePtr value address
   * @param valueSize value size
   * @return new size of key's value
   */
  public static int setRange(BigSortedMap map, long keyPtr, int keySize, int offset, long valuePtr, int valueSize) {
    Key kk = key.get();
    kk.address = keyPtr;
    kk.size = keySize;
    try {
      KeysLocker.writeLock(kk);
      int kSize = buildKey(keyPtr, keySize);
      StringSetRange setrange = stringSetrange.get();
      setrange.reset();
      setrange.setKeyAddress(keyArena.get());
      setrange.setKeySize(kSize);
      setrange.setValue(valuePtr, valueSize);
      setrange.setOffset(offset);
      map.execute(setrange);
      return setrange.getValueLength();
    } finally {
      KeysLocker.writeUnlock(kk);
    }
  }
  
  /**
   * Return the position of the first bit set to 1 or 0 in a string.
   * The position is returned, thinking of the string as an array of bits from left to right, 
   * where the first byte's most significant bit is at position 0, the second byte's most 
   * significant bit is at position 8, and so forth.
   * The same bit position convention is followed by GETBIT and SETBIT.
   * By default, all the bytes contained in the string are examined. It is possible to look for 
   * bits only in a specified interval passing the additional arguments start and end 
   * (it is possible to just pass start, the operation will assume that the end is the last byte 
   * of the string. However there are semantic differences as explained later). The range is interpreted 
   * as a range of bytes and not a range of bits, so start=0 and end=2 means to look at the first three bytes.
   * Note that bit positions are returned always as absolute values starting from bit zero even when start 
   * and end are used to specify a range.
   * Like for the GETRANGE command start and end can contain negative values in order to index bytes starting from the end of the string, where -1 is the last byte, -2 is the penultimate, and so forth.
   * Non-existent keys are treated as empty strings.
   * @param map sorted map storage
   * @param keyPtr key address
   * @param keySize key size
   * @param bit bit value to look for
   * @param start start offset (in bytes) inclusive
   * @param end end position (in bytes) inclusive, if Commons.NULL_LONG - means unspecified 
   * @return The command returns the position of the first bit set to 1 or 0 according to the request.
   * If we look for set bits (the bit argument is 1) and the string is empty or composed of just zero bytes,
   *  -1 is returned.
   * If we look for clear bits (the bit argument is 0) and the string only contains bit set to 1, 
   * the function returns the first bit not part of the string on the right. So if the string is three bytes 
   * set to the value 0xff the command BITPOS key 0 will return 24, since up to bit 23 all the bits are 1.
   * Basically, the function considers the right of the string as padded with zeros if you look for clear 
   * bits and specify no range or the start argument only.
   * However, this behavior changes if you are looking for clear bits and specify a range with both start 
   * and end. If no clear bit is found in the specified range, the function returns -1 as the user specified 
   * a clear range and there are no 0 bits in that range.
   */
  public static long bitPos(BigSortedMap map, long keyPtr, int keySize, int bit, long start, long end) {
    Key kk = key.get();
    kk.address = keyPtr;
    kk.size = keySize;
    try {
      KeysLocker.readLock(kk);
      int kSize = buildKey(keyPtr, keySize);
      StringBitPos bitcount = stringBitpos.get();
      bitcount.reset();
      bitcount.setKeyAddress(keyArena.get());
      bitcount.setKeySize(kSize);
      bitcount.setStartEnd(start, end);
      map.execute(bitcount);
      return bitcount.getPosition();
    } finally {
      KeysLocker.readUnlock(kk);
    }
  }
  
  /**
   * Sets the given keys to their respective values. MSET replaces existing values with new values,
   * just as regular SET. See MSETNX if you don't want to overwrite existing values.
   * MSET is atomic, so all given keys are set at once. It is not possible for clients to see that some 
   * of the keys were updated while others are unchanged.
   * @param map sorted map storage
   * @param kvs list of key-values to set
   */
  public static void mset(BigSortedMap map, List<KeyValue> kvs) {
    
    try {
      KeysLocker.writeLockAll(kvs);
      for(int i =0; i < kvs.size(); i++) {
        KeyValue kv = kvs.get(i);
        set(map, kv.keyPtr, kv.keySize, kv.valuePtr, kv.valueSize, 0, 
          MutationOptions.NONE, false);
      }
    } finally {
      KeysLocker.writeUnlockAll(kvs);
    }
  }
  
  /**
   * Sets the given keys to their respective values. MSETNX will not perform any operation 
   * at all even if just a single key already exists.
   * Because of this semantic MSETNX can be used in order to set different keys representing 
   * different fields of an unique logic object in a way that ensures that either all the fields 
   * or none at all are set.
   * MSETNX is atomic, so all given keys are set at once. It is not possible for clients to see 
   * that some of the keys were updated while others are unchanged.
   * Return value
   * Integer reply, specifically:
   *  1 if the all the keys were set.
   *   0 if no key was set (at least one key already existed).
   * @param map sorted map storage
   * @param kvs list of key-values to set
   * @return true on success, false - otherwise
   */
  public static boolean msetnx(BigSortedMap map, List<KeyValue> kvs) {
    
    try {
      KeysLocker.writeLockAll(kvs);
      for (int i=0; i < kvs.size(); i++) {
        KeyValue kv = kvs.get(i);
        if (map.exists(kv.keyPtr, kv.keySize)) {
          return false;
        }
      }
      for(int i =0; i < kvs.size(); i++) {
        KeyValue kv = kvs.get(i);
        set(map, kv.keyPtr, kv.keySize, kv.valuePtr, kv.valueSize, 0, 
          MutationOptions.NONE, false);
      }
    } finally {
      KeysLocker.writeUnlockAll(kvs);
    }
    return true;
  }
}


