/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.matchers

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import org.hamcrest.BaseMatcher
import org.hamcrest.Description

/**
 * @author Denis Bondarenko
 *         Date: 5/3/19
 *         Time: 3:44 PM
 *         E-mail: DenBond7@gmail.com
 */
class EmptyRecyclerViewMatcher<T : View> : BaseMatcher<T>() {
  override fun matches(item: Any): Boolean {
    return if (item is RecyclerView) {
      item.adapter?.itemCount == 0
    } else {
      false
    }
  }

  override fun describeTo(description: Description) {
    description.appendText("RecyclerView is not empty")
  }
}
