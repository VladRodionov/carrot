package org.bigbase.carrot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.bigbase.carrot.util.Bytes;
import org.bigbase.carrot.util.UnsafeAccess;
import org.bigbase.carrot.util.Utils;

/**
 * TODO: 1. minimize block overhead
 *       2. Check logic
 *       3. Do not keep address, but index in a array of addresses (?)
 *          
 * Records are sorted Format: [DATA_BLOCK_STATIC_PREFIX][BLOCK_START_KEY] 
 * 
 * 
 * TODO: reconsider overhead
 [DATA_BLOCK_STATIC_PREFIX] - 19 bytes
 
     dataPtr                  (8 bytes)
     blockSize                (2 bytes)
     dataSize                 (2 bytes)
     numRecords               (2 bytes)
     seqNumberSplitOrMerge    (1 byte) - no need for single thread version
     compressed               (1 byte)
     threadSafe               (1 byte)
     numDeletedAndUpdatedRecords (2 bytes)

 [BLOCK_START_KEY] = [len][key]
     [len] - 2 bytes
     [key] - key data
     [version] - 8 bytes
     [type]    - 1 byte (DELETE=0, PUT =1) 

 */
/**
 * TODO: how to handle deletion of a first key in a first data block?
 * @author jenium65
 *
 */

public final class IndexBlock implements Comparable<IndexBlock> {

	public final static int KEY_SIZE_LENGTH = 2;
	public final static long NOT_FOUND = -1L;
	public final static double MIN_COMPACT_RATIO = 0.25d;
	public final static double MAX_MERGE_RATIO = 0.25d;

	public final static String MAX_BLOCK_SIZE_KEY = "max.index.block.size";
	/*
	 * TODO: check usage for DataBlocks
	 */
	public static int MAX_BLOCK_SIZE = 4096;

	public final static int DATA_BLOCK_STATIC_PREFIX = 19;
	public final static int VERSION_SIZE = 8;
	public final static int TYPE_SIZE = 1;
	public final static int DATA_BLOCK_STATIC_OVERHEAD = DATA_BLOCK_STATIC_PREFIX + VERSION_SIZE + TYPE_SIZE; // 28
	public final static int INT_SIZE = 4;
	public final static int ADDRESS_SIZE = 8;
	
	/*
	 * This is heuristic value. Must be configurable. THis threshold defines the maximum posible
	 * schedule timeout for any working thread in the application. When threads are attached
	 * to a particular CPU cores - this value can be very low. The lower value is the better
	 * performance is for put/delete operations.
	 */
	private static long SAFE_UNSAFE_THRESHOLD = 500;// in ms
	
	// bytes

	static {
		String val = System.getProperty(MAX_BLOCK_SIZE_KEY);
		if (val != null) {
			MAX_BLOCK_SIZE = Integer.parseInt(val);
		}
	}


	static ThreadLocal<DataBlock> block = new ThreadLocal<DataBlock>() {

		@Override
		protected DataBlock initialValue() {
			return new DataBlock();
		}

	};

	 static ThreadLocal<DataBlock> block2 = new ThreadLocal<DataBlock>() {

	    @Override
	    protected DataBlock initialValue() {
	      return new DataBlock();
	    }

	  };
	
	static ThreadLocal<Long> keyBuffer = new ThreadLocal<Long>() {
	  @Override 
	  protected Long initialValue() {
	    int size = 4*1024;
	    long ptr = UnsafeAccess.malloc(4*1024);
      BigSortedMap.totalAllocatedMemory.addAndGet(size);	    
	    return ptr;
	  }
	};
	
	static ThreadLocal<Integer> keyBufferSize = new ThreadLocal<Integer>() {
	  @Override
	  protected Integer initialValue() {
	    return 4 * 1024;
	  }
	};
	
	/*
	 * TODO: make this configurable
	 */

	static float[] BLOCK_RATIOS = new float[] { 0.25f, 0.5f, 0.75f, 1.0f };
	static final int EXPANSION_SIZE = 512;
	/*
	 * Read-Write Lock TODO: StampedLock (Java 8)
	 */
	static ReentrantReadWriteLock[] locks = new ReentrantReadWriteLock[11113];
	static {
		for (int i = 0; i < locks.length; i++) {
			locks[i] = new ReentrantReadWriteLock();
		}
	}

	/**
	 * Get min size greater than current
	 * 
	 * @param max     - max size
	 * @param current current size
	 * @return min size or -1;
	 */
	static int getMinSizeGreaterThan(int max, int current) {
		for (int i = 0; i < BLOCK_RATIOS.length; i++) {
			int size = Math.round(max * BLOCK_RATIOS[i]);
			if (size > current)
				return size;
		}
		return -1;
	}

	/**
	 * Get min size greater than current
	 * 
	 * @param max     - max size
	 * @param current current size
	 * @return min size or -1;
	 */
	static int getMinSizeGreaterOrEqualsThan(int max, int current) {
		for (int i = 0; i < BLOCK_RATIOS.length; i++) {
			int size = Math.round(max * BLOCK_RATIOS[i]);
			if (size >= current)
				return size;
		}
		return -1;
	}

	
	static boolean mustAllocateExternally(int len) {
	  return len + DATA_BLOCK_STATIC_OVERHEAD + KEY_SIZE_LENGTH >= MAX_BLOCK_SIZE/2;
	}
	/*
	 * Index block address (current)
	 */
	volatile long dataPtr;

	/*
	 * Index block size
	 */
	short blockSize;

	/*
	 * Total data size (all index records)
	 */
	short blockDataSize = 0;
	/*
	 * Number of index records in a block - index size (volatile?)
	 */
	short numDataBlocks = 0;

	/*
	 * Split/Merge sequence number.
	 */
	volatile short seqNumberSplitOrMerge;

	/*
	 * Is index block compressed
	 */
	boolean compressed = false;

	/*
	 * Is thread safe block
	 */

	boolean threadSafe;

	/*
	 * Is block still valid
	 */
	volatile boolean valid = true;

	/*
	 * First key of this index block (used for comparisons) including version and
	 * type (DELETE = 0, PUT=1)
	 */
	byte[] firstKey;
	long version;
	byte type;
  boolean isFirst = false;
  /*
   * Recent unsafe modification time (ms): Creation, split, merge, update first key
   */
  volatile long lastUnsafeModTime;
	/**
	 * Constructor
	 * 
	 * @param initial size
	 */
	IndexBlock(int size) {
		this.dataPtr = UnsafeAccess.malloc(size);
		if (dataPtr == 0) {
			// TODO: OOM handling
			throw new RuntimeException("Failed to allocate " + size + " bytes");
		}
		// This is not accurate
		BigSortedMap.totalAllocatedMemory.addAndGet(size);
    BigSortedMap.totalBlockIndexSize.addAndGet(size);

		this.blockSize = (short) size;
		updateUnsafeModificationTime();
	}

	void updateUnsafeModificationTime() {
	  this.lastUnsafeModTime = System.currentTimeMillis();
	}
	
	boolean hasRecentUnsafeModification() {
	  return System.currentTimeMillis() - this.lastUnsafeModTime < SAFE_UNSAFE_THRESHOLD;
	}
	
	void setFirstIndexBlock() {
    byte[] kk = new byte[] { (byte) 0};
    put(kk, 0, kk.length, kk, 0, kk.length, -1, Op.PUT.ordinal());
    this.isFirst = true;
	}
	// TODO; first system {0}{0}?
	boolean isFirstIndexBlock() {
	  return this.isFirst;
	}
	
	/**
	 * The method is used only during navigating through CSLM
	 * 
	 * @param key     key array
	 * @param off     offset in array
	 * @param len     length of a key
	 * @param version version
	 */
  void putForSearch(byte[] key, int off, int len, long version) {
		this.threadSafe = true;
		long address = this.dataPtr + DATA_BLOCK_STATIC_PREFIX;
		checkKeyBuffer(len);
		putInternal(address, key, off, len, version, Op.DELETE);
	}

	
  private long allocateKeyExternally(byte[] key, int off, int len) {
    
    checkKeyBufferThreadSafe(len + INT_SIZE);
    long extAddress = isThreadSafe()? keyBuffer.get(): UnsafeAccess.malloc(len + INT_SIZE);
    if (extAddress <= 0) {
      // TODO allocation failure
      return UnsafeAccess.MALLOC_FAILED;
    }
    UnsafeAccess.putInt(extAddress, len);
    UnsafeAccess.copy(key, off, extAddress + INT_SIZE, len);
    if (!isThreadSafe()) {
      largeKVs.incrementAndGet();
      BigSortedMap.totalAllocatedMemory.addAndGet(len + INT_SIZE);
      BigSortedMap.totalIndexSize.addAndGet(len + INT_SIZE);
    }
    return extAddress;
  }
  
  private void checkKeyBufferThreadSafe(int required) {
    if (isThreadSafe() == false) return;
    checkKeyBuffer(required);
  }
  
  private void checkKeyBuffer(int required) {
    if (required > keyBufferSize.get()) {
      // Deallocate existing
      long oldSize = keyBufferSize.get();
      UnsafeAccess.free(keyBuffer.get());
      // Allocate new
      long ptr = UnsafeAccess.malloc(required);
      keyBuffer.set(ptr);
      keyBufferSize.set(required);
      BigSortedMap.totalAllocatedMemory.addAndGet(required - oldSize);
    }
  }
  
  private boolean putInternal(long address, byte[] key, int off, int len, long version, Op op) {
    boolean extAlloc = mustAllocateExternally(len);
    long extAddress = 0;
    if (extAlloc) {
      extAddress = allocateKeyExternally(key, off, len);
      if (extAddress == UnsafeAccess.MALLOC_FAILED) {
        return false;
      }
    }

    UnsafeAccess.putShort(address, extAlloc? (short)0:(short) len);
    address += KEY_SIZE_LENGTH;
    if(extAlloc) {
      UnsafeAccess.putLong(address, extAddress);
      address += ADDRESS_SIZE;
    } else {
      UnsafeAccess.copy(key, off, address, len);
      address += len;
    }
    UnsafeAccess.putLong(address, version);
    address += VERSION_SIZE;
    UnsafeAccess.putByte(address, (byte) op.ordinal());

    return true;
  }
	/**
	 * The method is used only during navigating through CSLM
	 * 
	 * @param key     key array
	 * @param off     offset in array
	 * @param len     length of a key
	 * @param version version
	 */
	void putForSearch(long keyPtr, int len, long version) {
		this.threadSafe = true;
		long address = this.dataPtr + DATA_BLOCK_STATIC_PREFIX;
		checkKeyBuffer(len);
		putInternal(address, keyPtr, len, version, Op.DELETE);
	}

  private long allocateKeyExternally(long keyPtr, int len) {
    
    checkKeyBufferThreadSafe(len + INT_SIZE);
    long extAddress = isThreadSafe()? keyBuffer.get():UnsafeAccess.malloc(len + INT_SIZE);
    if (extAddress <= 0) {
      // TODO allocation failure
      return UnsafeAccess.MALLOC_FAILED;
    }
 
    if (!isThreadSafe()) {
      largeKVs.incrementAndGet();
      BigSortedMap.totalAllocatedMemory.addAndGet(len + INT_SIZE);
      BigSortedMap.totalIndexSize.addAndGet(len + INT_SIZE);
    }
    UnsafeAccess.putInt(extAddress, len);
    UnsafeAccess.copy(keyPtr, extAddress + INT_SIZE, len);
    return extAddress;	
  }
  
