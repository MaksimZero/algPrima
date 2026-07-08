package com.example.newpl.demo

import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.canvas.Canvas
import javafx.scene.canvas.GraphicsContext
import javafx.scene.input.KeyCode
import javafx.scene.input.MouseButton
import javafx.scene.layout.StackPane
import javafx.scene.paint.Color
import javafx.stage.Stage
import javafx.scene.control.TextInputDialog
import javafx.scene.control.Alert
import javafx.scene.control.Alert.AlertType
import javafx.scene.control.Button
import javafx.scene.control.Slider
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.geometry.Pos
import javafx.geometry.Insets

fun main() {
    Application.launch(App::class.java)
}

class App : Application() {
    private val vertexes = mutableListOf<Vertex>()
    private val edges = mutableListOf<Edge>()
    private var selectedVertex: Vertex? = null
    private var dragTarget: Vertex? = null
    private var dragOffsetX: Double = 0.0
    private var dragOffsetY: Double = 0.0
    private var isDragging = false
    private var pressTarget: Vertex? = null

    override fun start(stage: Stage) {
        val canvas = Canvas(800.0, 600.0)
        val gc = canvas.graphicsContext2D

        // для отслеживания нажатий клавиш, пока лишнее
        //canvas.isFocusTraversable = true

        canvas.setOnMousePressed { event -> // нажатие кнопки мыши
            val x = event.x
            val y = event.y

            pressTarget = vertexes.findLast { it.contains(x, y) } // круг на который нажали
            isDragging = false

            when (event.button) {
                MouseButton.PRIMARY -> { // ЛКМ
                    if (pressTarget == null) {
                        // в пустое место - создаём вершину
                        addVertex(x, y)
                        drawAll(gc)
                    } else {
                        // на вершину - начинаем перетаскивание
                        val target = pressTarget!!
                        dragTarget = target
                        dragOffsetX = x - target.x
                        dragOffsetY = y - target.y
                    }
                }
                MouseButton.MIDDLE -> { // СКМ
                    if (pressTarget != null) { // если нажали на вершину
                        val target = pressTarget!!
                        when {
                            selectedVertex == null -> { // нет выделенной вершины - выделяем
                                selectedVertex = target
                                drawAll(gc)
                            }
                            selectedVertex != target -> { // есть выделенная вершина и нажатие не на неё - добавляет ребро
                                val weight = requestWeight()
                                if (weight != null) { // если ввели число добавляем ребро
                                    addEdge(selectedVertex!!, target, weight)
                                    selectedVertex = null
                                    drawAll(gc)
                                } else { // если отменили ввод то вершину не добавляем
                                    selectedVertex = null
                                    drawAll(gc)
                                }
                            }
                            else -> { // есть выделенная вершина и эта та на которую мы нажали - снимаем выделение
                                selectedVertex = null
                                drawAll(gc)
                            }
                        }
                    }
                }

                MouseButton.SECONDARY -> { // ПКМ
                    if (pressTarget != null) { // нажали на вершину - удаляем её
                        removeVertex(pressTarget!!)
                        drawAll(gc)
                    }
                }
                else -> {} // остальные кнопки мыши, else нельзя удалить, это when
            }
        }

        canvas.setOnMouseDragged { event -> // перемещение мыши (перетаскивание вершины)
            if (event.button == MouseButton.PRIMARY) { // если при этом зажата ЛКМ то двигаем вершину
                val target = dragTarget
                if (target != null) {
                    target.x = event.x - dragOffsetX
                    target.y = event.y - dragOffsetY
                    isDragging = true
                    drawAll(gc)
                }
            }
        }

        canvas.setOnMouseReleased { event -> // отжатие кнопки
            if (event.button == MouseButton.PRIMARY) { // заканчиваем перетаскивание
                dragTarget = null
                isDragging = false
            }
        }

        val btnClear = Button("✕")
        val btnUndo = Button("↩")
        val btnRedo = Button("↪")
        val btnSave = Button("1")
        val btnLoad = Button("2")

        val buttons = listOf(btnClear, btnUndo, btnRedo, btnSave, btnLoad)
        buttons.forEach { button ->
            button.style = """
        -fx-min-width: 80px;
        -fx-min-height: 80px;
        -fx-max-width: 80px;
        -fx-max-height: 80px;
        -fx-pref-width: 80px;
        -fx-pref-height: 80px;
        -fx-font-size: 32px;
        -fx-background-color: #f0f0f0;
        -fx-border-color: #cccccc;
        -fx-border-width: 0 1px 0 0;
        -fx-cursor: hand;
    """
            button.isFocusTraversable = false
        }

        //btnLoad.style = btnLoad.style + "-fx-border-width: 0;"

        val slider = Slider(0.0, 100.0, 50.0)
        slider.prefWidth = 150.0
        slider.isShowTickLabels = true
        slider.isShowTickMarks = true
        slider.majorTickUnit = 25.0
        slider.style = "-fx-padding: 0 15px;"

        btnClear.setOnAction {
            clearGraph()
            drawAll(gc)
        }

        btnUndo.setOnAction {
            println("Отмена (пока не реализовано)")
        }

        btnRedo.setOnAction {
            println("Повтор (пока не реализовано)")
        }

        btnSave.setOnAction {
            println("Сохранение (пока не реализовано)")
        }

        btnLoad.setOnAction {
            println("Загрузка (пока не реализовано)")
        }

        val controlBox = HBox()
        controlBox.alignment = Pos.CENTER
        controlBox.padding = Insets(0.0)
        controlBox.children.addAll(buttons)
        controlBox.children.addAll(slider)
        controlBox.style = "-fx-background-color: #f0f0f0; -fx-border-color: #cccccc; -fx-border-width: 1px 0 0 0;"

        controlBox.setPrefHeight(100.0)
        controlBox.setMinHeight(100.0)
        controlBox.setMaxHeight(100.0)

        val root = BorderPane()
        root.center = canvas
        root.bottom = controlBox

        canvas.widthProperty().bind(root.widthProperty())
        canvas.heightProperty().bind(root.heightProperty().subtract(controlBox.heightProperty()))

        canvas.widthProperty().addListener { _, _, _ -> drawAll(gc) }
        canvas.heightProperty().addListener { _, _, _ -> drawAll(gc) }

        val scene = Scene(root, 800.0, 600.0)

        stage.title = "Визуализация работы алгоритма Прима"
        stage.scene = scene
        stage.isMaximized = true
        stage.show()
    }

