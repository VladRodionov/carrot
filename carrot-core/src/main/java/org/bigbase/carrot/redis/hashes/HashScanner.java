package org.bigbase.carrot.redis.hashes;

import static org.bigbase.carrot.redis.Commons.NUM_ELEM_SIZE;

import java.io.IOException;

import org.bigbase.carrot.BigSortedMapDirectMemoryScanner;
import org.bigbase.carrot.redis.Commons;
import org.bigbase.carrot.util.Scanner;
import org.bigbase.carrot.util.UnsafeAccess;
import org.bigbase.carrot.util.Utils;

/**
 * Scanner to iterate through hash field- values
 * @author Vladimir Rodionov
 *
 */
public class HashScanner extends Scanner{
  
  /*
   * Base Map scanner
   */
  private BigSortedMapDirectMemoryScanner mapScanner;
  /*
   * Minimum field (inclusive)
   */
  long startFieldPtr;
  /*
   * Minimum field size
   */
  int startFieldSize;
  /*
   * Maximum field (exclusive) 
   */
  long stopFieldPtr;
  /*
   * Maximum field size
   */
  int stopFieldSize;
  
  /*
   * Current value address
   */
  long valueAddress;
  /*
   * Current value size
   */
  int valueSize;
  /*
   * Current offset in the value
   */
  int offset;
  
  /*
   * Current field size
   */
  int fieldSize;
  /*
   * Current field address
   */
  long fieldAddress;
  /*
   * Current field value size
   */
  int fieldValueSize;
  /*
   * Current field value address
   */
  long fieldValueAddress;
  /*
   * Current position (index)
   */
  long pos = 1;
  
  /*
   * Reverse scanner
   */
  boolean reverse;
  
  /*
   * 
   * Delete start/stop keys on close()
   */
  boolean disposeKeysOnClose;
  /**
   * Constructor
   * @param scanner base scanner
   * @throws IOException 
   */
  public HashScanner(BigSortedMapDirectMemoryScanner scanner) throws IOException {
    this(scanner, 0, 0, 0, 0);
  }
  
  
  /**
   * Constructor
   * @param scanner base scanner
   * @param reverse true, if reverse scanner, false - otherwise
   * @throws IOException 
   */
  public HashScanner(BigSortedMapDirectMemoryScanner scanner, boolean reverse) throws IOException {
    this(scanner, 0, 0, 0, 0, reverse);
  }
  
  /**
   * Constructor for a range scanner
   * @param scanner base scanner
   * @param start start field address 
   * @param startSize start field size
   * @param stop stop field address
   * @param stopSize stop field size
   * @throws IOException 
   */
  public HashScanner(BigSortedMapDirectMemoryScanner scanner, long start, int startSize, 
      long stop, int stopSize) throws IOException {
   this(scanner, start, startSize, stop, stopSize, false);
  }
  
  /**
   * Constructor for a range scanner
   * @param scanner base scanner
   * @param start start field address 
   * @param startSize start field size
   * @param stop stop field address
   * @param stopSize stop field size
   * @param reverse reverse scanner
   * @throws IOException 
   */
  public HashScanner(BigSortedMapDirectMemoryScanner scanner, long start, int startSize, 
      long stop, int stopSize, boolean reverse) throws IOException {
    this.mapScanner = scanner;
    this.startFieldPtr = start;
    this.startFieldSize = startSize;
    this.stopFieldPtr = stop;
    this.stopFieldSize = stopSize;
    this.reverse = reverse;
    init();
  }

  /**
   * Get hash field address from a key
   * @param ptr key address
   * @return address of a start of a field
   */
  private long getFieldAddress(long ptr) {
    int keySize = UnsafeAccess.toInt(ptr + Utils.SIZEOF_BYTE);
    return ptr + keySize + Utils.SIZEOF_BYTE + Commons.KEY_SIZE;
  }
  
  /**
   * Get hash field size from a key and key size
   * @param ptr address of a key
   * @param size key size
   * @return size of a field
   */
  private int getFieldSize(long ptr, int size) {
    return (int)(ptr + size - getFieldAddress(ptr));
  }
  
  /**
   * Set dispose start/stop keys on close()
   * @param b dispose if true
   */
  public void setDisposeKeysOnClose(boolean b) {
    this.disposeKeysOnClose = b;
  }
  
  private void init() throws IOException {
    this.valueAddress = mapScanner.valueAddress();
    this.valueSize = mapScanner.valueSize();
    if (this.valueAddress == -1) {
      // Hack, rewind block scanner by one record back
      // This hack works, b/c when scanner seeks first record
      // which is  *always* greater or equals to a startRow, but
      // we need the previous one, which is the largest row which is less
      // or equals to a startRow. 
      //TODO: Reverse scanner?
      mapScanner.getBlockScanner().prev();
      this.valueAddress = mapScanner.valueAddress();
      this.valueSize = mapScanner.valueSize();

    } else if (this.startFieldPtr > 0 && !reverse){
      // Check if current key in a mapScanner is equals to start
      long ptr = mapScanner.keyAddress();
      int size = mapScanner.keySize();
      size = getFieldSize(ptr, size);
      ptr = getFieldAddress(ptr);
      if (Utils.compareTo(this.startFieldPtr, this.startFieldSize, ptr, size) != 0) {
        // TODO: check the result
        mapScanner.getBlockScanner().prev();
        this.valueAddress = mapScanner.valueAddress();
        this.valueSize = mapScanner.valueSize();
      }
    }
    if (reverse) {
      if (!searchLastMember()) {
        boolean result = previous();
        if (!result) {
          throw new IOException("Empty scanner");
        }
      }
    } else {
      searchFirstMember();
    }
  }
  
