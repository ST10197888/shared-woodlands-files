package com.woodlands.mobile

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import kotlin.math.abs

private class HeroSlide(
    val heading: String,
    val accent: String,
    val sub: String,
    val image: Int,
    val cta: String,
    val category: String
)

private class CatInfo(val label: String, val desc: String, val image: Int)

private class HeroPager(context: Context) : FrameLayout(context) {
    var onSwipe: ((Int) -> Unit)? = null
    private var downX = 0f
    private var downY = 0f
    private var swiping = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop

    init { isClickable = true }

    private fun track(e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y; swiping = false }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.x - downX
                val dy = e.y - downY
                if (!swiping && abs(dx) > slop && abs(dx) > abs(dy)) {
                    swiping = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
        }
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        track(e)
        return swiping
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        track(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_UP -> {
                if (swiping && abs(e.x - downX) > slop * 3) onSwipe?.invoke(if (e.x < downX) 1 else -1)
                swiping = false
            }
            MotionEvent.ACTION_CANCEL -> swiping = false
        }
        return true
    }
}

private fun MainActivity.clipRound(v: View, fill: Int, stroke: Int, radius: Int) {
    v.background = bg(fill, stroke, radius)
    v.clipToOutline = true
    v.outlineProvider = ViewOutlineProvider.BACKGROUND
    v.elevation = dp(2).toFloat()
}

internal fun MainActivity.webHeader(eyebrow: String, title: String, action: String, onAction: () -> Unit) {
    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM; setPadding(dp(16), dp(26), dp(16), dp(10)) }
    val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    left.addView(tv(eyebrow, 10, red).apply { setTypeface(typeface, Typeface.BOLD); letterSpacing = 0.12f })
    left.addView(tv(title, 22, blue).apply { setTypeface(Typeface.SERIF, Typeface.BOLD) })
    row.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
    row.addView(tv("$action →", 12, red).apply { setTypeface(typeface, Typeface.BOLD); setPadding(dp(8), dp(8), 0, dp(4)); setOnClickListener { onAction() } })
    content.addView(row)
}

internal fun MainActivity.heroCarousel() {
    val slides = listOf(
        HeroSlide("Built to Last.", "Designed to Impress.", "Premium custom-built kitchen units, TV stands & built-in cupboards. PG Bison certified.", R.drawable.kitchen_12, "Shop Kitchens", "Kitchen Units"),
        HeroSlide("Your Bedroom,", "Perfectly Organised.", "Floor-to-ceiling built-in cupboards made exactly to your space and style.", R.drawable.kitchen_6, "View Cupboards", "Built-In Cupboards"),
        HeroSlide("Precision Cutting.", "Zero Compromise.", "CNC board cutting and edge banding for contractors, designers & trade clients.", R.drawable.kitchen_3, "Cutting & Edging", "Cutting & Edging"),
        HeroSlide("Entertainment Spaces", "Done Right.", "Custom TV units and entertainment consoles — wall-mounted or floor-standing.", R.drawable.tv_9, "View TV Stands", "TV Stands")
    )
    val pager = HeroPager(this)
    val images = slides.mapIndexed { i, s ->
        ImageView(this).apply { setImageResource(s.image); scaleType = ImageView.ScaleType.CENTER_CROP; alpha = if (i == 0) 1f else 0f }
    }
    images.forEach { pager.addView(it, FrameLayout.LayoutParams(-1, -1)) }
    val shade = View(this).apply {
        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.argb(70, 22, 50, 28), Color.argb(230, 22, 50, 28)))
    }
    pager.addView(shade, FrameLayout.LayoutParams(-1, -1))

    val heading = tv("", 28, Color.WHITE).apply { setTypeface(Typeface.SERIF, Typeface.BOLD) }
    val accent = tv("", 28, tan).apply { setTypeface(Typeface.SERIF, Typeface.BOLD) }
    val sub = tv("", 13, Color.rgb(240, 242, 238)).apply { setPadding(0, dp(10), 0, dp(14)) }
    val primary = button("", red, Color.WHITE)
    val quoteBtn = Button(this).apply {
        text = "Get a Free Quote"
        textSize = 13f
        setTextColor(Color.WHITE)
        setAllCaps(false)
        setTypeface(typeface, Typeface.BOLD)
        background = rippleBg(Color.argb(60, 22, 50, 28), Color.argb(160, 255, 255, 255), 10, 40)
        minHeight = dp(44)
        minimumHeight = dp(44)
        stateListAnimator = null
        setOnClickListener { showScreen("quote") }
    }
    val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    buttons.addView(primary, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, dp(8), 0) })
    buttons.addView(quoteBtn, LinearLayout.LayoutParams(0, -2, 1f))

    val textBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), 0, dp(20), dp(44)) }
    textBox.addView(heading)
    textBox.addView(accent)
    textBox.addView(sub)
    textBox.addView(buttons)
    pager.addView(textBox, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))

    val dotRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
    val dots = slides.map { View(this) }
    dots.forEach { dotRow.addView(it) }
    pager.addView(dotRow, FrameLayout.LayoutParams(-1, dp(24), Gravity.BOTTOM).apply { bottomMargin = dp(10) })

    var index = 0

    fun paintDots() {
        dots.forEachIndexed { i, d ->
            val on = i == index
            d.background = bg(if (on) tan else Color.argb(110, 255, 255, 255), Color.TRANSPARENT, 3)
            d.layoutParams = LinearLayout.LayoutParams(dp(if (on) 24 else 7), dp(6)).apply { setMargins(dp(3), 0, dp(3), 0) }
        }
    }

    fun fillText() {
        val s = slides[index]
        heading.text = s.heading
        accent.text = s.accent
        sub.text = s.sub
        primary.text = s.cta + " →"
    }

    fun go(next: Int) {
        val target = (next + slides.size) % slides.size
        if (target == index) return
        index = target
        images.forEachIndexed { i, v -> v.animate().alpha(if (i == index) 1f else 0f).setDuration(650).start() }
        textBox.animate().alpha(0f).setDuration(160).withEndAction {
            fillText()
            textBox.animate().alpha(1f).setDuration(320).start()
        }.start()
        paintDots()
    }

    val tick = object : Runnable {
        override fun run() {
            go(index + 1)
            pager.postDelayed(this, 5500)
        }
    }

    fun restart() {
        pager.removeCallbacks(tick)
        pager.postDelayed(tick, 5500)
    }

    primary.setOnClickListener { galleryFilter = slides[index].category; showScreen("gallery") }
    dots.forEachIndexed { i, d -> d.setOnClickListener { go(i); restart() } }
    pager.onSwipe = { dir -> go(index + dir); restart() }
    pager.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) { v.postDelayed(tick, 5500) }
        override fun onViewDetachedFromWindow(v: View) { v.removeCallbacks(tick) }
    })

    fillText()
    paintDots()
    content.addView(pager, LinearLayout.LayoutParams(-1, dp(400)))
}

