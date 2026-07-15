package org.example.newpl.demo

import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.application.Application
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.canvas.Canvas
import javafx.scene.canvas.GraphicsContext
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.MouseButton
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.stage.FileChooser
import javafx.stage.Stage
import javafx.util.Duration
import org.example.newpl.demo.backend.GraphExporter
import org.example.newpl.demo.backend.GraphParser
import org.example.newpl.demo.backend.PrimEngine
import org.example.newpl.demo.backend.models.AlgorithmSnapshot
import org.example.newpl.demo.backend.models.Edge
import org.example.newpl.demo.backend.models.Vertex
import java.io.File
import kotlin.apply
import kotlin.jvm.java

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

    private lateinit var scrollPane: ScrollPane

    private fun saveGraphToFile() {
        val fileChooser = FileChooser()
        fileChooser.title = "Сохранить граф в файл"
        fileChooser.extensionFilters.add(
            FileChooser.ExtensionFilter("Текстовые файлы (*.txt)", "*.txt")
        )

        val stage = gc.canvas.scene.window as Stage
        val file = fileChooser.showSaveDialog(stage)

        if (file != null) {
            try {
                val n = vertexes.size
                val generatedMatrix = Array(n) { i ->
                    DoubleArray(n) { j ->
                        if (i == j) -1.0 else Double.POSITIVE_INFINITY
                    }
                }
                for (edge in edges) {
                    val i = vertexes.indexOf(edge.from)
                    val j = vertexes.indexOf(edge.to)
                    if (i != -1 && j != -1) {
                        generatedMatrix[i][j] = edge.weight
                        generatedMatrix[j][i] = edge.weight
                    }
                }
                var snapshotsToSend: List<AlgorithmSnapshot>? = null

                if (snapshots.isNotEmpty()) {
                    val alert = Alert(Alert.AlertType.CONFIRMATION).apply {
                        title = "Выбор режима сохранения"
                        headerText = "Обнаружена история шагов алгоритма Прима."
                        contentText = "Желаете сохранить файл вместе с пошаговой визуализацией графа?"

                        val btnYes = ButtonType("Да")
                        val btnNo = ButtonType("Нет")
                        val btnCancel = ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE)

                        buttonTypes.setAll(btnYes, btnNo, btnCancel)
                    }

                    val result = alert.showAndWait()
                    if (result.isPresent) {
                        when (result.get().text) {
                            "Да" -> snapshotsToSend = snapshots
                            "Нет" -> snapshotsToSend = null
                            "Отмена" -> return
                        }
                    }
                }

                GraphExporter().saveGraphToFile(
                    filePath = file.absolutePath,
                    vertices = vertexes,
                    matrix = generatedMatrix,
                    mstSteps = snapshotsToSend
                )

                Alert(Alert.AlertType.INFORMATION).apply {
                    title = "Успех"
                    headerText = null
                    contentText = "Граф успешно сохранен!"
                    showAndWait()
                }

                updateCanvasSize()

            } catch (e: Exception) {
                showError("Не удалось сохранить файл: ${e.message}")
            }
        }
    }

    private fun loadGraphFromFile() {
        val fileChooser = FileChooser().apply {
            title = "Открыть файл графа"
            extensionFilters.add(FileChooser.ExtensionFilter("Текстовые файлы (*.txt)", "*.txt"))
        }

        val stage = gc.canvas.scene.window as Stage
        val file = fileChooser.showOpenDialog(stage)

        if (file != null) {
            try {
                val parsedGraph = GraphParser().parseFile(file.absolutePath)

                clearGraph()
                vertexes.addAll(parsedGraph.vertices)
                vertexCounter = vertexes.size

                val n = parsedGraph.vertices.size
                for (i in 0 until n) {
                    for (j in i + 1 until n) {
                        val weight = parsedGraph.matrix[i][j]
                        if (weight != -1.0 && weight != Double.POSITIVE_INFINITY) {
                            val fromVertex = parsedGraph.vertices[i]
                            val toVertex = parsedGraph.vertices[j]
                            addEdge(fromVertex, toVertex, weight)
                        }
                    }
                }

                if (parsedGraph.steps.isNotEmpty()) {
                    val btnYes = ButtonType("Да", ButtonBar.ButtonData.YES)
                    val btnNo = ButtonType("Нет", ButtonBar.ButtonData.NO)

                    val alert = Alert(Alert.AlertType.CONFIRMATION).apply {
                        title = "Загрузка истории шагов"
                        headerText = "В файле обнаружена записанная история работы алгоритма."
                        contentText = "Хотите загрузить её в плеер для пошагового просмотра?"
                        buttonTypes.setAll(btnYes, btnNo)
                    }

                    val result = alert.showAndWait()
                    if (result.isPresent && result.get() == btnYes) {
                        loadSnapshots(parsedGraph.steps)
                        currentIndex = 0
                    } else {
                        snapshots = emptyList()
                        currentIndex = 0
                    }
                }

                updateCanvasSize()

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
            showError("Граф пуст!")
            return
        }
        val start = startVertex ?: vertexes.firstOrNull()
        if (start == null) {
            showError("Нет стартовой вершины")
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
            showError("Алгоритм не смог построить остов (граф несвязный)")
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

            // отрисовка веса ребра
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
            gc.font = Font(14.0)
            gc.fillText("${edge.weight}", -10.0, -5.0)
            gc.restore()
        }

        // отрисовка вершин
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
        gc.font = Font(14.0)
        for (vertex in vertexes) {
            gc.fillText(vertex.name, vertex.x - 10, vertex.y + 5)
        }
    }

    private fun logSizes() {
        val scene = gc.canvas.scene
        val root = scene?.root as? BorderPane

        println("=" .repeat(50))
        println("SCENE: ${scene?.width} x ${scene?.height}")

        if (root != null) {
            println("ROOT: ${root.width} x ${root.height}")

            // Размеры панелей
            val rightPanel = root.right as? VBox
            val bottomPanel = root.bottom as? HBox

            println("RIGHT PANEL: ${rightPanel?.width} x ${rightPanel?.height}")
            println("BOTTOM PANEL: ${bottomPanel?.width} x ${bottomPanel?.height}")

            // Вычисляем доступную область
            val availableWidth = root.width - (rightPanel?.width ?: 0.0)
            val availableHeight = root.height - (bottomPanel?.height ?: 0.0)

            println("AVAILABLE: $availableWidth x $availableHeight")
            println("CANVAS: ${gc.canvas.width} x ${gc.canvas.height}")
            //println("VIEWPORT: ${scrollPane.viewportBounds.width} x ${scrollPane.viewportBounds.height}")
        }
        println("=" .repeat(50))
    }

    private fun updateCanvasSize() {
        logSizes()
        val canvas = gc.canvas
        val scene = gc.canvas.scene
        val root = scene?.root as? BorderPane

        // Получаем доступную область
        val rightPanel = root?.right as? VBox
        val bottomPanel = root?.bottom as? HBox
        val availableWidth = (root?.width ?: 0.0) - (rightPanel?.width ?: 0.0)
        val availableHeight = (root?.height ?: 0.0) - (bottomPanel?.height ?: 0.0)

        val viewportWidth = if (::scrollPane.isInitialized) scrollPane.viewportBounds.width else 800.0
        val viewportHeight = if (::scrollPane.isInitialized) scrollPane.viewportBounds.height else 600.0

        // Используем доступную область как минимум
        val minWidth = availableWidth.coerceAtLeast(viewportWidth)
        val minHeight = availableHeight.coerceAtLeast(viewportHeight)

        val padding = 100.0
        var minX = Double.MAX_VALUE
        var minY = Double.MAX_VALUE
        var maxX = Double.MIN_VALUE
        var maxY = Double.MIN_VALUE

        for (v in vertexes) {
            if (v.x < minX) minX = v.x
            if (v.y < minY) minY = v.y
            if (v.x > maxX) maxX = v.x
            if (v.y > maxY) maxY = v.y
        }

        val width = maxX - minX + padding * 2
        val height = maxY - minY + padding * 2

        println("\n\n\n$minWidth x $minHeight\n\n\n")
        println("${canvas.width} x ${canvas.height}")

        // Берем максимум между вычисленным размером и доступной областью
        canvas.width = width.coerceAtLeast(minWidth)
        canvas.height = height.coerceAtLeast(minHeight)

        // Смещаем вершины, чтобы они были в центре холста
        if (vertexes.isNotEmpty()) {
            val offsetX = padding - minX
            val offsetY = padding - minY
            for (v in vertexes) {
                v.x += offsetX
                v.y += offsetY
            }
        }
    }

    override fun start(stage: Stage) {
        val canvas = Canvas(800.0, 600.0)
        gc = canvas.graphicsContext2D

        val scrollPane = ScrollPane(canvas)
        scrollPane.style = "-fx-background-color: white;"
        scrollPane.isPannable = false
        scrollPane.hbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED
        scrollPane.vbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED

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
                        updateCanvasSize()
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

        canvas.setOnMouseDragged { event ->
            if (event.button == MouseButton.PRIMARY) {
                val target = dragTarget
                if (target != null) {
                    target.x = event.x - dragOffsetX
                    target.y = event.y - dragOffsetY
                    isDragging = true
                    drawAll(gc)

                    // Авто-скролл при перетаскивании к границам
                    val viewport = scrollPane.viewportBounds
                    val hValue = scrollPane.hvalue
                    val vValue = scrollPane.vvalue
                    val hMax = scrollPane.hmax
                    val vMax = scrollPane.vmax
                    val hMin = scrollPane.hmin
                    val vMin = scrollPane.vmin
                    val scrollSpeed = 0.02

                    val mouseX = event.x
                    val mouseY = event.y

                    if (mouseX < 30 && hValue > hMin) {
                        scrollPane.hvalue = (hValue - scrollSpeed).coerceAtLeast(hMin)
                    } else if (mouseX > viewport.width - 30 && hValue < hMax) {
                        scrollPane.hvalue = (hValue + scrollSpeed).coerceAtMost(hMax)
                    }

                    if (mouseY < 30 && vValue > vMin) {
                        scrollPane.vvalue = (vValue - scrollSpeed).coerceAtLeast(vMin)
                    } else if (mouseY > viewport.height - 30 && vValue < vMax) {
                        scrollPane.vvalue = (vValue + scrollSpeed).coerceAtMost(vMax)
                    }

                    // Расширение холста при перетаскивании за границы
                    val canvasWidth = canvas.width
                    val canvasHeight = canvas.height
                    var needUpdate = false

                    if (target.x < 50) {
                        val newWidth = canvasWidth + 50
                        canvas.width = newWidth
                        target.x += 50
                        for (v in vertexes) {
                            if (v != target) v.x += 50
                        }
                        needUpdate = true
                    } else if (target.x > canvasWidth - 50) {
                        canvas.width = canvasWidth + 50
                        needUpdate = true
                    }

                    if (target.y < 50) {
                        val newHeight = canvasHeight + 50
                        canvas.height = newHeight
                        target.y += 50
                        for (v in vertexes) {
                            if (v != target) v.y += 50
                        }
                        needUpdate = true
                    } else if (target.y > canvasHeight - 50) {
                        canvas.height = canvasHeight + 50
                        needUpdate = true
                    }

                    if (needUpdate) {
                        drawAll(gc)
                    }
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
            val resourceUrl = org.example.newpl.demo.App::class.java.getResource("/images/save.png")
            if (resourceUrl != null) {
                val image = Image(resourceUrl.toExternalForm())
                graphic = ImageView(image).apply {
                    fitWidth = 40.0
                    fitHeight = 40.0
                    isPreserveRatio = true
                }
            } else {
                text = "💾"
            }
            contentDisplay = ContentDisplay.GRAPHIC_ONLY
        }

        val btnLoad = Button().apply {
            val resourceUrl = org.example.newpl.demo.App::class.java.getResource("/images/load.png")
            if (resourceUrl != null) {
                val image = Image(resourceUrl.toExternalForm())
                graphic = ImageView(image).apply {
                    fitWidth = 40.0
                    fitHeight = 40.0
                    isPreserveRatio = true
                }
            } else {
                text = "📂"
            }
            contentDisplay = ContentDisplay.GRAPHIC_ONLY
        }

        val btnPlay = Button("▶")
        val btnPause = Button("⏸")
        val btnStop = Button("⏹")

        val btnAbout = Button("ℹ️").apply {
            style = """
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
            isFocusTraversable = false
            tooltip = Tooltip("О разработчиках")
            setOnAction { showAboutDialog() }
        }

        val allButtons = listOf(
            btnClear, btnSave, btnLoad,
            btnUndo, btnRedo,
            btnPlay, btnPause, btnStop,
            btnAbout
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

        // ==================== ПАНЕЛИ ====================

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
        root.center = scrollPane
        root.bottom = controlBox
        root.right = infoPanel

        root.widthProperty().addListener { _, _, _ ->
            updateCanvasSize()
            drawAll(gc)
        }
        root.heightProperty().addListener { _, _, _ ->
            updateCanvasSize()
            drawAll(gc)
        }

        updateCanvasSize()

        val scene = Scene(root, 1024.0, 768.0)
        stage.title = "Визуализация алгоритма Прима"
        stage.scene = scene
        stage.show()
    }

    // ==================== ДИАЛОГИ ====================

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
    private fun showAboutDialog() {
        val alert = Alert(Alert.AlertType.INFORMATION)
        alert.title = "О разработчиках"
        alert.headerText = "Визуализация алгоритма Прима"

        val developerInfo = """
        Бригада номер 6
        Разработчики:

           Зеров Максим
           Группа: 4343

           Семенчик Влад
           Группа: 4343

           Геращенкова Мария
           Группа: 4343
           

        Программа предназначена для визуализации работы 
        алгоритма Прима на взвешенных неориентированных графах.

     """.trimIndent()

        val vbox = VBox(15.0)
        vbox.padding = Insets(15.0)
        vbox.alignment = Pos.TOP_LEFT

        val label = Label(developerInfo)
        label.isWrapText = true
        label.style = "-fx-font-size: 14px;"
        vbox.children.add(label)

        val separator = Separator()
        separator.style = "-fx-padding: 5px 0;"
        vbox.children.add(separator)
        try {
            val memePaths = listOf(
                "src/main/resources/images/meme.jpg",
            )

            var imageFile: File? = null
            for (path in memePaths) {
                val f = File(path)
                if (f.exists()) {
                    imageFile = f
                    break
                }
            }

            if (imageFile == null) {
                val resourceUrl = org.example.newpl.demo.App::class.java.getResource("/images/meme.jpg")
                if (resourceUrl != null) {
                    val image = Image(resourceUrl.toExternalForm())
                    val imageView = ImageView(image)
                    imageView.fitWidth = 400.0
                    imageView.fitHeight = 300.0
                    imageView.isPreserveRatio = true
                    imageView.style = "-fx-border-color: #cccccc; -fx-border-width: 1px; -fx-border-radius: 8px;"

                    val memeLabel = Label("Любимый мем разработчиков:")
                    memeLabel.style = "-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #333333;"

                    val memeBox = VBox(5.0)
                    memeBox.alignment = Pos.CENTER
                    memeBox.children.addAll(memeLabel, imageView)
                    vbox.children.add(memeBox)

                    alert.dialogPane.content = vbox
                    alert.dialogPane.style = "-fx-min-width: 500px; -fx-min-height: 450px;"
                    alert.isResizable = true
                    alert.showAndWait()
                    return
                }
            }

            if (imageFile != null) {
                val image = Image(imageFile!!.toURI().toString())
                val imageView = ImageView(image)
                imageView.fitWidth = 400.0
                imageView.fitHeight = 300.0
                imageView.isPreserveRatio = true
                imageView.style = "-fx-border-color: #cccccc; -fx-border-width: 1px; -fx-border-radius: 8px;"

                val memeLabel = Label("Любимый мем разработчиков:")
                memeLabel.style = "-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #333333;"

                val memeBox = VBox(5.0)
                memeBox.alignment = Pos.CENTER
                memeBox.children.addAll(memeLabel, imageView)

                vbox.children.add(memeBox)
            } else {
                val errorLabel = Label("⚠️ Мем не найден. Положите картинку в resources/images/meme.jpg")
                errorLabel.style = "-fx-text-fill: #999999; -fx-font-size: 12px;"
                vbox.children.add(errorLabel)
            }
        } catch (e: Exception) {
            val errorLabel = Label("⚠️ Ошибка загрузки мема: ${e.message}")
            errorLabel.style = "-fx-text-fill: #999999; -fx-font-size: 12px;"
            vbox.children.add(errorLabel)
        }

        alert.dialogPane.content = vbox
        alert.dialogPane.style = "-fx-min-width: 500px; -fx-min-height: 450px;"
        alert.isResizable = true

        alert.showAndWait()
    }
}