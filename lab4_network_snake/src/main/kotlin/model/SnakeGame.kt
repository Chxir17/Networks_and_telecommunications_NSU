package onlinesnake.model

import onlinesnake.dto.Coord
import onlinesnake.dto.Direction
import onlinesnake.dto.Snake
import onlinesnake.dto.SnakeState
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

class SnakeGame(
    private val fieldWidth: Int,
    private val fieldHeight: Int,
    private val foodStatic: Int,
    snakeList: List<Snake>,
    foodList: List<Coord>,
    scores: List<IDWithScore>
) {
    constructor(fieldWidth: Int, fieldHeight: Int, foodStatic: Int) :
            this(fieldWidth, fieldHeight, foodStatic, listOf(), listOf(), listOf())

    private object FieldConstraints {
        const val MIN_FIELD_WIDTH: Int = 10
        const val MAX_FIELD_WIDTH: Int = 100

        const val MIN_FIELD_HEIGHT: Int = 10
        const val MAX_FIELD_HEIGHT: Int = 100

        const val MIN_FOOD_STATIC: Int = 0
        const val MAX_FOOD_STATIC: Int = 100
    }
    init {
        if (fieldWidth < FieldConstraints.MIN_FIELD_WIDTH || FieldConstraints.MAX_FIELD_WIDTH < fieldWidth) {
            throw IllegalArgumentException("expected: ${FieldConstraints.MIN_FIELD_WIDTH} < field width < " +
                   "${FieldConstraints.MAX_FIELD_WIDTH}, but got $fieldWidth")
        }
        if (fieldHeight < FieldConstraints.MIN_FIELD_HEIGHT || FieldConstraints.MAX_FIELD_HEIGHT < fieldHeight) {
            throw IllegalArgumentException("expected: ${FieldConstraints.MIN_FIELD_HEIGHT} < field height < " +
                    "${FieldConstraints.MAX_FIELD_HEIGHT}, but got $fieldWidth")
        }
        if (foodStatic < FieldConstraints.MIN_FOOD_STATIC || FieldConstraints.MAX_FOOD_STATIC < foodStatic) {
            throw IllegalArgumentException("expected: ${FieldConstraints.MIN_FOOD_STATIC} < food static < " +
                    "${FieldConstraints.MAX_FOOD_STATIC}, but got $foodStatic")
        }
    }

    private val fieldArraySize = fieldWidth * fieldHeight
    private val snakeGameField = IntArray(fieldArraySize)
    private val realSnakesMapper = RealSnakesMapper()
    private val foodSet: MutableSet<Int> = mutableSetOf()

    private val snakeIDToScore = mutableMapOf<Int, Int>()

    init {
        snakeList.forEach {
            realSnakesMapper.addExistingSnake(
                it.playerID,
                RealSnake(
                    fieldWidth = fieldWidth,
                    fieldHeight = fieldHeight,
                    keyPoints = it.pointList,
                    initState = it.snakeState,
                    initDirection = it.headDirection
                )
            )
        }
    }

    init {
        foodSet += foodList.map { coordToFieldArrayIndex(it) }
    }

    init {
        scores.forEach {
            snakeIDToScore[it.id] = it.score
        }
    }

    init {
        placeMissingFood()
    }

    init {
        printField()
    }

    private fun printField() {
        for (i in 0..<fieldHeight) {
            for (j in 0..<fieldWidth) {
                print("${snakeGameField[i * fieldWidth + j]} ")
            }
            println()
        }
    }

    private fun placeMissingFood() {
        val freeSpace = countFreeSpace()

        for (i in 0..<min((foodStatic + realSnakesMapper.numOfSnakes()), freeSpace) - foodSet.size) {
            placeFood(freeSpace - i)
        }
    }

    private fun cleanFoodSet() = foodSet.removeIf { snakeGameField[it] != -1 }
    private fun countFreeSpace(): Int = snakeGameField.size - foodSet.size - realSnakesMapper.getAllSnakes().sumOf { it.realSnake.coords.size }

    private fun placeFood(freeSpace: Int) {
        if (freeSpace <= 0) {
            throw IllegalArgumentException("freeSpace cannot be less or equal to 0 (got $freeSpace)")
        }
        val randomPos = (0..<freeSpace).random()
        var n = -1
        var i = 0
        while (n < randomPos) {
            if (snakeGameField[i++] == 0) {
                ++n
            }
        }
        snakeGameField[i-1] = -1
        foodSet.add(i-1)
    }

    private fun find5x5FreeRegion(): Pair<Int, Boolean> {
        for (i in 0..<this.fieldHeight) {
            for (j in 0..<this.fieldWidth) {
                if (isCorrectRegion(i, j)) {
                    return Pair(((i + 2) % this.fieldHeight) * this.fieldWidth + ((j + 2) % this.fieldWidth), true)
                }
            }
        }
        return Pair(0, false)
    }

    private fun isCorrectRegion(leftTopX: Int, leftTopY: Int) : Boolean {
        for (i in leftTopX..<leftTopX + 5) {
            for (j in leftTopY..<leftTopY + 5) {
                val ceil = this.snakeGameField[correctModule(i, this.fieldHeight) * this.fieldWidth + correctModule(
                    j,
                    this.fieldWidth
                )]
                if (ceil != 0 && ceil != -1) {
                    return false
                }
            }
        }
        val xc = this.snakeGameField[correctModule(leftTopX + 2, this.fieldHeight) * this.fieldWidth + correctModule(
            leftTopY + 2,
            this.fieldWidth
        )]
        val xl = this.snakeGameField[correctModule(leftTopX + 2, this.fieldHeight) * this.fieldWidth + correctModule(
            leftTopY + 1,
            this.fieldWidth
        )]
        val xd = this.snakeGameField[correctModule(leftTopX + 3, this.fieldHeight) * this.fieldWidth + correctModule(
            leftTopY + 2,
            this.fieldWidth
        )]
        val xr = this.snakeGameField[correctModule(leftTopX + 2, this.fieldHeight) * this.fieldWidth + correctModule(
            leftTopY + 3,
            this.fieldWidth
        )]
        val xu = this.snakeGameField[correctModule(leftTopX + 1, this.fieldHeight) * this.fieldWidth + correctModule(
            leftTopY + 2,
            this.fieldWidth
        )]
        return (xc == 0) && (xl == 0 || xd == 0 || xr == 0 || xu == 0)
    }

    private fun fieldArrayIndexToCoord(index: Int): Coord {
        return Coord(index % this.fieldWidth, index / this.fieldWidth)
    }

    private fun coordToFieldArrayIndex(coord: Coord): Int = coord.y * this.fieldWidth + coord.x

    fun createSnake(): Int {
        val result = find5x5FreeRegion()
        if (!result.second) {
            throw NoFreeSpaceException()
        }
        val headCoord = fieldArrayIndexToCoord(result.first)
        val context = getSurroundingContext(headCoord)
        val possibleDirections = Direction.entries.toTypedArray().filter { context[it] == 0 }

        val tailDirection = possibleDirections.random()

        val realSnake = RealSnake(
            headCoord,
            this.fieldWidth,
            this.fieldHeight,
            tailDirection.getOppositeDirection(),
            SnakeState.ALIVE
        )

        val id = realSnakesMapper.addNewSnake(realSnake)

        placeSnakeOnField(realSnake, id)

        placeMissingFood()
        snakeIDToScore[id] = 0

        return id
    }

    fun makeZombie(id: Int) {
        val snake = realSnakesMapper[id] ?: return
        snakeIDToScore.remove(id)
        snake.makeZombie()
    }

    private fun getLefterCoord(pos: Coord): Coord =
        Coord(correctModule(pos.x - 1, this.fieldWidth), pos.y)
    private fun getDownerCoord(pos: Coord): Coord =
        Coord(pos.x, correctModule(pos.y + 1, this.fieldHeight))
    private fun getRighterCoord(pos: Coord): Coord =
        Coord(correctModule(pos.x + 1, this.fieldWidth), pos.y)
    private fun getUpperCoord(pos: Coord): Coord =
        Coord(pos.x, correctModule(pos.y - 1, this.fieldHeight))

    private fun placeSnakeOnField(realSnake: RealSnake, snakeNumber: Int) =
        realSnake.coords.forEach { this.snakeGameField[coordToFieldArrayIndex(it)] = snakeNumber }

    private fun updateSnakeTailOnField(realSnake: RealSnake, snakeNumber: Int) {
        if (realSnake.tail != realSnake.oldTail) {
            this.snakeGameField[coordToFieldArrayIndex(realSnake.tail)] = snakeNumber
            this.snakeGameField[coordToFieldArrayIndex(realSnake.oldTail)] = 0
        }
    }

    private fun updateSnakeHeadOnField(realSnake: RealSnake, snakeNumber: Int) =
        snakeNumber.also { this.snakeGameField[coordToFieldArrayIndex(realSnake.head)] = it }

    private fun removeSnakeOnField(realSnake: RealSnake) {
        realSnake.coords.forEach {
            val newValue = (-1..0).random()
            this.snakeGameField[coordToFieldArrayIndex(it)] = newValue
            if (newValue == -1) {
                foodSet.add(coordToFieldArrayIndex(it))
            }
        }
    }

    fun updateField(): UpdateGameDto {
        val snakesList = realSnakesMapper.getAllSnakes()
        snakesList.forEach { snakeWithInfo -> snakeWithInfo.realSnake.move(getSurroundingContext(snakeWithInfo.realSnake.head)) }
        val snakesListForRemoval = snakesList.filter { snakeWithInfo ->
            val headCache = this.snakeGameField[coordToFieldArrayIndex(snakeWithInfo.realSnake.head)]
            if (headCache == 0 || headCache == -1) {
                if (headCache == -1) {
                    if (snakeWithInfo.realSnake.snakeState == SnakeState.ALIVE) {
                        snakeIDToScore[snakeWithInfo.id] = snakeIDToScore[snakeWithInfo.id]!! + 1
                    }
                }
                val collisionSnake = snakesList.find { collisionSnakeWithInfo ->
                    collisionSnakeWithInfo.realSnake.head == snakeWithInfo.realSnake.head &&
                            collisionSnakeWithInfo.id != snakeWithInfo.id
                }
                collisionSnake != null
            } else {
                val collisionSnake = snakesList.find { collisionSnakeWithInfo ->
                    collisionSnakeWithInfo.id == headCache
                }!!
                if (collisionSnake.realSnake.tail != collisionSnake.realSnake.oldTail &&
                    collisionSnake.realSnake.oldTail == snakeWithInfo.realSnake.head) {
                    false
                } else {
                    snakeIDToScore[collisionSnake.id] = snakeIDToScore[collisionSnake.id]!! + 1
                    true
                }
            }
        }
        realSnakesMapper.getAllSnakes().forEach { snakeWithInfo ->
            updateSnakeTailOnField(snakeWithInfo.realSnake, snakeWithInfo.id)
            updateSnakeHeadOnField(snakeWithInfo.realSnake, snakeWithInfo.id)
        }
        snakesListForRemoval.forEach { snakeWithInfo ->
            this.removeSnakeOnField(snakeWithInfo.realSnake)
            realSnakesMapper.removeSnake(snakeWithInfo.id)
            snakeIDToScore.remove(snakeWithInfo.id)
        }
        cleanFoodSet()
        placeMissingFood()

//        printField()

        return UpdateGameDto(
            snakes = realSnakesMapper.getAllSnakes().map {
                Snake(
                    playerID = it.id,
                    pointList = it.realSnake.keyPoints,
                    snakeState = it.realSnake.snakeState,
                    headDirection = it.realSnake.direction
                )
            },
            foodsPoint = foodSet.toList().map { fieldArrayIndexToCoord(it) },
            idForRemoval = snakesListForRemoval.map { it.id }
        )
    }

    fun getScore(id: Int): Int {
        return snakeIDToScore[id]!!
    }

    private fun getSurroundingContext(position: Coord): Context {
        val lefterCoord = getLefterCoord(position)
        val downerCoord = getDownerCoord(position)
        val righterCoord = getRighterCoord(position)
        val upperCoord = getUpperCoord(position)
        return Context(
            left = snakeGameField[coordToFieldArrayIndex(lefterCoord)],
            down = snakeGameField[coordToFieldArrayIndex(downerCoord)],
            right = snakeGameField[coordToFieldArrayIndex(righterCoord)],
            up = snakeGameField[coordToFieldArrayIndex(upperCoord)]
        )
    }

    fun removeSnake(id: Int) {
        val snake = this.realSnakesMapper.removeSnake(id)!!
        this.removeSnakeOnField(snake)
    }

    fun updateDirection(id: Int, direction: Direction) {
        val snake = realSnakesMapper[id] ?: return
        snake.newDirection(direction)
    }
}

