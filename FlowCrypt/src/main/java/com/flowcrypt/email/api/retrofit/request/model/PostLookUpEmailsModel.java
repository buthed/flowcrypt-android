/*
 * © 2016-2019 FlowCrypt Limited. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.api.retrofit.request.model;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * This is a POJO object which used to make a request to the API "https://flowcrypt.com/attester/lookup/email"
 * to retrieve info about an array of keys.
 *
 * @author Denis Bondarenko
 * Date: 13.11.2017
 * Time: 15:16
 * E-mail: DenBond7@gmail.com
 */

public class PostLookUpEmailsModel {
  @SerializedName("email")
  @Expose
  private List<String> emails;

  public PostLookUpEmailsModel(List<String> emails) {
    this.emails = emails;
  }

  public List<String> getEmails() {
    return emails;
  }
}
