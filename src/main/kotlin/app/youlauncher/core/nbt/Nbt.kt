package app.youlauncher.core.nbt

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/** Минимальный NBT (формат файлов игры): чтение и запись нужных тегов. */
sealed interface Tag {
    data class Byte(val v: kotlin.Byte) : Tag
    data class Short(val v: kotlin.Short) : Tag
    data class Int(val v: kotlin.Int) : Tag
    data class Long(val v: kotlin.Long) : Tag
    data class Float(val v: kotlin.Float) : Tag
    data class Double(val v: kotlin.Double) : Tag
    data class ByteArray(val v: kotlin.ByteArray) : Tag
    data class Str(val v: String) : Tag
    data class ListTag(val type: kotlin.Int, val items: List<Tag>) : Tag
    data class Compound(val map: Map<String, Tag>) : Tag {
        fun str(k: String) = (map[k] as? Str)?.v
        fun byte(k: String) = (map[k] as? Byte)?.v
        fun list(k: String) = (map[k] as? ListTag)?.items.orEmpty()
    }
    data class IntArray(val v: kotlin.IntArray) : Tag
    data class LongArray(val v: kotlin.LongArray) : Tag
}

object Nbt {
    fun readRoot(input: DataInputStream): Tag.Compound {
        val type = input.readUnsignedByte()
        if (type != 10) throw IOException("NBT: корневой тег не compound ($type)")
        input.readUTF()
        return read(input, 10) as Tag.Compound
    }

    fun read(input: DataInputStream, type: Int): Tag = when (type) {
        1 -> Tag.Byte(input.readByte())
        2 -> Tag.Short(input.readShort())
        3 -> Tag.Int(input.readInt())
        4 -> Tag.Long(input.readLong())
        5 -> Tag.Float(input.readFloat())
        6 -> Tag.Double(input.readDouble())
        7 -> Tag.ByteArray(ByteArray(input.readInt()).also { input.readFully(it) })
        8 -> Tag.Str(input.readUTF())
        9 -> {
            val itemType = input.readUnsignedByte()
            val n = input.readInt()
            Tag.ListTag(itemType, List(maxOf(n, 0)) { read(input, itemType) })
        }
        10 -> {
            val map = LinkedHashMap<String, Tag>()
            while (true) {
                val t = input.readUnsignedByte()
                if (t == 0) break
                map[input.readUTF()] = read(input, t)
            }
            Tag.Compound(map)
        }
        11 -> Tag.IntArray(IntArray(input.readInt()) { input.readInt() })
        12 -> Tag.LongArray(LongArray(input.readInt()) { input.readLong() })
        else -> throw IOException("NBT: неизвестный тег $type")
    }

    fun writeRoot(out: DataOutputStream, root: Tag.Compound) {
        out.writeByte(10)
        out.writeUTF("")
        write(out, root)
    }

    fun typeOf(tag: Tag): Int = when (tag) {
        is Tag.Byte -> 1; is Tag.Short -> 2; is Tag.Int -> 3; is Tag.Long -> 4; is Tag.Float -> 5; is Tag.Double -> 6
        is Tag.ByteArray -> 7; is Tag.Str -> 8; is Tag.ListTag -> 9; is Tag.Compound -> 10; is Tag.IntArray -> 11; is Tag.LongArray -> 12
    }

    fun write(out: DataOutputStream, tag: Tag) {
        when (tag) {
            is Tag.Byte -> out.writeByte(tag.v.toInt())
            is Tag.Short -> out.writeShort(tag.v.toInt())
            is Tag.Int -> out.writeInt(tag.v)
            is Tag.Long -> out.writeLong(tag.v)
            is Tag.Float -> out.writeFloat(tag.v)
            is Tag.Double -> out.writeDouble(tag.v)
            is Tag.ByteArray -> { out.writeInt(tag.v.size); out.write(tag.v) }
            is Tag.Str -> out.writeUTF(tag.v)
            is Tag.ListTag -> {
                out.writeByte(if (tag.items.isEmpty()) 0 else tag.type)
                out.writeInt(tag.items.size)
                tag.items.forEach { write(out, it) }
            }
            is Tag.Compound -> {
                tag.map.forEach { (k, v) -> out.writeByte(typeOf(v)); out.writeUTF(k); write(out, v) }
                out.writeByte(0)
            }
            is Tag.IntArray -> { out.writeInt(tag.v.size); tag.v.forEach(out::writeInt) }
            is Tag.LongArray -> { out.writeInt(tag.v.size); tag.v.forEach(out::writeLong) }
        }
    }
}
