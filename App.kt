package com.example.newpl.demo

import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.application.Application
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.canvas.Canvas
import javafx.scene.canvas.GraphicsContext
import javafx.scene.control.*
import javafx.scene.input.MouseButton
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.stage.Stage
import javafx.util.Duration
import org.example.backend.PrimEngine
import org.example.backend.models.AlgorithmSnapshot
import org.example.backend.models.Edge
import org.example.backend.models.Vertex
import org.example.backend.GraphParser

fun main() {
    Application.launch(App::class.java)
}

class App : Application() {
    private val vertexes = mutableListOf<Vertex>()
    private val edges = mutableListOf<Edge>()
    private var selectedVertex: Vertex? = null
    private var vertexCounter = 0
    private var dragTarget: Vertex? = null
    private var dragOffsetX = 0.0
    private var dragOffsetY = 0.0
    private var isDragging = false
    private var pressTarget: Vertex? = null

    private var snapshots: List<AlgorithmSnapshot> = emptyList()
    private var currentIndex = 0
    private var isPlaying = false
    private val timer = Timeline()
    private var speed: Long = 1000

    private lateinit var gc: GraphicsContext

    private val lblStep = Label("Шаг: 0 / 0")
    private val lblWeight = Label("Вес: 0.0")
    private val lblMessage = Label("Сообщение: ")
    private val lblEdges = Label("Рёбра в остове:")

    private val currentStep: AlgorithmSnapshot?
        get() = if (snapshots.isNotEmpty() && currentIndex in snapshots.indices) snapshots[currentIndex] else null

    private fun saveGraphToFile() {
        val fileChooser = javafx.stage.FileChooser()
        fileChooser.title = "Сохранить граф в файл"
        fileChooser.extensionFilters.add(
            javafx.stage.FileChooser.ExtensionFilter("Текстовые файлы (*.txt)", "*.txt")
        )

        val stage = gc.canvas.scene.window as javafx.stage.Stage
        val file = fileChooser.showSaveDialog(stage)

        if (file != null) {
            try {
                file.bufferedWriter().use { writer ->
                    for (vertex in vertexes) {
                        writer.write("pos ${vertex.name} ${vertex.x} ${vertex.y}\n")
                    }

                    for (edge in edges) {
                        writer.write("${edge.from.name} ${edge.to.name} ${edge.weight}\n")
                    }
                }

                val alert = Alert(Alert.AlertType.INFORMATION)
                alert.title = "Успешно"
                alert.headerText = null
                alert.contentText = "Граф успешно сохранён в файл: ${file.name}"
                alert.showAndWait()

            } catch (e: Exception) {
                showError("Не удалось сохранить файл: ${e.message}")
            }
        }
    }

    private fun loadGraphFromFile() {
        val fileChooser = javafx.stage.FileChooser()
        fileChooser.title = "Открыть файл графа"
        fileChooser.extensionFilters.add(
            javafx.stage.FileChooser.ExtensionFilter("Текстовые файлы (*.txt)", "*.txt")
        )

        val stage = gc.canvas.scene.window as javafx.stage.Stage
        val file = fileChooser.showOpenDialog(stage)

        if (file != null) {
            try {
                val parsedGraph = GraphParser().parseFile(file.absolutePath)
                clearGraph()
                vertexes.addAll(parsedGraph.vertices)
                vertexCounter = vertexes.size

                val n = parsedGraph.vertices.size
                for (i in 0 until n) {
                    for (j in i + 1 until n) { // Проверяем только верхний треугольник, чтобы не дублировать рёбра
                        val weight = parsedGraph.matrix[i][j]
                        if (weight != -1.0 && weight != Double.POSITIVE_INFINITY) {
                            val fromVertex = parsedGraph.vertices[i]
                            val toVertex = parsedGraph.vertices[j]
                            addEdge(fromVertex, toVertex, weight)
                        }
                    }
                }
                updateUI()
            } catch (e: Exception) {
                showError("Не удалось прочитать файл: ${e.message}")
            }
        }
    }



