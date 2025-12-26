package onlinesnake.ui.impl.contentpanels

import onlinesnake.dto.Coord
import onlinesnake.dto.Snake
import onlinesnake.dto.SnakeState
import java.awt.*
import javax.swing.JPanel
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.math.sign

class GameFieldPanel : JPanel() {
    private var fieldWidth: Int = 1
    private var fieldHeight: Int = 1
    private var myID: Int = 0
    private val foodColor = Color(50, 180, 40)
    private val myColor = Color(50, 90, 200)
    private val aliveEnemyColor = Color(190, 50, 30)
    private val zombieEnemyColor = Color(130, 130, 130)
    private var snakesList: List<Snake> = listOf()
    private var foodList: List<Coord> = listOf()

    override fun paint(g : Graphics?) {
        val centerWidth: Boolean
        val fieldSizeDimension = if (height.toDouble() * fieldWidth > width.toDouble() * fieldHeight) {
            centerWidth = false
            Dimension(width, (width.toDouble() / fieldWidth * fieldHeight).roundToInt())
        } else {
            centerWidth = true
            Dimension((height.toDouble() / fieldHeight * fieldWidth).roundToInt(), height)
        }

        val offset = if (centerWidth) {
            Dimension((width - fieldSizeDimension.width) / 2, 0)
        } else {
            Dimension(0, (height - fieldSizeDimension.height) / 2)
        }

        val brickWidth = fieldSizeDimension.width.toDouble() / this.fieldWidth
        val brickHeight = fieldSizeDimension.height.toDouble() / this.fieldHeight
        val brickWidthInt = brickWidth.roundToInt()
        val brickHeightInt = brickHeight.roundToInt()
        g?.color = Color.WHITE
        g?.fillRect(offset.width, offset.height, fieldSizeDimension.width, fieldSizeDimension.height)

        g?.color = Color(100, 100, 100)
        for (i in 1..<fieldHeight) {
            g?.drawLine(
                offset.width,
                (offset.height + i * brickHeight).roundToInt(),
                this.width - offset.width,
                (offset.height + i * brickHeight).roundToInt(),
            )
        }

        for (i in 1..<fieldWidth) {
            g?.drawLine(
                (offset.width + i * brickWidth).roundToInt(),
                offset.height,
                (offset.width + i * brickWidth).roundToInt(),
                this.height - offset.height,
            )
        }

        g?.color = foodColor
//        println("<==>")
        for (foodPoint in foodList) {
//            println("$foodPoint ${offset.width}, ${offset.height}, ${offset.width + (foodPoint.x * brickWidth).roundToInt()}, ${offset.height + (foodPoint.y + brickHeight).roundToInt()}")
            g?.fillRect(
                offset.width + (foodPoint.x * brickWidth).roundToInt(),
                offset.height + (foodPoint.y * brickHeight).roundToInt(),
                brickWidthInt,
                brickHeightInt,
            )
        }

        for (snakeIndex in snakesList.indices) {
            val snake = snakesList[snakeIndex]
            var cx = snake.pointList.first().x
            var cy = snake.pointList.first().y
            g?.color = when (snake.snakeState) {
                SnakeState.ALIVE -> {
                    if (snake.playerID == myID) {
                        myColor
                    } else {
                        aliveEnemyColor
                    }
                }
                SnakeState.ZOMBIE -> {
                    zombieEnemyColor
                }
            }
            g?.fillRect(
                offset.width + (cx * brickWidth).roundToInt(),
                offset.height + (cy * brickHeight).roundToInt(),
                brickWidthInt,
                brickHeightInt,
            )
            for (nextCoordIndex in 1..<snake.pointList.size) {
                if (snake.pointList[nextCoordIndex].x == 0) {
                    val yCache = snake.pointList[nextCoordIndex].y
                    val yCacheAbsolute = yCache.absoluteValue
                    val yCacheSign = yCache.sign
                    for (j in 0..<yCacheAbsolute) {
                        cy += yCacheSign
                        cy = when (cy) {
                            -1 -> fieldHeight - 1
                            fieldHeight -> 0
                            else -> cy
                        }
                        g?.fillRect(
                            offset.width + (cx * brickWidth).roundToInt(),
                            offset.height + (cy * brickHeight).roundToInt(),
                            brickWidthInt,
                            brickHeightInt,
                        )
                    }
                } else {
                    val xCache = snake.pointList[nextCoordIndex].x
                    val xCacheAbsolute = xCache.absoluteValue
                    val xCacheSign = xCache.sign
                    for (i in 0..<xCacheAbsolute) {
                        cx += xCacheSign
                        cx = when (cx) {
                            -1 -> fieldWidth - 1
                            fieldWidth -> 0
                            else -> cx
                        }
                        g?.fillRect(
                            offset.width + (cx * brickWidth).roundToInt(),
                            offset.height + (cy * brickHeight).roundToInt(),
                            brickWidthInt,
                            brickHeightInt,
                        )
                    }
                }
            }
        }
    }

    fun updateField(snakesList: List<Snake>, foodList: List<Coord>, myID: Int, fieldWidth: Int, fieldHeight: Int) {
        this.fieldWidth = fieldWidth
        this.fieldHeight = fieldHeight
        this.snakesList = snakesList
        this.myID = myID
        this.foodList = foodList
        repaint()
    }
}