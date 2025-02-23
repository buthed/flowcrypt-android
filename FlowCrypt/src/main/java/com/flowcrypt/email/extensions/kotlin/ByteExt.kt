/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: ivan
 */

package com.flowcrypt.email.extensions.kotlin

object ByteExtHelper {
  const val tab = '\t'.toByte()
  const val space = ' '.toByte()
  const val cr = '\r'.toByte()
  const val lf = '\n'.toByte()
  const val hexTable = "0123456789ABCDEF"
}

val Byte.isLineEnding: Boolean
  get() {
    return this == ByteExtHelper.cr || this == ByteExtHelper.lf
  }

val Byte.isWhiteSpace: Boolean
  get() {
    return isLineEnding || this == ByteExtHelper.tab || this == ByteExtHelper.space
  }

fun Byte.toUrlHex(): String {
  return "%${ByteExtHelper.hexTable[this.toInt() and 15]}" +
      "${ByteExtHelper.hexTable[(this.toInt() shr 4) and 15]}"
}