    fun addVertex(x: Double, y: Double, radius: Double = 20.0, color: Color = Color.LIGHTBLUE): Vertex {
        val name = "V${++vertexCounter}"
        val vertex = Vertex(name, x, y, radius, color)
        vertexes.add(vertex)
        return vertex
    }

    fun addEdge(from: Vertex, to: Vertex, weight: Double): Edge {
        val edge = Edge(from, to, weight)
        edges.add(edge)
        return edge
    }

    fun removeVertex(vertex: Vertex) {
        val edgesToRemove = edges.filter { it.from == vertex || it.to == vertex }
        edges.removeAll(edgesToRemove)
        vertexes.remove(vertex)
        if (selectedVertex == vertex) selectedVertex = null
    }

    fun removeEdge(edge: Edge) {
        edges.remove(edge)
    }

    fun clearGraph() {
        vertexes.clear()
        edges.clear()
        selectedVertex = null
        vertexCounter = 0
        snapshots = emptyList()
        currentIndex = 0
        reset()
        updateUI()
    }

    private fun loadSnapshots(steps: List<AlgorithmSnapshot>) {
        snapshots = steps
        currentIndex = 0
        reset()
        updateUI()
    }

    private fun stepForward() {
        if (snapshots.isEmpty()) {
            runAlgorithm(null)
            updateUI()
            return
        }
        if (currentIndex < snapshots.size - 1) {
            currentIndex++
            updateUI()
        } else {
            pause()
        }
    }

    private fun stepBackward() {
        if (snapshots.isEmpty()) return
        if (currentIndex > 0) {
            currentIndex--
            updateUI()
        }
    }

    private fun play() {
        if (snapshots.isEmpty() || currentIndex >= snapshots.size - 1) return
        isPlaying = true
        timer.stop()
        timer.keyFrames.clear()
        timer.keyFrames.add(KeyFrame(Duration.millis(speed.toDouble()), { stepForward() }))
        timer.cycleCount = Timeline.INDEFINITE
        timer.play()
    }

    private fun pause() {
        isPlaying = false
        timer.stop()
    }

    private fun reset() {
        pause()
        currentIndex = 0
        updateUI()
    }

    private fun clearMST() {
        pause()
        currentIndex = 0
        //currentStep = null
        snapshots = emptyList()
        updateUI()
        drawAll(gc)
    }

    private fun runAlgorithm(startVertex: Vertex?) {
        if (vertexes.isEmpty()) {
            println("Граф пуст!")
            return
        }
        val start = startVertex ?: vertexes.firstOrNull()
        if (start == null) {
            println("Нет стартовой вершины")
            return
        }

        val n = vertexes.size
        val matrix = Array(n) { i ->
            DoubleArray(n) { j ->
                if (i == j) -1.0 else Double.POSITIVE_INFINITY
            }
        }
        for (edge in edges) {
            val i = vertexes.indexOf(edge.from)
            val j = vertexes.indexOf(edge.to)
            if (i != -1 && j != -1) {
                matrix[i][j] = edge.weight
                matrix[j][i] = edge.weight
            }
        }

        val engine = PrimEngine()
        val steps = engine.calculatePrim(vertexes, matrix, start)
        if (steps.isEmpty()) {
            println("Алгоритм не смог построить остов (граф несвязный)")
            return
        }
        loadSnapshots(steps)
    }

    private fun updateUI() {
        val step = currentStep
        if (step != null) {
            lblStep.text = "Шаг: ${currentIndex + 1} / ${snapshots.size}"
            lblWeight.text = "Вес: ${step.totalWeight}"
            lblMessage.text = "Сообщение: ${step.logMessage}"
            val edgesStr = step.mstEdges.joinToString(", ") { "${it.from.name}–${it.to.name} (${it.weight})" }
            lblEdges.text = "Рёбра в остове: ${if (edgesStr.isNotEmpty()) edgesStr else "—"}"
        } else {
            lblStep.text = "Шаг: 0 / ${snapshots.size}"
            lblWeight.text = "Вес: 0.0"
            lblMessage.text = "Сообщение: "
            lblEdges.text = "Рёбра в остове: —"
        }
        drawAll(gc)
    }

