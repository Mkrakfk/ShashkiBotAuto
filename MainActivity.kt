package com.example.shashkibotauto

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import android.graphics.Color
import kotlin.math.abs

class MainActivity : Activity() {
    private lateinit var info: TextView
    private lateinit var boardView: BoardView
    private lateinit var fromX: EditText
    private lateinit var fromY: EditText
    private lateinit var toX: EditText
    private lateinit var toY: EditText
    private val game = DraughtsGame()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 20, 18, 18)
            setBackgroundColor(Color.rgb(250, 246, 238))
        }
        page.addView(TextView(this).apply {
            text = "♟ Шашки Бот AUTO"
            textSize = 25f; setTextColor(Color.rgb(70, 43, 25))
        })
        page.addView(TextView(this).apply {
            text = "Русские шашки • советник ходов"
            textSize = 14f; setTextColor(Color.DKGRAY)
        })
        info = TextView(this).apply {
            text = "Белые ходят первыми. Выбери шашку и клетку."
            textSize = 15f; setPadding(0, 12, 0, 12); setTextColor(Color.BLACK)
        }
        page.addView(info)
        boardView = BoardView(this, game) { msg -> info.text = msg }
        page.addView(boardView, LinearLayout.LayoutParams(-1, 0, 1f))
        val hint = Button(this).apply {
            text = "Найти лучший ход"
            setOnClickListener {
                val move = game.bestMove()
                if (move == null) info.text = "Доступных ходов нет."
                else {
                    game.highlight = move
                    boardView.invalidate()
                    info.text = "Совет: ${move.fr + 1},${move.fc + 1} → ${move.tr + 1},${move.tc + 1}. Проверь ход перед выполнением."
                }
            }
        }
        page.addView(hint)
        page.addView(Button(this).apply {
            text = "Новая партия"
            setOnClickListener { game.reset(); boardView.invalidate(); info.text = "Новая партия. Белые ходят первыми." }
        })
        page.addView(Button(this).apply {
            text = "Открыть настройки управления Android"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        page.addView(TextView(this).apply {
            text = "Внешняя игра: ниже можно выполнить два тестовых нажатия по координатам экрана. Сначала проверь координаты вручную. Автораспознавание чужой доски пока не реализовано."
            textSize = 12f; setTextColor(Color.DKGRAY); setPadding(0, 8, 0, 4)
        })
        val coords1 = LinearLayout(this)
        fromX = edit("X старта"); fromY = edit("Y старта")
        coords1.addView(fromX, params()); coords1.addView(fromY, params())
        page.addView(coords1)
        val coords2 = LinearLayout(this)
        toX = edit("X финиша"); toY = edit("Y финиша")
        coords2.addView(toX, params()); coords2.addView(toY, params())
        page.addView(coords2)
        page.addView(Button(this).apply {
            text = "Выполнить один тестовый ход"
            setOnClickListener { doExternalTap() }
        })
        setContentView(ScrollView(this).apply { addView(page) })
    }

    private fun edit(h: String) = EditText(this).apply {
        hint = h; inputType = 2; textSize = 13f; setSingleLine(true)
    }
    private fun params() = LinearLayout.LayoutParams(0, 54, 1f).apply { setMargins(3, 2, 3, 2) }

    private fun doExternalTap() {
        val service = TapAccessibilityService.instance
        if (service == null) {
            Toast.makeText(this, "Сначала включи сервис в настройках специальных возможностей.", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val x1 = fromX.text.toString().toFloat()
            val y1 = fromY.text.toString().toFloat()
            val x2 = toX.text.toString().toFloat()
            val y2 = toY.text.toString().toFloat()
            service.tap(x1, y1) { service.tap(x2, y2) }
        } catch (_: Exception) {
            Toast.makeText(this, "Заполни все четыре координаты.", Toast.LENGTH_LONG).show()
        }
    }
}

data class Move(val fr: Int, val fc: Int, val tr: Int, val tc: Int, val captured: Int = 0)

class DraughtsGame {
    // 1 white man, 2 black man, 3 white king, 4 black king
    val board = Array(8) { IntArray(8) }
    var whiteTurn = true
    var selected: Pair<Int, Int>? = null
    var highlight: Move? = null
    private val dirs = arrayOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)

    init { reset() }

    fun reset() {
        for (r in 0..7) for (c in 0..7) board[r][c] = 0
        for (r in 0..2) for (c in 0..7) if ((r + c) % 2 == 1) board[r][c] = 2
        for (r in 5..7) for (c in 0..7) if ((r + c) % 2 == 1) board[r][c] = 1
        whiteTurn = true; selected = null; highlight = null
    }

    private fun own(p: Int) = if (whiteTurn) p == 1 || p == 3 else p == 2 || p == 4
    private fun enemy(p: Int) = p != 0 && !own(p)
    private fun inside(r: Int, c: Int) = r in 0..7 && c in 0..7

    fun legalMoves(): List<Move> {
        val captures = mutableListOf<Move>()
        val normal = mutableListOf<Move>()
        for (r in 0..7) for (c in 0..7) {
            val p = board[r][c]
            if (!own(p)) continue
            for ((dr, dc) in dirs) {
                if (p == 3 || p == 4) {
                    var rr = r + dr; var cc = c + dc
                    while (inside(rr, cc) && board[rr][cc] == 0) {
                        normal.add(Move(r, c, rr, cc)); rr += dr; cc += dc
                    }
                    if (inside(rr, cc) && enemy(board[rr][cc])) {
                        val cr = rr + dr; val c2 = cc + dc
                        if (inside(cr, c2) && board[cr][c2] == 0) captures.add(Move(r, c, cr, c2, 1))
                    }
                } else {
                    val forward = if (p == 1) -1 else 1
                    val rr = r + dr; val cc = c + dc
                    if (dr == forward && inside(rr, cc) && board[rr][cc] == 0)
                        normal.add(Move(r, c, rr, cc))
                    val jr = r + 2 * dr; val jc = c + 2 * dc
                    if (inside(jr, jc) && inside(rr, cc) && enemy(board[rr][cc]) && board[jr][jc] == 0)
                        captures.add(Move(r, c, jr, jc, 1))
                }
            }
        }
        return if (captures.isNotEmpty()) captures else normal
    }

    fun tapCell(r: Int, c: Int): String {
        val p = board[r][c]
        val sel = selected
        if (sel == null) {
            if (p != 0 && own(p)) { selected = r to c; return "Шашка выбрана. Выбери клетку назначения." }
            return "Выбери свою шашку."
        }
        val move = legalMoves().firstOrNull { it.fr == sel.first && it.fc == sel.second && it.tr == r && it.tc == c }
        if (move == null) {
            selected = if (p != 0 && own(p)) r to c else null
            return "Недопустимый ход или обязательное взятие. Проверь подсветку."
        }
        apply(move)
        return if (whiteTurn) "Ход выполнен. Теперь ход белых." else "Ход выполнен. Теперь ход чёрных."
    }

    private fun apply(m: Move) {
        var piece = board[m.fr][m.fc]
        board[m.fr][m.fc] = 0
        if (m.captured > 0) {
            val mr = (m.fr + m.tr) / 2; val mc = (m.fc + m.tc) / 2
            board[mr][mc] = 0
        }
        if (piece == 1 && m.tr == 0) piece = 3
        if (piece == 2 && m.tr == 7) piece = 4
        board[m.tr][m.tc] = piece
        whiteTurn = !whiteTurn; selected = null; highlight = null
    }

    fun bestMove(): Move? {
        val moves = legalMoves()
        if (moves.isEmpty()) return null
        return moves.maxByOrNull { m ->
            val piece = board[m.fr][m.fc]
            var score = if (m.captured > 0) 100 else 0
            if ((piece == 1 && m.tr == 0) || (piece == 2 && m.tr == 7)) score += 30
            if (piece == 1) score += (7 - m.tr) * 2
            if (piece == 2) score += m.tr * 2
            if (piece == 3 || piece == 4) score += 4
            score
        }
    }
}

