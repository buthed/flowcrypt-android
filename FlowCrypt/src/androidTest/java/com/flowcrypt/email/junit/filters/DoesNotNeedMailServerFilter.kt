/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.junit.filters

import com.flowcrypt.email.junit.annotations.DependsOnMailServer
import org.junit.runner.Description

/**
 * @author Denis Bondarenko
 *         Date: 2/17/21
 *         Time: 5:06 PM
 *         E-mail: DenBond7@gmail.com
 */
class DoesNotNeedMailServerFilter : ReadyForCIFilter() {
  override fun evaluateTest(description: Description?): Boolean {
    val annotationClass = DependsOnMailServer::class.java
    val hasClassAnnotation = description?.testClass?.isAnnotationPresent(annotationClass) == true
    if (hasClassAnnotation) return false
    return super.evaluateTest(description) && description?.getAnnotation(annotationClass) == null
  }

  override fun describe() = "Filter tests that don't need an email server"
}
