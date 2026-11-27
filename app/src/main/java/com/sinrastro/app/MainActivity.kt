package com.sinrastro.app

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Size
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

private data class PhotoItem(val uri: Uri, val name: String)

class MainActivity : Activity() {
    private val photoItems = mutableListOf<PhotoItem>()
    private val worker = Executors.newSingleThreadExecutor()
    private val savedUris = mutableListOf<Uri>()

    private lateinit var countLabel: TextView
    private lateinit var emptyPicker: LinearLayout
    private lateinit var selectedSection: LinearLayout
    private lateinit var selectedList: LinearLayout
    private lateinit var addMoreButton: TextView
    private lateinit var cleanButton: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var progressLabel: TextView
    private lateinit var resultCard: LinearLayout
    private lateinit var resultTitle: TextView
    private lateinit var resultDetail: TextView
    private lateinit var openResultButton: TextView

    private var processing = false
    private var lastOutputUri: Uri? = null

    companion object {
        private const val REQUEST_PICK_IMAGES = 41
        private const val STATE_URIS = "selected_uris"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()

        savedInstanceState?.getStringArrayList(STATE_URIS)?.forEach { value ->
            val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return@forEach
            photoItems.add(PhotoItem(uri, ImageCleaner.displayName(this, uri)))
        }
        renderSelection()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList(STATE_URIS, ArrayList(photoItems.map { it.uri.toString() }))
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }

