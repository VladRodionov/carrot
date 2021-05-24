package org.bigbase.carrot.ops;

import org.bigbase.carrot.DataBlock;
import org.bigbase.carrot.util.UnsafeAccess;
import org.bigbase.carrot.util.Utils;

/**
 * This example of specific Update - atomic counter 
 * Implementation is not safe for regular scanners
 * 
 * Should introduce scanner with write locks?
 * @author Vladimir Rodionov
 *
 */
public class IncrementFloat extends Operation {
  
  static ThreadLocal<Long> buffer = new ThreadLocal<Long>() {
    @Override
    protected Long initialValue() {
      return UnsafeAccess.malloc(Utils.SIZEOF_FLOAT);
    }
  };
  
  float value;
  public IncrementFloat() {
    setReadOnlyOrUpdateInPlace(true);
  }
  
  /**
   * Set increment value
   * @param v value
   */
  public void setIncrement(float v) {
    this.value = v;
  }
  
  /**
   * Get increment value
   * @return value
   */
  public float getIncrement() {
    return value;
  }
  /**
   * Value after increment
   * @return value after increment
   */
  public float getValue() {
    return value;
  }
  
  @Override
  public void reset() {
    super.reset();
    value = 0;
    setReadOnlyOrUpdateInPlace(true);
  }
  
  @Override
  public boolean execute() {
    float fv = 0f;
    if (foundRecordAddress > 0) {
      int vSize = DataBlock.valueLength(foundRecordAddress);
      if (vSize != Utils.SIZEOF_FLOAT /*long size*/) {
        return false;
      }
      long ptr = DataBlock.valueAddress(foundRecordAddress);
      int v = UnsafeAccess.toInt(ptr);
      fv = Float.intBitsToFloat(v);
      this.value += fv;
      UnsafeAccess.putInt(ptr, Float.floatToIntBits(value));
      this.updatesCount = 0;
      return true;
    }
    this.value += fv;
    UnsafeAccess.putInt(buffer.get(), Float.floatToIntBits(value));
    this.updatesCount = 1;
    this.keys[0] = keyAddress;
    this.keySizes[0] = keySize;
    this.values[0] = buffer.get();
    this.valueSizes[0] = Utils.SIZEOF_FLOAT;
    return true;
  }

}