  private boolean putInternal(long address, long keyPtr, int len, long version, Op op) {
    boolean extAlloc = mustAllocateExternally(len);
    long extAddress = 0;
    if (extAlloc) {
      extAddress = allocateKeyExternally(keyPtr, len);
      if (extAddress == UnsafeAccess.MALLOC_FAILED) {
        return false;
      }
    }

    UnsafeAccess.putShort(address, extAlloc? (short)0:(short) len);
    address += KEY_SIZE_LENGTH;
    if(extAlloc) {
      UnsafeAccess.putLong(address, extAddress);
      address += ADDRESS_SIZE;
    } else {
      UnsafeAccess.copy(keyPtr, address, len);
      address += len;
    }
    UnsafeAccess.putLong(address, version);
    address += VERSION_SIZE;
    UnsafeAccess.putByte(address, (byte) op.ordinal());

    return true;
  }

	private boolean isExternalBlock(long blockAddress) {
	  return UnsafeAccess.toShort(blockAddress + DATA_BLOCK_STATIC_PREFIX) == 0;
	}
	
	private final int keyLength(long blockAddress) {
		if (isExternalBlock(blockAddress)) {
		  long recAddress = UnsafeAccess.toLong(blockAddress + DATA_BLOCK_STATIC_PREFIX + KEY_SIZE_LENGTH);
      return UnsafeAccess.toInt(recAddress);		
    } else {
      return UnsafeAccess.toShort(blockAddress + DATA_BLOCK_STATIC_PREFIX);
		}
	}
  
  private final int blockKeyLength(long blockAddress) {
    if (isExternalBlock(blockAddress)) {
      return ADDRESS_SIZE;
    } else {
      return UnsafeAccess.toShort(blockAddress + DATA_BLOCK_STATIC_PREFIX);
    }
  }
	
	private final long version(long blockAddress) {
		return UnsafeAccess.toLong(blockAddress + DATA_BLOCK_STATIC_PREFIX + KEY_SIZE_LENGTH 
		  + blockKeyLength(blockAddress));
	}

	private final byte type(long blockAddress) {
		return UnsafeAccess.toByte(
				blockAddress + DATA_BLOCK_STATIC_PREFIX + KEY_SIZE_LENGTH + blockKeyLength(blockAddress) 
				+ VERSION_SIZE);
	}

	private final long keyAddress(long blockAddress) {
	  if (isExternalBlock(blockAddress)) {
	    long recAddress = UnsafeAccess.toLong(blockAddress + DATA_BLOCK_STATIC_PREFIX + KEY_SIZE_LENGTH);
	    return recAddress + INT_SIZE;
	  } else {
	    return blockAddress + DATA_BLOCK_STATIC_PREFIX + KEY_SIZE_LENGTH;
	  }
	}

	private final long getExternalAllocationAddress(long blockAddress) {
	  return UnsafeAccess.toLong(blockAddress + DATA_BLOCK_STATIC_PREFIX + KEY_SIZE_LENGTH);
	}
	/**
	 * Set thread safe
	 * 
	 * @param b thread safe (true/false)
	 */
	public void setThreadSafe(boolean b) {
		this.threadSafe = b;
	}

	/**
	 * Is thread safe
	 * 
	 * @return
	 */
	public boolean isThreadSafe() {
		return threadSafe;
	}

	public boolean isCompressed() {
		return compressed;
	}

	public boolean isValid() {
		return valid;
	}

	/**
	 * Reset block for reuse
	 */
	public void reset() {
		this.seqNumberSplitOrMerge = 0;
		this.numDataBlocks = 0;
		this.blockDataSize = 0;
		this.compressed = false;
		this.firstKey = null;
		this.version = 0;
		this.type = 0;
		this.isFirst = false;
		this.threadSafe = false;
		this.valid = true;
	}

	public void invalidate() {
	  valid = false;
	}
	
	
	/**
	 * Read lock
	 * 
	 * @throws RetryOperationException
	 * @throws InterruptedException
	 */
	public void readLock() throws RetryOperationException {
	  readLock(true);
	}

	 public void readLock(boolean withException) throws RetryOperationException {
	    if (isThreadSafe())
	      return;
	    long before = this.seqNumberSplitOrMerge;
	    int index = (hashCode() % locks.length);
	    ReentrantReadWriteLock lock = locks[index];
	    lock.readLock().lock();
	    if (!isValid()) {
	      //The block can become invalid only before after deletion
	      // or after merge (which is not implemented)
	      throw new RetryOperationException();
	    }
	    long after = this.seqNumberSplitOrMerge;
	    if (before != after && withException) {
	      throw new RetryOperationException();
	    }
	  }
	/**
	 * Read unlock
	 */
	public void readUnlock() {
		if (isThreadSafe())
			return;
		int index = (hashCode() % locks.length);
		ReentrantReadWriteLock lock = locks[index];
		lock.readLock().unlock();
	}

	
	/**
	 * Write lock
	 * 
	 * @throws RetryOperationException
	 * @throws InterruptedException
	 */
	public void writeLock() throws RetryOperationException {

		if (isThreadSafe())
			return;
		long before = this.seqNumberSplitOrMerge;
		int index = (hashCode() % locks.length);
		ReentrantReadWriteLock lock = locks[index];
		lock.writeLock().lock();
    if (!isValid()) {
      throw new RetryOperationException();
    }
		long after = this.seqNumberSplitOrMerge;
		if (before != after) {
			throw new RetryOperationException();
		}
	}

	/**
	 * Write unlock
	 */
	public void writeUnlock() {
		if (isThreadSafe())
			return;
		int index = (hashCode() % locks.length);
		ReentrantReadWriteLock lock = locks[index];
		lock.writeLock().unlock();
	}

	/**
	 * Put operation
	 * 
	 * @param key
	 * @param keyOffset
	 * @param keyLength
	 * @param value
	 * @param valueOffset
	 * @param valueLength
	 * @param version
	 * @return true, if success, false otherwise (no room, split block)
	 */
	public boolean put(byte[] key, int keyOffset, int keyLength, byte[] value, int valueOffset, int valueLength,
			long version, long expire) throws RetryOperationException {
		try {
			// TODO: key-value size check
			// TODO: optimize locking: we do double locking: index block and data block
			writeLock();

			if (isEmpty()) {
				// Should be OK if k-v size is below block size
				return putEmpty(key, keyOffset, keyLength, value, valueOffset, valueLength, version, expire);
			}
			long ptr = search(key, keyOffset, keyLength, version, Op.PUT);
			// Try to insert, split if necessary (if split is possible)
			// return false if splitting of index block is required
			DataBlock b = block.get();
			b.set(this, ptr - dataPtr);
			boolean res = false;
			while (true) {
				res = b.put(key, keyOffset, keyLength, value, valueOffset, valueLength, version, expire);
				if (res == false) {
					if (b.canSplit() && !b.isLargerThanMax(key, keyOffset, keyLength, version)) {
						// get split position
						long addr = b.splitPos(false);
						// check if we will be able to insert new block
						int startKeyLength = DataBlock.keyLength(addr);
						if (mustAllocateExternally(startKeyLength)) {
						  startKeyLength = ADDRESS_SIZE;
						}
						
						int required = DATA_BLOCK_STATIC_OVERHEAD + KEY_SIZE_LENGTH + startKeyLength;
						if (blockDataSize + required > blockSize) {
							// index block split is required
						  return false;
						}
						// Do not enforce compaction, it was done already during put
						DataBlock right = b.split(false);
						// we do not check result - it must be true
						insertBlock(right);
						// select which block we should put k-v now
						int r1 = right.compareTo(key, keyOffset, keyLength, version, Op.PUT);
						if (r1 >= 0) {
							b = right;
						}
						continue;
					} else {
						// TODO: what is block can not be split? Is it possible? Seems, NO
					  int required = DATA_BLOCK_STATIC_OVERHEAD + KEY_SIZE_LENGTH +
					      (mustAllocateExternally(keyLength)? ADDRESS_SIZE: keyLength);
						if (blockDataSize + required > blockSize) {
							// index block split is required
							return false;
						}
						// new block insert after
						DataBlock bb = new DataBlock(MAX_BLOCK_SIZE);
						insertNewBlock(bb, key, keyOffset, keyLength, version, Op.PUT);
						// we do not check result - it should be OK (empty block)
						bb.put(key, keyOffset, keyLength, value, valueOffset, valueLength, version, expire);
						return true; // if false, then index block split is required
					}
				} else {
					break;
				}
			}
			// if false, then index block split is required
			return res;
		} finally {
			writeUnlock();
		}
	}

	private boolean insertBlock(DataBlock bb) {
		// required space for new entry
		int len = bb.getFirstKeyLength();
		boolean extAlloc = mustAllocateExternally(len);
		int required = (extAlloc? ADDRESS_SIZE:len) + KEY_SIZE_LENGTH + DATA_BLOCK_STATIC_OVERHEAD;
		if (blockDataSize + required > blockSize) {
			return false;
		}
		long addr = bb.getFirstKeyAddress();
		long version = bb.getFirstKeyVersion();
		Op type = bb.getFirstKeyType();
		long pos = search(addr, len, version, type);
		// Get to the next index record
		int skip = DATA_BLOCK_STATIC_OVERHEAD + KEY_SIZE_LENGTH + blockKeyLength(pos);
		pos += skip;

		UnsafeAccess.copy(pos, pos + required, blockDataSize - (pos - dataPtr));
		bb.register(this, pos - dataPtr);
		this.blockDataSize += required;
		BigSortedMap.totalDataInIndexBlocksSize.addAndGet(required);
    BigSortedMap.totalIndexSize.addAndGet(required);
		this.numDataBlocks += 1;
		setFirstKey(bb);
		return true;
	}

	private boolean insertNewBlock(DataBlock bb, byte[] key, int off, int len, long version, Op type) {
		// required space for new entry
	  boolean extAlloc = mustAllocateExternally(len);
	  
		int required = (extAlloc? ADDRESS_SIZE:len) + KEY_SIZE_LENGTH + DATA_BLOCK_STATIC_OVERHEAD;
		if (blockDataSize + required > blockSize) {
			return false;
		}
		long pos = search(key, off, len, version, type);
		boolean insert = pos < dataPtr + blockDataSize;
		if (insert) {
		  // Get to the next index record
		  int skip = DATA_BLOCK_STATIC_OVERHEAD + KEY_SIZE_LENGTH + blockKeyLength(pos);
		  pos += skip;
		  UnsafeAccess.copy(pos, pos + required, blockDataSize - (pos - dataPtr));
		} else {
		  // append to the end of active section of index block
		  // do nothing
		}
		bb.register(this, pos - dataPtr);
		this.blockDataSize += required;
    BigSortedMap.totalDataInIndexBlocksSize.addAndGet(required);
    BigSortedMap.totalIndexSize.addAndGet(required);

		this.numDataBlocks += 1;
		// set first key
		setFirstKey(pos, key, off, len, version, type);
		return true;
	}

