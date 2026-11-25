package app.chaukas.data

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Self-contained QR Code encoder (Byte Mode, ISO/IEC 18004) generating a boolean matrix,
 * rendered crisply in Jetpack Compose without external dependencies.
 */
object QrEncoder {

    // Capacity (data bytes) for Version 1..5 with Error Correction Level M
    private val TOTAL_CODEWORDS = intArrayOf(0, 26, 44, 70, 100, 134)
    private val EC_CODEWORDS = intArrayOf(0, 10, 16, 26, 36, 48)
    private val DATA_CAPACITY = intArrayOf(0, 16, 28, 44, 64, 86)
    // Alignment pattern center positions
    private val ALIGN_POS = arrayOf(
        intArrayOf(),
        intArrayOf(),
        intArrayOf(6, 18),
        intArrayOf(6, 22),
        intArrayOf(6, 26),
        intArrayOf(6, 30),
    )

    fun encode(text: String): Array<BooleanArray> {
        val bytes = text.toByteArray(Charsets.ISO_8859_1)
        val version = (1..5).firstOrNull { bytes.size <= DATA_CAPACITY[it] - 2 } ?: 5
        val size = 17 + 4 * version

        // 1. Bit buffer
        val bitBuffer = BitBuffer()
        bitBuffer.append(0b0100, 4) // Byte mode
        bitBuffer.append(bytes.size, 8)
        for (b in bytes) {
            bitBuffer.append(b.toInt() and 0xFF, 8)
        }
        val totalDataBits = (DATA_CAPACITY[version]) * 8
        val padZeros = minOf(4, totalDataBits - bitBuffer.length)
        bitBuffer.append(0, padZeros)
        while (bitBuffer.length % 8 != 0) {
            bitBuffer.append(0, 1)
        }
        val padBytes = intArrayOf(0xEC, 0x11)
        var padIdx = 0
        while (bitBuffer.length < totalDataBits) {
            bitBuffer.append(padBytes[padIdx % 2], 8)
            padIdx++
        }

        // 2. Reed-Solomon Error Correction
        val dataBytes = bitBuffer.toBytes()
        val numEc = EC_CODEWORDS[version]
        val ecBytes = rsEncode(dataBytes, numEc)
        val allCodewords = dataBytes + ecBytes

        // 3. Matrix construction
        val matrix = Array(size) { BooleanArray(size) }
        val reserved = Array(size) { BooleanArray(size) }

        // Finder patterns (top-left, top-right, bottom-left)
        fun setFinder(r: Int, c: Int) {
            for (dr in -1..7) {
                for (dc in -1..7) {
                    val nr = r + dr
                    val nc = c + dc
                    if (nr in 0 until size && nc in 0 until size) {
                        reserved[nr][nc] = true
                        val isBlack = (dr in 0..6 && (dc == 0 || dc == 6)) ||
                            (dc in 0..6 && (dr == 0 || dr == 6)) ||
                            (dr in 2..4 && dc in 2..4)
                        matrix[nr][nc] = isBlack
                    }
                }
            }
        }
        setFinder(0, 0)
        setFinder(0, size - 7)
        setFinder(size - 7, 0)

        // Timing patterns
        for (i in 8 until size - 8) {
            if (!reserved[6][i]) {
                reserved[6][i] = true
                matrix[6][i] = (i % 2 == 0)
            }
            if (!reserved[i][6]) {
                reserved[i][6] = true
                matrix[i][6] = (i % 2 == 0)
            }
        }

        // Alignment patterns
        val aligns = ALIGN_POS[version]
        for (ar in aligns) {
            for (ac in aligns) {
                if (!reserved[ar][ac]) {
                    for (dr in -2..2) {
                        for (dc in -2..2) {
                            reserved[ar + dr][ac + dc] = true
                            matrix[ar + dr][ac + dc] = (maxOf(Math.abs(dr), Math.abs(dc)) != 1)
                        }
                    }
                }
            }
        }

        // Dark module
        matrix[4 * version + 9][8] = true
        reserved[4 * version + 9][8] = true

        // Reserve Format info
        for (i in 0..8) {
            reserved[8][i] = true
            reserved[i][8] = true
            reserved[8][size - 1 - i] = true
            reserved[size - 1 - i][8] = true
        }

        // 4. Place Data Codewords (zigzag)
        var bitIndex = 0
        val totalBits = allCodewords.size * 8
        var right = size - 1
        while (right > 0) {
            if (right == 6) right-- // Skip vertical timing column
            for (vert in 0 until size) {
                for (horiz in 0..1) {
                    val col = right - horiz
                    val row = if (((right + 1) / 2) % 2 == 1) size - 1 - vert else vert
                    if (!reserved[row][col]) {
                        val bit = if (bitIndex < totalBits) {
                            val byteVal = allCodewords[bitIndex / 8]
                            val bitPos = 7 - (bitIndex % 8)
                            ((byteVal shr bitPos) and 1) == 1
                        } else false
                        bitIndex++
                        // Mask pattern 0: (row + col) % 2 == 0
                        val mask = (row + col) % 2 == 0
                        matrix[row][col] = bit xor mask
                    }
                }
            }
            right -= 2
        }

        // 5. Format information (EC Level M = 00, Mask 0 = 000 -> 00000 -> with BCH + XOR 0x5412 = 0x4B3B)
        val formatBits = 0x4B3B
        for (i in 0..14) {
            val bit = ((formatBits shr i) and 1) == 1
            // Top-left
            when {
                i < 6 -> matrix[8][i] = bit
                i == 6 -> matrix[8][7] = bit
                i == 7 -> matrix[8][8] = bit
                i == 8 -> matrix[7][8] = bit
                else -> matrix[14 - i][8] = bit
            }
            // Split around bottom-left & top-right
            if (i < 8) {
                matrix[size - 1 - i][8] = bit
            } else {
                matrix[8][size - 15 + i] = bit
            }
        }

        return matrix
    }

