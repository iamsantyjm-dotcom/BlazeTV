package com.pilltv.app.data

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/** Copia guardada de las series de una lista Xtream (mismo formato binario que ChannelCache). */
object SeriesCache {

    private const val MAGIC = 0x424C5A53

    fun write(file: File, list: List<Series>) {
        if (list.isEmpty()) {
            file.delete()
            return
        }
        val tmp = File(file.parentFile, file.name + ".tmp")
        DataOutputStream(BufferedOutputStream(FileOutputStream(tmp), 64 * 1024)).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(list.size)
            for (s in list) {
                out.writeUTF(s.sourceId)
                out.writeUTF(s.id)
                out.writeUTF(s.name)
                out.writeUTF(s.cover ?: "")
                out.writeUTF((s.plot ?: "").take(1200))
                out.writeUTF(s.year ?: "")
                out.writeUTF(s.rating ?: "")
                out.writeUTF(s.group)
            }
        }
        file.delete()
        tmp.renameTo(file)
    }

    fun read(file: File): List<Series> {
        if (!file.exists()) return emptyList()
        return DataInputStream(BufferedInputStream(FileInputStream(file), 64 * 1024)).use { input ->
            if (input.readInt() != MAGIC) return@use emptyList<Series>()
            val count = input.readInt()
            val shared = HashMap<String, String>()
            val out = ArrayList<Series>(count)
            repeat(count) {
                val sourceId = input.readUTF().let { shared.getOrPut(it) { it } }
                val id = input.readUTF()
                val name = input.readUTF()
                val cover = input.readUTF()
                val plot = input.readUTF()
                val year = input.readUTF()
                val rating = input.readUTF()
                val group = input.readUTF().let { shared.getOrPut(it) { it } }
                out.add(
                    Series(
                        sourceId = sourceId,
                        id = id,
                        name = name,
                        cover = cover.ifEmpty { null },
                        plot = plot.ifEmpty { null },
                        year = year.ifEmpty { null },
                        rating = rating.ifEmpty { null },
                        group = group,
                    )
                )
            }
            out
        }
    }
}