	private boolean insertNewBlock(DataBlock bb, long key, int len, long version, Op type) {
    // required space for new entry
    boolean extAlloc = mustAllocateExternally(len);
    
    int required = (extAlloc? ADDRESS_SIZE:len) + KEY_SIZE_LENGTH + DATA_BLOCK_STATIC_OVERHEAD;
    if (blockDataSize + required > blockSize) {
      return false;
    }
    long pos = search(key, len, version, type);
    boolean insert = pos < dataPtr + blockDataSize;
    if (insert) {
      // Get to the next index record
      int skip = DATA_BLOCK_STATIC_OVERHEAD + KEY_SIZE_LENGTH + blockKeyLength(pos);
      pos += skip;
      UnsafeAccess.copy(pos, pos + required, blockDataSize - (pos - dataPtr));
    } else {
      // append to the end of active section of index block
      // do nothing
    }
    bb.register(this, pos - dataPtr);
    this.blockDataSize += required;
    BigSortedMap.totalDataInIndexBlocksSize.addAndGet(required);
    BigSortedMap.totalIndexSize.addAndGet(required);

    this.numDataBlocks += 1;
    // set first key
    setFirstKey(pos, key, len, version, type);
    return true;
	}

	public boolean canSplit() {
		return numDataBlocks > 1;
	}

	private boolean putEmpty(byte[] key, int keyOffset, int keyLength, byte[] value, int valueOffset, int valueLength,
			long version, long expire) {
		DataBlock b = new DataBlock(MAX_BLOCK_SIZE);
		b.register(this, 0);
		if(mustAllocateExternally(keyLength)) {
      UnsafeAccess.putShort(dataPtr + DATA_BLOCK_STATIC_PREFIX, (short) 0);
      long addr = allocateKeyExternally(key, keyOffset, keyLength);
      if (addr == UnsafeAccess.MALLOC_FAILED) {
        return false;
      }
      UnsafeAccess.putLong(dataPtr + DATA_BLOCK_STATIC_PREFIX + KEY_SIZE_LENGTH, addr);
      keyLength = ADDRESS_SIZE;
      UnsafeAccess.putLong(dataPtr + DATA_BLOCK_STATIC_PREFIX + KEY_SIZE_LENGTH + keyLength, version);
      UnsafeAccess.putByte(dataPtr + DATA_BLOCK_STATIC_PREFIX + KEY_SIZE_LENGTH + keyLength + VERSION_SIZE,
        (byte) Op.PUT.ordinal());
		} else {
		  UnsafeAccess.putShort(dataPtr + DATA_BLOCK_STATIC_PREFIX, (short) keyLength);
		  UnsafeAccess.copy(key, keyOffset, dataPtr + DATA_BLOCK_STATIC_PREFIX + KEY_SIZE_LENGTH, keyLength);
		  UnsafeAccess.putLong(dataPtr + DATA_BLOCK_STATIC_PREFIX + KEY_SIZE_LENGTH + keyLength, version);
		  UnsafeAccess.putByte(dataPtr + DATA_BLOCK_STATIC_PREFIX + KEY_SIZE_LENGTH + keyLength + VERSION_SIZE,
				(byte) Op.PUT.ordinal());
		}
		this.version = version;
		this.type = (byte) Op.PUT.ordinal();
		this.numDataBlocks++;
		int required =  DATA_BLOCK_STATIC_OVERHEAD + KEY_SIZE_LENGTH + keyLength;
		this.blockDataSize += required;
    BigSortedMap.totalDataInIndexBlocksSize.addAndGet(required);
    BigSortedMap.totalIndexSize.addAndGet(required);

		return b.put(key, keyOffset, keyLength, value, valueOffset, valueLength, version, expire);
	}

	public boolean isEmpty() {
		return this.numDataBlocks == 0;
	}

  
	boolean tryUpdateFirstKey(DataBlock block) {

    long indexPtr = block.getIndexPtr();
    short klen = DataBlock.blockKeyLength(block.dataPtr);
    short vlen = DataBlock.blockValueLength(block.dataPtr);
    long address = block.dataPtr;
    address += klen + vlen + DataBlock.RECORD_TOTAL_OVERHEAD;

    int keyLength = DataBlock.keyLength(address);
    int available = this.blockSize - this.blockDataSize;
    int curKeyLength = blockKeyLength(indexPtr);
    int recLength = DATA_BLOCK_STATIC_OVERHEAD + KEY_SIZE_LENGTH + curKeyLength;
    int required;
    if (mustAllocateExternally(keyLength)) {
      required = DATA_BLOCK_STATIC_OVERHEAD + KEY_SIZE_LENGTH + ADDRESS_SIZE 
           - recLength;

      if (required > available) {
        // Required index block split
        return false;
      }
    } else {
      required = DATA_BLOCK_STATIC_OVERHEAD + KEY_SIZE_LENGTH + keyLength - recLength;

      if (required > available) {
        // Required index block split
        return false;
      }
    }
    return true;
  }
	
	void expand(int required) {
	  short increase = (short) Math.max(required, EXPANSION_SIZE);
	  this.blockSize = (short)(this.blockSize + increase);
	  this.dataPtr = UnsafeAccess.realloc(this.dataPtr, this.blockSize);
	  incrSeqNumberSplitOrMerge();
	}
	/**
	 * Updates first key at offset in index block
	 * @param offset offset
	 */
	void updateFirstKeyAtOffset (long offset) 
	{
	  DataBlock b = block.get();
	  b.set(this, offset);
	  updateFirstKey(b, true);
	}
	
	/**
   * This method can be called during delete operation, if first
   * key in a block were being deleted.
   * @param block data block	 
   * @return true on success
	 */
  boolean updateFirstKey(DataBlock block) {
    return updateFirstKey(block, false);
  }

	/**
   * This method can be called during delete operation, if first
   * key in a block were being deleted.
   * @param block data block
   * @param doNotSplit - do not split block - increase size
   */
  boolean updateFirstKey(DataBlock block, boolean doNotSplit) {
    updateUnsafeModificationTime();
    if (block.isEmpty()) {
      deleteBlock(block);
      return true;
    }
    long indexPtr = block.getIndexPtr();
    long key = block.getFirstKeyAddress();
    int keyLength = block.getFirstKeyLength();
    long version = block.getFirstKeyVersion();
    Op type = block.getFirstKeyType();    
    int available = this.blockSize - this.blockDataSize;
    int curKeyLength = blockKeyLength(indexPtr);
    int recLength = DATA_BLOCK_STATIC_OVERHEAD + KEY_SIZE_LENGTH +
         + curKeyLength;
    int required;
    if (mustAllocateExternally(keyLength)) {
      required = DATA_BLOCK_STATIC_OVERHEAD + KEY_SIZE_LENGTH +
          ADDRESS_SIZE - recLength;      
    } else {
      required = DATA_BLOCK_STATIC_OVERHEAD + KEY_SIZE_LENGTH +
           + keyLength - recLength;
    }
    if (required > available && !doNotSplit) {
      // Required index block split
      return false;
    } else if (required > available) {
      long off = indexPtr - this.dataPtr;
      expand (required - available);
      block.set(this, off);
      return updateFirstKey(block, true);
    }
    
    if (isExternalBlock(indexPtr)) {
      long address = getExternalAllocationAddress(indexPtr);
      // TODO: separate method for deallocation
      long size = UnsafeAccess.toInt(address) + INT_SIZE;
      UnsafeAccess.free(address);
      BigSortedMap.totalAllocatedMemory.addAndGet(-size);
      BigSortedMap.totalIndexSize.addAndGet(-size);
      largeKVs.decrementAndGet();
    }
    int toMove = required; 
    UnsafeAccess.copy(indexPtr+ recLength, indexPtr + recLength +toMove, 
      blockDataSize - indexPtr + dataPtr - recLength); 
      
      
    if (mustAllocateExternally(keyLength)) {  
      long addr = allocateKeyExternally(key, keyLength);
      if (addr == UnsafeAccess.MALLOC_FAILED) {
        return false;
      }
      UnsafeAccess.putShort(indexPtr + DATA_BLOCK_STATIC_PREFIX, (short) 0);
      UnsafeAccess.putLong(indexPtr + DATA_BLOCK_STATIC_PREFIX + KEY_SIZE_LENGTH, addr);
      keyLength = ADDRESS_SIZE;
    } else {
      UnsafeAccess.putShort(indexPtr + DATA_BLOCK_STATIC_PREFIX, (short) keyLength);
      UnsafeAccess.copy(key, indexPtr + DATA_BLOCK_STATIC_PREFIX + KEY_SIZE_LENGTH, keyLength);
    }
    UnsafeAccess.putLong(indexPtr + DATA_BLOCK_STATIC_PREFIX + KEY_SIZE_LENGTH + keyLength, version);
    UnsafeAccess.putByte(indexPtr + DATA_BLOCK_STATIC_PREFIX + KEY_SIZE_LENGTH + keyLength + VERSION_SIZE,
        (byte) type.ordinal());
    this.firstKey = null;
    this.blockDataSize += toMove;
    BigSortedMap.totalDataInIndexBlocksSize.addAndGet(toMove);
    BigSortedMap.totalIndexSize.addAndGet(toMove);

    return true;
  }
	
  protected void dumpIndexBlock() {
    int count =0;
    long ptr = dataPtr;
    System.out.println("Dump index block: numDataBlocks="+ numDataBlocks);
    while(count++ < numDataBlocks) {
      int klen = keyLength(ptr);
      long keyAddress = keyAddress(ptr);
      byte[] buf = new byte[klen];
      UnsafeAccess.copy(keyAddress, buf, 0, klen);
      String key = Bytes.toString(buf);
      key = key.substring(0, Math.min(16, key.length()));
      System.out.println(count +" : key="+ key);
      ptr += DATA_BLOCK_STATIC_OVERHEAD + KEY_SIZE_LENGTH + blockKeyLength(ptr);
      
    }
    if (ptr - dataPtr != blockDataSize) {
      System.out.println("FATAL: (ptr - dataPtr -dataSize)="+ (ptr - dataPtr - blockDataSize));
    }
  }
  
  void dumpIndexBlockExt() {
    int count =0;
    long ptr = dataPtr;
    System.out.println("Dump index block: numDataBlocks="+ numDataBlocks);
    while(count++ < numDataBlocks) {
      DataBlock b = block.get();
      b.set(this,  ptr - dataPtr);
      System.out.println(count +" dataSize=" + b.getDataInBlockSize() + " num="+ b.getNumberOfRecords());;
      ptr += DATA_BLOCK_STATIC_OVERHEAD + KEY_SIZE_LENGTH + blockKeyLength(ptr);
      
    }
    if (ptr - dataPtr != blockDataSize) {
      System.out.println("FATAL: (ptr - dataPtr -dataSize)="+ (ptr - dataPtr - blockDataSize));
    }
  }
  