    private fun distanceToPoint(px: Double, py: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val dx = x2 - x1
        val dy = y2 - y1
        val lenSq = dx * dx + dy * dy

        if (lenSq == 0.0) {
            return Math.hypot(px - x1, py - y1)
        }

        var t = ((px - x1) * dx + (py - y1) * dy) / lenSq
        t = t.coerceIn(0.0, 1.0)

        val projX = x1 + t * dx
        val projY = y1 + t * dy

        return Math.hypot(px - projX, py - projY)
    }

    private fun drawAll(gc: GraphicsContext) {
        gc.fill = Color.WHITE
        gc.fillRect(0.0, 0.0, gc.canvas.width, gc.canvas.height)

        val step = currentStep
        val mstEdges = step?.mstEdges ?: emptySet()
        val candidateEdges = step?.candidateEdges ?: emptySet()
        val mstVertices = step?.mstVertices ?: emptySet()

        for (edge in edges) {
            val isMst = mstEdges.any {
                (it.from == edge.from && it.to == edge.to) ||
                        (it.from == edge.to && it.to == edge.from)
            }
            val isCandidate = candidateEdges.any {
                (it.from == edge.from && it.to == edge.to) ||
                        (it.from == edge.to && it.to == edge.from)
            }
            gc.lineWidth = if (isMst) 4.0 else 2.0
            gc.stroke = when {
                isMst -> Color.GREEN
                isCandidate -> Color.ORANGE
                else -> Color.BLACK
            }
            if (isCandidate) {
                gc.setLineDashes(10.0, 5.0)
            } else {
                gc.setLineDashes()
            }

            gc.strokeLine(edge.from.x, edge.from.y, edge.to.x, edge.to.y)

            val midX = (edge.from.x + edge.to.x) / 2
            val midY = (edge.from.y + edge.to.y) / 2
            val dx = edge.to.x - edge.from.x
            val dy = edge.to.y - edge.from.y
            var angle = Math.atan2(dy, dx)
            if (angle > Math.PI / 2 || angle < -Math.PI / 2) {
                angle += Math.PI
            }

            gc.save()
            gc.translate(midX, midY)
            gc.rotate(Math.toDegrees(angle))
            gc.fill = Color.BLACK
            gc.font = javafx.scene.text.Font(14.0)
            gc.fillText("${edge.weight}", -10.0, -5.0)
            gc.restore()
        }

        for (vertex in vertexes) {
            val isInMst = vertex in mstVertices
            gc.fill = if (isInMst) Color.GREEN else vertex.color
            gc.fillOval(
                vertex.x - vertex.radius,
                vertex.y - vertex.radius,
                vertex.radius * 2,
                vertex.radius * 2
            )

            gc.stroke = when {
                vertex == selectedVertex -> Color.RED
                isInMst -> Color.DARKGREEN
                else -> Color.BLACK
            }
            gc.lineWidth = if (vertex == selectedVertex) 3.0 else 1.0
            gc.strokeOval(
                vertex.x - vertex.radius,
                vertex.y - vertex.radius,
                vertex.radius * 2,
                vertex.radius * 2
            )
        }

        gc.fill = Color.WHITE
        gc.font = javafx.scene.text.Font(14.0)
        for (vertex in vertexes) {
            gc.fillText(vertex.name, vertex.x - 10, vertex.y + 5)
        }
    }