class BoardView(
    context: android.content.Context,
    private val game: DraughtsGame,
    private val report: (String) -> Unit
) : android.view.View(context) {
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        val size = minOf(width, height) / 8f
        val left = (width - size * 8) / 2f
        val top = (height - size * 8) / 2f
        for (r in 0..7) for (c in 0..7) {
            val x = left + c * size; val y = top + r * size
            paint.color = if ((r + c) % 2 == 0) Color.rgb(238, 220, 190) else Color.rgb(139, 91, 60)
            canvas.drawRect(x, y, x + size, y + size, paint)
            val m = game.highlight
            if (m != null && ((r == m.fr && c == m.fc) || (r == m.tr && c == m.tc))) {
                paint.color = Color.argb(150, 40, 190, 90); canvas.drawRect(x, y, x + size, y + size, paint)
            }
            val p = game.board[r][c]
            if (p != 0) {
                paint.color = if (p == 1 || p == 3) Color.rgb(248, 232, 155) else Color.rgb(68, 39, 22)
                canvas.drawCircle(x + size / 2, y + size / 2, size * .37f, paint)
                paint.color = if (p == 1 || p == 3) Color.DKGRAY else Color.WHITE
                paint.textAlign = android.graphics.Paint.Align.CENTER
                paint.textSize = size * .45f
                if (p == 3 || p == 4) canvas.drawText("♛", x + size / 2, y + size * .66f, paint)
            }
        }
        val s = game.selected
        if (s != null) {
            paint.color = Color.YELLOW; paint.style = android.graphics.Paint.Style.STROKE; paint.strokeWidth = 4f
            canvas.drawRect(left + s.second * size, top + s.first * size, left + (s.second + 1) * size, top + (s.first + 1) * size, paint)
            paint.style = android.graphics.Paint.Style.FILL
        }
    }
    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.action != android.view.MotionEvent.ACTION_UP) return true
        val size = minOf(width, height) / 8f
        val left = (width - size * 8) / 2f
        val top = (height - size * 8) / 2f
        val c = ((event.x - left) / size).toInt()
        val r = ((event.y - top) / size).toInt()
        if (r in 0..7 && c in 0..7) { report(game.tapCell(r, c)); invalidate() }
        return true
    }
}