	/**
	 * Must be called only after Block2.register
	 * 
	 * @param block
	 */
	boolean setFirstKey(DataBlock block) {
		// TODO: handle key size change
		// If we deleted first key in a block
		long indexPtr = block.getIndexPtr();
		long key = block.getFirstKeyAddress();
		int keyLength = block.getFirstKeyLength();
		long version = block.getFirstKeyVersion();
		Op type = block.getFirstKeyType();
		if (mustAllocateExternally(keyLength)) {
      long addr = allocateKeyExternally(key, keyLength);
      if (addr == UnsafeAccess.MALLOC_FAILED) {
        return false;
      }
      UnsafeAccess.putShort(indexPtr + DATA_BLOCK_STATIC_PREFIX, (short) 0);
      UnsafeAccess.putLong(indexPtr + DATA_BLOCK_STATIC_PREFIX + KEY_SIZE_LENGTH, addr);
      keyLength = ADDRESS_SIZE;
		} else {
		  UnsafeAccess.putShort(indexPtr + DATA_BLOCK_STATIC_PREFIX, (short) keyLength);
		  UnsafeAccess.copy(key, indexPtr + DATA_BLOCK_STATIC_PREFIX + KEY_SIZE_LENGTH, keyLength);
		}
		UnsafeAccess.putLong(indexPtr + DATA_BLOCK_STATIC_PREFIX + KEY_SIZE_LENGTH + keyLength, version);
		UnsafeAccess.putByte(indexPtr + DATA_BLOCK_STATIC_PREFIX + KEY_SIZE_LENGTH + keyLength + VERSION_SIZE,
				(byte) type.ordinal());
		return true;
	}

	
	
	boolean setFirstKey(long indexPtr, byte[] key, int keyOffset, int keyLength, long version, Op type) {
		// TODO: handle key size change
		// If we deleted first key in a block
	  if (mustAllocateExternally(keyLength)) {
	    long addr = allocateKeyExternally(key, keyOffset, keyLength);
	    if (addr == UnsafeAccess.MALLOC_FAILED) {
	      return false;
	    }
	    UnsafeAccess.putShort(indexPtr + DATA_BLOCK_STATIC_PREFIX, (short) 0);
      UnsafeAccess.putLong(indexPtr + DATA_BLOCK_STATIC_PREFIX + KEY_SIZE_LENGTH, addr);
	    keyLength = ADDRESS_SIZE;
	  } else {
	    UnsafeAccess.putShort(indexPtr + DATA_BLOCK_STATIC_PREFIX, (short) keyLength);
	    UnsafeAccess.copy(key, keyOffset, indexPtr + DATA_BLOCK_STATIC_PREFIX + KEY_SIZE_LENGTH, keyLength);
	  }
		UnsafeAccess.putLong(indexPtr + DATA_BLOCK_STATIC_PREFIX + KEY_SIZE_LENGTH + keyLength, version);
		UnsafeAccess.putByte(indexPtr + DATA_BLOCK_STATIC_PREFIX + KEY_SIZE_LENGTH + keyLength + VERSION_SIZE,
				(byte) type.ordinal());
		return true;
	}

	boolean setFirstKey(long indexPtr, long keyPtr, int keyLength, long version, Op type) {
    // TODO: handle key size change
    // If we deleted first key in a block
    if (mustAllocateExternally(keyLength)) {
      long addr = allocateKeyExternally(keyPtr,  keyLength);
      if (addr == UnsafeAccess.MALLOC_FAILED) {
        return false;
      }
      UnsafeAccess.putShort(indexPtr + DATA_BLOCK_STATIC_PREFIX, (short) 0);
      UnsafeAccess.putLong(indexPtr + DATA_BLOCK_STATIC_PREFIX + KEY_SIZE_LENGTH, addr);
      keyLength = ADDRESS_SIZE;
    } else {
      UnsafeAccess.putShort(indexPtr + DATA_BLOCK_STATIC_PREFIX, (short) keyLength);
      UnsafeAccess.copy(keyPtr, indexPtr + DATA_BLOCK_STATIC_PREFIX + KEY_SIZE_LENGTH, keyLength);
    }
    UnsafeAccess.putLong(indexPtr + DATA_BLOCK_STATIC_PREFIX + KEY_SIZE_LENGTH + keyLength, version);
    UnsafeAccess.putByte(indexPtr + DATA_BLOCK_STATIC_PREFIX + KEY_SIZE_LENGTH + keyLength + VERSION_SIZE,
        (byte) type.ordinal());
    return true;
	}

	/**
	 * Checks if a given key is larger than maximum key in this data block
	 * 
	 * @param key
	 * @param keyOffset
	 * @param keyLength
	 * @param version
	 * @return
	 */
	public boolean isLargerThanMax(byte[] key, int keyOffset, int keyLength, long version) {
		// TODO: Locking is required, make sure we hold read lock
		long address = search(key, keyOffset, keyLength, version, Op.PUT);
		int keylen = blockKeyLength(address);
		return address + keylen + KEY_SIZE_LENGTH + DATA_BLOCK_STATIC_OVERHEAD == dataPtr + getDataInBlockSize();
	}

	/**
	 * Checks if a given key is larger than maximum key in this data block
	 * 
	 * @param key
	 * @param keyOffset
	 * @param keyLength
	 * @param version
	 * @return
	 */
	public boolean isLargerThanMax(long keyPtr, int keyLength, long version) {
		// TODO: Locking is required, make sure we hold read lock
		long address = search(keyPtr, keyLength, version, Op.PUT);	
    int keylen = blockKeyLength(address);
    return address + keylen + KEY_SIZE_LENGTH + DATA_BLOCK_STATIC_OVERHEAD == dataPtr + getDataInBlockSize();
  }

	/**
	 * Type is PUT for this call
	 * @param keyPtr key address
	 * @param keyLength key length
	 * @param version version
	 * @return true/false 
	 */
	boolean isLessThanMin(long keyPtr, int keyLength, long version) 
	  throws RetryOperationException
	  
	{
	  byte[] firstKey = getFirstKey();
	  int result = Utils.compareTo(firstKey, 0, firstKey.length, keyPtr, keyLength);
	  if (result != 0) {
	    return result > 0;
	  }
	  // result == 0;
	  long ver = version(dataPtr);
	  if (ver != version) {
	    return version > ver;
	  }
	  // we do not compare types as since answer will always be false
	  return false;
	}
	
	 /**
   * Type is PUT for this call
   * @param keyPtr
   * @param keyLength
   * @param version
   * @return true/false
   */
  boolean isLessOrEqualsThanMin(long keyPtr, int keyLength, long version) 
    throws RetryOperationException
    
  {
    byte[] firstKey = getFirstKey();
    int result = Utils.compareTo(firstKey, 0, firstKey.length, keyPtr, keyLength);
    if (result != 0) {
      return result > 0;
    }  
    //TODO - version?
    return true;
  }
  
	public boolean put(long keyPtr, int keyLength, long valuePtr, int valueLength, long version, 
      long expire) throws RetryOperationException {
	  return put(keyPtr, keyLength, valuePtr, valueLength, version, expire, false);
	}
	
	/**
	 * Deletes range of Key-Value pairs
	 * @param startKeyPtr start key address (inclusive)
	 * @param startKeySize start key 
	 * @param endKeyPtr
	 * @param endkeySize
	 * @param version
	 * @return number of deleted records
	 */
	public long deleteRange(long startKeyPtr, int startKeySize, long endKeyPtr, 
	    int endKeySize, long version) {
	  
	  long deleted = 0;
	  try {
	    writeLock();
	    if (isLessOrEqualsThanMin(endKeyPtr, endKeySize, version)) {
	      return 0;
	    }
	    long ptr = this.dataPtr;
	    if(!isLessOrEqualsThanMin(startKeyPtr, startKeySize, version)) {
	       ptr = search(startKeyPtr, startKeySize, version, Op.PUT);
	    }
      // Try to insert, split if necessary (if split is possible)
      // return false if splitting of index block is required
      DataBlock b = block.get();
      b.set(this, ptr - dataPtr);
      // List of blocks to be deleted
      List<Key> toDelete = null; 
      // List of blocks which requires update first key
      List<Long> toUpdate = null;
      do {  
        long del = b.deleteRange(startKeyPtr, startKeySize, endKeyPtr, endKeySize, version);
        if (del == 0) {
          break;
        }
        deleted += del;
        if (b.isEmpty()) {
          long indexPtr = b.getIndexPtr();
          long keyPtr = keyAddress(indexPtr);
          int keySize = keyLength(indexPtr);
          long kPtr = UnsafeAccess.allocAndCopy(keyPtr, keySize);
          if (toDelete == null) {
            toDelete = new ArrayList<Key>();
          }
          toDelete.add(new Key(kPtr, keySize));
          
        } else {
          if (updateRequired(b)) {
            if (toUpdate == null) {
              toUpdate = new ArrayList<Long>();
            }
            // Add offset of the block
            toUpdate.add(b.getIndexPtr() - this.dataPtr);
          }
        }
      } while ((b = nextBlock(b)) != null);
      
      processUpdates(toUpdate);
      processDeletes(toDelete);
      
	  } finally {
	    writeUnlock();
	  }
	  
	  return deleted;
	}
	
	private void processDeletes(List<Key> toDelete) {
	  for(int i = 0; i < toDelete.size(); i++) {
	    deleteBlockStartWith(toDelete.get(i));
	  }
  }

  private void processUpdates(List<Long> toUpdate) {
    // During bulk first key updates we can not split index block
    // index block size will be increased
    // We iterate from the end to preserve yet not processed offsets
    for(int i = toUpdate.size() -1; i >= 0; i--) {
      updateFirstKeyAtOffset(toUpdate.get(i));
    }
  }

  private boolean updateRequired(DataBlock b) {
	  long ptr = b.getIndexPtr();
	  long keyPtr = keyAddress(ptr);
	  int keySize = keyLength(ptr);
	  long blockKeyPtr = DataBlock.keyAddress(b.getDataPtr());
	  int blockKeySize = DataBlock.keyLength(b.getDataPtr());
	  return Utils.compareTo(keyPtr,  keySize,  blockKeyPtr, blockKeySize) != 0;
	}
	/**
	 * Put key-value operation
	 * 
	 * @param keyPtr key address
	 * @param keyLength key length
	 * @param valuePtr value address
	 * @param valueLength value length
	 * @param version version of operation
	 * @param expire expiration time
	 * @return true, if success, false otherwise
	 * @throws RetryOperationException
	 */
	public boolean put(long keyPtr, int keyLength, long valuePtr, int valueLength, long version, 
	    long expire, boolean reuseValue)
			throws RetryOperationException {
    try {
      // TODO: key-value size check
      // TODO: optimize locking: we do double locking: index block and data block
      writeLock();

      if (isEmpty()) {
        // Should be OK if k-v size is below block size
        return putEmpty(keyPtr, keyLength, valuePtr, valueLength, version, expire);
      }
      long ptr = search(keyPtr, keyLength, version, Op.PUT);
      // Try to insert, split if necessary (if split is possible)
      // return false if splitting of index block is required
      DataBlock b = block.get();
      b.set(this, ptr - dataPtr);
      boolean res = false;
      while (true) {
        res = b.put(keyPtr,  keyLength, valuePtr, valueLength, version, expire, reuseValue);
        if (res == false) {
          if (b.canSplit() && !b.isLargerThanMax(keyPtr,  keyLength, version)) {
            // get split position
            long addr = b.splitPos(false);
            // check if we will be able to insert new block
            int startKeyLength = DataBlock.keyLength(addr);
            if (mustAllocateExternally(startKeyLength)) {
              startKeyLength = ADDRESS_SIZE;
            }
            
            int required = DATA_BLOCK_STATIC_OVERHEAD + KEY_SIZE_LENGTH + startKeyLength;
            if (blockDataSize + required > blockSize) {
              // index block split is required
              return false;
            }
            // Do not enforce compaction, it was done already during put
            DataBlock right = b.split(false);
            // we do not check result - it must be true
            insertBlock(right);
            // select which block we should put k-v now
            int r1 = right.compareTo(keyPtr, keyLength, version, Op.PUT);
            if (r1 >= 0) {
              b = right;
            }
            continue;
          } else {

            // TODO: what is block can not be split? Is it possible? Seems, NO
            int required = DATA_BLOCK_STATIC_OVERHEAD + KEY_SIZE_LENGTH +
                (mustAllocateExternally(keyLength)? ADDRESS_SIZE: keyLength);
            if (blockDataSize + required > blockSize) {
              // index block split is required
              return false;
            }
            // new block insert after
            DataBlock bb = new DataBlock(MAX_BLOCK_SIZE);
            insertNewBlock(bb, keyPtr, keyLength, version, Op.PUT);
            // we do not check result - it should be OK (empty block)
            bb.put(keyPtr, keyLength, valuePtr, valueLength, version, expire, reuseValue);
            return true; // if false, then index block split is required
          }
        } else {
          break;
        }
      }
      // if false, then index block split is required
      return res;
    } finally {
      writeUnlock();
    }
	}

