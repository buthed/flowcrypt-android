/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.ui.activity

import android.app.Activity
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.flowcrypt.email.R
import com.flowcrypt.email.rules.AddAccountToDatabaseRule
import com.flowcrypt.email.rules.AddPrivateKeyToDatabaseRule
import com.flowcrypt.email.rules.ClearAppSettingsRule
import com.flowcrypt.email.ui.activity.base.BasePassphraseActivityTest
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith

/**
 * @author Denis Bondarenko
 * Date: 3/13/19
 * Time: 12:15 PM
 * E-mail: DenBond7@gmail.com
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
@Ignore("Failed on CI")
class ChangePassPhraseActivityTest : BasePassphraseActivityTest() {
  private val addAccountToDatabaseRule = AddAccountToDatabaseRule()

  override val useIntents: Boolean = true
  override val activityScenarioRule = activityScenarioRule<ChangePassPhraseActivity>()

  @get:Rule
  var ruleChain: TestRule = RuleChain
      .outerRule(ClearAppSettingsRule())
      .around(addAccountToDatabaseRule)
      .around(AddPrivateKeyToDatabaseRule())
      .around(activityScenarioRule)

  @Test
  @Ignore("fix me")
  fun testUseCorrectPassPhrase() {
    onView(withId(R.id.editTextKeyPassword))
        .check(matches(isDisplayed()))
        .perform(replaceText(PERFECT_PASSWORD), closeSoftKeyboard())
    onView(withId(R.id.buttonSetPassPhrase))
        .check(matches(isDisplayed()))
        .perform(click())
    onView(withId(R.id.editTextKeyPasswordSecond))
        .check(matches(isDisplayed()))
        .perform(replaceText(PERFECT_PASSWORD), closeSoftKeyboard())
    onView(withId(R.id.buttonConfirmPassPhrases))
        .check(matches(isDisplayed()))
        .perform(click())

    assertResultAfterFinish(Activity.RESULT_OK)
  }
}