private fun MainActivity.categoryCard(label: String, desc: String, image: Int, count: Int): View {
    val box = FrameLayout(this)
    clipRound(box, Color.rgb(35, 33, 30), Color.TRANSPARENT, 16)
    box.addView(ImageView(this).apply { setImageResource(image); scaleType = ImageView.ScaleType.CENTER_CROP }, FrameLayout.LayoutParams(-1, -1))
    box.addView(View(this).apply {
        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.argb(0, 22, 50, 28), Color.argb(235, 22, 50, 28)))
    }, FrameLayout.LayoutParams(-1, -1))
    val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(12)) }
    val countText = if (count == 0) "COMING SOON" else "$count PRODUCT${if (count == 1) "" else "S"}"
    info.addView(tv(countText, 9, tan).apply { setTypeface(typeface, Typeface.BOLD); letterSpacing = 0.1f })
    info.addView(tv(label, 17, Color.WHITE).apply { setTypeface(Typeface.SERIF, Typeface.BOLD) })
    info.addView(tv(desc, 10, Color.rgb(224, 231, 223)).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END; setPadding(0, dp(2), 0, 0) })
    box.addView(info, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
    box.setOnClickListener { galleryFilter = label; showScreen("gallery") }
    return box
}

internal fun MainActivity.categoryGrid() {
    webHeader("BROWSE BY CATEGORY", "What are you looking for?", "View all") { galleryFilter = "All"; showScreen("gallery") }
    val products = db.loadProducts()
    val cats = listOf(
        CatInfo("Kitchen Units", "Custom kitchens crafted in PG Bison melamine", R.drawable.kitchen_12),
        CatInfo("TV Stands", "Wall-mounted and floor-standing entertainment units", R.drawable.tv_1),
        CatInfo("Built-In Cupboards", "Floor-to-ceiling fitted wardrobes and storage", R.drawable.kitchen_6),
        CatInfo("Cutting & Edging", "CNC precision cutting and edge banding services", R.drawable.kitchen_3)
    )
    cats.chunked(2).forEach { pair ->
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        pair.forEachIndexed { i, c ->
            val count = products.count { it.category == c.label }
            val params = LinearLayout.LayoutParams(0, dp(190), 1f).apply { setMargins(if (i == 0) 0 else dp(5), 0, if (i == 0) dp(5) else 0, 0) }
            row.addView(categoryCard(c.label, c.desc, c.image, count), params)
        }
        content.addView(row, marginParams(16, 5, 16, 5))
    }
}

