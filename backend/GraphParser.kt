package org.example.backend

import java.io.File
import org.example.backend.models.Vertex
import org.example.backend.models.Edge
import org.example.backend.models.AlgorithmSnapshot

data class ParsedGraph(
    val vertices: List<Vertex>,
    val matrix: Array<DoubleArray>,
    val steps: List<AlgorithmSnapshot>
)

class GraphParser {

    fun parseFile(filePath: String): ParsedGraph {
        val file = File(filePath)
        val verticesList = mutableListOf<Vertex>()
        val rawEdges = mutableListOf<Triple<String, String, Double>>()
        val steps = mutableListOf<AlgorithmSnapshot>()

        file.forEachLine { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachLine

            if (line.startsWith("pos ")) {
                val parts = line.split("\\s+".toRegex())
                if (parts.size >= 4) {
                    val id = parts[1]
                    val x = parts[2].toDoubleOrNull() ?: 0.0
                    val y = parts[3].toDoubleOrNull() ?: 0.0
                    // Вызывается конструктор: Vertex(name, x, y), radius и color подставятся по умолчанию
                    verticesList.add(Vertex(id, x, y))
                }
            } else if (line.startsWith("mst_step")) {
                val cleanLine = line.removePrefix("mst_step ")
                val sections = cleanLine.split("|")
                if (sections.size >= 6) {
                    val stepNumber = sections[0].trim().toIntOrNull() ?: 0
                    val totalWeight = sections[1].trim().toDoubleOrNull() ?: 0.0
                    val logMessage = sections[2].trim()

                    val vPart = sections[3].removePrefix("v:").trim()
                    val ePart = sections[4].removePrefix("e:").trim()
                    val cPart = sections[5].removePrefix("c:").trim()

                    val mstVertices = if (vPart.isEmpty() || vPart == "—") emptySet()
                    else vPart.split(",").mapNotNull { name ->
                        verticesList.find { it.name == name.trim() }
                    }.toSet()

                    val mstEdges = parseEdges(ePart, verticesList)
                    val candidateEdges = parseEdges(cPart, verticesList)

                    // Создаем снимок строго по именам параметров твоей модели
                    steps.add(
                        AlgorithmSnapshot(
                            stepNumber = stepNumber,
                            mstVertices = mstVertices,
                            mstEdges = mstEdges,
                            candidateEdges = candidateEdges,
                            logMessage = logMessage,
                            totalWeight = totalWeight
                        )
                    )
                }
            } else {
                val parts = line.split("\\s+".toRegex())
                if (parts.size >= 3) {
                    val from = parts[0]
                    val to = parts[1]
                    val weight = parts[2].toDoubleOrNull() ?: 0.0
                    rawEdges.add(Triple(from, to, weight))
                }
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

        return ParsedGraph(verticesList, matrix, steps)
    }

    private fun parseEdges(part: String, vertices: List<Vertex>): Set<Edge> {
        if (part.isEmpty() || part == "—") return emptySet()
        val set = mutableSetOf<Edge>()
        val tokens = part.split(",")
        for (token in tokens) {
            val subParts = token.trim().split("-")
            if (subParts.size == 3) {
                val fromV = vertices.find { it.name == subParts[0].trim() }
                val toV = vertices.find { it.name == subParts[1].trim() }
                val weight = subParts[2].trim().toDoubleOrNull() ?: 0.0
                if (fromV != null && toV != null) {
                    set.add(Edge(fromV, toV, weight))
                }
            }
        }
        return set
    }
}