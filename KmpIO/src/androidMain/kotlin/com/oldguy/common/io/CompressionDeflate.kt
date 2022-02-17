package com.oldguy.common.io

import java.util.zip.Deflater
import java.util.zip.Inflater
import kotlin.math.min

/**
 * Uses Android Inflater/Deflater classes for implementation of the Deflate algorithm.
 * This class maintains state on the current compression op. NEVER use the same instance for multiple
 * different simultaneous operations. Instance state is not thread-safe.
 *
 * Note - for compatibility with Zip, both the deflater and the inflater use the nowrap=true option
 * by default. This can be overridden for both, but this class does not offer the ability to use
 * a deflater and inflater that do not use the same nowrap option.
 * Nowrap = true adds a header and CRC to the payload on deflate, and expects these on inflate
 */
actual class CompressionDeflate actual constructor(noWrap: Boolean): Compression {
    actual enum class Strategy {Default, Filtered, Huffman}
    actual override val algorithm: CompressionAlgorithms = CompressionAlgorithms.Deflate

    private val inflater = Inflater(noWrap)
    private var deflater = Deflater(Deflater.DEFAULT_STRATEGY, noWrap)

    private fun setDeflater(strategy: Strategy) {
        deflater = Deflater(when (strategy) {
            Strategy.Default -> Deflater.DEFAULT_STRATEGY
            Strategy.Filtered -> Deflater.FILTERED
            Strategy.Huffman -> Deflater.HUFFMAN_ONLY
        }, true)
    }

    actual suspend fun compress(strategy: Strategy,
                        input: suspend () -> ByteBuffer,
                        output: suspend (buffer: ByteBuffer) -> Unit
    ): ULong {
        setDeflater(strategy)
        return compress(input, output)
    }


    /**
     * Call this with one or more blocks of data to compress any amount of data using the algorithm specified at
     * constructor time.
     * @param input Uncompressed data. Will compress starting at the current position (typically 0) until limit.
     * [compress] function will continue operation until an empty ByteBuffer is returned (positionLimit(0,0))
     * @return buffer large enough to contain all Compressed data from input.  Buffer size and remaining value are
     * both equal to the uncompressed byte count. Position of the buffer on return is zero, no rewind required. Size
     * will always be <= input remaining
     */
    actual override suspend fun compress(input: suspend () -> ByteBuffer,
                                         output: suspend (buffer: ByteBuffer) -> Unit
    ): ULong {
        return compressArray(
            input = {
                input().getBytes()
            }
        ) {
            output(ByteBuffer(it))
        }
    }

    actual suspend fun compressArray(strategy: Strategy,
                                     input: suspend () -> ByteArray,
                                     output: suspend (buffer: ByteArray) -> Unit
    ): ULong {
        setDeflater(strategy)
        return compressArray(input, output)
    }

    /**
     * Call this with one or more blocks of data to compress any amount of data using the algorithm specified at
     * constructor time.
     * @param input Invoked for each block of Uncompressed data. Entire ByteArray will be processed.
     * Pass an empty ByteArray to signal end of data.
     * @param output Invoked once for each chunk of compressed data produced,
     * @return Count of total compressed bytes.
     */
    actual override suspend fun compressArray(input: suspend () -> ByteArray,
                                              output: suspend (buffer: ByteArray) -> Unit
    ): ULong {
        var count = 0UL
        val bufferSize = 4096
        deflater.apply {
            reset()
            val out = ByteArray(bufferSize)
            var run = true
            while (run) {
                input().also {
                    if (it.isEmpty()) {
                        finish()
                        while (!finished()) {
                            val opCount = deflate(out)
                            if (opCount > 0) {
                                output(out.sliceArray(0 until opCount))
                                count += opCount.toUInt()
                            }
                        }
                        run = false
                    } else {
                        setInput(it)
                        while (!needsInput()) {
                            val opCount = deflate(out)
                            if (opCount > 0) {
                                output(out.sliceArray(0 until opCount))
                                count += opCount.toUInt()
                            }
                        }
                    }
                }
            }
            end()
        }
        return count
    }

    /**
     * Perform a decompress operation of any amount of data using the algorithm specified at
     * constructor time.
     * If the selected algorithm fails during the operation, an Exception is thrown. There is no
     * attempt at dynamically determining the algorithm used to originally do the compression.
     * @param totalCompressedBytes Compressed data byte count. This is the number of input bytes to
     * process.  Function will continue until this number of bytes are provided via the [input] function.
     * @param bufferSize specifies max amount of bytes that will be passed in the bytesToRead argument
     * @param input will be invoked once for each time the process needs more compressed data.
     * Total size (sum of remainings) of all ByteBuffers provided must equal [totalCompressedBytes].
     * If total number of bytes passed to all input calls exceeds [totalCompressedBytes] an
     * exception is thrown.
     * @param output will be called repeatedly as decompressed bytes are produced. Buffer argument will
     * have position zero and limit set to however many bytes were uncompressed. This buffer has a
     * capacity equal to the first input ByteBuffer, but the number of bytes it contains will be 0 < limit
     * <= capacity, as any one compress can produce any non-zero number of bytes.
     * Implementation should consume Buffer content (between position 0 and remaining) as for large
     * payloads with high compression ratios, this function may be called MANY times.
     * @return sum of all uncompressed bytes count passed via [output] function calls.
     */
    actual override suspend fun decompress(
        totalCompressedBytes: ULong,
        bufferSize: UInt,
        input: suspend (bytesToRead: Int) -> ByteBuffer,
        output: suspend (buffer: ByteBuffer) -> Unit
    ): ULong {
        var remaining = totalCompressedBytes
        var outCount = 0UL
        var inBuf = input(min(bufferSize.toULong(), remaining).toInt())
        remaining -= inBuf.remaining.toULong()
        val outBuf = ByteArray(inBuf.capacity)
        inflater.apply {
            reset()
            val b = inBuf.getBytes()
            setInput(b)
            var count: Int
            while (!finished()) {
                count = inflate(outBuf)
                if (count > 0) {
                    output(ByteBuffer(outBuf.sliceArray(0 until count)))
                    outCount += count.toULong()
                }
                if (!finished() && needsInput()) {
                    inBuf = input(min(bufferSize.toULong(), remaining).toInt())
                    remaining -= inBuf.remaining.toULong()
                    if (remaining < 0UL)
                        throw IllegalStateException("totalCompressedBytes expected: $totalCompressedBytes. Excess bytes provided: ${remaining.toLong() *-1L}")
                    setInput(inBuf.getBytes())
                }
            }
            end()
        }
        return outCount
    }
    /**
     * Call this with one or more blocks of data to de-compress any amount of data using the algorithm specified at
     * constructor time.
     * @param totalCompressedBytes Compressed data byte count. This is the number of input bytes to
     * process.  Function will continue until this number of bytes are provided via the [input] function.
     * @param bufferSize specifies max amount of bytes that will be passed in the bytesToRead argument
     * @param input will be invoked once for each time the process needs more compressed data.
     * Total size (sum of remainings) of all ByteBuffers provided must equal [totalCompressedBytes].
     * If total number of bytes passed to all input calls exceeds [totalCompressedBytes] an
     * exception is thrown.
     * @param output will be called repeatedly as decompressed bytes are produced. Buffer argument will
     * have position zero and limit set to however many bytes were uncompressed. This buffer has a
     * capacity equal to the first input ByteBuffer, but the number of bytes it contains will be 0 < limit
     * <= capacity, as any one compress can produce any non-zero number of bytes.
     * Implementation should consume Buffer content (between position 0 and remaining) as for large
     * payloads with high compression ratios, this function may be called MANY times.
     * @return sum of all uncompressed bytes count passed via [output] function calls.
     */
    actual override suspend fun decompressArray(
        totalCompressedBytes: ULong,
        bufferSize: UInt,
        input: suspend (bytesToRead: Int) -> ByteArray,
        output: suspend (buffer: ByteArray) -> Unit
    ): ULong {
        TODO("Not yet implemented")

    }

    /**
     * Use this to reset state back to the same as initialization.  This allows reuse of this instance for additional
     * operations
     */
    actual override fun reset() {
        inflater.reset()
        deflater.reset()
    }
}