/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.api.retrofit.response.model

import android.os.Parcel
import android.os.Parcelable
import com.google.gson.annotations.SerializedName

interface MsgBlock : Parcelable {
  val type: Type
  val content: String?
  val complete: Boolean
  val error: MsgBlockError?

  enum class Type : Parcelable {
    UNKNOWN,

    @SerializedName("plainText")
    PLAIN_TEXT,

    @SerializedName("decryptedText")
    DECRYPTED_TEXT,

    @SerializedName("encryptedMsg")
    ENCRYPTED_MSG,

    @SerializedName("publicKey")
    PUBLIC_KEY,

    @SerializedName("signedMsg")
    SIGNED_MSG,

    @SerializedName("encryptedMsgLink")
    ENCRYPTED_MSG_LINK,

    @SerializedName("attestPacket")
    ATTEST_PACKET,

    @SerializedName("privateKey")
    PRIVATE_KEY,

    @SerializedName("plainAtt")
    PLAIN_ATT,

    @SerializedName("encryptedAtt")
    ENCRYPTED_ATT,

    @SerializedName("decryptedAtt")
    DECRYPTED_ATT,

    @SerializedName("encryptedAttLink")
    ENCRYPTED_ATT_LINK,

    @SerializedName("plainHtml")
    PLAIN_HTML,

    @SerializedName("decryptedHtml")
    DECRYPTED_HTML,

    @SerializedName("decryptErr")
    DECRYPT_ERROR,

    @SerializedName("certificate")
    CERTIFICATE,

    @SerializedName("signature")
    SIGNATURE,

    @SerializedName("signedText")
    SIGNED_TEXT,

    @SerializedName("signedHtml")
    SIGNED_HTML,

    @SerializedName("verifiedMsg")
    VERIFIED_MSG,

    @SerializedName("decryptedAndOrSignedContent")
    DECRYPTED_AND_OR_SIGNED_CONTENT;

    override fun describeContents(): Int {
      return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
      dest.writeInt(ordinal)
    }

    fun isContentBlockType(): Boolean = CONTENT_BLOCK_TYPES.contains(this)

    companion object {
      @JvmField
      val CREATOR: Parcelable.Creator<Type> = object : Parcelable.Creator<Type> {
        override fun createFromParcel(source: Parcel): Type = values()[source.readInt()]
        override fun newArray(size: Int): Array<Type?> = arrayOfNulls(size)
      }

      val KEY_BLOCK_TYPES = setOf(PUBLIC_KEY, PRIVATE_KEY)

      val REPLACEABLE_BLOCK_TYPES = setOf(
        PUBLIC_KEY, PRIVATE_KEY, SIGNED_MSG, ENCRYPTED_MSG, ENCRYPTED_MSG_LINK
      )

      val WELL_KNOWN_BLOCK_TYPES = setOf(
        PUBLIC_KEY, PRIVATE_KEY, SIGNED_MSG, ENCRYPTED_MSG
      )

      val SIGNED_BLOCK_TYPES = setOf(
        SIGNED_TEXT, SIGNED_HTML, SIGNED_MSG, DECRYPTED_AND_OR_SIGNED_CONTENT
      )

      val CONTENT_BLOCK_TYPES = setOf(
        PLAIN_TEXT,
        PLAIN_HTML,
        DECRYPTED_TEXT,
        DECRYPTED_HTML,
        SIGNED_MSG,
        VERIFIED_MSG,
        DECRYPTED_AND_OR_SIGNED_CONTENT
      )

      val DECRYPTED_CONTENT_BLOCK_TYPES = setOf(
        DECRYPTED_HTML, DECRYPTED_TEXT, DECRYPTED_ATT
      )

      fun ofSerializedName(serializedName: String): Type {
        for (v in values()) {
          val field = Type::class.java.getField(v.name)
          val annotation = field.getAnnotation(SerializedName::class.java)
          if (annotation != null && annotation.value == serializedName) return v
        }
        throw IllegalArgumentException("Unknown block type serialized name '$serializedName'")
      }
    }
  }
}
