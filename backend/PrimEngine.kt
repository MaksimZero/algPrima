package org.example.backend

import org.example.backend.models.Edge
import org.example.backend.models.Vertex
import org.example.backend.models.AlgorithmSnapshot

class PrimEngine {

    private fun convertMatrixForEdges(matrix: Array<DoubleArray>, vertices: List<Vertex>): List<Edge> {
        val edges = mutableListOf<Edge>()
        val n = matrix.size
        for (i in 0 until n) {
            for (j in (i + 1) until n) {
                val w = matrix[i][j]
                if (w != -1.0 && w != Double.POSITIVE_INFINITY) {
                    edges.add(Edge(vertices[i], vertices[j], w.toInt()))
                }
            }
        }
        return edges
    }

    fun calculatePrim(vertices: List<Vertex>, matrix: Array<DoubleArray>, startVertex: Vertex): List<AlgorithmSnapshot> {
        val snapshots = mutableListOf<AlgorithmSnapshot>()
        val edges = convertMatrixForEdges(matrix, vertices)

        val visitedVertices = mutableSetOf<Vertex>()
        val selectedEdges = mutableSetOf<Edge>()

        visitedVertices.add(startVertex)

        var stepCounter = 1

        while (vertices.size > visitedVertices.size) {
            val candidateEdges = edges.filter { edge ->
                val isFromVisited = edge.from in visitedVertices
                val isToVisited = edge.to in visitedVertices
                isFromVisited xor isToVisited
            }.toSet()

            if (candidateEdges.isEmpty()) {
                println("Ошибка: Граф несвязный! Невозможно построить единое MST.")
                break
            }

            snapshots.add(AlgorithmSnapshot(
                stepCounter,
                visitedVertices.toSet(),
                selectedEdges.toSet(),
                candidateEdges,
                "Шаг $stepCounter: Рассматриваем рёбра-кандидаты (${candidateEdges.size} шт.)",
                selectedEdges.sumOf { it.weight }
            ))

            val newEdge = candidateEdges.minByOrNull { it.weight }!!
            selectedEdges.add(newEdge)

            if (newEdge.from in visitedVertices) {
                visitedVertices.add(newEdge.to)
            } else {
                visitedVertices.add(newEdge.from)
            }

            snapshots.add(AlgorithmSnapshot(
                stepCounter,
                visitedVertices.toSet(),
                selectedEdges.toSet(),
                emptySet(),
                "Шаг $stepCounter: Добавлено минимальное ребро ${newEdge.from.id}-${newEdge.to.id} с весом ${newEdge.weight}",
                selectedEdges.sumOf { it.weight }
            ))

            stepCounter++
        }
        return snapshots
    }
}