private fun MainActivity.productTile(p: Product): View {
    val tile = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    clipRound(tile, Color.WHITE, lineColor, 16)
    tile.setOnClickListener { selectedProduct = p.id; showScreen("product") }
    val imgBox = FrameLayout(this)
    imgBox.addView(ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setProductImage(this, p.image) }, FrameLayout.LayoutParams(-1, -1))
    val tag = p.tag
    if (!tag.isNullOrBlank()) {
        val badge = tv(tag.uppercase(), 9, blue).apply {
            setTypeface(typeface, Typeface.BOLD)
            letterSpacing = 0.08f
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = bg(tan, Color.TRANSPARENT, 6)
        }
        imgBox.addView(badge, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { setMargins(dp(10), dp(10), 0, 0) })
    }
    tile.addView(imgBox, LinearLayout.LayoutParams(-1, dp(150)))
    val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(14)) }
    body.addView(tv(p.category.uppercase(), 9, red).apply { setTypeface(typeface, Typeface.BOLD); letterSpacing = 0.1f })
    body.addView(tv(p.title, 16, blue).apply { setTypeface(Typeface.SERIF, Typeface.BOLD); maxLines = 1; ellipsize = TextUtils.TruncateAt.END; setPadding(0, dp(2), 0, 0) })
    body.addView(tv(p.tagline, 11, muted).apply { minLines = 2; maxLines = 2; ellipsize = TextUtils.TruncateAt.END; setPadding(0, dp(3), 0, 0) })
    if (p.price.isNotBlank()) {
        val priceRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, 0) }
        priceRow.addView(tv(p.price, 14, blue).apply { setTypeface(typeface, Typeface.BOLD) }, LinearLayout.LayoutParams(0, -2, 1f))
        priceRow.addView(tv("View →", 12, red).apply { setTypeface(typeface, Typeface.BOLD) })
        body.addView(priceRow)
    }
    tile.addView(body)
    return tile
}

internal fun MainActivity.featuredStrip() {
    val all = db.loadProducts()
    val picks = all.filter { it.tag == "Popular" || it.tag == "New" }.ifEmpty { all }.take(6)
    if (picks.isEmpty()) return
    webHeader("HANDPICKED", "Featured products", "View all") { galleryFilter = "All"; showScreen("gallery") }
    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(12), dp(4), dp(12), dp(10)) }
    picks.forEach { p ->
        row.addView(productTile(p), LinearLayout.LayoutParams(dp(230), -2).apply { setMargins(dp(4), 0, dp(4), 0) })
    }
    content.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) })
}

internal fun MainActivity.bisonBanner() {
    val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(16)) }
    clipRound(box, greenBg, Color.rgb(205, 218, 203), 16)
    box.addView(tv("PG BISON PARTNER", 10, red).apply { setTypeface(typeface, Typeface.BOLD); letterSpacing = 0.12f })
    box.addView(tv("Authorised PG Bison Partner", 18, blue).apply { setTypeface(Typeface.SERIF, Typeface.BOLD); setPadding(0, dp(2), 0, dp(4)) })
    box.addView(tv("Every unit we build uses 100% genuine PG Bison board materials — South Africa's standard for quality.", 12, muted))
    val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(12), 0, 0) }
    listOf("Chipboard", "MDF", "Supawood", "Melamine").forEach {
        chips.addView(chip(it), LinearLayout.LayoutParams(-2, -2).apply { setMargins(0, 0, dp(6), 0) })
    }
    box.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(chips) })
    content.addView(box, marginParams(16, 26, 16, 8))
}

internal fun MainActivity.testimonialStrip() {
    val list = db.loadTestimonials().take(4)
    if (list.isEmpty()) return
    webHeader("WHAT CLIENTS SAY", "Trusted across Gauteng", "Read all") { showScreen("testimonials") }
    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(12), dp(4), dp(12), dp(10)) }
    list.forEach { t ->
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)) }
        clipRound(c, Color.WHITE, lineColor, 16)
        c.addView(tv("★".repeat(t.rating.coerceIn(0, 5)), 15, tan))
        c.addView(tv("\"${t.review}\"", 13, muted).apply { maxLines = 5; ellipsize = TextUtils.TruncateAt.END; setPadding(0, dp(8), 0, dp(10)) })
        c.addView(tv(t.name, 13, blue).apply { setTypeface(typeface, Typeface.BOLD) })
        c.addView(tv(t.role, 11, muted))
        row.addView(c, LinearLayout.LayoutParams(dp(280), -2).apply { setMargins(dp(4), 0, dp(4), 0) })
    }
    content.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) })
}

internal fun MainActivity.homeFooter() {
    val f = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(28), dp(20), dp(28)); setBackgroundColor(blue) }
    f.addView(tv("Woodlands", 24, Color.WHITE).apply { setTypeface(Typeface.SERIF, Typeface.BOLD); gravity = Gravity.CENTER })
    f.addView(tv("DESIGNER BOARDS", 10, tan).apply { setTypeface(typeface, Typeface.BOLD); letterSpacing = 0.2f; gravity = Gravity.CENTER; setPadding(0, dp(2), 0, dp(12)) })
    f.addView(tv("Premium custom-built units using PG Bison materials.", 12, Color.rgb(224, 231, 223)).apply { gravity = Gravity.CENTER })
    f.addView(tv("Soweto  ·  Roodepoort  ·  Randfontein", 12, tan).apply { gravity = Gravity.CENTER; setPadding(0, dp(10), 0, 0) })
    content.addView(f, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) })
}