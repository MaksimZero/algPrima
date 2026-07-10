package org.example.backend

import java.io.File
import org.example.backend.models.Vertex

data class ParsedGraph(
    val vertices: List<Vertex>,
    val matrix: Array<DoubleArray>
    )

class GraphParser{

    fun parseFile(filePath: String) : ParsedGraph {
        val verticesList = mutableListOf<Vertex>()
        val rawEdges = mutableListOf<Triple<String, String, Double>>()
        File(filePath).forEachLine { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachLine

            val parts = line.split("\\s+".toRegex())

            if (parts[0] == "pos") {
                val id = parts[1]
                val x = parts[2].toDouble()
                val y = parts[3].toDouble()
                verticesList.add(Vertex(id, x, y))
            } else {
                val from = parts[0]
                val to = parts[1]
                val weight = parts[2].toDouble()
                rawEdges.add(Triple(from, to, weight))
            }
        }

        val n = verticesList.size
        val matrix = Array(n) { i ->
            DoubleArray(n) { j ->
                if (i == j) -1.0 else Double.POSITIVE_INFINITY
            }
        }

        for (edge in rawEdges) {
            val indexFrom = verticesList.indexOfFirst { it.name == edge.first }
            val indexTo = verticesList.indexOfFirst { it.name == edge.second }

            if (indexFrom != -1 && indexTo != -1) {
                matrix[indexFrom][indexTo] = edge.third
                matrix[indexTo][indexFrom] = edge.third
            }
        }

        return ParsedGraph(verticesList, matrix)
    }
}