    fun addVertex(x: Double, y: Double, radius: Double = 20.0, color: Color = Color.LIGHTBLUE): Vertex {
        val vertex = Vertex(x, y, radius, color)
        vertexes.add(vertex)
        return vertex
    }

    fun addEdge(circle1: Vertex, circle2: Vertex, weight: Double = 0.0): Edge {
        val edge = Edge(circle1, circle2, weight)
        edges.add(edge)
        return edge
    }

    fun removeVertex(vertex: Vertex) {
        val edgesToRemove = edges.filter {
            it.circle1 == vertex || it.circle2 == vertex
        }
        edges.removeAll(edgesToRemove)
        vertexes.remove(vertex)
        if (selectedVertex == vertex) {
            selectedVertex = null
        }
    }

    fun removeEdge(edge: Edge) {
        edges.remove(edge)
    }

    fun clearGraph() {
        vertexes.clear()
        edges.clear()
        selectedVertex = null
    }

    fun getVertices(): List<Vertex> = vertexes.toList()
    fun getEdges(): List<Edge> = edges.toList()
    fun findVertexAt(x: Double, y: Double): Vertex? {
        return vertexes.findLast { it.contains(x, y) }
    }


    private fun drawAll(gc: GraphicsContext) { // отрисовка холства
        gc.fill = Color.WHITE
        gc.fillRect(0.0, 0.0, gc.canvas.width, gc.canvas.height)

        // ребра
        for (edge in edges) {
            gc.stroke = Color.BLACK
            gc.lineWidth = 2.0
            gc.strokeLine(edge.circle1.x, edge.circle1.y, edge.circle2.x, edge.circle2.y)

            val midX = (edge.circle1.x + edge.circle2.x) / 2 // середина ребра, сбда встанет текст
            val midY = (edge.circle1.y + edge.circle2.y) / 2

            val dx = edge.circle2.x - edge.circle1.x // смещение, для определения угла наклона для текста
            val dy = edge.circle2.y - edge.circle1.y
            var angle = Math.atan2(dy, dx) // угол

            if (angle > Math.PI / 2 || angle < -Math.PI / 2) { // чтобы не вверх ногами был
                angle += Math.PI
            }

            // танцы с бубном для добавления текста
            gc.save()
            gc.translate(midX, midY)
            gc.rotate(Math.toDegrees(angle))
            gc.fill = Color.BLACK
            gc.font = javafx.scene.text.Font(14.0)
            gc.fillText("${edge.weight}", -10.0, -5.0)
            gc.restore()
        }

        // вершины
        for (vertex in vertexes) {
            gc.fill = vertex.color
            gc.fillOval(
                vertex.x - vertex.radius,
                vertex.y - vertex.radius,
                vertex.radius * 2,
                vertex.radius * 2
            )

            if (vertex == selectedVertex) {
                gc.stroke = Color.RED
                gc.lineWidth = 3.0
            } else {
                gc.stroke = Color.BLACK
                gc.lineWidth = 1.0
            }
            gc.strokeOval(
                vertex.x - vertex.radius,
                vertex.y - vertex.radius,
                vertex.radius * 2,
                vertex.radius * 2
            )
        }

        gc.fill = Color.GRAY
        gc.font = javafx.scene.text.Font(14.0)
    }
}

// окно для ввода веса ребра
private fun requestWeight(): Double? {
    val dialog = TextInputDialog("1.0")
    dialog.title = "Вес ребра"
    dialog.headerText = "Введите вес ребра"
    dialog.contentText = "Вес (положительное число):"

    val result = dialog.showAndWait()

    return if (result.isPresent) {
        val input = result.get().trim()
        try {
            val weight = input.toDouble()
            if (weight > 0) weight else {
                showError("Вес должен быть положительным числом!")
                null
            }
        } catch (e: NumberFormatException) {
            showError("Пожалуйста, введите корректное число!")
            null
        }
    } else {
        null
    }
}

private fun showError(message: String) {
    val alert = Alert(AlertType.ERROR)
    alert.title = "Ошибка"
    alert.headerText = null
    alert.contentText = message
    alert.showAndWait()
}

// класс вершины
data class Vertex(
    var x: Double,
    var y: Double,
    val radius: Double,
    var color: Color
) {
    fun contains(px: Double, py: Double): Boolean {
        val dx = px - x
        val dy = py - y
        return dx * dx + dy * dy <= radius * radius
    }
}

// класс ребра
data class Edge(
    val circle1: Vertex,
    val circle2: Vertex,
    var weight: Double = 0.0
)