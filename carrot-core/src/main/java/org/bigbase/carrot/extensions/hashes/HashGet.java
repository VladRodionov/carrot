package org.bigbase.carrot.extensions.hashes;

import static org.bigbase.carrot.extensions.Commons.KEY_SIZE;
import static org.bigbase.carrot.extensions.Commons.elementAddressFromKey;
import static org.bigbase.carrot.extensions.Commons.elementSizeFromKey;
import static org.bigbase.carrot.extensions.Commons.keySize;

import org.bigbase.carrot.DataBlock;
import org.bigbase.carrot.ops.Operation;
import org.bigbase.carrot.util.UnsafeAccess;
import org.bigbase.carrot.util.Utils;

public class HashGet extends Operation{

  private long bufferPtr;
  private int bufferSize;
  private int foundValueSize = -1;
  
  public HashGet() {
    setFloorKey(true);
  }

  @Override
  public void reset() {
    super.reset();
    setFloorKey(true);
    bufferPtr = 0;
    bufferSize=0;
    foundValueSize = -1;
  }
  
  public void setBufferPtr(long ptr) {
    this.bufferPtr = ptr;
  }
  
  public void setBufferSize(int size) {
    this.bufferSize = size;
  }
  
  public int getFoundValueSize() {
    return foundValueSize;
  }
  
  @Override
  public boolean execute() {
    if (foundRecordAddress <=0) {
      return false;
    }
    // check prefix
    int setKeySize = keySize(keyAddress);
    int foundKeySize = DataBlock.keyLength(foundRecordAddress);
    if (foundKeySize <= setKeySize + KEY_SIZE) {
      return false;
    }
    long foundKeyAddress = DataBlock.keyAddress(foundRecordAddress);
    // Prefix keys must be equals
    if (Utils.compareTo(keyAddress, setKeySize +  KEY_SIZE, foundKeyAddress, 
      setKeySize +  KEY_SIZE) != 0) {
      return false;
    }
    
    long fieldPtr = elementAddressFromKey(keyAddress);
    int fieldSize = elementSizeFromKey(keyAddress, keySize);
    // Set no updates
    updatesCount = 0;
    // First two bytes are number of elements in a value
    long address = Hashes.exactSearch(foundRecordAddress, fieldPtr, fieldSize);
    if (address < 0) {
      this.foundValueSize = -1;
      return false;
    }
    
    // address of a field-value pair
    foundValueSize = Hashes.getValueSize(address);
    
    if (foundValueSize > bufferSize) {
      // just return
      return true;
    }
    long vAddress = Hashes.getValueAddress(address);
    UnsafeAccess.copy(vAddress, bufferPtr, foundValueSize);
    return true;
  }
}
