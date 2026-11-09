package com.woodlands.mobile.TutorialDemo

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.woodlands.mobile.MainActivity

/**
 * Temporary tutorial practice screens.
 *
 * All information entered here stays in TutorialDemoData only.
 * This file must never read from or write to LocalDb.
 */
internal fun MainActivity.tutorialDemoScreen() {
    content.removeAllViews()

    val page = tutorialDemoPage

    fun addText(
        value: String,
        size: Int = 14,
        color: Int = text,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(16), dp(7), dp(16), dp(7))
        }
    }

    fun addButton(
        label: String,
        backgroundColor: Int = blue,
        onClick: () -> Unit
    ) {
        val buttonView = button(label, backgroundColor, Color.WHITE).apply {
            setOnClickListener { onClick() }
        }
        content.addView(
            buttonView,
            marginParams(16, 6, 16, 6)
        )
    }

    fun addHeading(title: String, subtitle: String = "") {
        pageIntro(title, subtitle)
    }

    fun addDisclaimer() {
        content.addView(
            addText(
                "All tutorial actions are temporary and are not saved once the tutorial ends",
                12,
                muted
            ).apply {
                setPadding(dp(16), dp(12), dp(16), dp(12))
                setBackgroundColor(greenBg)
            },
            marginParams(12, 10, 12, 10)
        )
    }

    fun addReturnButton() {
        addButton("← Back to Tutorial", red) {
            returnToTutorial()
        }
    }

    fun addField(
        label: String,
        value: String,
        hint: String,
        onChanged: (String) -> Unit
    ) {
        val field = field(label, hint, required = false).apply {
            setText(value)
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    onChanged(s?.toString().orEmpty())
                }

                override fun afterTextChanged(s: android.text.Editable?) = Unit
            })
        }

        content.addView(field, marginParams(16, 5, 16, 5))
    }

    addDisclaimer()

    when (page) {
        "gallery" -> {
            tutorialDemoGallery()
        }

        "product" -> {
            addHeading(
                TutorialDemoData.selectedProduct.ifBlank {
                    "Example Product"
                },
                "This is a sample product details page."
            )

            content.addView(
                addText(
                    "Example product",
                    12,
                    red,
                    true
                )
            )

            content.addView(
                addText(
                    "This practice screen demonstrates how a customer can review a product before requesting a quote.",
                    14,
                    muted
                )
            )

            sectionTitle("Example details", "")
            infoRow("Product", TutorialDemoData.selectedProduct.ifBlank { "Example product" })
            infoRow("Material", "Example board finish")
            infoRow("Availability", "Made to order")
            infoRow("Price", "Request a quote")

            sectionTitle("About this product", "")
            para(
                "This is demonstration content. Product information and pricing are examples only and are not taken from the live catalogue."
            )

            addButton("Practise requesting a quote") {
                openTutorialDemo("quote")
            }

            addButton("← Back to Gallery", blue) {
                openTutorialDemo("gallery")
            }

            addReturnButton()
        }

        "quote" -> {
            addHeading(
                "Practice Quote Request",
                "Enter sample information to practise completing a quote form."
            )

            content.addView(
                addText(
                    "Selected product: ${TutorialDemoData.selectedProduct.ifBlank { "None selected" }}",
                    13,
                    blue,
                    true
                )
            )

            addField(
                "Customer name",
                TutorialDemoData.customerName,
                "Enter a sample name"
            ) {
                TutorialDemoData.customerName = it
            }

            addField(
                "Email address",
                TutorialDemoData.customerEmail,
                "example@email.com"
            ) {
                TutorialDemoData.customerEmail = it
            }

            addField(
                "Phone number",
                TutorialDemoData.customerPhone,
                "Enter a sample phone number"
            ) {
                TutorialDemoData.customerPhone = it
            }

            addField(
                "Project description",
                TutorialDemoData.projectDescription,
                "Describe the example project"
            ) {
                TutorialDemoData.projectDescription = it
            }

            addField(
                "Nearest branch",
                TutorialDemoData.selectedBranch,
                "Enter an example branch"
            ) {
                TutorialDemoData.selectedBranch = it
            }

            addButton("Preview Example Quote", green) {
                TutorialDemoData.sampleQuoteCreated = true
                openTutorialDemo("quotePreview")
            }

            addReturnButton()
        }

        "quotePreview" -> {
            addHeading(
                "Example Quote Preview",
                "Review the temporary information you entered."
            )

            infoRow("Customer", TutorialDemoData.customerName.ifBlank { "Not entered" })
            infoRow("Email", TutorialDemoData.customerEmail.ifBlank { "Not entered" })
            infoRow("Phone", TutorialDemoData.customerPhone.ifBlank { "Not entered" })
            infoRow("Branch", TutorialDemoData.selectedBranch.ifBlank { "Not entered" })
            infoRow("Product", TutorialDemoData.selectedProduct.ifBlank { "None selected" })

            sectionTitle("Project description", "")
            para(
                TutorialDemoData.projectDescription.ifBlank {
                    "No description entered."
                }
            )

            content.addView(
                addText(
                    "This is only a practice preview. Nothing has been submitted or saved.",
                    13,
                    green,
                    true
                )
            )

            addButton("Edit Example Quote") {
                openTutorialDemo("quote")
            }

            addReturnButton()
        }

        "profile" -> {
            addHeading(
                "Practice Profile",
                "See how a sample customer profile might look."
            )

            addField(
                "Display name",
                TutorialDemoData.sampleProfileName,
                "Enter a sample display name"
            ) {
                TutorialDemoData.sampleProfileName = it
            }

            sectionTitle("Example account information", "")
            infoRow("Account type", "Demo customer")
            infoRow("Account status", "Practice account")
            infoRow("Saved quotes", "No real quotes")

            content.addView(
                addText(
                    "Changes on this page are temporary and do not affect a real customer account.",
                    12,
                    muted
                )
            )

            addReturnButton()
        }

        else -> {
            addHeading(
                "Practice Area",
                "Choose an example section to explore."
            )

            addButton("Open Practice Gallery") {
                openTutorialDemo("gallery")
            }

            addButton("Open Practice Profile") {
                openTutorialDemo("profile")
            }

            addReturnButton()
        }
    }
}
