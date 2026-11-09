
package com.woodlands.mobile.TutorialDemo

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.woodlands.mobile.MainActivity

internal fun MainActivity.tutorialDemoGallery() {
    content.removeAllViews()

    fun addText(
        value: String,
        size: Float,
        colour: Int,
        bold: Boolean = false
    ) {
        val view = TextView(this).apply {
            text = value
            textSize = size
            setTextColor(colour)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        content.addView(view)
    }

    fun addButton(label: String, action: () -> Unit) {
        val view = TextView(this).apply {
            text = label
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(blue)
            isClickable = true
            setOnClickListener { action() }
        }

        content.addView(
            view,
            LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(dp(16), dp(6), dp(16), dp(6))
            }
        )
    }

    addText("TUTORIAL DEMO", 12f, red, true)
    addText("Browse products", 25f, blue, true)

    addText(
        "This is a practice version of the product gallery. " +
                "You can explore the examples without changing your real app data.",
        14f,
        muted
    )

    addText(
        "All tutorial actions are temporary and are not saved once the tutorial ends",
        13f,
        green,
        true
    )

    val categories = listOf("All", "Kitchens", "TV Units", "Bedrooms")

    categories.forEach { category ->
        addButton(
            if (TutorialDemoData.selectedCategory == category) {
                "✓ $category"
            } else {
                category
            }
        ) {
            TutorialDemoData.selectedCategory = category
            tutorialDemoGallery()
        }
    }

    val samples = listOf(
        "Sample Kitchen Design" to "Kitchens",
        "Modern TV Unit" to "TV Units",
        "Bedroom Storage Design" to "Bedrooms"
    )

    samples
        .filter {
            TutorialDemoData.selectedCategory == "All" ||
                    it.second == TutorialDemoData.selectedCategory
        }
        .forEach { sample ->
            addText(sample.first, 17f, blue, true)
            addText("Example product · ${sample.second}", 13f, muted)

            addButton("View example") {
                TutorialDemoData.selectedProduct = sample.first
                openTutorialDemo("product")
            }
        }

    addButton("Return to tutorial") {
        returnToTutorial()
    }
}
