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

import org.bigbase.carrot.redis.RedisConf;

public class TestCOMMAND_COUNT extends CommandBase {
  
  protected String[] validRequests = new String[] {
      "COMMAND COUNT"               /* 103 */
  };
  
  protected String[] validResponses = new String[] {
      ":"+ RedisConf.getInstance().getCommandsCount()+"\r\n",
  };
  
  
  protected String[] invalidRequests = new String[] {
      "command x y",                      /* unsupported command */
      "COMMAND COUNT X",                  /* wrong number of arguments*/
      "COMMAND X",                        /* wrong number of arguments*/
  };
  
  protected String[] invalidResponses = new String[] {
    "-ERR Unsupported command: command\r\n",
    "-ERR: Wrong number of arguments\r\n",
    "-ERR: Unsupported command: COMMAND X\r\n"
  };
  
  /**
   * Subclasses must override
   */
  protected String[] getValidRequests() {
    return validRequests;
  }
  
  protected String[] getValidResponses() {
    return validResponses;
  }
  protected String[] getInvalidRequests() {
    return invalidRequests;
  }
  protected String[] getInvalidResponses() {
    return invalidResponses;
  }
}