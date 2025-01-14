/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.api.email.javamail

import java.io.ByteArrayInputStream
import java.util.Properties
import javax.mail.Session
import javax.mail.internet.InternetHeaders
import javax.mail.internet.MimeMessage

/**
 * It's a custom realization of [MimeMessage] which has an own realization of creation [InternetHeaders]
 *
 * @author Denis Bondarenko
 *         Date: 2/19/21
 *         Time: 4:26 PM
 *         E-mail: DenBond7@gmail.com
 */
class CustomMimeMessage constructor(
  session: Session = Session.getInstance(Properties()),
  rawHeaders: String?
) : MimeMessage(session) {
  init {
    headers = InternetHeaders(ByteArrayInputStream(rawHeaders?.toByteArray() ?: "".toByteArray()))
  }

  fun setMessageId(msgId: String) {
    setHeader("Message-ID", msgId)
  }
}
