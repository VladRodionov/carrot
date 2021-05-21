package org.bigbase.carrot.ops;


/**
 * This class encapsulate read-modify-write 
 * transactional access pattern. Subclasses must provide
 * key (address, size), version and implement execute()
 * operation. The result can be one or two PUTs, which will be 
 * executed in the context of this atomic operation or one Delete and PUT
 * or no updates at all
 * @author Vladimir Rodionov
 *
 */
public abstract class Operation {

  /*
   * operation sequence number
   */
  protected long version;
  /*
   * Key address associated with update
   */
  protected long keyAddress;
  
  /*
   * Expiration
   */
  protected long expire;
  
  /*
   * Key size
   */
  protected int keySize;
  /*
   * Found K-V record address
   */
  protected long foundRecordAddress;

  /* 
   * These are result of update operation
   * There can be 1 or 2 K_V pairs to insert
   * Result key addresses
   */
  protected long[] keys = new long[2];
  /*
   * Result key sizes 
   */
  protected int[]  keySizes = new int[2];
  /*
   * Result value addresses
   */
  protected long[] values = new long[2];
  /*
   * Result value sizes
   */
  protected int[] valueSizes = new int[2];
  /*
   * Update types for updates: false - PUT, true - Delete
   */
  protected boolean[] updateTypes = new boolean[] {false, false};
  
  /* 
   * Reuse values for updates if possible
   * 
   */
  protected boolean[] reuseValues = new boolean[] {false, false};
  
  /*
   * Number of results (0, 1,  2 updates/puts/ deletes) 
   */
  protected int updatesCount; // 1 or 2
  
  /*
   * If true, look for largest key which less or equals
   */
  protected boolean floorKey = false; 
  
  /*
   * Read - only operation
   */
  protected boolean readOnly = false;
  
  /*
   * Constructor
   */
  public Operation() {
  }
  
  /**
   * Sets expire time
   * @param expire expire time
   */
  public final void setExpire(long expire) {
    this.expire = expire;
  }
  
  /**
   * Gets expire time
   * @return expire time
   */
  public final long getExpire() {
    return expire;
  }
  
  /**
   * Sets key address to look for
   * @param address key address
   */
  public final void setKeyAddress(long address) {
    this.keyAddress = address;
  }
  
  /**
   * Gets key address
   * @return key address
   */
  public final long getKeyAddress() {
    return keyAddress;
  }
  
  /**
   * Sets key size
   * @param size key size
   */
  public final void setKeySize (int size) {
    this.keySize = size;
  }
  
  /**
   * Gets key size
   * @return key size
   */
  public final int getKeySize() {
    return keySize;
  }
  
  /**
   * Sets version (not used anymore)
   * @param version version
   * @deprecated
   */
  public final void setVersion(long version) {
    this.version = version;
  }
  
  /**
   * Get the vesrion
   * @return version (deprectaed)
   * @deprecated
   */
  public final long getVersion() {
    return version;
  }
 
  /**
   * This operation is read-only
   * @param b
   */
  public final void setReadOnly(boolean b) {
    this.readOnly = b;
  }
 
  /**
   * Is operation read-only
   * @return read only
   */
  public boolean isReadOnly() {
    return this.readOnly;
  }
  
  public void reset() {
    this.keyAddress = 0;
    this.keySize = 0;
    this.version = 0;
    this.keys[0] = 0;
    this.keys[1] = 0;
    this.keySizes[0] = 0;
    this.keySizes[1] = 0;
    this.values[0] = 0;
    this.values[1] = 0;
    this.valueSizes[0] = 0;
    this.valueSizes[1] = 0;
    this.foundRecordAddress = 0;
    this.updateTypes[0] = false;
    this.updateTypes[1] = false;
    this.floorKey = false;
    this.reuseValues[0] = false;
    this.reuseValues[1] = false;
    this.readOnly = false;
  }
  
  /**
   * Sets floor key (look for greatest key which are less or equals to)
   * @param b
   */
  public final void setFloorKey(boolean b) {
    this.floorKey = b;
  }
  
  /**
   * Is floor key
   * @return true/false
   */
  public final boolean isFloorKey() {
    return floorKey;
  }
  
  /**
   * Set found record address before execution update
   * Must handle NOT_FOUND
   * @param address
   */
  public final void setFoundRecordAddress(long address) {
    this.foundRecordAddress = address;
  }

  /**
   * Execute update operation on a found K-V record
   * @return true, if success, false - to abort
   */
  public abstract boolean execute() ;
  
 
  /**
   * Gets total update result count (zero, one or two puts)
   * 0 - means update in place was done
   * 1 - update for current key
   * 2 - split of a current Key Value into two consecutive key values
   * @return number of updates required
   */
  public final int getUpdatesCount() {
    return updatesCount;
  }
  
  /**
   * Gets result key addresses
   * @return key addresses
   */
  public final long[] keys() {
    return this.keys;
  }
  
  /**
   * Gets result key sizes
   * @return key sizes
   */
  public final int[] keySizes() {
    return keySizes;
  }
  
  /**
   * Get result value addresses
   * @return value addresses
   */
  public final long[] values() {
    return values;
  }
  
  /**
   * Get result value sizes
   * @return value sizes
   */
  public final int[] valueSizes() {
    return valueSizes;
  }
  /**
   * Get update types for update operations
   * @return
   */
  public final boolean[] updateTypes() {
    return updateTypes;
  }
  /**
   * Get reuse values
   * @return reuse values
   */
  public final boolean[] reuseValues() {
    return reuseValues;
  }
}
