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
package org.bigbase.carrot.redis;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

import org.bigbase.carrot.BigSortedMap;
 
/**
 *
 *  Simple network server for MVP
 *  Scalability and performance is not a goal #1 yet
 */
 
public class Server2 {
  
  /**
   * Executor service
   */
  static RequestHandlers service;
  
  /**
   * In memory data store
   */
  static BigSortedMap store;
  
  /**
   * I/O selector for async operations
   */
  static Selector selector;
  
  static boolean started = false;
  
  /**
   * Has server started yet?
   * @return true, false
   */
  public static boolean hasStarted() {
    return started;
  }
  
  
  public static void start() {
    new Thread(() -> {
      try {
        Server.main(new String[] {});
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }).start();
    
    while(!hasStarted()) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      
      log("started="+ started);
    }
  }
  
  public static void main(String[] args) throws IOException {
    log("Carrot server starting ...");
    
    initStore();
    log("Store started");
    
    startExecutorService();
    log("Executor service started");
    
    // Selector: multiplexor of SelectableChannel objects
    Selector selector = Selector.open(); // selector is open here
    log("Selector started");

    // ServerSocketChannel: selectable channel for stream-oriented listening sockets
    ServerSocketChannel serverSocket = ServerSocketChannel.open();
    log("Server socket opened");

    int port = RedisConf.getInstance().getServerPort();
    InetSocketAddress serverAddr = new InetSocketAddress("localhost", port);
 
    // Binds the channel's socket to a local address and configures the socket to listen for connections
    serverSocket.bind(serverAddr);
    log("Carrot server started on port="+ port);
    // Adjusts this channel's blocking mode.
    serverSocket.configureBlocking(false);
 
    int ops = serverSocket.validOps();
    serverSocket.register(selector, ops, null);
    log("Selector registered server");

    started = true;
    log("Carrot server started.");

    // Infinite loop..
    // Keep server running
    try {
      while (true) {

        // Selects a set of keys whose corresponding channels are ready for I/O operations
        selector.select();

        // token representing the registration of a SelectableChannel with a Selector
        Set<SelectionKey> keys = selector.selectedKeys();
        Iterator<SelectionKey> keysIterator = keys.iterator();

        while (keysIterator.hasNext()) {
          SelectionKey myKey = keysIterator.next();
          if (!myKey.isValid()) {
            keysIterator.remove();
            continue;
          }
          // Tests whether this key's channel is ready to accept a new socket connection
          if (myKey.isAcceptable()) {
            SocketChannel client = serverSocket.accept();

            // Adjusts this channel's blocking mode to false
            client.configureBlocking(false);
            client.setOption(StandardSocketOptions.TCP_NODELAY, true);
            // Operation-set bit for read operations
            client.register(selector, SelectionKey.OP_READ);
            log("Connection Accepted: " + client.getLocalAddress() + "\n");

            // Tests whether this key's channel is ready for reading
            // and is not being currently processed
          } else if (myKey.isReadable()) {
            RequestHandlers.Attachment att = (RequestHandlers.Attachment) myKey.attachment();
            if (att != null && att.inUse()) {
              continue;
            }
            service.submit(myKey);
          }
          keysIterator.remove();
        }
      }
    } catch (ClosedSelectorException e) {
      log("Shutting down server ...");
      service.shutdown();
      serverSocket.close();
      store.dispose();
      store = null;
      service = null;
      selector = null;
      log("Complete");
    }
  }
 
  
  public static void shutdown() {
    if (selector != null) {
      try {
        selector.close();
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
  }
  private static void initStore() {
    RedisConf conf = RedisConf.getInstance();
    long limit = conf.getDataStoreMaxSize();
    store = new BigSortedMap(limit);
    BigSortedMap.setCompressionCodec(conf.getCompressionCodec());
  }

  private static void startExecutorService() {
    RedisConf conf = RedisConf.getInstance();
    int numThreads = conf.getWorkingThreadPoolSize();
    service = RequestHandlers.create(store, numThreads);
    service.start();
  }

  static void log(String str) {
    System.out.println(str);
  }
  
  static void logError(String str) {
    System.err.println(str);
  }
}
