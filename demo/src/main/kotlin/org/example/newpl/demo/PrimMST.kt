package org.example.newpl.demo

fun primMST(matrix: Array<DoubleArray>, startVertex: Int = 0): List<Pair<Int, Int>> {
    val n = matrix.size
    if (n == 0) throw IllegalArgumentException("Матрица не может быть пустой")
    if (!isConnected(matrix)) throw IllegalArgumentException("Граф несвязный")

    val visited = BooleanArray(n)
    val minWeight = DoubleArray(n) { Double.POSITIVE_INFINITY }
    val parent = IntArray(n) { -1 }
    val result = mutableListOf<Pair<Int, Int>>()

    minWeight[startVertex] = 0.0

    for (i in 0 until n) {
        var u = -1
        var min = Double.POSITIVE_INFINITY
        for (j in 0 until n) {
            if (!visited[j] && minWeight[j] < min) {
                min = minWeight[j]
                u = j
            }
        }

        if (u == -1) break

        visited[u] = true

        if (parent[u] != -1) {
            result.add(Pair(parent[u], u))
        }

        for (v in 0 until n) {
            if (!visited[v] && matrix[u][v] < minWeight[v]) {
                minWeight[v] = matrix[u][v]
                parent[v] = u
            }
        }
    }

    return result
}

fun isConnected(matrix: Array<DoubleArray>): Boolean {
    val n = matrix.size
    if (n == 0) return true
    if (n == 1) return true

    val visited = BooleanArray(n)
    val stack = mutableListOf(0)
    visited[0] = true

    while (stack.isNotEmpty()) {
        val u = stack.removeAt(stack.size - 1)
        for (v in 0 until n) {
            if (!visited[v] && matrix[u][v] < Double.POSITIVE_INFINITY) {
                visited[v] = true
                stack.add(v)
            }
        }
    }

    return visited.all { it }
}