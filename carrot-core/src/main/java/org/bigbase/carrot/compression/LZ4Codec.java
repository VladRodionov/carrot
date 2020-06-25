/*******************************************************************************
* Copyright (c) 2013 Vladimir Rodionov. All Rights Reserved
*
* This code is released under the GNU Affero General Public License.
*
* See: http://www.fsf.org/licensing/licenses/agpl-3.0.html
*
* VLADIMIR RODIONOV MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY
* OF THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
* IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, OR
* NON-INFRINGEMENT. Vladimir Rodionov SHALL NOT BE LIABLE FOR ANY DAMAGES SUFFERED
* BY LICENSEE AS A RESULT OF USING, MODIFYING OR DISTRIBUTING THIS SOFTWARE OR
* ITS DERIVATIVES.
*
* Author: Vladimir Rodionov
*
*******************************************************************************/
package org.bigbase.carrot.compression;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.log4j.Logger;

import org.bigbase.compression.lz4.LZ4;


// TODO: Auto-generated Javadoc
/**
 * The Class QuickLZCodec.
 */
public class LZ4Codec implements Codec {

	/** The Constant LOG. */
	@SuppressWarnings("unused")
    private final static Logger LOG = Logger.getLogger(LZ4Codec.class);
	
	/** The min comp size. */
	private int minCompSize = 100;
	
	/** The total size. */
	private long totalSize = 0;
	
	/** The total comp size. */
	private long totalCompSize = 0;
	
	/** The level. */
	private int level = 1;
	
	/**
	 * Instantiates a new quick lz codec.
	 */
	public LZ4Codec() {
		minCompSize = Integer.parseInt(System.getProperty(COMPRESSION_THRESHOLD, "100"));
	}
	
	/* (non-Javadoc)
	 * @see com.koda.compression.Codec#compress(java.nio.ByteBuffer, java.nio.ByteBuffer)
	 */
	@Override
	public int compress(ByteBuffer src, ByteBuffer dst) throws IOException {
		
		this.totalSize += (src.limit() - src.position());
		int total = LZ4.compress(src, dst);
		this.totalCompSize += total;
		return total;
	}

	/* (non-Javadoc)
	 * @see com.koda.compression.Codec#decompress(java.nio.ByteBuffer, java.nio.ByteBuffer)
	 */
	@Override
	public int decompress(ByteBuffer src, ByteBuffer dst) throws IOException {
		
		int total = LZ4.decompress(src, dst);
		return total;
	}
  @Override
  public int compress(long src, int srcSize, long dst, int dstCapacity) {
    return LZ4.compressDirectAddress(src, srcSize, dst, dstCapacity);
  }

  @Override
  public int decompress(long src, int compressedSize, long dst, int dstCapacity) {
    return LZ4.decompressDirectAddress(src, compressedSize, dst, dstCapacity);
  }
  
	/* (non-Javadoc)
	 * @see com.koda.compression.Codec#getCompressionThreshold()
	 */
	@Override
	public int getCompressionThreshold() {
		
		return minCompSize;
	}

	/* (non-Javadoc)
	 * @see com.koda.compression.Codec#getType()
	 */
	@Override
	public CodecType getType() {
		return CodecType.LZ4;
	}

	/* (non-Javadoc)
	 * @see com.koda.compression.Codec#setCompressionThreshold(int)
	 */
	@Override
	public void setCompressionThreshold(int val) {
		minCompSize = val;

	}

	/* (non-Javadoc)
	 * @see com.koda.compression.Codec#getAvgCompressionRatio()
	 */
	@Override
	public double getAvgCompressionRatio() {
		if(totalCompSize == 0){
			return 1.d;
		} else{
			return ((double)totalSize)/totalCompSize;
		}
	}
	
	/* (non-Javadoc)
	 * @see com.koda.compression.Codec#getLevel()
	 */
	@Override
	public int getLevel() {

		return level;
	}
	
	/* (non-Javadoc)
	 * @see com.koda.compression.Codec#setLevel(int)
	 */
	@Override
	public void setLevel(int level) {
		this.level = level;
		
	}
  
	@Override
  public long getTotalProcessed() {
    return totalSize;
  }
  
  public static void main(String[] args) throws IOException{
    
    String str = 
      "teruyiuylo[piptuytrtyytytytyttryjtruyrktuyuyrktyrytrjytjyuyrkg.kyrtyytejyyteyuyrkuyutuyuyruyrukytuyrkuy"+
      "teruyiuylo[piptuytrtyytytytyttryjtruyrktuyuyrktyrytrjytjyuyrkg.kyrtyytejyyteyuyrkuyutuyuyruyrukytuyrkuy"+
      "teruyiuylo[piptuytrtyytytytyttryjtruyrktuyuyrktyrytrjytjyuyrkg.kyrtyytejyyteyuyrkuyutuyuyruyrukytuyrkuy"+
      "teruyiuylo[piptuytrtyytytytyttryjtruyrktuyuyrktyrytrjytjyuyrkg.kyrtyytejyyteyuyrkuyutuyuyruyrukytuyrkuy"+
      "teruyiuylo[piptuytrtyytytytyttryjtruyrktuyuyrktyrytrjytjyuyrkg.kyrtyytejyyteyuyrkuyutuyuyruyrukytuyrkuy";
    
    str += str;
    str += str;
    str += str;
    str += str;
    str += str;
    
    ByteBuffer src = ByteBuffer.allocateDirect(102400);
    ByteBuffer dst = ByteBuffer.allocateDirect(102400);
    Codec codec = new LZ4Codec();
    
    byte[] buf = str.getBytes();
    src.put(buf);
    src.flip();
    int compSize = codec.compress(src, dst);
    System.out.println("Size="+ str.length() +" compressed ="+compSize);
    
    src.clear();
    
    int decSize = codec.decompress(dst, src);
    System.out.println("Size="+ str.length() +" decompressed ="+decSize);        
    
  }


}
