/*
 * © 2016-2018 FlowCrypt Limited. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.api.retrofit.response.base;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

/**
 * This POJO object describes a base error from the API.
 *
 * @author Denis Bondarenko
 * Date: 12.07.2017
 * Time: 9:26
 * E-mail: DenBond7@gmail.com
 */

public class ApiError implements Parcelable {

  public static final Creator<ApiError> CREATOR = new Creator<ApiError>() {
    @Override
    public ApiError createFromParcel(Parcel source) {
      return new ApiError(source);
    }

    @Override
    public ApiError[] newArray(int size) {
      return new ApiError[size];
    }
  };

  @Expose
  private int code;

  @SerializedName("message")
  @Expose
  private String msg;

  @Expose
  private String internal;

  public ApiError() {
  }

  protected ApiError(Parcel in) {
    this.code = in.readInt();
    this.msg = in.readString();
    this.internal = in.readString();
  }

  @Override
  public String toString() {
    return "ApiError{" +
        "code=" + code +
        ", msg='" + msg + '\'' +
        ", internal='" + internal + '\'' +
        '}';
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeInt(this.code);
    dest.writeString(this.msg);
    dest.writeString(this.internal);
  }

  public int getCode() {
    return code;
  }

  public String getMessage() {
    return msg;
  }

  public String getInternal() {
    return internal;
  }
}
