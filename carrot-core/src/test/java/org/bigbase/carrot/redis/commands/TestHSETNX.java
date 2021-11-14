/**
 * Copyright (C) 2021-present Carrot, Inc.
 *
 * <p>This program is free software: you can redistribute it and/or modify it under the terms of the
 * Server Side Public License, version 1, as published by MongoDB, Inc.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * Server Side Public License for more details.
 *
 * <p>You should have received a copy of the Server Side Public License along with this program. If
 * not, see <http://www.mongodb.com/licensing/server-side-public-license>.
 */
package org.bigbase.carrot.redis.commands;

public class TestHSETNX extends CommandBase {

  protected String[] validRequests =
      new String[] {
        "HSETNX key1 field1 value1", /* 1 */
        "HGET key1 field1", /* value1 */
        "hsetnx key1 field1 value2", /* 0 */
        "HGET key1 field1" /* value1 */
      };

  protected String[] validResponses =
      new String[] {":1\r\n", "$6\r\nvalue1\r\n", ":0\r\n", "$6\r\nvalue1\r\n"};

  protected String[] invalidRequests =
      new String[] {
        "HSETNX", /* wrong number of arguments*/
        "HSETNX x y", /* wrong number of arguments*/
        "HSETNX x y z zz" /* wrong number of arguments*/
      };

  protected String[] invalidResponses =
      new String[] {
        "-ERR: Wrong number of arguments\r\n",
        "-ERR: Wrong number of arguments\r\n",
        "-ERR: Wrong number of arguments\r\n"
      };

  /** Subclasses must override */
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
