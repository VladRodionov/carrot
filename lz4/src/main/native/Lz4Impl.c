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

/* DO NOT EDIT THIS FILE - it is machine generated */
#include "org_bigbase_compression_lz4_LZ4.h"
/* Header for class org_bigbase_compression_lz4_LZ4 */


#ifdef __cplusplus
extern "C" {
#endif
    /*
     * Class:     org_bigbase_compression_lz4_LZ4
     * Method:    compress
     * Signature: ([B)[B
     */
    JNIEXPORT jbyteArray JNICALL Java_org_bigbase_compression_lz4_LZ4_compress
    (JNIEnv *env, jclass clz, jbyteArray array)
    {
    }
    
    /*
     * Class:     org_bigbase_compression_lz4_LZ4
     * Method:    decompress
     * Signature: ([B)[B
     */
    JNIEXPORT jbyteArray JNICALL Java_org_bigbase_compression_lz4_LZ4_decompress
    (JNIEnv *env, jclass clz, jbyteArray arr)
    {
        //TODO
    }
    
    /*
     * Class:     org_bigbase_compression_lz4_LZ4
     * Method:    compressDirect
     * Signature: (Ljava/nio/ByteBuffer;IILjava/nio/ByteBuffer;II)I
     */
    JNIEXPORT jint JNICALL Java_org_bigbase_compression_lz4_LZ4_compressDirect
    (JNIEnv *env, jclass clz, jobject srcBuffer, jint offset, jint length, jobject dstBuffer, jint where, jint dstCapacity)
    {
        jsize size = length;
        char *source = (char *) (*env)->GetDirectBufferAddress(env, srcBuffer);
        source += offset;
        char *compressed = (char *) (*env)->GetDirectBufferAddress(env, dstBuffer);
        compressed += where;
        jsize r = LZ4_compress_default(source, compressed, size, dstCapacity);

        return r;

    }

   /*
     * Class:     org_bigbase_compression_lz4_LZ4
     * Method:    compressDirectHC
     * Signature: (Ljava/nio/ByteBuffer;IILjava/nio/ByteBuffer;III)I
     */
    JNIEXPORT jint JNICALL Java_org_bigbase_compression_lz4_LZ4_compressDirectHC
    (JNIEnv *env, jclass clz, jobject srcBuffer, jint offset, jint length, jobject dstBuffer, jint where,
    jint dstCapacity, jint level)
    {
        jsize size = length;
        char *source = (char *) (*env)->GetDirectBufferAddress(env, srcBuffer);
        source += offset;
        char *compressed = (char *) (*env)->GetDirectBufferAddress(env, dstBuffer);
        compressed += where;
        jsize r = LZ4_compress_HC(source, compressed, size, dstCapacity, level);

        return r;

    }
 
    JNIEXPORT jint JNICALL Java_org_bigbase_compression_lz4_LZ4_compressDirectAddress
    (JNIEnv *env, jclass clz, jlong src, jint length, jlong dst, jint dstCapacity)
    {
        jsize size = length;
        char *source = (char *) src;
        char *compressed = (char *) dst;
        jsize r = LZ4_compress_default(source, compressed, size, dstCapacity);
        
        return r;
        
    }

    /*
     * Class:     org_bigbase_compression_lz4_LZ4
     * Method:    decompressDirect
     * Signature: (Ljava/nio/ByteBuffer;ILjava/nio/ByteBuffer;I)I
     */
    JNIEXPORT jint JNICALL Java_org_bigbase_compression_lz4_LZ4_decompressDirect
    (JNIEnv *env, jclass clz, jobject srcBuffer, jint offset, jint compressedSize, jobject dstBuffer, 
    jint where, jint dstCapacity)
    {

        
        char *source = (char *) (*env)->GetDirectBufferAddress(env, srcBuffer);
        source += offset;
        char *decompressed = (char *) (*env)->GetDirectBufferAddress(env, dstBuffer);
        decompressed += where; 

        jsize r = LZ4_decompress_safe(source, decompressed, compressedSize, dstCapacity);
 
        return r;
 
    }
    
    /*
     * Class:     org_bigbase_compression_lz4_LZ4
     * Method:    decompressDirectHC
     * Signature: (Ljava/nio/ByteBuffer;ILjava/nio/ByteBuffer;I)I
     */
    JNIEXPORT jint JNICALL Java_org_bigbase_compression_lz4_LZ4_decompressDirectHC
    (JNIEnv *env, jclass clz, jobject srcBuffer, jint offset, jint compressedSize, jobject dstBuffer,
     jint where, jint dstCapacity)
    {

        return Java_org_bigbase_compression_lz4_LZ4_decompressDirect(env, clz, srcBuffer, offset, compressedSize, dstBuffer,
         where, dstCapacity);

    }

    JNIEXPORT jint JNICALL Java_org_bigbase_compression_lz4_LZ4_decompressDirectAddress
    (JNIEnv *env, jclass clz, jlong src,  jint compressedSize, jlong dst, jint dstCapacity)
    {
        
        char *source = (char *) src;
        char *decompressed = (char *) dst;        
        jsize r = LZ4_decompress_safe(source, decompressed, compressedSize, dstCapacity);
        return r;
        
    }
    
    JNIEXPORT jint JNICALL Java_org_bigbase_compression_lz4_LZ4_compressDirectAddressHC
    (JNIEnv * env, jclass cls, jlong src, jint length, jlong dst,
    jint dstCapacity, jint level)
    {
        jsize size = length;
        char *source = (char *) src;
        char *compressed = (char *) dst;
        jsize r = LZ4_compress_HC(source, compressed, size, dstCapacity, level);
        
        return r;
    }
    
    JNIEXPORT jint JNICALL Java_org_bigbase_compression_lz4_LZ4_decompressDirectAddressHC
    (JNIEnv *env, jclass cls, jlong src, jint compressedSize, jlong dst, 
    jint dstCapacity)
    {
        return Java_org_bigbase_compression_lz4_LZ4_decompressDirectAddress(env, cls, src, 
        compressedSize, dst, dstCapacity);
    }

#ifdef __cplusplus
}
#endif


