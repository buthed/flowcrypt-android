/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.ui.activity

import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.ActivityTestRule
import com.flowcrypt.email.R
import com.flowcrypt.email.base.BaseTest
import com.flowcrypt.email.database.FlowCryptRoomDatabase
import com.flowcrypt.email.matchers.CustomMatchers.Companion.withEmptyListView
import com.flowcrypt.email.model.PgpContact
import com.flowcrypt.email.rules.AddAccountToDatabaseRule
import com.flowcrypt.email.rules.ClearAppSettingsRule
import com.flowcrypt.email.ui.activity.settings.ContactsSettingsActivity
import org.hamcrest.Matchers.anything
import org.hamcrest.Matchers.not
import org.junit.AfterClass
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith

/**
 * @author Denis Bondarenko
 * Date: 20.02.2018
 * Time: 15:43
 * E-mail: DenBond7@gmail.com
 */
@LargeTest
@Ignore("fix me")
@RunWith(AndroidJUnit4::class)
class ContactsSettingsActivityTest : BaseTest() {

  override val activityTestRule: ActivityTestRule<*>? = ActivityTestRule(ContactsSettingsActivity::class.java)

  @get:Rule
  var ruleChain: TestRule = RuleChain
      .outerRule(ClearAppSettingsRule())
      .around(AddAccountToDatabaseRule())
      .around(activityTestRule)

  @Test
  fun testShowHelpScreen() {
    testHelpScreen()
  }

  @Test
  fun testEmptyList() {
    onView(withId(R.id.recyclerViewContacts))
        .check(matches(withEmptyListView())).check(matches(not<View>(isDisplayed())))
    onView(withId(R.id.emptyView))
        .check(matches(isDisplayed())).check(matches(withText(R.string.no_results)))
  }

  @Test
  fun testDeleteContacts() {
    addContactsToDatabase()
    for (ignored in EMAILS) {
      onData(anything())
          .inAdapterView(withId(R.id.recyclerViewContacts))
          .onChildView(withId(R.id.imageButtonDeleteContact))
          .atPosition(0)
          .perform(click())
    }
    testEmptyList()
    clearContactsFromDatabase()
  }

  private fun addContactsToDatabase() {
    for (email in EMAILS) {
      val pgpContact = PgpContact(email, null, "", true, null, null, null, null, 0)
      FlowCryptRoomDatabase.getDatabase(getTargetContext()).contactsDao().insert(pgpContact.toContactEntity())
    }
  }

  companion object {
    private val EMAILS = arrayOf(
        "contact_0@denbond7.com",
        "contact_1@denbond7.com",
        "contact_2@denbond7.com",
        "contact_3@denbond7.com")

    @AfterClass
    fun clearContactsFromDatabase() {
      for (email in EMAILS) {
        val dao = FlowCryptRoomDatabase.getDatabase(ApplicationProvider.getApplicationContext()).contactsDao()

        val contact = dao.getContactByEmails(email) ?: continue
        dao.delete(contact)
      }
    }
  }
}
