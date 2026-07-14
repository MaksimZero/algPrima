package org.example.backend

import java.io.File
import org.example.backend.models.Vertex
import org.example.backend.models.AlgorithmSnapshot

class GraphExporter(){
    fun saveGraphToFile(filePath: String, vertices: List<Vertex>, matrix: Array<DoubleArray>, mstSteps: List<AlgorithmSnapshot>? = null){
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
            if (!mstSteps.isNullOrEmpty()) {
                writer.write("\n# Шаги алгоритма\n")
                for (step in mstSteps){
                    val vNames = step.mstVertices.joinToString(",") { it.name }
                    val eNames = step.mstEdges.joinToString(",") { "${it.from.name}-${it.to.name}-${it.weight}" }
                    val cNames = step.candidateEdges.joinToString(",") { "${it.from.name}-${it.to.name}-${it.weight}" }
                    //[номер] | [вес] | [лог] | v:[вершины] | e:[рёбра] | c:[кандидаты]
                    writer.write("mst_step ${step.stepNumber} | ${step.totalWeight} | ${step.logMessage} | $vNames | $eNames | $cNames\n")
                }
            }
        } finally {
            writer.flush()
            writer.close()
        }
    }
}
