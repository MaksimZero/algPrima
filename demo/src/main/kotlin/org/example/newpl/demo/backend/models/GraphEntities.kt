package org.example.newpl.demo.backend.models

import javafx.scene.paint.Color

// Вершина
data class Vertex(
    val name: String,
    var x: Double,
    var y: Double,
    val radius: Double = 20.0,
    var color: Color = Color.LIGHTBLUE
) {
    // Функция проверки попадания клика мыши в круг вершины
    fun contains(px: Double, py: Double): Boolean {
        val dx = px - x
        val dy = py - y
        return dx * dx + dy * dy <= radius * radius
    }
}

// Ребро
data class Edge(
    val from: Vertex,
    val to: Vertex,
    val weight: Double
)

// Снимок шага для пошаговой визуализации
data class AlgorithmSnapshot(
    val stepNumber: Int,
    val mstVertices: Set<Vertex>,
    val mstEdges: Set<Edge>,
    val candidateEdges: Set<Edge>,
    val logMessage: String,
    val totalWeight: Double
)