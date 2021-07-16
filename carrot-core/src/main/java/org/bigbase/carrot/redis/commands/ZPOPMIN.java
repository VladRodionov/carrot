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
package org.bigbase.carrot.redis.commands;

import org.bigbase.carrot.BigSortedMap;
import org.bigbase.carrot.redis.commands.RedisCommand.ReplyType;
import org.bigbase.carrot.redis.zsets.ZSets;
import org.bigbase.carrot.util.UnsafeAccess;
import org.bigbase.carrot.util.Utils;

public class ZPOPMIN implements RedisCommand {

  @Override
  public void execute(BigSortedMap map, long inDataPtr, long outBufferPtr, int outBufferSize) {
    try {
      int count = 1;
      int numArgs = UnsafeAccess.toInt(inDataPtr);
      if (numArgs != 2 && numArgs != 3) {
        Errors.write(outBufferPtr, Errors.TYPE_GENERIC, Errors.ERR_WRONG_ARGS_NUMBER);
        return;
      }
      inDataPtr += Utils.SIZEOF_INT;
      // skip command name
      inDataPtr = skip(inDataPtr, 1);
      // read key
      int keySize = UnsafeAccess.toInt(inDataPtr);
      inDataPtr += Utils.SIZEOF_INT;
      long keyPtr = inDataPtr;
      inDataPtr += keySize;
      if (numArgs == 3) {
        int countSize = UnsafeAccess.toInt(inDataPtr);
        inDataPtr += Utils.SIZEOF_INT;
        long countPtr = inDataPtr;
        count = (int) Utils.strToLong(countPtr, countSize);

      }
      int off = Utils.SIZEOF_BYTE + Utils.SIZEOF_INT;
      int size =
          (int) ZSets.ZPOPMIN(map, keyPtr, keySize, count, outBufferPtr + off, outBufferSize - off);
      UnsafeAccess.putByte(outBufferPtr, (byte) ReplyType.ZARRAY.ordinal());
      UnsafeAccess.putInt(outBufferPtr + Utils.SIZEOF_BYTE, size + off);

    } catch (NumberFormatException e) {
      Errors.write(outBufferPtr, Errors.TYPE_GENERIC, Errors.ERR_WRONG_NUMBER_FORMAT,
        "count is not a valid number");
    }
  }

}