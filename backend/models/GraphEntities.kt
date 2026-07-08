package org.example.backend.models

data class Vertex(
    val id: String,
    var X: Double,
    var Y: Double
    )

data class Edge(
    val from: Vertex,
    val to: Vertex,
    val weight: Int
)

data class AlgorithmSnapshot(
    val stepNumber: Int,
    val mstVertices: Set<Vertex>,
    val mstEdge: Set<Edge>,
    val candidatesEdges: Set<Edge>,
    val logMessage: String,
    val totalWeight: Int
)