	private boolean putEmpty(long keyPtr, int keyLength, long valuePtr, int valueLength, long version, long expire) {
    DataBlock b = new DataBlock(MAX_BLOCK_SIZE);
    b.register(this, 0);
    if(mustAllocateExternally(keyLength)) {
      UnsafeAccess.putShort(dataPtr + DATA_BLOCK_STATIC_PREFIX, (short) 0);
      long addr = allocateKeyExternally(keyPtr,  keyLength);
      if (addr == UnsafeAccess.MALLOC_FAILED) {
        return false;
      }
      UnsafeAccess.putLong(dataPtr + DATA_BLOCK_STATIC_PREFIX + KEY_SIZE_LENGTH, addr);
      keyLength = ADDRESS_SIZE;
      UnsafeAccess.putLong(dataPtr + DATA_BLOCK_STATIC_PREFIX + KEY_SIZE_LENGTH + keyLength, version);
      UnsafeAccess.putByte(dataPtr + DATA_BLOCK_STATIC_PREFIX + KEY_SIZE_LENGTH + keyLength + VERSION_SIZE,
        (byte) Op.PUT.ordinal());
    } else {
      UnsafeAccess.putShort(dataPtr + DATA_BLOCK_STATIC_PREFIX, (short) keyLength);
      UnsafeAccess.copy(keyPtr, dataPtr + DATA_BLOCK_STATIC_PREFIX + KEY_SIZE_LENGTH, keyLength);
      UnsafeAccess.putLong(dataPtr + DATA_BLOCK_STATIC_PREFIX + KEY_SIZE_LENGTH + keyLength, version);
      UnsafeAccess.putByte(dataPtr + DATA_BLOCK_STATIC_PREFIX + KEY_SIZE_LENGTH + keyLength + VERSION_SIZE,
        (byte) Op.PUT.ordinal());
    }
    this.version = version;
    this.type = (byte) Op.PUT.ordinal();
    this.numDataBlocks++;
    int required = DATA_BLOCK_STATIC_OVERHEAD + KEY_SIZE_LENGTH + keyLength;
    this.blockDataSize += required;   
    BigSortedMap.totalDataInIndexBlocksSize.addAndGet(required);
    BigSortedMap.totalIndexSize.addAndGet(required);
    return b.put(keyPtr, keyLength, valuePtr, valueLength, version, expire);
	}

	/**
	 * TODO: handle not found (new block) Search position of a block, which can
	 * contain this key
	 * 
	 * @param key
	 * @param keyOffset
	 * @param keyLength
	 * @return address to insert (or update)
	 */
  long search(byte[] key, int keyOffset, int keyLength, long version, Op type) {
    long ptr = dataPtr;
    long prevPtr = NOT_FOUND;
    int count = 0;
    long recentTxId = BigSortedMap.getMostRecentActiveTxSeqId();
    while (count++ < numDataBlocks) {
      int keylen = keyLength(ptr);
      int res = Utils.compareTo(key, keyOffset, keyLength, keyAddress(ptr), keylen);
      if (res < 0) {
        if (prevPtr == NOT_FOUND) {
          // It is possible situation (race condition when first key in
          // index block was deleted after we found this block and before we locked it
          return ptr = NOT_FOUND;
        } else {
          return prevPtr;
        }
      } else if (res == 0) {
        // compare versions
        long ver = version(ptr);
        if (ver < recentTxId) {
          if (ver < version) {
            if (prevPtr == NOT_FOUND && count > 1) {
              // FATAL error???
              return ptr = NOT_FOUND;
            }
            return count > 1 ? prevPtr : ptr;
          } else if (ver == version) {
            byte _type = type(ptr);
            if (_type > type.ordinal() && count > 1) {
              if (prevPtr == NOT_FOUND) {
                // FATAL error???
                return ptr = NOT_FOUND;
              }
              return count > 1 ? prevPtr : ptr;
            }
          }
        } else {
          return ptr;
        }
      }
      prevPtr = ptr;
      if (isExternalBlock(ptr)) {
        keylen = ADDRESS_SIZE;
      }
      ptr += keylen + KEY_SIZE_LENGTH + DATA_BLOCK_STATIC_OVERHEAD;
    }
    // last data block
    return prevPtr;

  }

	/**
	 * TODO: handle not found (new block) Search position of a block, which can
	 * contain this key
	 * TODO: why do we need this method for?
	 * @param key
	 * @param keyOffset
	 * @param keyLength
	 * @return address to insert (or update)
	 */
  long searchForGet(byte[] key, int keyOffset, int keyLength, long start) {
    long ptr = start;
    int keylen = blockKeyLength(ptr);
    ptr += keylen + KEY_SIZE_LENGTH + DATA_BLOCK_STATIC_OVERHEAD;
    if (ptr >= this.dataPtr + this.blockDataSize) {
      return NOT_FOUND;
    }
    int res = Utils.compareTo(key, keyOffset, keyLength, keyAddress(ptr), keyLength(ptr));
    if (res != 0) {
      return ptr = NOT_FOUND;
    } else {
      return ptr;
    }

  }

	/**
	 * Public API, therefore we lock/unlock
	 * Multiple instances UNSAFE
	 * @param key key buffer
	 * @param keyOffset key offset
	 * @param keyLength key length
	 * @param type operation type
	 * @return block found or null
	 */
	DataBlock searchBlock(byte[] key, int keyOffset, int keyLength, long version, Op type) {

		try {
			readLock();
			long ptr = search(key, keyOffset, keyLength, version, type);
			if (ptr == NOT_FOUND) {
				return null;
			} else {
				DataBlock b = block.get();
				b.set(this, ptr - dataPtr);
				return b;
			}
		} finally {
			readUnlock();
		}
	}

	/**
   * Public API, therefore we lock/unlock
   * Multiple instances UNSAFE
   * @param key key buffer
   * @param keyOffset key offset
   * @param keyLength key length
   * @param type operation type
   * @param b data block to reuse
   * @return block found or null
   */
  DataBlock searchBlock(byte[] key, int keyOffset, int keyLength, long version, 
      Op type, DataBlock b) {

    try {
      readLock();
      long ptr = search(key, keyOffset, keyLength, version, type);
      if (ptr == NOT_FOUND) {
        return null;
      } else {
        b.set(this, ptr - dataPtr);
        return b;
      }
    } finally {
      readUnlock();
    }
  }

	/**
	 * Return first block if present
	 * Multiple instance UNSAFE
	 * @return block
	 */
	DataBlock firstBlock() {
		if (isEmpty())
			return null;
		try {
			readLock();
			DataBlock b = block.get();
			b.set(this, 0);
			return b;
		} finally {
			readUnlock();
		}
	}

	 /**
   * Return first block if present
   * Multiple instance SAFE
   * @param b data block to reuse
   * @return block
   */
  DataBlock firstBlock(DataBlock b) {
    
    if (isEmpty())
      return null;
    if (b == null) {
      b = new DataBlock();
    }
    try {
      readLock();
      b.set(this, 0);
      return b;
    } finally {
      readUnlock();
    }
  }
  /**
   * Get first block which contains given key
   * @param b data block for reuse
   * @param keyPtr key address
   * @param keySize key size
   * @return data block
   */
  DataBlock firstBlock(DataBlock b, long keyPtr, int keySize) {
    if (keyPtr == 0) {
      return firstBlock(b);
    }
    if (b == null) {
      b = new DataBlock();
    }
    long ptr = this.dataPtr;
    final long limit = this.dataPtr + this.blockDataSize; 
    while(ptr < limit) {
      int keyLength = blockKeyLength(ptr);
      b.set(this, ptr - this.dataPtr);
      if (!b.isLargerThanMax(keyPtr, keyLength, 0)) {
        return b;
      }
      ptr += + DATA_BLOCK_STATIC_OVERHEAD + KEY_SIZE_LENGTH + keyLength;
    }
    return null;
  }
	/**
	 * Implement first block Public API, therefore we lock/unlock
	 * Multiple instances SAFE
	 * @param blck current blck, can be null
	 * @return block found or null
	 */
	DataBlock nextBlock(DataBlock blck) {
		return nextBlock(blck, true);
	}

	
	
	
  /**
   * Implement first block Public API, therefore we lock/unlock
   * Multiple instances UNSAFE
   * @param blck current blck, can be null
   * @return block found or null
   */
  DataBlock nextBlock(DataBlock blck, boolean safe) {

    try {
      readLock();
      if (blck == null) {
        if (safe) {
          return firstBlock();
        } else {
          return firstBlock(new DataBlock());
        }
      }
      long ptr = blck != null ? blck.getIndexPtr() : getAddress();
      int keyLength = blockKeyLength(ptr);
      if (ptr + DATA_BLOCK_STATIC_OVERHEAD + KEY_SIZE_LENGTH + keyLength >= dataPtr + blockDataSize) {
        // last block
        return null;
      }
      blck.set(this, ptr + DATA_BLOCK_STATIC_OVERHEAD + KEY_SIZE_LENGTH + keyLength - dataPtr);
      return blck;
    } finally {
      readUnlock();
    }
  }
	
	/**
	 * Used for scanning 
	 * Multiple instances SAFE - it used in context one method call
	 * @param ptr
	 * @return next block
	 */
	DataBlock nextBlock(long ptr) {

	   try {
	      readLock();
	      if (ptr < 0) {
	        return firstBlock();
	      }
	      int keyLength = blockKeyLength(ptr);
	      if (ptr + DATA_BLOCK_STATIC_OVERHEAD + KEY_SIZE_LENGTH + keyLength >= dataPtr + blockDataSize) {
	        // last block
	        return null;
	      }
	      DataBlock b = block.get();
	      b.set(this, ptr + DATA_BLOCK_STATIC_OVERHEAD + KEY_SIZE_LENGTH + keyLength - dataPtr);
	      return b;
	    } finally {
	      readUnlock();
	    }
	}
	
