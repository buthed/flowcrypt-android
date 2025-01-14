/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.ui.activity

import android.os.Bundle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.clearText
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.flowcrypt.email.R
import com.flowcrypt.email.base.BaseTest
import com.flowcrypt.email.rules.AddAccountToDatabaseRule
import com.flowcrypt.email.rules.AddPrivateKeyToDatabaseRule
import com.flowcrypt.email.rules.ClearAppSettingsRule
import com.flowcrypt.email.rules.RetryRule
import com.flowcrypt.email.rules.ScreenshotTestRule
import com.flowcrypt.email.ui.activity.settings.SettingsActivity
import com.flowcrypt.email.util.TestGeneralUtil
import org.hamcrest.Matchers.startsWith
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import java.util.Locale

/**
 * @author Denis Bondarenko
 * Date: 3/13/19
 * Time: 12:15 PM
 * E-mail: DenBond7@gmail.com
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class CheckPassphraseStrengthFragmentTest : BaseTest() {
  private val addAccountToDatabaseRule = AddAccountToDatabaseRule()

  override val activityScenarioRule = activityScenarioRule<SettingsActivity>(
    TestGeneralUtil.genIntentForNavigationComponent(
      uri = "flowcrypt://email.flowcrypt.com/settings/security/check_passphrase",
      extras = Bundle().apply {
        putInt("popBackStackIdIfSuccess", R.id.securitySettingsFragment)
        putString("title", getResString(R.string.change_pass_phrase))
      }
    )
  )

  @get:Rule
  var ruleChain: TestRule = RuleChain
    .outerRule(ClearAppSettingsRule())
    .around(addAccountToDatabaseRule)
    .around(AddPrivateKeyToDatabaseRule())
    .around(RetryRule.DEFAULT)
    .around(activityScenarioRule)
    .around(ScreenshotTestRule())

  @Test
  fun testShowDialogWithPasswordRecommendation() {
    onView(withId(R.id.iBShowPasswordHint))
      .check(matches(isDisplayed()))
      .perform(click())
    onView(withId(R.id.webView))
      .check(matches(isDisplayed()))
  }

  @Test
  fun testEmptyPassPhrase() {
    closeSoftKeyboard()
    onView(withId(R.id.btSetPassphrase))
      .check(matches(isDisplayed()))
      .perform(click())

    checkIsNonEmptyHintShown()
  }

  @Test
  fun testChangingQualityOfPassPhrase() {
    val passPhrases = arrayOf(
      WEAK_PASSWORD, POOR_PASSWORD, REASONABLE_PASSWORD, GOOD_PASSWORD,
      GREAT_PASSWORD, PERFECT_PASSWORD
    )

    val degreeOfReliabilityOfPassPhrase = arrayOf(
      getResString(R.string.password_quality_weak),
      getResString(R.string.password_quality_poor),
      getResString(R.string.password_quality_reasonable),
      getResString(R.string.password_quality_good),
      getResString(R.string.password_quality_great),
      getResString(R.string.password_quality_perfect)
    )

    for (i in passPhrases.indices) {
      onView(withId(R.id.eTPassphrase))
        .check(matches(isDisplayed()))
        .perform(replaceText(passPhrases[i]))
      onView(withId(R.id.tVPassphraseQuality))
        .check(matches(withText(startsWith(degreeOfReliabilityOfPassPhrase[i].toUpperCase(Locale.getDefault())))))
      onView(withId(R.id.eTPassphrase))
        .check(matches(isDisplayed()))
        .perform(clearText())
    }
  }

  @Test
  fun testShowDialogAboutBadPassPhrase() {
    val badPassPhrases = arrayOf(WEAK_PASSWORD, POOR_PASSWORD)

    for (passPhrase in badPassPhrases) {
      onView(withId(R.id.eTPassphrase))
        .check(matches(isDisplayed()))
        .perform(replaceText(passPhrase), closeSoftKeyboard())
      onView(withId(R.id.btSetPassphrase))
        .check(matches(isDisplayed()))
        .perform(click())
      onView(withText(getResString(R.string.select_stronger_pass_phrase)))
        .check(matches(isDisplayed()))
      onView(withId(android.R.id.button1))
        .check(matches(isDisplayed()))
        .perform(click())
      onView(withId(R.id.eTPassphrase))
        .check(matches(isDisplayed()))
        .perform(clearText())
    }
  }

  private fun checkIsNonEmptyHintShown() {
    onView(withText(getResString(R.string.passphrase_must_be_non_empty)))
      .check(matches(isDisplayed()))
    onView(withId(com.google.android.material.R.id.snackbar_action))
      .check(matches(isDisplayed()))
      .perform(click())
  }

  companion object {
    internal const val WEAK_PASSWORD = "weak"
    internal const val POOR_PASSWORD = "weak, perfect, great"
    internal const val REASONABLE_PASSWORD = "weak, poor, reasonable"
    internal const val GOOD_PASSWORD = "weak, poor, good,"
    internal const val GREAT_PASSWORD = "weak, poor, great, good"
    internal const val PERFECT_PASSWORD = "unconventional blueberry unlike any other"
  }
}
