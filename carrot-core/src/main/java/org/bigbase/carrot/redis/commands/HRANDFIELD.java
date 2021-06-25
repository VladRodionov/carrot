package org.bigbase.carrot.redis.commands;

import org.bigbase.carrot.BigSortedMap;
import org.bigbase.carrot.redis.hashes.Hashes;
import org.bigbase.carrot.util.UnsafeAccess;
import org.bigbase.carrot.util.Utils;

public class HRANDFIELD implements RedisCommand {

  @Override
  public void execute(BigSortedMap map, long inDataPtr, long outBufferPtr, int outBufferSize) {
    
    try {
    int count = 1;
    boolean withValues = false;
    
    int numArgs = UnsafeAccess.toInt(inDataPtr);

    if (numArgs < 2 || numArgs > 4) {
      Errors.write(outBufferPtr, Errors.TYPE_GENERIC, Errors.ERR_WRONG_ARGS_NUMBER);
      return;
    }
    inDataPtr += Utils.SIZEOF_INT;
    
    int keySize = UnsafeAccess.toInt(inDataPtr);
    inDataPtr += Utils.SIZEOF_INT;
    long keyPtr = inDataPtr;
    inDataPtr += keySize;
 
    if (numArgs > 2) {
      int size = UnsafeAccess.toInt(inDataPtr);
      inDataPtr += Utils.SIZEOF_INT;
      count = (int) Utils.strToLong(inDataPtr, size);
      inDataPtr += size;
    }
    
    if (numArgs > 3) {
      int size = UnsafeAccess.toInt(inDataPtr);
      inDataPtr += Utils.SIZEOF_INT;
      if (Utils.compareTo(WITHVALUES_FLAG, WITHVALUES_LENGTH, inDataPtr, size) == 0) {
        withValues = true;
      } else {
        throw new IllegalArgumentException(Utils.toString(inDataPtr, size));
      }
    }
    int size = (int) Hashes.HRANDFIELD(map, keyPtr, keySize, count, withValues, 
      outBufferPtr + Utils.SIZEOF_BYTE + Utils.SIZEOF_INT, outBufferSize - Utils.SIZEOF_BYTE - Utils.SIZEOF_INT); 
    // Array reply
    UnsafeAccess.putByte(outBufferPtr, (byte) ReplyType.VARRAY.ordinal());
    // Array serialized size
    UnsafeAccess.putInt(outBufferPtr + Utils.SIZEOF_BYTE, size + Utils.SIZEOF_BYTE + Utils.SIZEOF_INT);
    // All data is in the out buffer, including number of elements
    } catch (NumberFormatException e) {
      Errors.write(outBufferPtr, Errors.TYPE_GENERIC, Errors.ERR_WRONG_NUMBER_FORMAT);
    } catch (IllegalArgumentException ee) {
      Errors.write(outBufferPtr, Errors.TYPE_GENERIC, Errors.ERR_ILLEGAL_ARGS, ee.getMessage());
    }
  }

}
