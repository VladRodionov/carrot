/*
 Copyright (C) 2021-present Carrot, Inc.

 <p>This program is free software: you can redistribute it and/or modify it under the terms of the
 Server Side Public License, version 1, as published by MongoDB, Inc.

 <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 Server Side Public License for more details.

 <p>You should have received a copy of the Server Side Public License along with this program. If
 not, see <http://www.mongodb.com/licensing/server-side-public-license>.
*/
package org.bigbase.carrot.redis.cluster;

import org.bigbase.carrot.redis.RedisConf;

public class Cluster {

  /**
   * Get cluster slots
   *
   * @return cluster slots
   */
  public static Object[] SLOTS() {
    RedisConf conf = RedisConf.getInstance();
    return conf.getClusterSlots();
  }
}
