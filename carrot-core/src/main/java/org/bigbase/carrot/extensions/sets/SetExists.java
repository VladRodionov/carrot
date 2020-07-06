package org.bigbase.carrot.extensions.sets;

import static org.bigbase.carrot.extensions.Commons.KEY_SIZE;
import static org.bigbase.carrot.extensions.Commons.elementAddressFromKey;
import static org.bigbase.carrot.extensions.Commons.elementSizeFromKey;
import static org.bigbase.carrot.extensions.Commons.keySize;

import org.bigbase.carrot.DataBlock;
import org.bigbase.carrot.ops.Operation;
import org.bigbase.carrot.util.Utils;



/**
 * Although not a mutation this operation is executed as a mutation
 * to avoid copy - on -read. It checks if element exists in a set defined by a
 * Key in place w/o copying Value data.
 * @author Vladimir Rodionov
 *
 */
public class SetExists extends Operation{
  // TODO: use own keyArena

    
  public SetExists() {
    setFloorKey(true);
  }
  
  @Override
  public void reset() {
    super.reset();
    setFloorKey(true);
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
    
    long elementPtr = elementAddressFromKey(keyAddress);
    int elementSize = elementSizeFromKey(keyAddress, keySize);
    // Set no updates
    updatesCount = 0;
    // First two bytes are number of elements in a value
    return Sets.exactSearch(foundRecordAddress, elementPtr, elementSize) > 0;
  }
  

}