    override fun start(stage: Stage) {
        val canvas = Canvas(800.0, 600.0)
        gc = canvas.graphicsContext2D

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
                        clearMST()
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
                                    clearMST()
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
                    // Сначала проверяем вершину
                    if (pressTarget != null) {
                        removeVertex(pressTarget!!)
                        clearMST()
                        drawAll(gc)
                    } else {
                        // Если не вершина, проверяем ребро
                        val clickedEdge = edges.findLast { edge ->
                            distanceToPoint(x, y, edge.from.x, edge.from.y, edge.to.x, edge.to.y) < 10.0
                        }
                        if (clickedEdge != null) {
                            removeEdge(clickedEdge)
                            clearMST()
                            drawAll(gc)
                        }
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
        val btnSave = Button().apply {
            val image = javafx.scene.image.Image("file:src/main/resources/images/save.png")
            graphic = javafx.scene.image.ImageView(image).apply {
                fitWidth = 40.0
                fitHeight = 40.0
                isPreserveRatio = true
            }
            contentDisplay = javafx.scene.control.ContentDisplay.GRAPHIC_ONLY
        }

        val btnLoad = Button().apply {
            val image = javafx.scene.image.Image("file:src/main/resources/images/load.png")
            graphic = javafx.scene.image.ImageView(image).apply {
                fitWidth = 40.0
                fitHeight = 40.0
                isPreserveRatio = true
            }
            contentDisplay = javafx.scene.control.ContentDisplay.GRAPHIC_ONLY
        }
        val btnPlay = Button("▶")
        val btnPause = Button("⏸")
        val btnStop = Button("⏹")

        val allButtons = listOf(
            btnClear, btnSave, btnLoad,
            btnUndo, btnRedo,
            btnPlay, btnPause, btnStop
        )

        allButtons.forEach { button ->
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



        val slider = Slider(200.0, 3000.0, 1000.0)
        slider.prefWidth = 150.0
        slider.isShowTickLabels = true
        slider.isShowTickMarks = true
        slider.majorTickUnit = 500.0
        slider.style = "-fx-padding: 0 15px;"
        slider.valueProperty().addListener { _, _, newValue ->
            speed = newValue.toLong()
            if (isPlaying) {
                pause()
                play()
            }
        }

        btnClear.setOnAction {
            clearGraph()
            drawAll(gc)
        }

        btnSave.setOnAction {
            saveGraphToFile()
        }

        btnLoad.setOnAction {
            loadGraphFromFile()
        }

        btnPlay.setOnAction {
            if (snapshots.isEmpty()) {
                runAlgorithm(null)
            }
            if (snapshots.isNotEmpty() && currentIndex < snapshots.size - 1) {
                play()
            }

        }
        btnPause.setOnAction { pause() }
        btnStop.setOnAction { reset() }
        btnUndo.setOnAction { stepBackward() }
        btnRedo.setOnAction { stepForward() }

        val infoPanel = VBox(10.0)
        infoPanel.padding = Insets(15.0)
        infoPanel.style = "-fx-background-color: #f4f4f4; -fx-border-color: #cccccc;"
        lblMessage.isWrapText = true
        lblEdges.isWrapText = true
        infoPanel.children.addAll(
            lblStep, lblWeight, lblMessage,
            Label("---"),
            lblEdges
        )
        infoPanel.prefWidth = 250.0

        val controlBox = HBox(10.0)
        controlBox.alignment = Pos.CENTER
        controlBox.padding = Insets(0.0)
        controlBox.children.addAll(allButtons + slider)
        controlBox.style = "-fx-background-color: #f0f0f0; -fx-border-color: #cccccc; -fx-border-width: 1px 0 0 0;"
        controlBox.prefHeight = 100.0
        controlBox.minHeight = 100.0
        controlBox.maxHeight = 100.0

        val root = BorderPane()
        root.center = canvas
        root.bottom = controlBox
        root.right = infoPanel

        canvas.widthProperty().bind(root.widthProperty().subtract(infoPanel.widthProperty()))
        canvas.heightProperty().bind(root.heightProperty().subtract(controlBox.heightProperty()))

        canvas.widthProperty().addListener { _, _, _ -> drawAll(gc) }
        canvas.heightProperty().addListener { _, _, _ -> drawAll(gc) }

        val scene = Scene(root, 1024.0, 768.0)
        stage.title = "Визуализация алгоритма Прима"
        stage.scene = scene
        stage.show()
    }

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
        } else null
    }

    private fun showError(message: String) {
        val alert = Alert(Alert.AlertType.ERROR)
        alert.title = "Ошибка"
        alert.headerText = null
        alert.contentText = message
        alert.showAndWait()
    }
}