    private fun buildScreen() {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setBackgroundColor(color(0xFFF6F5F0))
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(12), dp(22), dp(30))
        }
        scroll.addView(page)

        page.addView(buildHeader())
        page.addView(buildHero(), lp(top = 22))
        page.addView(buildPrivacyNote(), lp(top = 14))
        page.addView(buildSelectionSection(), lp(top = 28))
        page.addView(buildCleanButton(), lp(top = 18))
        page.addView(buildProgress(), lp(top = 14))
        page.addView(buildResultCard(), lp(top = 14))
        page.addView(buildFooter(), lp(top = 24))
        setContentView(scroll)
    }

    private fun buildHeader(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val mark = FrameLayout(this).apply {
            background = rounded(color(0xFF246B4B), 15f)
        }
        mark.addView(SymbolView(this, "shield", color(0xFFFFFFFF), strokeWidthDp = 1.55f).apply {
            layoutParams = FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER)
        })
        row.addView(mark, LinearLayout.LayoutParams(dp(46), dp(46)))

        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(11), 0, 0, 0)
        }
        brand.addView(text("SIN RASTRO", 15f, color(0xFF1C3028), bold = true, letterSpacing = .08f))
        brand.addView(text("FOTOS MÁS PRIVADAS", 10f, color(0xFF77847B), bold = true, letterSpacing = .08f), lp(top = 3))
        row.addView(brand, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(text("100% LOCAL", 10f, color(0xFF4D795F), bold = true).apply {
            background = rounded(color(0xFFE5F0E7), 30f)
            setPadding(dp(10), dp(7), dp(10), dp(7))
        })
        return row
    }

    private fun buildHero(): View {
        val card = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(color(0xFF193C30), color(0xFF102B25))
            ).apply { cornerRadius = dp(24).toFloat() }
            setPadding(dp(21), dp(20), dp(21), dp(20))
            minimumHeight = dp(218)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        topRow.addView(
            text("PRIVACIDAD PARA TUS FOTOS", 10f, color(0xFFAED9B9), bold = true, letterSpacing = .1f),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        val iconDisc = FrameLayout(this).apply {
            background = rounded(color(0xFF315849), 50f)
        }
        iconDisc.addView(SymbolView(this, "shield", color(0xFFBDE6C8), strokeWidthDp = 1.55f).apply {
            layoutParams = FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER)
        })
        topRow.addView(iconDisc, LinearLayout.LayoutParams(dp(44), dp(44)))
        content.addView(topRow)
        content.addView(text("Compartí la foto,\nno tus datos.", 27f, color(0xFFFFFFFF), bold = true).apply {
            setLineSpacing(dp(1).toFloat(), 1f)
        }, lp(top = 13))
        content.addView(text("Una copia nueva. El original queda intacto.", 13f, color(0xFFD2E0D7)), lp(top = 10))
        card.addView(content, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_VERTICAL
        ))
        return card
    }

    private fun buildPrivacyNote(): View {
        val note = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(color(0xFFE9EEE7), 14f)
            setPadding(dp(13), dp(12), dp(13), dp(12))
        }
        note.addView(SymbolView(this, "spark", color(0xFF397451)), LinearLayout.LayoutParams(dp(19), dp(19)))
        note.addView(text("Quita GPS, cámara, celular, fecha y otros datos.", 12f, color(0xFF415349)), lp(start = 9))
        return note
    }

    private fun buildSelectionSection(): View {
        selectedSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        heading.addView(text("Tus fotos", 19f, color(0xFF20332A), bold = true), LinearLayout.LayoutParams(0, dp(28), 1f))
        countLabel = text("0 elegidas", 11f, color(0xFF68796D), bold = true).apply {
            background = rounded(color(0xFFE9EEE7), 30f)
            setPadding(dp(11), dp(6), dp(11), dp(6))
        }
        heading.addView(countLabel)
        selectedSection.addView(heading)

        emptyPicker = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = rippleRounded(color(0xFFFFFFFF), 19f, color(0xFFDCE5DB), color(0xFFE9F0E9))
            setPadding(dp(18), dp(20), dp(18), dp(20))
            isClickable = true
            isFocusable = true
            setOnClickListener { openPicker() }
        }
        val addCircle = FrameLayout(this).apply {
            background = rounded(color(0xFFE5F1E7), 40f)
        }
        addCircle.addView(SymbolView(this, "image", color(0xFF246B4B)).apply {
            layoutParams = FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER)
        })
        emptyPicker.addView(addCircle, LinearLayout.LayoutParams(dp(52), dp(52)))
        emptyPicker.addView(text("Elegir imágenes", 16f, color(0xFF21362B), bold = true), lp(top = 11))
        emptyPicker.addView(text("Seleccioná una o varias del celular", 12f, color(0xFF78857B)), lp(top = 4))
        selectedSection.addView(emptyPicker, lp(top = 13))

        selectedList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        selectedSection.addView(selectedList, lp(top = 12))

        addMoreButton = text("+  Agregar más fotos", 13f, color(0xFF276B4C), bold = true).apply {
            gravity = Gravity.CENTER
            minHeight = dp(44)
            background = rippleRounded(color(0xFFEAF1E9), 14f, color(0xFFEAF1E9), color(0xFFDDE9DC))
            isClickable = true
            isFocusable = true
            setOnClickListener { openPicker() }
            visibility = View.GONE
        }
        selectedSection.addView(addMoreButton, lp(top = 9))
        return selectedSection
    }

    private fun buildCleanButton(): View {
        cleanButton = text("Crear copia limpia", 15f, color(0xFFFFFFFF), bold = true).apply {
            gravity = Gravity.CENTER
            minHeight = dp(58)
            background = rounded(color(0xFF246B4B), 17f)
            isClickable = true
            isFocusable = true
            setOnClickListener { startCleaning() }
        }
        return cleanButton
    }

    private fun buildProgress(): View {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        progressLabel = text("Preparando tus copias…", 12f, color(0xFF65756A))
        wrapper.addView(progressLabel)
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            progressTintList = ColorStateList.valueOf(color(0xFF39805A))
            progressBackgroundTintList = ColorStateList.valueOf(color(0xFFE0E8E0))
            isIndeterminate = false
        }
        wrapper.addView(progressBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(5)).apply {
            topMargin = dp(8)
        })
        progressBar.tag = "wrapper"
        return wrapper
    }

    private fun buildResultCard(): View {
        resultCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(color(0xFFE7F2E8), 18f)
            setPadding(dp(16), dp(15), dp(16), dp(15))
            visibility = View.GONE
        }
        resultTitle = text("", 15f, color(0xFF214D34), bold = true)
        resultCard.addView(resultTitle)
        resultDetail = text("", 12f, color(0xFF536D5A)).apply {
            setLineSpacing(dp(3).toFloat(), 1f)
        }
        resultCard.addView(resultDetail, lp(top = 6))
        openResultButton = text("Abrir última copia  →", 13f, color(0xFF246B4B), bold = true).apply {
            setPadding(0, dp(11), 0, 0)
            isClickable = true
            isFocusable = true
            setOnClickListener { openLastResult() }
        }
        resultCard.addView(openResultButton)
        return resultCard
    }

    private fun buildFooter(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(
                SymbolView(this@MainActivity, "lock", color(0xFF728277), strokeWidthDp = 1.55f),
                LinearLayout.LayoutParams(dp(15), dp(15))
            )
            addView(text("  Sin cuentas ni subidas. Todo queda en tu teléfono.", 11f, color(0xFF77847A)))
        }

    private fun openPicker() {
        if (processing) return
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        runCatching { startActivityForResult(intent, REQUEST_PICK_IMAGES) }
            .onFailure { Toast.makeText(this, "No se pudo abrir el selector de imágenes.", Toast.LENGTH_SHORT).show() }
    }

    @Deprecated("Uses the system document picker without an additional dependency.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_PICK_IMAGES || resultCode != RESULT_OK || data == null) return

        val incoming = LinkedHashSet<Uri>()
        data.clipData?.let { clip ->
            for (index in 0 until clip.itemCount) clip.getItemAt(index).uri?.let(incoming::add)
        }
        data.data?.let(incoming::add)
        val existing = photoItems.mapTo(mutableSetOf()) { it.uri }
        incoming.filterNot(existing::contains).forEach { uri ->
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            photoItems.add(PhotoItem(uri, ImageCleaner.displayName(this, uri)))
        }
        resultCard.visibility = View.GONE
        renderSelection()
    }

    private fun renderSelection() {
        val count = photoItems.size
        countLabel.text = if (count == 1) "1 elegida" else "$count elegidas"
        val hasPhotos = count > 0
        emptyPicker.visibility = if (hasPhotos) View.GONE else View.VISIBLE
        selectedList.visibility = if (hasPhotos) View.VISIBLE else View.GONE
        addMoreButton.visibility = if (hasPhotos) View.VISIBLE else View.GONE
        cleanButton.text = when (count) {
            0 -> "Crear copia limpia"
            1 -> "Crear copia limpia"
            else -> "Crear $count copias limpias"
        }
        cleanButton.alpha = if (hasPhotos && !processing) 1f else .56f
        cleanButton.isEnabled = hasPhotos && !processing
        addMoreButton.isEnabled = !processing

        selectedList.removeAllViews()
        photoItems.take(5).forEach { item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = rounded(color(0xFFFFFFFF), 15f)
                setPadding(dp(9), dp(8), dp(10), dp(8))
            }
            val thumbnail = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = rounded(color(0xFFE8EEE8), 10f)
                clipToOutline = true
                tag = item.uri
                contentDescription = "Vista previa de ${item.name}"
            }
            row.addView(thumbnail, LinearLayout.LayoutParams(dp(46), dp(46)))
            val labels = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(11), 0, dp(8), 0)
            }
            labels.addView(text(item.name, 13f, color(0xFF2C3D32), bold = true).apply {
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            labels.addView(text("El original no se modifica", 10f, color(0xFF859087)), lp(top = 3))
            row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(text("×", 22f, color(0xFF7F8A80)).apply {
                gravity = Gravity.CENTER
                minWidth = dp(32)
                minHeight = dp(36)
                isClickable = true
                isFocusable = true
                background = rippleRounded(color(0xFFFFFFFF), 18f, color(0xFFFFFFFF), color(0xFFF0F3EF))
                contentDescription = "Quitar ${item.name}"
                setOnClickListener {
                    photoItems.removeAll { it.uri == item.uri }
                    resultCard.visibility = View.GONE
                    renderSelection()
                }
            })
            selectedList.addView(row, lp(bottom = 7))
            loadThumbnail(item.uri, thumbnail)
        }
        if (count > 5) {
            selectedList.addView(text("+ ${count - 5} fotos más", 12f, color(0xFF68786D)), lp(start = 5, top = 1))
        }
    }

    private fun loadThumbnail(uri: Uri, target: ImageView) {
        worker.execute {
            val bitmap = runCatching { contentResolver.loadThumbnail(uri, Size(128, 128), null) }.getOrNull()
            if (bitmap != null) {
                runOnUiThread {
                    if (!isFinishing && target.tag == uri) target.setImageBitmap(bitmap)
                    else bitmap.recycle()
                }
            }
        }
    }

    private fun startCleaning() {
        if (processing || photoItems.isEmpty()) return
        processing = true
        savedUris.clear()
        lastOutputUri = null
        resultCard.visibility = View.GONE
        cleanButton.isEnabled = false
        cleanButton.alpha = .56f
        addMoreButton.isEnabled = false

        val progressWrapper = progressBar.parent as View
        progressWrapper.visibility = View.VISIBLE
        progressBar.max = photoItems.size
        progressBar.progress = 0

        val work = photoItems.toList()
        var completed = 0
        var failures = 0
        var resized = 0
        worker.execute {
            work.forEach { item ->
                runCatching { ImageCleaner.clean(this, item.uri) }
                    .onSuccess { result ->
                        savedUris.add(result.uri)
                        lastOutputUri = result.uri
                        if (result.resizedForMemory) resized++
                    }
                    .onFailure { failures++ }
                completed++
                val done = completed
                runOnUiThread {
                    progressBar.progress = done
                    progressLabel.text = "Procesando $done de ${work.size}…"
                }
            }
            runOnUiThread {
                processing = false
                progressWrapper.visibility = View.GONE
                renderSelection()
                showResults(work.size - failures, failures, resized)
            }
        }
    }

    private fun showResults(successes: Int, failures: Int, resized: Int) {
        if (successes == 0) {
            resultCard.background = rounded(color(0xFFF8E9E3), 18f)
            resultTitle.setTextColor(color(0xFF8A3E2C))
            resultDetail.setTextColor(color(0xFF765A50))
            resultTitle.text = "No se pudo crear la copia"
            resultDetail.text = "Probá con otra imagen o elegila desde una carpeta local del teléfono."
            openResultButton.visibility = View.GONE
        } else {
            resultCard.background = rounded(color(0xFFE7F2E8), 18f)
            resultTitle.setTextColor(color(0xFF214D34))
            resultDetail.setTextColor(color(0xFF536D5A))
            resultTitle.text = if (failures == 0) {
                if (successes == 1) "¡Lista! Una copia limpia." else "¡Listo! $successes copias limpias."
            } else {
                "$successes listas · $failures no se pudieron procesar"
            }
            val details = mutableListOf(
                "Nombres neutros · Imágenes / SinRastro.",
                "Tus originales siguen intactos."
            )
            if (resized > 0) details.add("$resized foto(s) grande(s) se redujeron para evitar bloqueos.")
            resultDetail.text = details.joinToString("\n")
            openResultButton.visibility = View.VISIBLE
        }
        resultCard.visibility = View.VISIBLE
    }

    private fun openLastResult() {
        val uri = lastOutputUri ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(intent) }
            .onFailure { Toast.makeText(this, "La copia está en Imágenes / SinRastro.", Toast.LENGTH_LONG).show() }
    }

    private fun text(
        value: CharSequence,
        size: Float,
        tint: Int,
        bold: Boolean = false,
        letterSpacing: Float = 0f
    ) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(tint)
        typeface = if (bold) android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        else android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        this.letterSpacing = letterSpacing
        includeFontPadding = false
    }

    private fun lp(
        top: Int = 0,
        bottom: Int = 0,
        start: Int = 0,
        end: Int = 0
    ) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply {
        topMargin = dp(top)
        bottomMargin = dp(bottom)
        marginStart = dp(start)
        marginEnd = dp(end)
    }

    private fun rounded(fill: Int, radius: Float, strokeColor: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radius.toInt()).toFloat()
            strokeColor?.let { setStroke(dp(1), it) }
        }

    private fun rippleRounded(fill: Int, radius: Float, stroke: Int, pressed: Int) =
        RippleDrawable(
            ColorStateList.valueOf(pressed),
            rounded(fill, radius, stroke),
            null
        )

    private fun color(value: Long) = (value or 0xFF000000).toInt()
    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
    private fun dp(value: Float) = (value * resources.displayMetrics.density + .5f).toInt()
}

