package org.example.backend

import java.io.File
import org.example.Vertex

class GraphExporter(){
    fun SaveGraphToFile(filePath: String, vertices: List<Vertex>, matrix: Array<DoubleArray>){
        val file = File(filePath)
        val writer = file.bufferedWriter()

        try {
            writer.write("# Координаты вершин\n")
            for (v in vertices) {
                writer.write("pos ${v.name} ${v.x} ${v.y}\n")
            }

            writer.write("\n# Связи и веса\n")
            val n = matrix.size
            for (i in 0 until n) {
                for (j in (i + 1) until n) {
                    val w = matrix[i][j]
                    if (w != -1.0 && w != Double.POSITIVE_INFINITY) {
                        writer.write("${vertices[i].name} ${vertices[j].name} ${w.toInt()}\n")
                    }
                }
            }
        } finally {
            writer.flush()
            writer.close()
        }
    }
}