private data class Context(
    val left: Int,
    val down: Int,
    val right: Int,
    val up: Int
) {
    operator fun get(direction: Direction): Int = when (direction) {
        Direction.LEFT -> left
        Direction.DOWN -> down
        Direction.RIGHT -> right
        Direction.UP -> up
    }
}

private data class RealSnakeWithInfo(
    val realSnake: RealSnake,
    val id: Int,
)

private class RealSnakesMapper {
    private val releasedIndexesSet = HashSet<Int>()
    private var maxUnusedIndex = 1
    private val idToRealSnakeWithId = HashMap<Int, RealSnake>()

    fun addNewSnake(realSnake: RealSnake): Int {
        return if (releasedIndexesSet.isEmpty()) {
            val id = maxUnusedIndex++
            idToRealSnakeWithId[id] = realSnake
            id
        } else {
            val iter = releasedIndexesSet.iterator()
            val currentSnakeIndex = iter.next()
            iter.remove()
            val id = currentSnakeIndex
            idToRealSnakeWithId[id] = realSnake
            id
        }
    }

    fun addExistingSnake(id: Int, realSnake: RealSnake): Int {
        idToRealSnakeWithId[id] = realSnake
        maxUnusedIndex = max(id+1, maxUnusedIndex)
        return id
    }

