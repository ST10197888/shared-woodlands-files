package com.woodlands.mobile

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout

internal fun MainActivity.welcomeScreen() {
    content.removeAllViews()

    val page = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(26), dp(30), dp(26), dp(30))
    }

    val logo = ImageView(this).apply {
        setImageResource(R.drawable.app_logo_transparent)
        scaleType = ImageView.ScaleType.FIT_CENTER
        contentDescription = "Woodlands Designer Boards logo"
    }

    page.addView(
        logo,
        LinearLayout.LayoutParams(dp(200), dp(200)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(22)
        }
    )

    page.addView(
        tv("WOODLANDS DESIGNER BOARDS", 11, red).apply {
            setTypeface(typeface, Typeface.BOLD)
            letterSpacing = 0.16f
            gravity = Gravity.CENTER
        }
    )

    page.addView(
        tv("Welcome to Woodlands", 27, blue).apply {
            setTypeface(Typeface.SERIF, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(12))
        }
    )

    page.addView(
        tv(
            "Woodlands helps you bring your spaces to life with custom-made kitchens, " +
                    "TV stands, built-in cupboards and other tailored board solutions.\n\n" +
                    "Explore our products, discover what is possible, find your nearest branch " +
                    "and request a quote for your project. All from one place.",
            15,
            text
        ).apply {
            gravity = Gravity.CENTER
            setLineSpacing(dp(3).toFloat(), 1.12f)
            setPadding(0, 0, 0, dp(24))
        }
    )

    page.addView(
        button("Get Started ", blue, Color.WHITE).apply {
            setOnClickListener {
                showScreen("tutorial")
            }
        },
        LinearLayout.LayoutParams(-1, dp(52)).apply {
            setMargins(0, dp(8), 0, dp(10))
        }
    )

    page.addView(
        tv("Let us show you around.", 12, muted).apply {
            gravity = Gravity.CENTER
        }
    )

    content.addView(page, LinearLayout.LayoutParams(-1, -1))
}