	/**
	 * Used for merge data blocks
	 * @param blck current block
	 * @return next block
	 */
  DataBlock nextBlockInIndex(DataBlock blck) {
    // No locking is required
    if (blck == null) {
      return firstBlock();
    }
    long ptr = blck != null ? blck.getIndexPtr() : getAddress();
    int keyLength = blockKeyLength(ptr);
    if (ptr + DATA_BLOCK_STATIC_OVERHEAD + KEY_SIZE_LENGTH + keyLength >= dataPtr + blockDataSize) {
      // last block
      return null;
    }
    DataBlock b = block2.get();
    b.set(this, ptr + DATA_BLOCK_STATIC_OVERHEAD + KEY_SIZE_LENGTH + keyLength - dataPtr);
    return b;
  }
	
  /**
   * Used for merge data blocks
   * @param blck current block
   * @return next block
   */
  DataBlock prevBlockInIndex(DataBlock blck) {
    // No locking is required
    long blockPtr =  blck.getIndexPtr();
    long prevPtr = 0, ptr= this.dataPtr;
    if (blockPtr == this.dataPtr) {
      return null;
    }
    while( ptr < blockPtr) {
      prevPtr = ptr;
      int keylen = blockKeyLength(ptr);
      ptr += keylen + KEY_SIZE_LENGTH + DATA_BLOCK_STATIC_OVERHEAD;
    
    }
    DataBlock b = block2.get();
    b.set(this, prevPtr - dataPtr);
    return b;
  }
  
  private boolean tryMergeAdjacentBlocks(DataBlock b) {
    if (!b.shouldMerge()) {
      return false;
    }
    boolean result = false;
    DataBlock bb = nextBlockInIndex(b);
    result = tryMergeBlocks(b, bb);
    if (result) {
      return result;
    }
    bb = prevBlockInIndex(b);
    result = tryMergeBlocks(bb, b);
    return result;
  }
  
  private boolean tryMergeBlocks(DataBlock left, DataBlock right) {
    if (left == null || right == null) {
      return false;
    }
    int maxBlockSize = DataBlock.getMaximumBlockSize();
    int leftSize = left.getDataInBlockSize();
    int rightSize = right.getDataInBlockSize();
    if (leftSize + rightSize <= maxBlockSize) {
      // Do not compact TODO
      boolean result = left.merge(right, false);
      if (result) {
        deleteBlock(right, false);
      }
      return result;
    }
    return false;
  }
  
	/**
	 * Search position of a first key which is greater or equals to a given key
	 * 
	 * @param keyPtr
	 * @param keyLength
	 * @return address to insert (or update)
	 */
  long search(long keyPtr, int keyLength, long version, Op type) {
    long ptr = dataPtr;
    long prevPtr = NOT_FOUND;
    int count = 0;
    long recentTxId = BigSortedMap.getMostRecentActiveTxSeqId();
    while (count++ < numDataBlocks) {
      int keylen = keyLength(ptr);
      int res = Utils.compareTo(keyPtr, keyLength, keyAddress(ptr), keylen);
      if (res < 0) {
        if (prevPtr == NOT_FOUND) {
          // It is possible situation (race condition when first key in
          // index block was deleted after we found this block and before we locked it
          return ptr = NOT_FOUND;
        } else {
          return prevPtr;
        }
      } else if (res == 0) {
        // compare versions
        long ver = version(ptr);
        if (ver < recentTxId) {
          if (ver < version) {
            if (prevPtr == NOT_FOUND && count > 1) {
              // FATAL error???
              return ptr = NOT_FOUND;
            }
            return count > 1 ? prevPtr : ptr;
          } else if (ver == version) {
            byte _type = type(ptr);
            if (_type > type.ordinal() && count > 1) {
              if (prevPtr == NOT_FOUND) {
                // FATAL error???
                return ptr = NOT_FOUND;
              }
              return count > 1 ? prevPtr : ptr;
            }
          }
        } else {
          return ptr;
        }
      }
      prevPtr = ptr;
      if (isExternalBlock(ptr)) {
        keylen = ADDRESS_SIZE;
      }
      ptr += keylen + KEY_SIZE_LENGTH + DATA_BLOCK_STATIC_OVERHEAD;
    }
    // last data block
    return prevPtr;

  }

  /**
   * Search largest block which is less than a given key
   * @param keyPtr key address
   * @param keyLength key length
   * @param version version
   * @param type type
   * @return position of a data block
   */
  long searchLower(long keyPtr, int keyLength, long version, Op type) {
    long ptr = dataPtr;
    long prevPtr = NOT_FOUND;
    int count = 0;
    long recentTxId = BigSortedMap.getMostRecentActiveTxSeqId();
    while (count++ < numDataBlocks) {
      int keylen = keyLength(ptr);
      int res = Utils.compareTo(keyPtr, keyLength, keyAddress(ptr), keylen);
      if (res < 0) {
        if (prevPtr == NOT_FOUND) {
          // It is possible situation (race condition when first key in
          // index block was deleted after we found this block and before we locked it
          return ptr = NOT_FOUND;
        } else {
          return prevPtr;
        }
      } else if (res == 0) {
        // compare versions
        long ver = version(ptr);
        if (ver < recentTxId) {
          if (ver < version) {
            if (prevPtr == NOT_FOUND && count > 1) {
              // FATAL error???
              return ptr = NOT_FOUND;
            }
            return count > 1 ? prevPtr : ptr;
          } else if (ver == version) {
            byte _type = type(ptr);
            if (_type > type.ordinal() && count > 1) {
              if (prevPtr == NOT_FOUND) {
                // FATAL error???
                return ptr = NOT_FOUND;
              }
              return count > 1 ? prevPtr : ptr;
            }
          }
        } else {
          return prevPtr;
        }
      }
      prevPtr = ptr;
      if (isExternalBlock(ptr)) {
        keylen = ADDRESS_SIZE;
      }
      ptr += keylen + KEY_SIZE_LENGTH + DATA_BLOCK_STATIC_OVERHEAD;
    }
    // last data block
    return prevPtr;

  }
  
	/**
	 * TODO: handle not found (new block) Search position of a block, which can
	 * contain this key
	 * 
	 * @param key
	 * @param keyOffset
	 * @param keyLength
	 * @return address to insert (or update)
	 */
  long searchForGet(long keyPtr, int keyLength, long start) {
    long ptr = start;
    int keylen = blockKeyLength(ptr);
    ptr += keylen + KEY_SIZE_LENGTH + DATA_BLOCK_STATIC_OVERHEAD;
    if (ptr >= this.dataPtr + this.blockDataSize) {
      return NOT_FOUND;
    }
    int res = Utils.compareTo(keyPtr, keyLength, keyAddress(ptr), keyLength(ptr));
    if (res != 0) {
      return ptr = NOT_FOUND;
    } else {
      return ptr;
    }
  }

	/**
	 * Search block by a given key equals to a given key
	 * Multiple instance UNSAFE
	 * @param keyPtr
	 * @param keyLength
	 * @return address to insert (or update)
	 */
	DataBlock searchBlock(long keyPtr, int keyLength, long version, Op type) {
		try {
		  readLock();
			long ptr = search(keyPtr, keyLength, version, type);
			if (ptr == NOT_FOUND) {
				return null;
			} else {
				DataBlock b = block.get();
				b.set(this, ptr - dataPtr);
				return b;
			}
		} finally {
		  //TODO: why unlock w/o lock?
			readUnlock();
		}
	}
	
	 /**
   * Search block which is lower than a given key 
   * Multiple instance UNSAFE
   * @param keyPtr
   * @param keyLength
   * @return address to insert (or update)
   */
	DataBlock searchLowerBlock(long keyPtr, int keyLength, long version, Op type) {
	    try {
	      readLock();
	      long ptr = searchLower(keyPtr, keyLength, version, type);
	      if (ptr == NOT_FOUND) {
	        return null;
	      } else {
	        DataBlock b = block.get();
	        b.set(this, ptr - dataPtr);
	        return b;
	      }
	    } finally {
	      //TODO: why unlock w/o lock?
	      readUnlock();
	    }
	  }
	
	 /**
   * Search block by a given key equals to a given key
   * Multiple instance UNSAFE
   * @param keyPtr key address
   * @param keyLength key length
   * @param version  version
   * @param b data block to reuse
   * @return address to insert (or update)
   */
  DataBlock searchBlock(long keyPtr, int keyLength, long version, Op type, DataBlock b) {
    try {
      readLock();
      long ptr = search(keyPtr, keyLength, version, type);
      if (ptr == NOT_FOUND) {
        return null;
      } else {
        b.set(this, ptr - dataPtr);
        return b;
      }
    } finally {
      //TODO: why unlock w/o lock?
      readUnlock();
    }
  }

  /**
  * Search block by a given key equals to a given key
  * Multiple instance UNSAFE
  * @param keyPtr key address
  * @param keyLength key length
  * @param version  version
  * @param b data block to reuse
  * @return address to insert (or update)
  */
 DataBlock searchLowerBlock(long keyPtr, int keyLength, long version, Op type, DataBlock b) {
   try {
     readLock();
     long ptr = searchLower(keyPtr, keyLength, version, type);
     if (ptr == NOT_FOUND) {
       return null;
     } else {
       b.set(this, ptr - dataPtr);
       return b;
     }
   } finally {
     //TODO: why unlock w/o lock?
     readUnlock();
   }
 }
	/**
	 * Delete operation.
	 * @param key
	 * @param keyOffset
	 * @param keyLength
	 * @param version
	 * @return true, if success, false otherwise
	 * @throws RetryOperationException
	 */

	public OpResult delete(byte[] key, int keyOffset, int keyLength, long version) 
	    throws RetryOperationException {

		try {
			writeLock();
			long ptr = search(key, keyOffset, keyLength, version, Op.DELETE);
			DataBlock b = block.get();
			b.set(this, ptr - dataPtr);
			OpResult result =  deleteInBlock(b, ptr, key, keyOffset, keyLength, version);
			if (result == OpResult.OK &&  b.isValid() && !b.isEmpty()) {
			  // try merge adjacent blocks
			  tryMergeAdjacentBlocks(b);
			}
			return result;
		} finally {
			writeUnlock();
		}
	}
	
  private OpResult deleteInBlock(DataBlock b, long address, byte[] key, int keyOffset,
      int keyLength, long version) {
    OpResult res = null;
    while (true) {
      res = b.delete(key, keyOffset, keyLength, version);
      if (res == OpResult.SPLIT_REQUIRED) {
        // This is a case when active  Tx or scanner with fixed seqId
        if (b.canSplit()) {
          // get split position
          long addr = b.splitPos(false);
          // check if we will be able to insert new block
          int startKeyLength = DataBlock.keyLength(addr);
          if (mustAllocateExternally(startKeyLength)) {
            startKeyLength = ADDRESS_SIZE;
          }
          int required = DATA_BLOCK_STATIC_OVERHEAD + KEY_SIZE_LENGTH + startKeyLength;
          if (blockDataSize + required > blockSize) {
            // index block split is required
            return OpResult.SPLIT_REQUIRED;
          }
          DataBlock right = b.split(false);
          // we do not check result - it must be true
          insertBlock(right);
          // select which block we should delete k-v now
          int r1 = right.compareTo(key, keyOffset, keyLength, version, Op.DELETE);
          if (r1 >= 0) {
            b = right;
          }
          continue;
        } else {
          // We can not split block and can delete directly
          // we need to *add* delete tombstone to a new block
          int required = DATA_BLOCK_STATIC_OVERHEAD + KEY_SIZE_LENGTH
              + (mustAllocateExternally(keyLength) ? ADDRESS_SIZE : keyLength);

          if (blockDataSize + required > blockSize) {
            // index block split is required
            return OpResult.SPLIT_REQUIRED;
          }
          // new block insert after
          DataBlock bb = new DataBlock(MAX_BLOCK_SIZE);
          insertNewBlock(bb, key, keyOffset, keyLength, version, Op.DELETE);
          // we do not check result - it should be OK (empty block)
          bb.addDelete(key, keyOffset, keyLength, version);
          return OpResult.OK; // if false, then index block split is required
        }
      } else if (res == OpResult.NOT_FOUND) {
        // This method checks next data block and
        // if it exists and has the same start key as a given key
        // it returns its address
        address = searchForGet(key, keyOffset, keyLength, address);
        if (address == NOT_FOUND) {
          return OpResult.NOT_FOUND;
        } else {
          b.set(this, address - this.dataPtr);
          res = b.delete(key, keyOffset, keyLength, version);
          if (res == OpResult.OK ) {
            return res;
          } else if (res == OpResult.PARENT_SPLIT_REQUIRED) { 
            return OpResult.SPLIT_REQUIRED;
          } else {
            // Call recursively if SPLIT_REQUIRED or NOT_FOUND (will check next block)
            return deleteInBlock(b, address, key, keyOffset, keyLength, version);
          }
        }
      } else if (res == OpResult.PARENT_SPLIT_REQUIRED) {
        return OpResult.SPLIT_REQUIRED;
      } else {
        return res;
      }
    }
  }
	