    fun removeSnake(id: Int): RealSnake? {
        return if (id in idToRealSnakeWithId) {
            val currentSnakeWithIndex = idToRealSnakeWithId.remove(id)!!
//            releasedIndexesSet.add(id)
            currentSnakeWithIndex
        } else {
            null
        }
    }

    fun getAllSnakes(): List<RealSnakeWithInfo> = idToRealSnakeWithId.entries
        .map{ entry ->
            RealSnakeWithInfo(id = entry.key, realSnake = entry.value)
        }

    fun numOfSnakes(): Int = idToRealSnakeWithId.size

    operator fun get(id: Int): RealSnake? = idToRealSnakeWithId[id]
}

private fun correctModule(a: Int, b: Int): Int {
    return (b + (a % b)) % b
}

private class RealSnake(
    private val fieldWidth: Int,
    private val fieldHeight: Int,
    keyPoints: List<Coord>,
    initState: SnakeState,
    initDirection: Direction,
) {
    constructor(head: Coord, fieldWidth: Int, fieldHeight: Int,
                initDirection: Direction, initState: SnakeState) :
            this(
                fieldWidth,
                fieldHeight,
                listOf(
                    head,
                    when (initDirection) {
                        Direction.LEFT -> Coord(1, 0)
                        Direction.DOWN -> Coord(0, -1)
                        Direction.RIGHT -> Coord(-1, 0)
                        Direction.UP -> Coord(0, 1)
                    }
                ),
                initState,
                initDirection)

    val coords = ArrayDeque<Coord>(keyPoints.size)

    init {
        coords.addFirst(keyPoints.first())
        var cx = keyPoints.first().x
        var cy = keyPoints.first().y
        for (i in 1..<keyPoints.size) {
            if (keyPoints[i].x == 0) {
                val yCache = keyPoints[i].y
                val yCacheAbsolute = yCache.absoluteValue
                val yCacheSign = yCache.sign
                for (j in 0..<yCacheAbsolute) {
                    cy += yCacheSign
                    cy = when (cy) {
                        -1 -> fieldHeight - 1
                        fieldHeight -> 0
                        else -> cy
                    }
                    coords += Coord(cx, cy)
                }
            } else {
                val xCache = keyPoints[i].x
                val xCacheAbsolute = xCache.absoluteValue
                val xCacheSign = xCache.sign
                for (j in 0..<xCacheAbsolute) {
                    cx += xCacheSign
                    cx = when (cx) {
                        -1 -> fieldWidth - 1
                        fieldWidth -> 0
                        else -> cx
                    }
                    coords += Coord(cx, cy)
                }
            }
        }
    }

    val keyPoints = ArrayDeque(keyPoints)
    private var prevDirection = initDirection
    private var headDirection = initDirection
    private var _snakeState = initState

    val snakeState: SnakeState
        get() = _snakeState

    val direction: Direction
        get() = headDirection
    val head: Coord
        get() = coords.first()
    val tail: Coord
        get() = coords.last()
    var oldTail = tail
        private set



    fun makeZombie() {
        _snakeState = SnakeState.ZOMBIE
    }

    fun newDirection(newDirection: Direction) {
        this.headDirection = if (newDirection == prevDirection.getOppositeDirection())
            prevDirection
        else
            newDirection
    }

    fun move(context: Context) {
        val head = coords[0]
        oldTail = tail


        when (direction) {
            Direction.LEFT -> {
                if (head.x == 0) {
                    coords.addFirst(Coord(fieldWidth - 1, head.y))
                } else {
                    coords.addFirst(Coord(head.x - 1, head.y))
                }

                if (direction == prevDirection) {
                    keyPoints[1] = Coord(keyPoints[1].x + 1, keyPoints[1].y)
                    if (keyPoints.first().x == 0) {
                        keyPoints[0] = Coord(fieldWidth - 1, keyPoints.first().y)
                    } else {
                        keyPoints[0] = Coord(keyPoints.first().x - 1, keyPoints.first().y)
                    }
                } else {
                    if (keyPoints.first().x == 0) {
                        keyPoints.addFirst(Coord(fieldWidth - 1, keyPoints.first().y))
                    } else {
                        keyPoints.addFirst(Coord(keyPoints.first().x - 1, keyPoints.first().y))
                    }
                    keyPoints[1] = Coord(1, 0)
                }
            }
            Direction.DOWN -> {
                if (head.y == fieldHeight - 1) {
                    coords.addFirst(Coord(head.x, 0))
                } else {
                    coords.addFirst(Coord(head.x, head.y + 1))
                }

                if (direction == prevDirection) {
                    keyPoints[1] = Coord(keyPoints[1].x, keyPoints[1].y - 1)
                    if (keyPoints.first().y == fieldHeight - 1) {
                        keyPoints[0] = Coord(keyPoints.first().x, 0)
                    } else {
                        keyPoints[0] = Coord(keyPoints.first().x, keyPoints.first().y + 1)
                    }
                } else {
                    if (keyPoints.first().y == fieldHeight - 1) {
                        keyPoints.addFirst(Coord(keyPoints.first().x, 0))
                    } else {
                        keyPoints.addFirst(Coord(keyPoints.first().x, keyPoints.first().y + 1))
                    }
                    keyPoints[1] = Coord(0, -1)
                }
            }
            Direction.RIGHT -> {
                if (head.x == fieldWidth - 1) {
                    coords.addFirst(Coord(0, head.y))
                } else {
                    coords.addFirst(Coord(head.x + 1, head.y))
                }

                if (direction == prevDirection) {
                    keyPoints[1] = Coord(keyPoints[1].x - 1, keyPoints[1].y)
                    if (keyPoints.first().x == fieldWidth - 1) {
                        keyPoints[0] = Coord(0, keyPoints.first().y)
                    } else {
                        keyPoints[0] = Coord(keyPoints.first().x + 1, keyPoints.first().y)
                    }
                } else {
                    if (keyPoints.first().x == fieldWidth - 1) {
                        keyPoints.addFirst(Coord(0, keyPoints.first().y))
                    } else {
                        keyPoints.addFirst(Coord(keyPoints.first().x + 1, keyPoints.first().y))
                    }
                    keyPoints[1] = Coord(-1, 0)
                }
            }
            Direction.UP -> {
                if (head.y == 0) {
                    coords.addFirst(Coord(head.x, fieldHeight - 1))
                } else {
                    coords.addFirst(Coord(head.x, head.y - 1))
                }

                if (direction == prevDirection) {
                    keyPoints[1] = Coord(keyPoints[1].x, keyPoints[1].y + 1)
                    if (keyPoints.first().y == 0) {
                        keyPoints[0] = Coord(keyPoints.first().x, fieldHeight - 1)
                    } else {
                        keyPoints[0] = Coord(keyPoints.first().x, keyPoints.first().y - 1)
                    }
                } else {
                    if (keyPoints.first().y == 0) {
                        keyPoints.addFirst(Coord(keyPoints.first().x, fieldHeight - 1))
                    } else {
                        keyPoints.addFirst(Coord(keyPoints.first().x, keyPoints.first().y - 1))
                    }
                    keyPoints[1] = Coord(0, 1)
                }
            }
        }
        if (context[direction] != -1) {
            coords.removeLast()
//            println("at != -1: ${keyPoints}")
//            if (keyPoints.size == 2) {
//                keyPoints[1] = when (direction) {
//                    Direction.LEFT -> Coord(keyPoints.last().x - 1, keyPoints.last().y)
//                    Direction.DOWN -> Coord(keyPoints.last().x, keyPoints.last().y + 1)
//                    Direction.RIGHT ->  Coord(keyPoints.last().x + 1, keyPoints.last().y)
//                    Direction.UP -> Coord(keyPoints.last().x, keyPoints.last().y - 1)
//                }
//            } else {
            val lastX = keyPoints.last().x
            val lastY = keyPoints.last().y
            if (lastX == 0) {
                if (lastY.absoluteValue == 1) {
                    keyPoints.removeLast()
                } else {
                    keyPoints[keyPoints.size - 1] = Coord(0, (lastY.absoluteValue - 1) * lastY.sign)
                }
            } else {
                if (lastX.absoluteValue == 1) {
                    keyPoints.removeLast()
                } else {
                    keyPoints[keyPoints.size - 1] = Coord((lastX.absoluteValue - 1) * lastX.sign, 0)
                }
            }

//            println("after != -1: ${keyPoints}")
//            }
//            if (diffX == 0) {
//                if (diffY.absoluteValue == 1) {
//                    keyPoints.removeLast()
//                } else {
//                    keyPoints[keyPoints.size - 1] = Coord(0, (diffY.absoluteValue - 1) * diffY.sign)
//                }
//            } else {
//                println("DIFFX: $keyPoints")
//                if (diffX.absoluteValue == 1) {
//                    keyPoints.removeLast()
//                } else {
//                    keyPoints[keyPoints.size - 1] = Coord((diffX.absoluteValue - 1) * diffX.sign, 0)
//                }
//            }
//            keyPoints.removeLast()
//            if (coords.last() != keyPoints.last()) {
//                keyPoints.addLast(coords.last())
//
        }
        prevDirection = direction
    }
}