private class SymbolView(
    context: android.content.Context,
    private val symbol: String,
    private val tint: Int,
    strokeWidthDp: Float = 2.1f
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tint
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthDp * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val d = resources.displayMetrics.density
        val side = minOf(width, height).toFloat()
        val scale = side / 24f
        canvas.save()
        canvas.translate((width - side) / 2f, (height - side) / 2f)
        canvas.scale(scale, scale)
        when (symbol) {
            "shield" -> {
                val path = Path().apply {
                    moveTo(12f, 2f)
                    lineTo(20f, 5f)
                    lineTo(20f, 11f)
                    cubicTo(20f, 16f, 16.8f, 20f, 12f, 22f)
                    cubicTo(7.2f, 20f, 4f, 16f, 4f, 11f)
                    lineTo(4f, 5f)
                    close()
                }
                canvas.drawPath(path, paint)
                canvas.drawLine(8.5f, 12.2f, 10.8f, 14.5f, paint)
                canvas.drawLine(10.8f, 14.5f, 15.8f, 9.5f, paint)
            }
            "image" -> {
                canvas.drawRoundRect(RectF(3f, 4f, 21f, 20f), 3f, 3f, paint)
                canvas.drawCircle(9f, 9f, 1.5f, paint)
                val path = Path().apply {
                    moveTo(4f, 17f)
                    lineTo(9f, 12f)
                    lineTo(12f, 15f)
                    lineTo(15f, 12f)
                    lineTo(20f, 17f)
                }
                canvas.drawPath(path, paint)
            }
            "spark" -> {
                canvas.drawLine(12f, 2.5f, 12f, 8.5f, paint)
                canvas.drawLine(12f, 15.5f, 12f, 21.5f, paint)
                canvas.drawLine(2.5f, 12f, 8.5f, 12f, paint)
                canvas.drawLine(15.5f, 12f, 21.5f, 12f, paint)
                canvas.drawLine(5.3f, 5.3f, 9f, 9f, paint)
                canvas.drawLine(15f, 15f, 18.7f, 18.7f, paint)
                canvas.drawLine(18.7f, 5.3f, 15f, 9f, paint)
                canvas.drawLine(9f, 15f, 5.3f, 18.7f, paint)
            }
            "lock" -> {
                canvas.drawRoundRect(RectF(4f, 10f, 20f, 21f), 2.5f, 2.5f, paint)
                val path = Path().apply {
                    moveTo(7f, 10f)
                    lineTo(7f, 7f)
                    cubicTo(7f, 1.8f, 17f, 1.8f, 17f, 7f)
                    lineTo(17f, 10f)
                }
                canvas.drawPath(path, paint)
                canvas.drawCircle(12f, 15f, 1f, paint)
            }
        }
        canvas.restore()
    }
}
