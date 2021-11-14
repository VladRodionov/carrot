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
package org.bigbase.compression.lz4;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.bigbase.common.nativelib.LibLoader;

@SuppressWarnings("unused")
public class LZ4 {

  static {
    LibLoader.loadNativeLibrary("lz4");
  }

  public static int compress(ByteBuffer src, ByteBuffer dst) {
    int len = src.remaining();
    int offset = src.position();
    int where = dst.position();
    int dstCapacity = dst.capacity() - where;
    int r = compressDirect(src, offset, len, dst, where, dstCapacity);
    dst.position(where);
    dst.limit(where + r);
    return r;
  }

  public static int decompress(ByteBuffer src, ByteBuffer dst) {
    // First 4 bytes contains orig length
    int compressedSize = src.position();
    int offset = src.position();
    int where = dst.position();
    int dstCapacity = dst.remaining();
    int r = decompressDirect(src, offset, compressedSize, dst, where, dstCapacity);
    // check if r != 0
    dst.position(where);
    dst.limit(where + r);
    return r;
  }

  public static int compressHC(ByteBuffer src, ByteBuffer dst, int level) {
    int len = src.remaining();
    int offset = src.position();
    int where = dst.position();
    int dstCapacity = dst.capacity() - where;
    int r = compressDirectHC(src, offset, len, dst, where, dstCapacity);
    dst.position(where);
    dst.limit(where + r);
    return r;
  }

  public static int decompressHC(ByteBuffer src, ByteBuffer dst) {
    // First 4 bytes contains orig length
    int len = src.remaining();
    int offset = src.position();
    int where = dst.position();
    int dstCapacity = dst.capacity() - where;

    int r = decompressDirectHC(src, offset, len, dst, where, dstCapacity);
    dst.position(where);
    dst.limit(where + r);
    return r;
  }

  /**
   * Compress block of data
   *
   * @param src - source buffer (MUST BE DIRECT BUFFER)
   * @param offset - offset in source
   * @param len - number of bytes to compress
   * @param dst - destination buffer (MUST BE DIRECT BUFFER)
   * @param where - offset in destination buffer
   * @return - total compressed bytes
   */
  public static native int compressDirect(
      ByteBuffer src, int offset, int len, ByteBuffer dst, int where, int dstCapacity);

  public static native int compressDirectHC(
      ByteBuffer src, int offset, int len, ByteBuffer dst, int where, int dstCapacity);

  public static native int compressDirectAddress(long src, int len, long dst, int dstCapacity);

  public static native int compressDirectAddressHC(
      long src, int len, long dst, int dstCapacity, int level);
  /**
   * Decompress block of data
   *
   * @param src - source buffer
   * @param offset - offset in source buffer
   * @param dst - destination buffer
   * @param where - offset in destination buffer
   * @return - total decompressed bytes
   */
  public static native int decompressDirect(
      ByteBuffer src, int offset, int origSize, ByteBuffer dst, int where, int dstCapacity);

  public static native int decompressDirectHC(
      ByteBuffer src, int offset, int origSize, ByteBuffer dst, int where, int dstCapacity);

  public static native int decompressDirectAddress(
      long src, int compressedSize, long dst, int dstCapacity);

  public static native int decompressDirectAddressHC(
      long src, int compressedSize, long dst, int dstCapacity);

  public static void main(String[] args) {
    String test = "TestTest12345678";

    int bufSize = 4096 * 1024;

    ByteBuffer src = ByteBuffer.allocateDirect(bufSize);
    src.order(ByteOrder.nativeOrder());
    ByteBuffer dst = ByteBuffer.allocateDirect(bufSize);
    dst.order(ByteOrder.nativeOrder());
    //		byte[] buf = test.getBytes();
    //	    int off = 0;
    //		while(off < bufSize){
    //		    src.put(buf);
    //		    off += buf.length;
    //		}
    //        int origSize = src.limit();
    //		int numIterations = 100000;
    //        long start = System.currentTimeMillis();
    //        src.flip();
    //        for(int i=0; i < numIterations; i++){
    //		    dst.clear();
    //		    int compressedSize = compress(src, dst);
    //		//System.out.println("Original size="+origSize+" comp size="+compressedSize);
    //		    src.position(0);
    //		    int r = decompress(dst, src);
    //		}
    //		long stop = System.currentTimeMillis();
    //
    //		System.out.println((numIterations*1000)/(stop - start) +" of "+origSize +" blocks per sec");
    //
    //        byte[] b = new byte[origSize];
    //		System.out.println("src off="+src.position()+" src.limit="+src.limit());
    //		src.get(b);
    //
    //		System.out.println("Original     = "+test);
    //		System.out.println("Decompressed = "+new String(b));

    String value =
        "value-value-value-value-value-value-value-value-value-value-value-value-value-value-value"
            + "value-value-value-value-value-value-value-value-value-value-value-value-value-value-value"
            + "value-value-value-value-value-value-value-value-value-value-value-value-value-value-value";
    int DST = 0;
    int SRC = 0;
    src.clear();
    dst.clear();
    src.position(SRC);
    dst.position(DST);
    src.put(value.getBytes());
    int pos = src.position();
    src.position(SRC);
    src.limit(pos);

    int compressedSize = compressHC(src, dst, 1);
    System.out.println("Original size=" + value.length() + " comp size=" + compressedSize);

    for (int i = 0; i < compressedSize; i++) {
      System.out.print(dst.get(DST + i) + " ");
    }
    System.out.println();
    src.position(SRC);
    dst.position(DST);
    dst.limit(DST + compressedSize);
    src.limit(src.capacity());
    System.out.println(
        "src.pos="
            + dst.position()
            + " size="
            + dst.remaining()
            + " dst.pos="
            + src.position()
            + " limit="
            + src.limit());
    int r = decompressHC(dst, src);
    int size = src.limit() - SRC;
    byte[] v = new byte[size];
    src.position(SRC);
    src.get(v);
    String newValue = new String(v);
    System.out.println(value);
    System.out.println(newValue);
    System.out.println(newValue.equals(value));
  }
}
