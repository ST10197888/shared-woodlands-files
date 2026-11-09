package com.woodlands.mobile.TutorialDemo

internal object TutorialDemoData {

    var selectedCategory: String = "All"
    var selectedProduct: String = ""
    var customerName: String = ""
    var customerEmail: String = ""
    var customerPhone: String = ""
    var projectDescription: String = ""
    var selectedBranch: String = ""
    var sampleQuoteCreated: Boolean = false
    var sampleProfileName: String = "Demo User"

    fun clear() {
        selectedCategory = "All"
        selectedProduct = ""
        customerName = ""
        customerEmail = ""
        customerPhone = ""
        projectDescription = ""
        selectedBranch = ""
        sampleQuoteCreated = false
        sampleProfileName = "Demo User"
    }
}