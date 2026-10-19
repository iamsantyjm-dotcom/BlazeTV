package com.pilltv.app.data

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/** Copia guardada de los canales en formato binario: se lee mucho más rápido que volver a analizar el M3U. */
object ChannelCache {

    // Versión 4: se agregan año y puntuación. Una copia vieja se ignora y la lista se vuelve a descargar.
    private const val MAGIC = 0x424C5A34

    fun write(file: File, list: List<Channel>) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        DataOutputStream(BufferedOutputStream(FileOutputStream(tmp), 64 * 1024)).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(list.size)
            for (c in list) {
                out.writeUTF(c.name)
                out.writeUTF(c.url)
                out.writeUTF(c.logo ?: "")
                out.writeUTF(c.group)
                out.writeUTF(c.tvgId ?: "")
                out.writeByte(c.kind.ordinal)
                out.writeUTF(c.year ?: "")
                out.writeUTF(c.rating ?: "")
            }
        }
        file.delete()
        tmp.renameTo(file)
    }

    fun read(file: File): List<Channel> {
        return DataInputStream(BufferedInputStream(FileInputStream(file), 64 * 1024)).use { input ->
            if (input.readInt() != MAGIC) return@use emptyList<Channel>()
            val count = input.readInt()
            val kinds = Kind.values()
            val groups = HashMap<String, String>()
            val out = ArrayList<Channel>(count)
            repeat(count) {
                val name = input.readUTF()
                val url = input.readUTF()
                val logo = input.readUTF()
                val rawGroup = input.readUTF()
                val tvg = input.readUTF()
                val kind = kinds[input.readUnsignedByte()]
                val year = input.readUTF()
                val rating = input.readUTF()
                val group = groups.getOrPut(rawGroup) { rawGroup }
                out.add(
                    Channel(
                        name = name,
                        url = url,
                        logo = if (logo.isEmpty()) null else logo,
                        group = group,
                        tvgId = if (tvg.isEmpty()) null else tvg,
                        kind = kind,
                        year = if (year.isEmpty()) null else year,
                        rating = if (rating.isEmpty()) null else rating,
                    )
                )
            }
            out
        }
    }
}
