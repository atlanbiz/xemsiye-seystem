package com.solarpulse.app.pdf

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Small A4 PDF composer on top of [PdfDocument]. All text goes through [StaticLayout], so Arabic
 * and Uyghur are shaped and bidi-ordered by the platform text stack; in RTL documents the page
 * layout (columns, alignment) is mirrored as well.
 */
class PdfBuilder(private val rtl: Boolean) {
    private val doc = PdfDocument()
    private var page: PdfDocument.Page? = null
    private var pageNo = 0
    val width = 595f
    val height = 842f
    val margin = 40f
    val contentWidth get() = width - 2 * margin
    var y = margin
        private set

    private val canvas: Canvas get() = page!!.canvas

    private fun paint(size: Float, color: Int = TEXT, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    fun newPage() {
        page?.let { doc.finishPage(it) }
        pageNo++
        page = doc.startPage(PdfDocument.PageInfo.Builder(width.toInt(), height.toInt(), pageNo).create())
        y = margin
    }

    private fun ensure(space: Float) {
        if (page == null || y + space > height - margin) newPage()
    }

    /** Lays out text in a box; "start" follows the document direction. */
    private fun layout(text: CharSequence, p: TextPaint, w: Float, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL, maxLines: Int = Int.MAX_VALUE): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, p, w.toInt().coerceAtLeast(1))
            .setAlignment(align)
            .setTextDirection(if (rtl) TextDirectionHeuristics.FIRSTSTRONG_RTL else TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .setIncludePad(false)
            .setMaxLines(maxLines)
            .setEllipsize(if (maxLines == Int.MAX_VALUE) null else TextUtils.TruncateAt.END)
            .build()

    /** x of a box that starts at [startOffset] from the document's start edge. */
    private fun xStart(startOffset: Float, w: Float) = if (rtl) width - margin - startOffset - w else margin + startOffset

    private fun draw(l: StaticLayout, x: Float, top: Float) {
        canvas.save()
        canvas.translate(x, top)
        l.draw(canvas)
        canvas.restore()
    }

    fun text(text: String, size: Float = 11f, color: Int = TEXT, bold: Boolean = false, gapAfter: Float = 6f, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL) {
        val l = layout(text, paint(size, color, bold), contentWidth, align)
        ensure(l.height.toFloat())
        draw(l, margin, y)
        y += l.height + gapAfter
    }

    fun space(h: Float) {
        y += h
    }

    /** Brand header: gradient bar with logo mark + title, and a right-hand caption. */
    fun header(title: String, subtitle: String, caption: String) {
        ensure(90f)
        val bar = RectF(margin, y, width - margin, y + 70f)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(bar.left, bar.top, bar.right, bar.bottom, BRAND_DARK, BRAND_LIGHT, Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(bar, 14f, 14f, bg)
        // lightning mark
        val markX = if (rtl) bar.right - 52f else bar.left + 14f
        val mark = RectF(markX, bar.top + 15f, markX + 38f, bar.top + 53f)
        canvas.drawRoundRect(mark, 10f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF })
        val bolt = Path().apply {
            val cx = mark.left
            val cy = mark.top
            moveTo(cx + 21f, cy + 6f); lineTo(cx + 11f, cy + 21f); lineTo(cx + 18f, cy + 21f)
            lineTo(cx + 16f, cy + 32f); lineTo(cx + 27f, cy + 16f); lineTo(cx + 20f, cy + 16f); close()
        }
        canvas.drawPath(bolt, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        val textW = contentWidth - 240f
        val t = layout(title, paint(17f, Color.WHITE, true), textW, maxLines = 1)
        val s = layout(subtitle, paint(9.5f, 0xDDFFFFFF.toInt()), textW, maxLines = 2)
        draw(t, xStart(64f, textW), bar.top + 14f)
        draw(s, xStart(64f, textW), bar.top + 16f + t.height)
        val capW = 170f
        val cap = layout(caption, paint(9.5f, Color.WHITE, true), capW, Layout.Alignment.ALIGN_OPPOSITE, maxLines = 3)
        draw(cap, if (rtl) margin + 14f else width - margin - 14f - capW, bar.top + (70f - cap.height) / 2)
        y = bar.bottom + 18f
    }

    /** Row of summary tiles (label + value). */
    fun tiles(items: List<Pair<String, String>>) {
        if (items.isEmpty()) return
        ensure(60f)
        val gap = 8f
        val w = (contentWidth - gap * (items.size - 1)) / items.size
        var tallest = 0f
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TILE }
        items.forEachIndexed { i, (label, value) ->
            val x = xStart(i * (w + gap), w)
            val l = layout(label, paint(8.5f, MUTED), w - 16f, maxLines = 2)
            val v = layout(value, paint(13f, TEXT, true), w - 16f, maxLines = 1)
            val h = l.height + v.height + 18f
            tallest = maxOf(tallest, h)
            canvas.drawRoundRect(RectF(x, y, x + w, y + h), 10f, 10f, bg)
            draw(l, x + 8f, y + 8f)
            draw(v, x + 8f, y + 10f + l.height)
        }
        y += tallest + 14f
    }

    /** Key/value block in [cols] columns. */
    fun keyValues(items: List<Pair<String, String>>, cols: Int = 3) {
        val gap = 12f
        val w = (contentWidth - gap * (cols - 1)) / cols
        items.chunked(cols).forEach { row ->
            var tallest = 0f
            val layouts = row.map { (k, v) -> layout(k, paint(8.5f, MUTED), w) to layout(v, paint(11f, TEXT, true), w) }
            layouts.forEach { (k, v) -> tallest = maxOf(tallest, (k.height + v.height + 4).toFloat()) }
            ensure(tallest)
            layouts.forEachIndexed { i, (k, v) ->
                val x = xStart(i * (w + gap), w)
                draw(k, x, y)
                draw(v, x, y + k.height + 2f)
            }
            y += tallest + 10f
        }
    }

    /**
     * Table with relative column [weights]; the header repeats on each new page. Columns follow
     * the reading direction (first column at the start edge).
     */
    fun table(columns: List<String>, rows: List<List<String>>, weights: List<Float> = List(columns.size) { 1f }) {
        val total = weights.sum()
        val widths = weights.map { contentWidth * it / total }
        val headPaint = paint(8.5f, MUTED, true)
        val cellPaint = paint(9.5f, TEXT)
        val pad = 5f
        val line = Paint().apply { color = LINE; strokeWidth = 0.7f }
        fun rowLayouts(cells: List<String>, p: TextPaint) = cells.mapIndexed { i, cell -> layout(cell, p, widths[i] - 2 * pad, maxLines = 3) }
        fun drawRow(ls: List<StaticLayout>, fill: Int?) {
            val h = (ls.maxOfOrNull { it.height } ?: 0) + 2 * pad
            if (fill != null) canvas.drawRect(margin, y, width - margin, y + h, Paint().apply { color = fill })
            var offset = 0f
            ls.forEachIndexed { i, l ->
                draw(l, xStart(offset, widths[i]) + pad, y + pad)
                offset += widths[i]
            }
            y += h
            canvas.drawLine(margin, y, width - margin, y, line)
        }
        val head = rowLayouts(columns, headPaint)
        ensure((head.maxOfOrNull { it.height } ?: 0) + 40f)
        drawRow(head, TILE)
        rows.forEach { r ->
            val ls = rowLayouts(r, cellPaint)
            val h = (ls.maxOfOrNull { it.height } ?: 0) + 2 * pad
            if (y + h > height - margin) {
                newPage()
                drawRow(head, TILE)
            }
            drawRow(ls, null)
        }
        y += 12f
    }

    /** Simple filled area/line chart of [values] (left-to-right in every language). */
    fun areaChart(values: List<Double>, chartHeight: Float = 120f, caption: String? = null) {
        if (values.size < 2) return
        ensure(chartHeight + 24f)
        val max = (values.maxOrNull() ?: 0.0).takeIf { it > 0 } ?: 1.0
        val left = margin
        val right = width - margin
        val top = y
        val bottom = y + chartHeight
        val grid = Paint().apply { color = LINE; strokeWidth = 0.5f }
        for (i in 0..3) {
            val gy = top + (bottom - top) * i / 3
            canvas.drawLine(left, gy, right, gy, grid)
        }
        fun px(i: Int) = left + (right - left) * i / (values.size - 1)
        fun py(v: Double) = (bottom - (v / max) * (bottom - top - 6)).toFloat()
        val line = Path().apply {
            moveTo(px(0), py(values[0]))
            for (i in 1 until values.size) lineTo(px(i), py(values[i]))
        }
        val area = Path(line).apply {
            lineTo(px(values.size - 1), bottom)
            lineTo(px(0), bottom)
            close()
        }
        canvas.drawPath(area, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, top, 0f, bottom, 0x553B74F6, 0x003B74F6, Shader.TileMode.CLAMP)
        })
        canvas.drawPath(line, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BRAND_LIGHT
            style = Paint.Style.STROKE
            strokeWidth = 1.6f
        })
        y = bottom + 6f
        if (caption != null) text(caption, 8.5f, MUTED)
        y += 8f
    }

    /** Highlighted total box at the end edge. */
    fun totalBox(label: String, value: String) {
        ensure(44f)
        val w = 220f
        val x = if (rtl) margin else width - margin - w
        canvas.drawRoundRect(RectF(x, y, x + w, y + 36f), 10f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BRAND_TINT })
        val l = layout(label, paint(12f, TEXT, true), w / 2 - 12f)
        val v = layout(value, paint(13f, BRAND_DARK, true), w / 2 - 12f, Layout.Alignment.ALIGN_OPPOSITE)
        val ly = y + (36f - l.height) / 2
        if (rtl) {
            draw(l, x + w / 2 + 4f, ly)
            draw(v, x + 8f, ly)
        } else {
            draw(l, x + 8f, ly)
            draw(v, x + w / 2 + 4f, ly)
        }
        y += 48f
    }

    fun footer(text: String) {
        ensure(30f)
        y = maxOf(y, height - margin - 20f)
        text(text, 8.5f, MUTED, align = Layout.Alignment.ALIGN_CENTER)
    }

    /** Writes the document to cache/pdf/[name] and returns the file. */
    fun save(context: Context, name: String): File {
        page?.let { doc.finishPage(it) }
        page = null
        val dir = File(context.cacheDir, "pdf").apply { mkdirs() }
        val file = File(dir, name)
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    companion object {
        const val TEXT = 0xFF0F172A.toInt()
        const val MUTED = 0xFF64748B.toInt()
        const val LINE = 0xFFE2E8F0.toInt()
        const val TILE = 0xFFF1F5F9.toInt()
        const val BRAND_DARK = 0xFF1D44D8.toInt()
        const val BRAND_LIGHT = 0xFF3B74F6.toInt()
        const val BRAND_TINT = 0xFFEFF5FF.toInt()

        /** Opens the system share sheet for a generated PDF (via FileProvider). */
        fun share(context: Context, file: File, title: String) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
