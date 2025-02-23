/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: Ivan Pizhenko
 *               DenBond7
 */

package com.flowcrypt.email.api.retrofit.response.model

import android.os.Parcel
import android.os.Parcelable
import com.google.gson.annotations.Expose

data class EncryptedAttLinkMsgBlock(
  @Expose override val attMeta: AttMeta,
  @Expose override val error: MsgBlockError? = null
) : AttMsgBlock {

  @Expose
  override val content: String = ""

  @Expose
  override val type: MsgBlock.Type = MsgBlock.Type.ENCRYPTED_ATT_LINK

  @Expose
  override val complete: Boolean = true

  constructor(source: Parcel) : this(
    source.readParcelable<AttMeta>(AttMeta::class.java.classLoader)!!,
    source.readParcelable<MsgBlockError>(MsgBlockError::class.java.classLoader)
  )

  override fun writeToParcel(parcel: Parcel, flags: Int) {
    parcel.writeParcelable(attMeta, flags)
    parcel.writeParcelable(error, flags)
  }

  override fun describeContents(): Int {
    return 0
  }

  companion object CREATOR : Parcelable.Creator<EncryptedAttLinkMsgBlock> {
    override fun createFromParcel(parcel: Parcel) = EncryptedAttLinkMsgBlock(parcel)
    override fun newArray(size: Int): Array<EncryptedAttLinkMsgBlock?> = arrayOfNulls(size)
  }
}