    // Reed-Solomon GF(256) arithmetic
    private val EXP = IntArray(512)
    private val LOG = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            EXP[i] = x
            EXP[i + 255] = x
            LOG[x] = i
            x = (x shl 1)
            if (x >= 256) x = x xor 0x11D
        }
    }

    private fun gfMul(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        return EXP[LOG[a] + LOG[b]]
    }

    private fun rsGenPoly(n: Int): IntArray {
        var g = intArrayOf(1)
        for (i in 0 until n) {
            val next = IntArray(g.size + 1)
            for (j in g.indices) {
                next[j] = next[j] xor gfMul(g[j], EXP[i])
                next[j + 1] = next[j + 1] xor g[j]
            }
            g = next
        }
        return g
    }

    private fun rsEncode(data: IntArray, n: Int): IntArray {
        val gen = rsGenPoly(n)
        val res = IntArray(data.size + n)
        System.arraycopy(data, 0, res, 0, data.size)
        for (i in data.indices) {
            val coef = res[i]
            if (coef != 0) {
                for (j in gen.indices) {
                    res[i + j] = res[i + j] xor gfMul(gen[j], coef)
                }
            }
        }
        return res.copyOfRange(data.size, res.size)
    }

    private class BitBuffer {
        private val list = mutableListOf<Boolean>()
        val length get() = list.size

        fun append(value: Int, bits: Int) {
            for (i in bits - 1 downTo 0) {
                list.add(((value shr i) and 1) == 1)
            }
        }

        fun toBytes(): IntArray {
            val bytes = IntArray(list.size / 8)
            for (i in list.indices) {
                if (list[i]) {
                    bytes[i / 8] = bytes[i / 8] or (1 shl (7 - (i % 8)))
                }
            }
            return bytes
        }
    }
}

@Composable
fun QrCodeView(
    text: String,
    modifier: Modifier = Modifier,
    size: Dp = 156.dp,
    dotColor: Color = Color(0xFF24212C),
    backgroundColor: Color = Color.White,
) {
    val matrix = remember(text) { runCatching { QrEncoder.encode(text) }.getOrNull() }
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .padding(8.dp),
    ) {
        if (matrix != null) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val count = matrix.size
                val cellSize = this.size.width / count
                for (r in 0 until count) {
                    for (c in 0 until count) {
                        if (matrix[r][c]) {
                            drawRect(
                                color = dotColor,
                                topLeft = Offset(c * cellSize, r * cellSize),
                                size = Size(cellSize + 0.5f, cellSize + 0.5f),
                            )
                        }
                    }
                }
            }
        }
    }
}