	/**
	 * Delete operation.
	 * 
	 * @param keyPtr
	 * @param keyLength
	 * @param version
	 * @return true if success, false otherwise
	 * @throws RetryOperationException
	 */
	public OpResult delete(long keyPtr, int keyLength, long version) throws RetryOperationException {
		try {
			writeLock();
			long address = search(keyPtr, keyLength, version, Op.DELETE);
			DataBlock b = block.get();
			b.set(this, address - dataPtr);
			OpResult result = deleteInBlock(b, address, keyPtr, keyLength, version);
	    if (result == OpResult.OK && b.isValid() && !b.isEmpty()) {
	      // try merge adjacent blocks
	      tryMergeAdjacentBlocks(b);
	    }
	    return result;
		} finally {
			writeUnlock();
		}
	}

	
  private OpResult deleteInBlock(DataBlock b, long address, long keyPtr, int keyLength,
      long version) {
    OpResult res = null;
    while (true) {
      res = b.delete(keyPtr, keyLength, version);
      if (res == OpResult.SPLIT_REQUIRED) {
        // This is a case when active  Tx or scanner with fixed seqId
        if (b.canSplit()) {
          // get split position
          long addr = b.splitPos(false);
          // check if we will be able to insert new block
          int startKeyLength = DataBlock.keyLength(addr);
          if (mustAllocateExternally(startKeyLength)) {
            startKeyLength = ADDRESS_SIZE;
          }
          int required = DATA_BLOCK_STATIC_OVERHEAD + KEY_SIZE_LENGTH + startKeyLength;
          if (blockDataSize + required > blockSize) {
            // index block split is required
            return OpResult.SPLIT_REQUIRED;
          }
          DataBlock right = b.split(false);
          // we do not check result - it must be true
          insertBlock(right);
          // select which block we should delete k-v now
          int r1 = right.compareTo(keyPtr, keyLength, version, Op.DELETE);
          if (r1 >= 0) {
            b = right;
          }
          continue;
        } else {
          // We can not split block and can delete directly
          // we need to *add* delete tombstone to a new block
          int required = DATA_BLOCK_STATIC_OVERHEAD + KEY_SIZE_LENGTH
              + (mustAllocateExternally(keyLength) ? ADDRESS_SIZE : keyLength);

          if (blockDataSize + required > blockSize) {
            // index block split is required
            return OpResult.SPLIT_REQUIRED;
          }
          // new block insert after
          DataBlock bb = new DataBlock(MAX_BLOCK_SIZE);
          insertNewBlock(bb, keyPtr, keyLength, version, Op.DELETE);
          // we do not check result - it should be OK (empty block)
          bb.addDelete(keyPtr, keyLength, version);
          return OpResult.OK; // if false, then index block split is required
        }
      } else if (res == OpResult.NOT_FOUND) {
        // This method checks next data block and
        // if it exists and has the same start key as a given key
        // it returns its address
        address = searchForGet(keyPtr, keyLength, address);
        if (address == NOT_FOUND) {
          return OpResult.NOT_FOUND;
        } else {
          b.set(this, address - this.dataPtr);
          res = b.delete(keyPtr, keyLength, version);
          if (res == OpResult.OK ) {
            return res;
          } else if (res == OpResult.PARENT_SPLIT_REQUIRED) { 
            return OpResult.SPLIT_REQUIRED;
          } else {
            // Call recursively if SPLIT_REQUIRED or NOT_FOUND (will check next block)
            return deleteInBlock(b, address, keyPtr, keyLength, version);
          }
        }
      } else if (res == OpResult.PARENT_SPLIT_REQUIRED) {
        return OpResult.SPLIT_REQUIRED;
      } else {
        return res;
      }
    }

  }
  
  /**
   * Delete block, which start with a given key
   * @param key key
   */
  private void deleteBlockStartWith(Key key) {
    long ptr = search(key.address, key.length, Long.MAX_VALUE, Op.PUT);
    DataBlock b = block.get();
    b.set(this, ptr - dataPtr);
    deleteBlock(b, true);
  }
  
	// TODO: delete block when it had one record only
	private void deleteBlock(DataBlock b) {
	  deleteBlock(b, true);
  }
	
  private void deleteBlock(DataBlock b, boolean extAllocs) {
    updateUnsafeModificationTime();

    long indexPtr = b.getIndexPtr();
    int keyLength = keyLength(indexPtr);
    int blockKeyLength = blockKeyLength(indexPtr);
    
    int toMove =  DATA_BLOCK_STATIC_OVERHEAD + KEY_SIZE_LENGTH +
         blockKeyLength; 
    if (mustAllocateExternally(keyLength)) {
      long addr = getExternalAllocationAddress(indexPtr);
      long size = UnsafeAccess.toInt(addr);
      BigSortedMap.totalAllocatedMemory.addAndGet(-size - INT_SIZE);
      //???
      BigSortedMap.totalIndexSize.addAndGet(-size - INT_SIZE);
      UnsafeAccess.free(addr);
      largeKVs.decrementAndGet();
    }
    
    b.free(extAllocs);

    UnsafeAccess.copy(indexPtr + toMove, indexPtr, blockDataSize - indexPtr + dataPtr - toMove);
    this.blockDataSize -= toMove;
    BigSortedMap.totalDataInIndexBlocksSize.addAndGet(-toMove);
    BigSortedMap.totalIndexSize.addAndGet(-toMove);
    
    this.numDataBlocks -=1;
    boolean firstBlockInIndex = this.dataPtr == b.getIndexPtr();
    if (firstBlockInIndex && this.numDataBlocks > 0) {
      firstKey = null;
    }
  }

  /**
	 * Get key-value offset in a block
	 * 
	 * @param key
	 * @param keyOffset
	 * @param keyLength
	 * @return record offset or -1 if not found
	 * @throws RetryOperationException
	 */
	public long get(byte[] key, int keyOffset, int keyLength, long version) throws RetryOperationException {
		try {
			readLock();
			long ptr = search(key, keyOffset, keyLength, version, Op.DELETE);// TODO: is it right?
	    // It is possible that key is less than a first key in this index block
      // we will try again with locking index block in BigSortedMap.get()
      if (ptr <=0) {
        return NOT_FOUND;
      }
			DataBlock b = block.get();
			b.set(this, ptr - dataPtr);
			long res = b.get(key, keyOffset, keyLength, version);
			if (res != DataBlock.NOT_FOUND) {
				return res;
			} else {
				long address = ptr;
				while ((address = searchForGet(key, keyOffset, keyLength, address)) != NOT_FOUND) {
					b.set(this, address - this.dataPtr);
					res = b.get(key, keyOffset, keyLength, version);
					if (res != DataBlock.NOT_FOUND) {
						return res;
					}
				}
				return NOT_FOUND;
			}
		} finally {
			readUnlock();
		}
	}

	/**
	 * Get value by key in a block
	 * 
	 * @param key
	 * @param keyOffset
	 * @param keyLength
	 * @return value length or NOT_FOUND if not found
	 * @throws RetryOperationException
	 */
	public long get(byte[] key, int keyOffset, int keyLength, byte[] valueBuf, int valOffset, long version)
			throws RetryOperationException {
		try {
			readLock();
			long ptr = search(key, keyOffset, keyLength, version, Op.DELETE);
			// It is possible that key is less than a first key in this index block
			// we will try again with locking index block in BigSortedMap.get()
			if (ptr <=0) {
			  return NOT_FOUND;
			}
			DataBlock b = block.get();
			b.set(this, ptr - dataPtr);
			long res = b.get(key, keyOffset, keyLength, valueBuf, valOffset, version);
			if (res != DataBlock.NOT_FOUND) {
				return res;
			} else {
				long address = ptr;
				while ((address = searchForGet(key, keyOffset, keyLength, address)) != NOT_FOUND) {
					b.set(this, address - this.dataPtr);
					res = b.get(key, keyOffset, keyLength, valueBuf, valOffset, version);
					if (res != DataBlock.NOT_FOUND) {
						return res;
					}
				}
				return NOT_FOUND;
			}
		} finally {
			readUnlock();
		}

	}

	/**
	 * Get key-value address
	 * 
	 * @param key
	 * @param keyOffset
	 * @param keyLength
	 * @return record offset or NOT_FOUND if not found
	 * @throws RetryOperationException
	 */
	public long get(long keyPtr, int keyLength, long version) throws RetryOperationException {
    try {
      readLock();
      long ptr = search(keyPtr, keyLength, version, Op.DELETE);// TODO: is it right?
      // It is possible that key is less than a first key in this index block
      // we will try again with locking index block in BigSortedMap.get()
      if (ptr <=0) {
        return NOT_FOUND;
      }
      DataBlock b = block.get();
      b.set(this, ptr - dataPtr);
      long res = b.get(keyPtr, keyLength, version);
      if (res != DataBlock.NOT_FOUND) {
        return res;
      } else {
        long address = ptr;
        while ((address = searchForGet(keyPtr, keyLength, address)) != NOT_FOUND) {
          b.set(this, address - this.dataPtr);
          res = b.get(keyPtr, keyLength, version);
          if (res != DataBlock.NOT_FOUND) {
            return res;
          }
        }
        return NOT_FOUND;
      }
    } finally {
      readUnlock();
    }
	}

