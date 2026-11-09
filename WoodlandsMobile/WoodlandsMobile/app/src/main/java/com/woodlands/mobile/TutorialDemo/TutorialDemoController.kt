
package com.woodlands.mobile.TutorialDemo

import com.woodlands.mobile.MainActivity

internal object TutorialDemoController {

    val visitedSteps = BooleanArray(8)

    fun start(activity: MainActivity) {
        TutorialDemoData.clear()
        visitedSteps.fill(false)
        activity.startTutorialDemo()
    }

    fun open(activity: MainActivity, page: String) {
        if (!activity.tutorialDemoActive) return

        activity.openTutorialDemo(page)
    }

    fun returnToTutorial(activity: MainActivity) {
        if (!activity.tutorialDemoActive) return

        activity.returnToTutorial()
    }

    fun finish(activity: MainActivity) {
        TutorialDemoData.clear()
        visitedSteps.fill(false)
        activity.clearTutorialDemo()
        activity.markTutorialCompleted()
        activity.showScreen("home")
    }

    fun cancel(activity: MainActivity) {
        TutorialDemoData.clear()
        visitedSteps.fill(false)
        activity.clearTutorialDemo()
        activity.showScreen("home")
    }
}
