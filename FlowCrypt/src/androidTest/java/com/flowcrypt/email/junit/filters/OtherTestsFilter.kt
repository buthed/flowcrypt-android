/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.junit.filters

import org.junit.runner.Description
import org.junit.runner.manipulation.Filter

/**
 * @author Denis Bondarenko
 *         Date: 2/18/21
 *         Time: 10:42 AM
 *         E-mail: DenBond7@gmail.com
 */
class OtherTestsFilter : Filter() {
  private val dependsOnMailServerFilter = DependsOnMailServerFilter()
  private val doesNotNeedMailServerFilter = DoesNotNeedMailServerFilter()

  override fun shouldRun(description: Description?): Boolean {
    return description?.isTest == false || (!dependsOnMailServerFilter.shouldRun(description) && !doesNotNeedMailServerFilter.shouldRun(description))
  }

  override fun describe() = "Filter tests that are not related to any conditions"
}