	 /**
   * Get key-value address
   * 
   * @param key
   * @param keyOffset
   * @param keyLength
   * @param floor if true returns the largest key which is less or equals
   * @return record offset or NOT_FOUND if not found
   * @throws RetryOperationException
   */
  public long get(long keyPtr, int keyLength, long version, boolean floor) throws RetryOperationException {
    if (!floor) {
      return get(keyPtr, keyLength, version);
    }
    try {
      readLock();
      long ptr = search(keyPtr, keyLength, version, Op.DELETE);// TODO: is it right?
      // It is possible that key is less than a first key in this index block
      // we will try again with locking index block in BigSortedMap.get()
      if (ptr <=0) {
        return NOT_FOUND;
      }
      DataBlock b = block.get();
      b.set(this, ptr - dataPtr);
      long res = b.get(keyPtr, keyLength, version, floor);
      //TODO res = -1
      if (res == NOT_FOUND && floor) {
        // get previous
        b = previousBlock(b);
        if (b == null) {
          return NOT_FOUND;
        } else {
          // It is possible that this block is empty?
          return b.last();
        }
      }
      return res;
    } finally {
      readUnlock();
    }
  }
  
  
  /**
   * Get last K-V record address in this index
   * @return address or NOT_FOUND if index is empty
   */
  long lastRecordAddress() {
    DataBlock b = lastBlock();
    if (b == null) {
      return NOT_FOUND;
    }
    return b.last();
  }
  
  /**
   * Get last data block in this index
   * Multiple scanner unsafe
   * @return last data block
   */
  DataBlock lastBlock() {
    DataBlock b = block.get();
    return lastBlock(b);
  }
  
  /**
   * Get last data block in this index
   * Multiple scanner SAFE
   * @return last data block
   */
  DataBlock lastBlock(DataBlock b) {
    if (b == null) {
      b = new DataBlock();
    }
    long ptr = this.dataPtr;
    final long limit = this.dataPtr + this.blockDataSize; 
    while(ptr < limit) {
      int keyLength = blockKeyLength(ptr);
      if (ptr + DATA_BLOCK_STATIC_OVERHEAD + KEY_SIZE_LENGTH + keyLength == limit) {
        b.set(this, ptr - this.dataPtr);
        return b;
      }
      ptr += + DATA_BLOCK_STATIC_OVERHEAD + KEY_SIZE_LENGTH + keyLength;
    }
    return null;
  }
  
  /**
   * Get last data block in this index, which is not greater than
   * a given key
   * Multiple scanner SAFE
   * @return last data block
   */
  DataBlock lastBlock(DataBlock b, long keyPtr, int keySize) {
    if (keyPtr == 0) {
      return lastBlock(b);
    }
    if (b == null) {
      b = new DataBlock();
    }
    long prevPtr = 0;
    long ptr = this.dataPtr;
    final long limit = this.dataPtr + this.blockDataSize; 
    while(ptr < limit) {
      int keyLength = blockKeyLength(ptr);
      b.set(this, ptr - this.dataPtr);
      if (b.compareTo(keyPtr, keyLength, 0, Op.PUT) <= 0) {
        if (prevPtr > 0) {
          b.set(this, prevPtr - this.dataPtr);
          return b;
        } else {
          return null;
        }
      }
      if (ptr + DATA_BLOCK_STATIC_OVERHEAD + KEY_SIZE_LENGTH + keyLength == limit) {
        b.set(this, ptr - this.dataPtr);
        return b;
      }
      prevPtr = ptr;
      ptr += + DATA_BLOCK_STATIC_OVERHEAD + KEY_SIZE_LENGTH + keyLength;
    }
    return null;
  }
  
  
	/**
	 * Get previous data block
	 * @param b current data block
	 * @return previous data block or null
	 */
	DataBlock previousBlock(DataBlock b) {
	  if (b == null) {
	    return null;
	  }
	  long bPtr = b.getIndexPtr();
	  if (bPtr == this.dataPtr) {
	    return null;
	  }
	  long ptr = this.dataPtr;
	  while(ptr < bPtr) {
	    int keyLength = blockKeyLength(ptr);
	    if (ptr + DATA_BLOCK_STATIC_OVERHEAD + KEY_SIZE_LENGTH + keyLength == bPtr) {
	      b.set(this, ptr - this.dataPtr);
	      return b;
	    }
	    ptr += + DATA_BLOCK_STATIC_OVERHEAD + KEY_SIZE_LENGTH + keyLength;
	  }
    return null;
  }

  /**
	 * Get key-value offset in a block
	 * 
	 * @param key
	 * @param keyOffset
	 * @param keyLength
	 * @param valueBufLength value buffer length
	 * @return value length if found, or NOT_FOUND. if value length > valueBufLength
	 *         no copy will be made - one must repeat call with new value buffer
	 * @throws RetryOperationException
	 */
	public long get(long keyPtr, int keyLength, long valueBuf, int valueBufLength, long version)
			throws RetryOperationException {
    try {
      readLock();
      long ptr = search(keyPtr, keyLength, version, Op.DELETE);
      // It is possible that key is less than a first key in this index block
      // we will try again with locking index block in BigSortedMap.get()
      if (ptr <=0) {
        return NOT_FOUND;
      }
      DataBlock b = block.get();
      b.set(this, ptr - dataPtr);
      long res = b.get(keyPtr, keyLength, valueBuf, valueBufLength, version);
      if (res != DataBlock.NOT_FOUND) {
        return res;
      } else {
        long address = ptr;
        while ((address = searchForGet(keyPtr, keyLength, address)) != NOT_FOUND) {
          b.set(this, address - this.dataPtr);
          res = b.get(keyPtr, keyLength, valueBuf, valueBufLength, version);
          if (res != DataBlock.NOT_FOUND) {
            return res;
          }
        }
        return NOT_FOUND;
      }
    } finally {
      readUnlock();
    }

	}

	/**
	 * Get block size
	 * 
	 * @return block size
	 */
	public int getBlockSize() {
		return blockSize;
	}

	/**
	 * Get data size
	 * 
	 * @return data size
	 */
	public int getDataInBlockSize() {
		return blockDataSize;
	}

	/**
	 * Get number of records in a block
	 * 
	 * @return number of records
	 */
	public int getNumberOfDataBlock() {
		return numDataBlocks;
	}

	public long getSeqNumberSplitOrMerge() {
		return seqNumberSplitOrMerge;
	}

	/**
	 * Increment
	 * 
	 * @param delta
	 */
	public final void incrSeqNumberSplitOrMerge() {
		short v = seqNumberSplitOrMerge;
		if (v == Short.MAX_VALUE) {
			v = 0;
		} else {
			v += 1;
		}
		seqNumberSplitOrMerge = v;
	}

	/**
	 * Must always compact before splitting block
	 * 
	 * @return new (left) block
	 * @throws RetryOperationException
	 */
	public IndexBlock split() throws RetryOperationException {

		try {
			writeLock();
			incrSeqNumberSplitOrMerge();
			long ptr = dataPtr;
			int recCount = 0;
			// Now we should have zero deleted records
			while (recCount < this.numDataBlocks/2) {
				short keylen = (short) blockKeyLength(ptr);
				ptr += keylen + KEY_SIZE_LENGTH + DATA_BLOCK_STATIC_OVERHEAD;
				recCount++;
			}
			int oldDataSize = this.blockDataSize;
			int oldNumRecords = this.numDataBlocks;
			this.blockDataSize = (short) (ptr - dataPtr);
			this.numDataBlocks = (short) recCount;
			int rightBlockSize = this.blockSize;//getMinSizeGreaterThan(getBlockSize(), leftDataSize);
			IndexBlock right = new IndexBlock(rightBlockSize);

			right.numDataBlocks = (short) (oldNumRecords - this.numDataBlocks);
			right.blockDataSize = (short) (oldDataSize - this.blockDataSize);
			UnsafeAccess.copy(ptr, right.dataPtr, right.blockDataSize);
			updateUnsafeModificationTime();
			return right;
		} finally {
			writeUnlock();
		}
	}

	/**
	 * Should merge this block
	 * 
	 * @return true, false
	 */
	public boolean shouldMerge() {
		return (double) blockDataSize / blockSize < MAX_MERGE_RATIO;
	}

	/**
	 * TODO: Finish it
	 * Merge two adjacent blocks
	 * 
	 * @param left
	 * @param forceCompact
	 * @param forceMerge
	 * @return true, if merge successful, false - otherwise
	 * @throws RetryOperationException
	 */
	public boolean merge(IndexBlock left, boolean forceCompact) throws RetryOperationException {

		try {
			writeLock();
			left.writeLock();
			incrSeqNumberSplitOrMerge();
			left.incrSeqNumberSplitOrMerge();
			if ((!shouldMerge() || !left.shouldMerge())) {
				return false;
			}

			UnsafeAccess.copy(left.dataPtr, this.dataPtr + blockDataSize, left.blockDataSize);
			this.numDataBlocks += left.numDataBlocks;
			this.blockDataSize += left.blockDataSize;

			// After merge left block becomes invalid
			// TODO
			updateUnsafeModificationTime();
			return true;
		} finally {
			left.writeUnlock();
			writeUnlock();
		}
	}

	/**
	 * Get block address
	 * 
	 * @return address
	 */
	public long getAddress() {
		return dataPtr;
	}

	/**
	 * Free memory
	 */
	public void free() {
		// TODO: deallocate data blocks
	  deallocateBlocks();
	  // deallocate large keys
	  deallocateLargeKeys();
		UnsafeAccess.free(dataPtr);
		BigSortedMap.totalDataInIndexBlocksSize.addAndGet(-blockDataSize);
    BigSortedMap.totalBlockIndexSize.addAndGet(-blockSize);
    BigSortedMap.totalAllocatedMemory.addAndGet(-blockSize);
		valid = false;

	}

	
	public static AtomicLong largeKVs = new AtomicLong();
	
	private void deallocateLargeKeys() {
    long ptr = dataPtr;
    int count = 0;
    try {
      while (count++ < numDataBlocks) {
        int keylen =  keyLength(ptr);
        
        if (isExternalBlock(ptr)) {
          long recAddress = getExternalAllocationAddress(ptr);
          long size = UnsafeAccess.toInt(recAddress) + INT_SIZE;
          UnsafeAccess.free(recAddress);
          largeKVs.decrementAndGet();
          BigSortedMap.totalAllocatedMemory.addAndGet(-size);
          BigSortedMap.totalIndexSize.addAndGet(-size);
          keylen = ADDRESS_SIZE;
        }
        ptr += keylen + KEY_SIZE_LENGTH + DATA_BLOCK_STATIC_OVERHEAD;
      }
      // last data block
    } finally {
      //checkPointer(ptr);
    }    
  }

	//TODO : IS IT SAFE?
  private void deallocateBlocks() {
	  DataBlock curr = null;
	  long ptr = -1;
	  int count = 0;
	  while(count++ < numDataBlocks) {
	    curr = nextBlock(ptr);
	    ptr = curr.getIndexPtr();
	    curr.free();
	  }
  }

  @Override
	public int compareTo(IndexBlock o) {
		if (this == o)
			return 0;

		byte[] firstKey = getFirstKey();
		byte[] firstKey1 = o.getFirstKey();
		int res = Utils.compareTo(firstKey, 0, firstKey.length, firstKey1, 0, firstKey1.length);
		if (res == 0) {
			long ver = o.version;
			if (version == ver) {
				return type - o.type;
			} else if (ver < version) {
				return -1;
			} else {
				return 1;
			}
		} else {
			return res;
		}
	}

	public byte[] getFirstKey() {
		
		try {
			readLock(false);
			if (firstKey != null) {
	      return firstKey;
	    }
			int keylen =  keyLength(dataPtr);
			byte[] buf = new byte[keylen];
			UnsafeAccess.copy(keyAddress(dataPtr), buf, 0, buf.length);
			firstKey = buf;
			return firstKey;
		} finally {
			readUnlock();
		}
	}
}