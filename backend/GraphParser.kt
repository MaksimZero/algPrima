package org.example.backend

import java.io.File
import org.example.backend.models.Vertex

data class ParsedGraph(
    val vertices: List<Vertex>,
    val matrix: Array<DoubleArray>
    )

class GraphParser{
    fun parseFile(filePath: String) : ParsedGraph{
        val verticesList = mutableListOf<Vertex>()
        val rawEdges = mutableListOf<Triple<String, String, Double>>()
        var currentMode = ""

        File(filePath).forEachLine { rawLine->
            val line = rawLine.trim()

            if (line.isEmpty() || line.startsWith("#")) return@forEachLine

            if (line == "VERTICES"){
                currentMode = "VERTICES"
                return@forEachLine
            }

            if (line == "EDGES"){
                currentMode = "EDGES"
                return@forEachLine
            }

            val parts = line.split("\\s+".toRegex())

            when(currentMode){
                "VERTICES" -> {
                    if (parts.size >= 3){
                        val id = parts[0]
                        val X = parts[1].toDouble()
                        val Y = parts[2].toDouble()
                        verticesList.add(Vertex(id, X, Y))
                    }
                }
                "EDGES" -> {
                    if (parts.size >= 3){
                        val from = parts[0]
                        val to = parts[1]
                        val weight = parts[2].toDouble()
                        rawEdges.add(Triple(from, to, weight))
                    }
                }
            }
        }
        val n = verticesList.size
        val matrix = Array(n) { i ->
            DoubleArray(n) {j ->
                if (i == j) -1.0 else Double.POSITIVE_INFINITY
            }
        }
        for (edge in rawEdges){
            val fromId = verticesList.indexOfFirst{it.id == edge.first}
            val toId = verticesList.indexOfFirst { it.id == edge.second}
            val w = edge.third
            if (fromId != -1 && toId != -1){
                matrix[fromId][toId] = w
                matrix[toId][fromId] = w
            }else {
                println("Предупреждение: Пропущено ребро (${edge.first} - ${edge.second}), так как одна из вершин не найдена.")
            }
        }
        return ParsedGraph(verticesList, matrix)
    }
}
