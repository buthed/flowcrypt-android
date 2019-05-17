/*
 * © 2016-2019 FlowCrypt Limited. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.api.retrofit.response.attester;

import android.os.Parcel;

import com.flowcrypt.email.api.retrofit.response.base.ApiError;
import com.flowcrypt.email.api.retrofit.response.base.ApiResponse;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import org.jetbrains.annotations.NotNull;

/**
 * Response from the API
 * "https://flowcrypt.com/attester/lookup/email"
 *
 * @author DenBond7
 * Date: 24.04.2017
 * Time: 13:20
 * E-mail: DenBond7@gmail.com
 */

public class LookUpEmailResponse implements ApiResponse {

  public static final Creator<LookUpEmailResponse> CREATOR = new Creator<LookUpEmailResponse>() {
    @Override
    public LookUpEmailResponse createFromParcel(Parcel source) {
      return new LookUpEmailResponse(source);
    }

    @Override
    public LookUpEmailResponse[] newArray(int size) {
      return new LookUpEmailResponse[size];
    }
  };

  @SerializedName("error")
  @Expose
  private ApiError apiError;

  @SerializedName("has_cryptup")
  @Expose
  private boolean hasCryptup;

  @SerializedName("pubkey")
  @Expose
  private String pubKey;

  @Expose
  private String email;

  @SerializedName("longid")
  @Expose
  private String longId;

  public LookUpEmailResponse() {
  }

  public LookUpEmailResponse(Parcel in) {
    this.apiError = in.readParcelable(ApiError.class.getClassLoader());
    this.hasCryptup = in.readByte() != 0;
    this.pubKey = in.readString();
    this.email = in.readString();
    this.longId = in.readString();
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeParcelable(this.apiError, flags);
    dest.writeByte(this.hasCryptup ? (byte) 1 : (byte) 0);
    dest.writeString(this.pubKey);
    dest.writeString(this.email);
    dest.writeString(this.longId);
  }

  @NotNull
  @Override
  public ApiError getApiError() {
    return apiError;
  }

  public boolean hasCryptup() {
    return hasCryptup;
  }

  public String getPubKey() {
    return pubKey;
  }

  public String getEmail() {
    return email;
  }

  public String getLongId() {
    return longId;
  }
}
