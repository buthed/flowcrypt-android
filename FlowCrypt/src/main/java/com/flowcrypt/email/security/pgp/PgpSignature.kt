/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.security.pgp


import org.bouncycastle.openpgp.PGPPublicKeyRingCollection
import org.pgpainless.PGPainless
import org.pgpainless.decryption_verification.ConsumerOptions
import org.pgpainless.decryption_verification.OpenPgpMetadata
import org.pgpainless.decryption_verification.cleartext_signatures.ClearsignedMessageUtil
import org.pgpainless.decryption_verification.cleartext_signatures.InMemoryMultiPassStrategy
import org.pgpainless.decryption_verification.cleartext_signatures.MultiPassStrategy
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * @author Denis Bondarenko
 *         Date: 8/31/21
 *         Time: 2:45 PM
 *         E-mail: DenBond7@gmail.com
 */
object PgpSignature {
  fun extractClearText(source: String?, isSilent: Boolean = true): String? {
    return extractClearText(ByteArrayInputStream(source?.toByteArray()), isSilent)
  }

  @Suppress("MemberVisibilityCanBePrivate")
  fun extractClearText(srcInputStream: InputStream, isSilent: Boolean = true): String? {
    srcInputStream.use { srcStream ->
      return try {
        val multiPassStrategy = MultiPassStrategy.keepMessageInMemory()
        ClearsignedMessageUtil.detachSignaturesFromInbandClearsignedMessage(
          srcStream,
          multiPassStrategy.messageOutputStream
        )
        String(multiPassStrategy.bytes)
      } catch (e: Exception) {
        if (isSilent) {
          e.printStackTrace()
          null
        } else throw e
      }
    }
  }

  fun verifyClearTextSignature(
    srcInputStream: InputStream,
    publicKeys: PGPPublicKeyRingCollection
  ): ClearTextVerificationResult {
    ByteArrayOutputStream().use { outStream ->
      return try {
        val verificationStream = PGPainless.verifyCleartextSignedMessage()
          .onInputStream(srcInputStream)
          .withStrategy(InMemoryMultiPassStrategy())
          .withOptions(ConsumerOptions().addVerificationCerts(publicKeys))
          .verificationStream

        verificationStream.use { it.copyTo(outStream) }
        ClearTextVerificationResult(
          openPgpMetadata = verificationStream.result,
          clearText = String(outStream.toByteArray())
        )
      } catch (e: Exception) {
        ClearTextVerificationResult(exception = e)
      }
    }
  }

  data class ClearTextVerificationResult(
    val openPgpMetadata: OpenPgpMetadata? = null,
    val clearText: String? = null,
    val exception: Exception? = null
  )
}
