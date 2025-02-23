/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: Ivan Pizhenko
 *               DenBond7
 */

package com.flowcrypt.email.api.retrofit.response.model

import android.os.Parcel
import android.os.Parcelable
import com.google.gson.annotations.Expose

/**
 * Generic message block represents any message block without a dedicated support.
 *
 * @author Denis Bondarenko
 * Date: 3/26/19
 * Time: 9:46 AM
 * E-mail: DenBond7@gmail.com
 *
 * @author Ivan Pizhenko
 */
data class GenericMsgBlock(
  @Expose override val type: MsgBlock.Type = MsgBlock.Type.UNKNOWN,
  @Expose override val content: String?,
  @Expose override val complete: Boolean,
  @Expose override val error: MsgBlockError? = null
) : MsgBlock {

  constructor(type: MsgBlock.Type, source: Parcel) : this(
    type,
    source.readString(),
    1 == source.readInt(),
    source.readParcelable<MsgBlockError>(MsgBlockError::class.java.classLoader)
  )

  override fun describeContents() = 0

  override fun writeToParcel(dest: Parcel, flags: Int) = with(dest) {
    writeParcelable(type, flags)
    writeString(content)
    writeInt(if (complete) 1 else 0)
    writeParcelable(error, flags)
  }

  companion object CREATOR : Parcelable.Creator<MsgBlock> {
    override fun createFromParcel(parcel: Parcel): MsgBlock {
      val partType = parcel.readParcelable<MsgBlock.Type>(MsgBlock.Type::class.java.classLoader)!!
      return MsgBlockFactory.fromParcel(partType, parcel)
    }

    override fun newArray(size: Int): Array<MsgBlock?> = arrayOfNulls(size)
  }
}
