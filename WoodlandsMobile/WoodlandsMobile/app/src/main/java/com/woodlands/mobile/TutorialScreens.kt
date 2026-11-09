package com.woodlands.mobile

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.woodlands.mobile.TutorialDemo.TutorialDemoController
import com.woodlands.mobile.TutorialDemo.TutorialDemoData

/**
 * One-screen, eight-step guide built with the same colours and UI helpers as the
 * existing Woodlands app. The examples link to the real app screens where useful.
 */
internal fun MainActivity.tutorialScreen() {
    data class Step(val title: String, val text: String)

    val steps = listOf(
        Step(
            "Welcome",
            "Welcome to Woodlands. Browse custom kitchens, TV stands and cupboards, and request a quote in minutes. This short tour takes about a minute."
        ),
        Step(
            "Getting around",
            "Find your way. The menu button opens more pages. The bottom bar jumps to Home, Gallery, Quote, Branches and More. Quote starts a request anywhere."
        ),
        Step(
            "Browse products",
            "Browse the gallery. Tap a category to filter the products. Tap any product to see more."
        ),
        Step(
            "Product details",
            "See the details. Check the price, lead time, features and finishes. Tap Request Quote to start with this product selected."
        ),
        Step(
            "Request a quote",
            "Request a free quote. Fill in your details and project description, choose your branch, and read the privacy notice. Submit, and you'll get a confirmation email."
        ),
        Step(
            "Find a branch",
            "Find your nearest branch. Call the branch, open it in Maps, or start a quote for that branch."
        ),
        Step(
            "Account and Help",
            "Your account and help. Register or log in to see your quotes. Open Help any time for tips, FAQs and your branch manager's contact details."
        ),
        Step(
            "Stay up to date",
            "Pull down to refresh. Swipe down at the top of any screen to update products and quotes. The app also works offline with your last saved data."
        )
    )

    var selectedCategory = TutorialDemoData.selectedCategory
    var feedbackText = "Try the example below."

    val page = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(18))
    }
    content.addView(page)

    val brand = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        background = bg(blue, Color.TRANSPARENT, 16)
    }
    brand.addView(tv("WOODLANDS APP GUIDE", 10, tan).apply {
        setTypeface(typeface, Typeface.BOLD)
        letterSpacing = 0.14f
    })
    brand.addView(tv("Explore the app", 25, Color.WHITE).apply {
        setTypeface(Typeface.SERIF, Typeface.BOLD)
        setPadding(0, dp(5), 0, dp(3))
    })
    brand.addView(tv("An interactive walkthrough · 8 quick steps", 12, Color.rgb(226, 232, 224)))
    page.addView(brand)

    val progressScroll = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        clipToPadding = false
    }
    val progressRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(14), 0, dp(8))
    }
    progressScroll.addView(progressRow)
    page.addView(progressScroll)

    val counter = tv("", 11, muted).apply {
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(8), 0, dp(2))
    }
    page.addView(counter)

    val title = tv("", 24, blue).apply {
        setTypeface(Typeface.SERIF, Typeface.BOLD)
        setPadding(0, dp(2), 0, dp(5))
    }
    page.addView(title)

    val description = tv("", 14, text).apply {
        setLineSpacing(0f, 1.12f)
        setPadding(0, 0, 0, dp(14))
    }
    page.addView(description)

    val demoCard = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(14), dp(14), dp(12))
        background = bg(Color.WHITE, lineColor, 16)
        elevation = dp(2).toFloat()
    }
    demoCard.addView(tv("TRY IT", 10, red).apply {
        setTypeface(typeface, Typeface.BOLD)
        letterSpacing = 0.12f
    })
    val demoContent = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    demoCard.addView(demoContent)
    val feedback = tv(feedbackText, 12, green).apply {
        setPadding(0, dp(10), 0, 0)
    }
    demoCard.addView(feedback)
    // Added the interactive demo card to the tutorial page.
    page.addView(
        demoCard,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, dp(4), 0, dp(12))
        }
    )
    page.addView(
        tv(
            "All tutorial actions are temporary and are not saved once the tutorial ends",
            11,
            muted
        ).apply {
            setPadding(dp(4), dp(4), dp(4), dp(12))
        }
    )

    val navRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(8), 0, dp(8))
    }
    val backButton = outlineButton("← Back", blue)
    val nextButton = button("Next →", blue, Color.WHITE)
    navRow.addView(backButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
        setMargins(0, 0, dp(8), 0)
    })
    navRow.addView(nextButton, LinearLayout.LayoutParams(0, dp(48), 1f))
    page.addView(navRow)

    fun setFeedback(message: String) {
        feedbackText = message
        feedback.text = message
    }

    fun demoText(value: String, size: Int = 13, colour: Int = text) {
        demoContent.addView(tv(value, size, colour).apply {
            setPadding(0, dp(4), 0, dp(4))
        })
    }

    fun demoButton(label: String, action: () -> Unit) {
        demoContent.addView(outlineButton(label, blue).apply {
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(-1, dp(44)).apply {
            setMargins(0, dp(5), 0, dp(2))
        })
    }

    fun demoPill(label: String, active: Boolean, action: () -> Unit): TextView {
        return chip(label, active).apply { setOnClickListener { action() } }
    }

    fun renderDemo() {
        demoContent.removeAllViews()
        when (tutorialStepIndex) {
            0 -> {
                demoText("WOODLANDS DESIGNER BOARDS", 10, red)
                demoText("Built to last. Designed for you.", 20, blue)
                demoText("Custom kitchens · TV stands · Built-in cupboards", 12, muted)
                demoButton("Explore products") { setFeedback("You can explore the Gallery in the demonstration.") }
                demoButton("Request a quote") { TutorialDemoController.open(this, "quote") }
            }

            1 -> {
                demoText("The app's main navigation", 15, blue)
                demoText("MENU  ·  HOME  ·  GALLERY  ·  QUOTE  ·  BRANCHES  ·  MORE", 11, muted)
                demoText("Try one of the real app destinations:", 12, muted)
                demoButton("Home") {
                    setFeedback("Home displays the main app dashboard. This is a demonstration only.")
                }

                demoButton("Gallery") { setFeedback("Gallery lets you browse kitchens, TV stands, cupboards and other products.") }
                demoButton("Quote") { setFeedback("The Quote section lets customers request a quote. No real quote is created in this tutorial.") }
                demoButton("Branches") { setFeedback("The Branches section provides branch locations and contact information.") }
                demoButton("More") { setFeedback("More provides access to additional app features, account options and help.") }
            }

            2 -> {
                demoText("Choose a gallery category", 15, blue)

                val categories = listOf(
                    "All",
                    "Kitchen Units",
                    "TV Stands",
                    "Built-In Cupboards",
                    "Cutting & Edging"
                )

                val chipsRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, dp(5), 0, dp(5))
                }

                categories.forEach { category ->
                    chipsRow.addView(
                        demoPill(category, selectedCategory == category) {
                            selectedCategory = category
                            TutorialDemoData.selectedCategory = category
                            setFeedback("$category selected. This is a simulated gallery filter.")
                            renderDemo()
                        },
                        LinearLayout.LayoutParams(-2, -2).apply {
                            setMargins(0, 0, dp(6), 0)
                        }
                    )
                }

                demoContent.addView(
                    HorizontalScrollView(this).apply {
                        isHorizontalScrollBarEnabled = false
                        addView(chipsRow)
                    }
                )

                demoText("Example products are displayed in the Gallery.", 12, muted)
                demoText("Your selected category: $selectedCategory", 13, green)
                demoText("This selection is temporary and does not change your real Gallery.", 12, muted)
            }

            3 -> {
                demoText("Example product details", 15, blue)
                demoText("Woodlands Custom Kitchen", 18, blue)
                demoText("Designed to suit your home and your style.", 13, muted)
                demoText("Example price: R25,000", 14, text)
                demoText("Example lead time: 4–6 weeks", 12, muted)
                demoText("Features: Custom sizing, finish options and quality materials.", 12, muted)

                demoButton("Select this example product") {
                    TutorialDemoData.selectedProduct = "Woodlands Custom Kitchen"
                    setFeedback("Example product selected. No real product or quote has been changed.")
                }

                demoText(
                    "This is a demonstration only. It does not open a real product or quote.",
                    12,
                    green
                )
            }

            4 -> {
                demoText("The quote form asks for:", 15, blue)
                listOf(
                    "First and last name",
                    "Email address and optional phone number",
                    "Nearest branch and service required",
                    "Project description",
                    "Privacy notice acknowledgement"
                ).forEach { item ->
                    demoText("✓  $item", 12, muted)
                }
                demoText("The live form validates required fields and saves the quote request.", 12, muted)
                demoButton("Try the example quote form") { TutorialDemoController.open(this, "quote") }
            }

            5 -> {
                demoText("Find a Woodlands branch", 15, blue)

                demoText("Example Branch 1", 14, blue)
                demoText("Randfontein · Monday–Friday, 08:00–17:00", 12, muted)

                demoText("Example Branch 2", 14, blue)
                demoText("Soweto · Monday–Friday, 08:00–17:00", 12, muted)

                demoText("Example Branch 3", 14, blue)
                demoText("Roodepoort · Monday–Friday, 08:00–17:00", 12, muted)

                demoText("Branch services may include:", 13, blue)
                demoText("• Contacting a branch", 12, muted)
                demoText("• Finding directions", 12, muted)
                demoText("• Requesting a quote", 12, muted)

                demoButton("Try the branch example") {
                    TutorialDemoData.selectedBranch = "Example Branch 1"
                    setFeedback("Example Branch 1 selected. No real branch has been contacted.")
                }
            }

            // This section is explanatory only: completing the tutorial never requires sign-in.
            6 -> {
                demoText("Your account", 15, blue)

                demoText(
                    "Register or log in to access your account and see your saved quotes.",
                    12,
                    muted
                )

                demoText("Help and information", 15, blue)

                demoText(
                    "The Help section provides tips, FAQs and useful information about using the app.",
                    12,
                    muted
                )

                demoButton("Try the example account") {
                    TutorialDemoData.sampleProfileName = "Demo User"
                    setFeedback("You are viewing a sample account. No login or registration has taken place.")
                }

                demoButton("Explore example help") {
                    setFeedback("Example help: Browse products, find a branch or request a quote.")
                }

                demoText(
                    "You do not need to log in to explore or complete this tutorial.",
                    11,
                    green
                )
            }

            7 -> {
                demoText("Refresh your app", 15, blue)
                demoText("Learn how refreshing works. The app can update products and quotes when you refresh. This tutorial demonstrates the feature without starting a real sync", 12, muted)
            // demoButton("Try syncing now") { setFeedback("This is a demonstration. No sync has been started.") }
                demoButton("Return to Home") { setFeedback("You can return to the Home screen after completing the tutorial.") }
            }
        }
    }

    fun render() {
        val step = steps[tutorialStepIndex]
        TutorialDemoController.visitedSteps[tutorialStepIndex] = true
        counter.text = "STEP ${tutorialStepIndex + 1} OF ${steps.size}"
        title.text = step.title
        description.text = step.text

        progressRow.removeAllViews()
        steps.forEachIndexed { index, item ->
            val isCurrent = index == tutorialStepIndex
            val progressChip = TextView(this).apply {
                text = "${index + 1}. ${item.title}"
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(if (isCurrent) Color.WHITE else blue)
                setTypeface(typeface, if (isCurrent) Typeface.BOLD else Typeface.NORMAL)
                setPadding(dp(12), dp(9), dp(12), dp(9))
                background = bg(if (isCurrent) blue else Color.WHITE, if (isCurrent) blue else lineColor, 20)
                setOnClickListener {
                    tutorialStepIndex = index
                    setFeedback("Try the example below.")
                    render()
                }
            }
            progressRow.addView(progressChip, LinearLayout.LayoutParams(-2, -2).apply {
                setMargins(0, 0, dp(6), 0)
            })
        }

        backButton.isEnabled = tutorialStepIndex > 1
        backButton.alpha = if (tutorialStepIndex > 1) 1f else 0.45f
        nextButton.text = if (tutorialStepIndex == steps.lastIndex) "Finish " else "Next "

        nextButton.isEnabled =
            tutorialStepIndex != steps.lastIndex || TutorialDemoController.visitedSteps.all { it }

        nextButton.alpha = if (nextButton.isEnabled) 1f else 0.45f
        renderDemo()
        setFeedback("Try the example below.")
    }   

    backButton.setOnClickListener {
        if (tutorialStepIndex > 0) {
            tutorialStepIndex--
            render()
        } else if (tutorialCompleted()) {
            showScreen("help")
        } else {
            toast("Please complete the tutorial before leaving.")
        }
    }

    nextButton.setOnClickListener {
        if (tutorialStepIndex < steps.lastIndex) {
            tutorialStepIndex++
            render()
        } else {
            if (TutorialDemoController.visitedSteps.all { it }) {
                TutorialDemoController.finish(this)
                toast("Tutorial completed successfully!")
            } else {
                toast("Please visit all tutorial sections before finishing.")
            }
        }
    }

    render()
}