  private boolean searchFirstMember() {
    
    if(this.valueAddress <= 0) {
      return false;
    }
    this.offset = NUM_ELEM_SIZE;
    this.fieldSize = Utils.readUVInt(this.valueAddress + this.offset);
    int fSizeSize = Utils.sizeUVInt(this.fieldSize);
    this.fieldValueSize = Utils.readUVInt(this.valueAddress + this.offset+ fSizeSize);
    int vSizeSize = Utils.sizeUVInt(this.fieldValueSize);
    this.fieldAddress = this.valueAddress + this.offset + fSizeSize + vSizeSize;
    this.fieldValueAddress = this.fieldAddress + this.fieldSize;
    
    try {
      if (this.startFieldPtr != 0) {
        while (hasNext()) {
          int fSize = Utils.readUVInt(this.valueAddress + this.offset);
          fSizeSize = Utils.sizeUVInt(fSize);
          int vSize = Utils.readUVInt(this.valueAddress + this.offset + fSizeSize);
          vSizeSize = Utils.sizeUVInt(vSize);
          long fPtr = this.valueAddress + this.offset + fSizeSize + vSizeSize;
          int res = Utils.compareTo(fPtr, fSize, this.startFieldPtr, this.startFieldSize);
          if (res >= 0) {
            return true;
          }
          next();
        }
      } else if (this.valueSize == NUM_ELEM_SIZE) { // skip possible empty K-V (first one)
        next();
      }
    } catch (IOException e) {
      // should never be thrown
    }
    return false;
  }
  /**
   * Search last field in a current Value
   * @return true on success, false - otherwise
   */
  private boolean searchLastMember() {
    
    if (this.valueAddress <= 0) {
      return false;
    }
    this.valueAddress = mapScanner.valueAddress();
    this.valueSize = mapScanner.valueSize();
    // check if it it is not empty
    this.offset = NUM_ELEM_SIZE;
    int prevOffset = 0;
    while (this.offset < this.valueSize) {
      int fSize = Utils.readUVInt(valueAddress + offset);
      int fSizeSize = Utils.sizeUVInt(fSize);
      int vSize = Utils.readUVInt(valueAddress + offset + fSizeSize);
      int vSizeSize = Utils.sizeUVInt(vSize);
      long fPtr = valueAddress + offset + fSizeSize + vSizeSize;
      
      if (stopFieldPtr > 0) {
        int res = Utils.compareTo(fPtr, fSize, this.stopFieldPtr, this.stopFieldSize);
        if (res >= 0) {
          break;
        }
      }
      prevOffset = offset;
      offset += fSize + vSize + fSizeSize + vSizeSize;
      //*DEBUG*/ System.out.println("off="+ this.offset);
    }
    
    this.offset = prevOffset;
    if (this.offset == 0) {
      return false;
    }
    
    if (startFieldPtr > 0) {
      int fSize = Utils.readUVInt(valueAddress + offset);
      int fSizeSize = Utils.sizeUVInt(fSize);
      int vSize = Utils.readUVInt(valueAddress + offset + fSizeSize);
      int vSizeSize = Utils.sizeUVInt(vSize);
      long fPtr = valueAddress + offset + fSizeSize + vSizeSize;
      
      int res = Utils.compareTo(fPtr, fSize, this.startFieldPtr, this.startFieldSize);
      if (res < 0) {
        this.offset = 0;
        return false;
      }
    }
    updateFields();
    return true;
  }
  
  private void updateFields() {
    this.fieldSize = Utils.readUVInt(valueAddress + offset);
    int fSizeSize = Utils.sizeUVInt(this.fieldSize);
    this.fieldValueSize = Utils.readUVInt(valueAddress + offset + fSizeSize);
    int vSizeSize = Utils.sizeUVInt(this.fieldValueSize);
    
    this.fieldAddress = valueAddress + offset + fSizeSize + vSizeSize;
    this.fieldValueAddress = this.fieldAddress + this.fieldSize;
  }
  
  /**
   * Checks if scanner has next element
   * @return true, false
   * @throws IOException
   */
  public boolean hasNext() throws IOException {
    if (this.valueAddress <= 0) {
      return false;
    }
    if (reverse) {
      throw new UnsupportedOperationException("hasNext");
    }
    if (offset < valueSize && offset >= NUM_ELEM_SIZE) {
      if (stopFieldPtr == 0) {
        return true;
      } else {

        if (Utils.compareTo(this.fieldAddress, this.fieldSize, this.stopFieldPtr,
          this.stopFieldSize) >= 0) {
          return false;
        } else {
          return true;
        }
      }
    } else {
      // First K-V in a set can be empty, we need to scan to the next one
      mapScanner.next();
      if (mapScanner.hasNext()) {
        this.valueAddress = mapScanner.valueAddress();
        this.valueSize = mapScanner.valueSize();
        this.offset = NUM_ELEM_SIZE;
        updateFields();
        return true;
      } else {
        return false;
      }
    }
  }
  
