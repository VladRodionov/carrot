/**
 *    Copyright (C) 2021-present Carrot, Inc.
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the Server Side Public License, version 1,
 *    as published by MongoDB, Inc.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    Server Side Public License for more details.
 *
 *    You should have received a copy of the Server Side Public License
 *    along with this program. If not, see
 *    <http://www.mongodb.com/licensing/server-side-public-license>.
 *
 */
package org.bigbase.carrot.redis.hashes;

import static org.bigbase.carrot.redis.util.Commons.elementAddressFromKey;
import static org.bigbase.carrot.redis.util.Commons.elementSizeFromKey;
import static org.bigbase.carrot.redis.util.Commons.keySizeWithPrefix;

import org.bigbase.carrot.DataBlock;
import org.bigbase.carrot.ops.Operation;
import org.bigbase.carrot.util.Utils;
/**
 * Although not a mutation this operation is executed as a mutation
 * to avoid copy - on -read. It checks if field exists in a hash defined by a
 * Key in place, w/o copying Value data.
 * 
 *
 */
public class HashExists extends Operation {

  public HashExists() {
    setFloorKey(true);
    setReadOnly(true);
  }
  
  @Override
  public void reset() {
    super.reset();
    setFloorKey(true);
    setReadOnly(true);
  }
  
  @Override
  public boolean execute() {
    if (foundRecordAddress <=0) {
      return false;
    }
    // check prefix
    int setKeySize = keySizeWithPrefix(keyAddress);
    int foundKeySize = DataBlock.keyLength(foundRecordAddress);
    if (foundKeySize <= setKeySize ) {
      return false;
    }
    long foundKeyAddress = DataBlock.keyAddress(foundRecordAddress);
    // Prefix keys must be equals
    if (Utils.compareTo(keyAddress, setKeySize , foundKeyAddress, 
      setKeySize ) != 0) {
      return false;
    }
    
    long fieldPtr = elementAddressFromKey(keyAddress);
    int fieldSize = elementSizeFromKey(keyAddress, keySize);
    // Set no updates
    updatesCount = 0;
    // First two bytes are number of elements in a value
    return Hashes.exactSearch(foundRecordAddress, fieldPtr, fieldSize) > 0;
  }

}
