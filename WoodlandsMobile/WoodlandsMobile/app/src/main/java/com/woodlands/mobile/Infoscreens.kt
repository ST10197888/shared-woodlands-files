package com.woodlands.mobile

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri

private class LegalSection(val heading: String?, val body: String)

private class LegalDoc(
    val label: String,
    val blurb: String,
    val title: String,
    val note: String,
    val sections: List<LegalSection>,
    val email: String? = null,
    val emailSubject: String? = null
)

/** Text mirrors the website views (Views/Home/Privacy, Terms, Refunds, Cookies .cshtml). */
private val LEGAL_DOCS: Map<String, LegalDoc> = linkedMapOf(
    "privacy" to LegalDoc(
        label = "Privacy",
        blurb = "What we collect and how it is used",
        title = "Privacy Notice",
        note = "Prototype notice for the website. The business owner must confirm the contact, hosting, retention and service-provider details before this site is used with real customers.",
        sections = listOf(
            LegalSection("Information this prototype collects", "When you request a quote, the form asks for your name, email address, nearest branch, requested service and project description. Phone number is optional. Creating an account asks for your name, email address and password; phone number is optional there too. The app also keeps security and account activity records for administration."),
            LegalSection("How it is used", "Quote details are sent to the project API so staff can respond and manage the request. Account details support sign-in and account management. The form does not sign you up for marketing, and the project contains no email newsletter or unsubscribe flow."),
            LegalSection("Cookies and service providers", "The website uses an essential sign-in cookie for authenticated pages. It does not implement advertising, analytics or a cookie-consent banner. The prototype also loads its interface framework and fonts from third-party CDNs; those providers may receive technical connection data when a page loads. Confirm the production hosting, API, database and provider arrangements before launch."),
            LegalSection("Retention, access and deletion", "This codebase does not set a customer-data retention schedule or provide self-service account deletion. To request access, correction or deletion, email woodlandboards14@gmail.com from the address associated with the request or account. The responsible business must verify identity, handle the request, and define lawful retention exceptions and timelines before production use."),
            LegalSection("Contact", "Privacy contact shown in this prototype: woodlandboards14@gmail.com. Verify this address and the legal entity responsible for personal information before publishing.")
        ),
        email = "woodlandboards14@gmail.com",
        emailSubject = "Privacy request"
    ),
    "terms" to LegalDoc(
        label = "Terms",
        blurb = "Quotes, orders and using the app",
        title = "Terms of Service",
        note = "Prototype terms for review by the business owner before public use. The project currently has no checkout or online payment feature.",
        sections = listOf(
            LegalSection("Quotes and orders", "A quote request is an enquiry, not an accepted order or final price. Any price or availability shown on this prototype must be confirmed in a written quote. Work begins only after the customer and business agree to the final scope, price, schedule and payment arrangements."),
            LegalSection("Using the site", "Provide accurate information, use only accounts assigned to you, and do not attempt to access another person's account or disrupt the service. The business may restrict access where needed to protect users or the service."),
            LegalSection("Contact and governing details", "Contact the business at info@woodlandsdb.co.za. The legal business name, registration details, address and final governing terms have not been supplied in this project and must be completed by the owner before launch.")
        ),
        email = "info@woodlandsdb.co.za"
    ),
    "refunds" to LegalDoc(
        label = "Refunds",
        blurb = "Refunds and cancellations",
        title = "Refunds and Cancellations",
        note = "Prototype information. No online checkout, payment collection or order placement is implemented in this project.",
        sections = listOf(
            LegalSection(null, "Because this site currently collects quote enquiries only, it does not take payment and there is no website refund transaction to process. Before accepting paid orders, the business must publish its confirmed cancellation, deposit, refund, faulty-goods and custom-made-goods terms, consistent with applicable consumer law."),
            LegalSection(null, "For an existing order or payment enquiry, contact info@woodlandsdb.co.za. Confirm this address before launch.")
        ),
        email = "info@woodlandsdb.co.za"
    ),
    "cookies" to LegalDoc(
        label = "Cookies",
        blurb = "How cookies are used",
        title = "Cookie Notice",
        note = "This prototype uses cookies needed for sign-in. Confirm the production configuration before launch.",
        sections = listOf(
            LegalSection("Essential sign-in cookie", "When you sign in, ASP.NET Core Identity uses an HTTP-only, same-site authentication cookie to keep your session. It supports the account features you request. The site does not currently set advertising or analytics cookies and does not need a cookie preference banner for those features."),
            LegalSection("Third-party page resources", "The interface loads Tailwind CSS and the Fraunces and Inter fonts from external CDNs. A browser may connect to those providers when a page loads. Self-host these resources or document the providers and review their privacy terms before production use.")
        )
    )
)

internal val LEGAL_SCREENS = LEGAL_DOCS.keys

/** Information hub: lists Privacy, Terms, Refunds and Cookies. */
internal fun MainActivity.legalHubScreen() {
    pageIntro("Legal Information", "Privacy, terms, refunds and cookies for Woodlands Designer Boards.")
    LEGAL_DOCS.forEach { (key, doc) ->
        val c = card().apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setOnClickListener { showScreen(key) }
        }
        c.addView(tv(doc.label, 17, blue).apply { setTypeface(typeface, Typeface.BOLD) })
        c.addView(tv(doc.blurb, 12, muted).apply { setPadding(0, dp(4), 0, 0) })
        content.addView(c, marginParams(16, 6, 16, 6))
    }
    footerNote()
}

/** One policy page. [key] is "privacy", "terms", "refunds" or "cookies". */
internal fun MainActivity.legalDocScreen(key: String) {
    val doc = LEGAL_DOCS[key] ?: run { showScreen("legal"); return }
    pageIntro(doc.title, "")
    content.addView(tv(doc.note, 12, muted).apply { setPadding(dp(16), dp(14), dp(16), dp(4)) })
    doc.sections.forEach { s ->
        s.heading?.let { h ->
            content.addView(tv(h, 17, blue).apply {
                setTypeface(Typeface.SERIF, Typeface.BOLD)
                setPadding(dp(16), dp(18), dp(16), dp(2))
            })
        }
        content.addView(tv(s.body, 14, text).apply {
            setPadding(dp(16), dp(6), dp(16), dp(4))
            setLineSpacing(0f, 1.2f)
        })
    }
    doc.email?.let { email ->
        content.addView(outlineButton("Email $email", blue).apply {
            setOnClickListener {
                val uri = "mailto:$email" + (doc.emailSubject?.let { "?subject=" + Uri.encode(it) } ?: "")
                try { startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse(uri))) } catch (e: Exception) { toast("No email app found") }
            }
        }, marginParams(16, 18, 16, 6))
    }
    if (key == "cookies") {
        content.addView(outlineButton("Read the Privacy Notice", blue).apply { setOnClickListener { showScreen("privacy") } }, marginParams(16, 0, 16, 6))
    }
    content.addView(outlineButton("Back to Legal Information", blue).apply { setOnClickListener { showScreen("legal") } }, marginParams(16, 0, 16, 24))
}