  /**
   * Advance scanner by one element 
   * MUST BE USED IN COMBINATION with hasNext()
   * @return true if operation succeeded , false - end of scanner
   * @throws IOException
   */
  public boolean next() throws IOException {
    if (reverse) {
      throw new UnsupportedOperationException("next");
    }
    if (offset < valueSize) {
      int fSize = Utils.readUVInt(valueAddress + offset);
      int fSizeSize = Utils.sizeUVInt(fSize);
      int vSize = Utils.readUVInt(valueAddress + offset + fSizeSize);
      int vSizeSize = Utils.sizeUVInt(vSize);
      offset += fSize + vSize + fSizeSize + vSizeSize;
      if (offset < valueSize) {
        updateFields();
        pos++;
        return true;
      }
    }
    // TODO next K-V can not have 0 elements - it must be deleted
    // but first element can, so we has to check what next() call return
    // the best way is to use do
    
    // TODO: fix previous, hashScanner.next previous
    
    mapScanner.next();

    while(mapScanner.hasNext()) {
      this.valueAddress = mapScanner.valueAddress();
      this.valueSize = mapScanner.valueSize();
      // check if it it is not empty
      this.offset = NUM_ELEM_SIZE;
      if (valueSize <= NUM_ELEM_SIZE) {
        // empty set in K-V? Must be deleted
        mapScanner.next();
        continue;
      } 
      updateFields();
      if (this.stopFieldPtr > 0) {  
        if (Utils.compareTo(this.fieldAddress, this.fieldSize, 
          this.stopFieldPtr, this.stopFieldSize) >=0) {
          this.offset = 0;
          return false;
        } else {
          pos++;
          return true;
        }
      } else {
        pos++;
        return true;
      }
    }
    this.offset = 0;
    return false;
  }
  
  /**
   * Get current field address
   * @return field address
   */
  public long fieldAddress() {
    return this.fieldAddress;
  }
  
  /**
   * Gets current field size
   * @return field size
   */
  public int fieldSize() {
    return this.fieldSize;
  }
  
  /**
   * Get current field-value address
   * @return field value address
   */
  public long fieldValueAddress() {
    return this.fieldValueAddress;
  }
  
  /**
   * Gets current field value size
   * @return field value size
   */
  public int fieldValueSize() {
    return this.fieldSize;
  }
  
  /**
   * Get current position of a scanner (in field-values)
   * @return position
   */
  public long getPosition() {
    return this.pos;
  }
  
  /**
   * Skips to position
   * @param pos position to skip
   * @return current position (can be less than pos)
   */
  public long skipTo(long pos) {
    if (pos <= this.pos) return this.pos;
    while(this.pos < pos) {
      try {
        boolean res = next();
        if (!res) break;
      } catch (IOException e) {
        // Should not throw
      }
    }
    return this.pos;
  }
  
  @Override
  public void close() throws IOException {
    mapScanner.close(disposeKeysOnClose);
  }
  
  /**
   * Delete current Element
   * @return true if success, false - otherwise
   */
  public boolean delete() {
    //TODO
    return false;
  }
  
  /**
   * Delete all Elements in this scanner
   * @return true on success, false?
   */
  public boolean deleteAll() {
    return false;
  }


  @Override
  public boolean first() throws IOException {
    // TODO Auto-generated method stub
    return false;
  }


  @Override
  public boolean last() throws IOException {
    // TODO Auto-generated method stub
    return false;
  }


  @Override
  public boolean previous() throws IOException {
    if (!reverse) {
      throw new UnsupportedOperationException("previous");
    }
    if (this.offset > NUM_ELEM_SIZE) {
      int off = NUM_ELEM_SIZE;
      while (off < this.offset) {
        int fSize = Utils.readUVInt(this.valueAddress + off);
        int fSizeSize = Utils.sizeUVInt(fSize);
        int vSize = Utils.readUVInt(this.valueAddress + off + fSizeSize);
        int vSizeSize = Utils.sizeUVInt(vSize);
        if (off + fSize + vSize + fSizeSize + vSizeSize >= this.offset) {
          break;
        }
        off += fSize + vSize + fSizeSize + vSizeSize;
      }
      this.offset = off;
      updateFields();
      
      if (this.startFieldPtr > 0) {
        int result = Utils.compareTo(this.fieldAddress, this.fieldSize, 
          this.startFieldPtr, this.startFieldSize);
        if (result < 0) {
          return false;
        }
      }
      return true;
    }
    
    boolean result = mapScanner.previous();
    if (result) {
      return searchLastMember();
    } else {
      return false;
    }
  }


  @Override
  public boolean hasPrevious() throws IOException {
    throw new UnsupportedOperationException("hasPrevious");
  }
}