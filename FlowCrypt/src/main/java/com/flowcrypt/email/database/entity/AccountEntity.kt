/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.database.entity

import android.accounts.Account
import android.os.Parcel
import android.os.Parcelable
import android.provider.BaseColumns
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import com.flowcrypt.email.api.email.JavaEmailConstants
import com.flowcrypt.email.api.email.gmail.GmailConstants
import com.flowcrypt.email.api.email.model.AuthCredentials
import com.flowcrypt.email.api.email.model.SecurityType
import com.flowcrypt.email.api.retrofit.response.model.OrgRules
import com.flowcrypt.email.util.FlavorSettings
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import java.util.Locale

/**
 * @author Denis Bondarenko
 *         Date: 12/4/19
 *         Time: 2:50 PM
 *         E-mail: DenBond7@gmail.com
 */
@Entity(
  tableName = "accounts",
  indices = [
    Index(name = "email_account_type_in_accounts", value = ["email", "account_type"], unique = true)
  ]
)
data class AccountEntity constructor(
  @PrimaryKey(autoGenerate = true)
  @ColumnInfo(name = BaseColumns._ID) val id: Long? = null,
  val email: String,
  @ColumnInfo(name = "account_type", defaultValue = "NULL") val accountType: String? = null,
  @ColumnInfo(name = "display_name", defaultValue = "NULL") val displayName: String? = null,
  @ColumnInfo(name = "given_name", defaultValue = "NULL") val givenName: String? = null,
  @ColumnInfo(name = "family_name", defaultValue = "NULL") val familyName: String? = null,
  @ColumnInfo(name = "photo_url", defaultValue = "NULL") val photoUrl: String? = null,
  @ColumnInfo(name = "is_enabled", defaultValue = "1") val isEnabled: Boolean? = true,
  @ColumnInfo(name = "is_active", defaultValue = "0") val isActive: Boolean? = false,
  val username: String,
  val password: String,
  @ColumnInfo(name = "imap_server") val imapServer: String,
  @ColumnInfo(name = "imap_port", defaultValue = "143") val imapPort: Int? = 143,
  @ColumnInfo(name = "imap_use_ssl_tls", defaultValue = "0") val imapUseSslTls: Boolean? = false,
  @ColumnInfo(name = "imap_use_starttls", defaultValue = "0") val imapUseStarttls: Boolean? = false,
  @ColumnInfo(name = "imap_auth_mechanisms") val imapAuthMechanisms: String? = null,
  @ColumnInfo(name = "smtp_server") val smtpServer: String,
  @ColumnInfo(name = "smtp_port", defaultValue = "25") val smtpPort: Int? = 25,
  @ColumnInfo(name = "smtp_use_ssl_tls", defaultValue = "0") val smtpUseSslTls: Boolean? = false,
  @ColumnInfo(name = "smtp_use_starttls", defaultValue = "0") val smtpUseStarttls: Boolean? = false,
  @ColumnInfo(name = "smtp_auth_mechanisms") val smtpAuthMechanisms: String? = null,
  @ColumnInfo(
    name = "smtp_use_custom_sign",
    defaultValue = "0"
  ) val smtpUseCustomSign: Boolean? = false,
  @ColumnInfo(name = "smtp_username", defaultValue = "NULL") val smtpUsername: String? = null,
  @ColumnInfo(name = "smtp_password", defaultValue = "NULL") val smtpPassword: String? = null,
  @ColumnInfo(name = "contacts_loaded", defaultValue = "0") val contactsLoaded: Boolean? = false,
  @ColumnInfo(
    name = "show_only_encrypted",
    defaultValue = "0"
  ) val showOnlyEncrypted: Boolean? = false,
  @ColumnInfo(defaultValue = "NULL") val uuid: String? = null,
  @ColumnInfo(
    name = "client_configuration",
    defaultValue = "NULL"
  ) val clientConfiguration: OrgRules? = null,
  @ColumnInfo(name = "use_api", defaultValue = "0") val useAPI: Boolean = false
) : Parcelable {

  @Ignore
  val account: Account = Account(
    this.email, accountType
      ?: this.email.substring(this.email.indexOf('@') + 1).toLowerCase(Locale.US)
  )

  val useOAuth2: Boolean
    get() = JavaEmailConstants.AUTH_MECHANISMS_XOAUTH2 == imapAuthMechanisms

  constructor(
    googleSignInAccount: GoogleSignInAccount, uuid: String? = null, orgRules: OrgRules? = null
  ) :
      this(
        email = googleSignInAccount.email!!.toLowerCase(Locale.US),
        accountType = googleSignInAccount.account?.type?.toLowerCase(Locale.US),
        displayName = googleSignInAccount.displayName,
        givenName = googleSignInAccount.givenName,
        familyName = googleSignInAccount.familyName,
        photoUrl = googleSignInAccount.photoUrl?.toString(),
        isEnabled = true,
        isActive = false,
        username = googleSignInAccount.email!!,
        password = "",
        imapServer = GmailConstants.GMAIL_IMAP_SERVER,
        imapPort = GmailConstants.GMAIL_IMAP_PORT,
        imapUseSslTls = true,
        imapUseStarttls = false,
        imapAuthMechanisms = JavaEmailConstants.AUTH_MECHANISMS_XOAUTH2,
        smtpServer = GmailConstants.GMAIL_SMTP_SERVER,
        smtpPort = GmailConstants.GMAIL_SMTP_PORT,
        smtpUseSslTls = true,
        smtpUseStarttls = false,
        smtpAuthMechanisms = JavaEmailConstants.AUTH_MECHANISMS_XOAUTH2,
        smtpUseCustomSign = false,
        smtpUsername = null,
        smtpPassword = null,
        contactsLoaded = false,
        showOnlyEncrypted = false,
        uuid = uuid,
        clientConfiguration = orgRules,
        useAPI = FlavorSettings.isGMailAPIEnabled()
      )

  constructor(authCredentials: AuthCredentials, uuid: String? = null, orgRules: OrgRules? = null) :
      this(
        email = authCredentials.email.toLowerCase(Locale.US),
        accountType = authCredentials.email.substring(authCredentials.email.indexOf('@') + 1)
          .toLowerCase(Locale.getDefault()),
        displayName = authCredentials.displayName,
        givenName = null,
        familyName = null,
        photoUrl = null,
        isEnabled = true,
        isActive = false,
        username = authCredentials.username,
        password = authCredentials.password,
        imapServer = authCredentials.imapServer.toLowerCase(Locale.US),
        imapPort = authCredentials.imapPort,
        imapUseSslTls = authCredentials.imapOpt === SecurityType.Option.SSL_TLS,
        imapUseStarttls = authCredentials.imapOpt === SecurityType.Option.STARTLS,
        imapAuthMechanisms = if (authCredentials.useOAuth2) JavaEmailConstants.AUTH_MECHANISMS_XOAUTH2 else null,
        smtpServer = authCredentials.smtpServer.toLowerCase(Locale.US),
        smtpPort = authCredentials.smtpPort,
        smtpUseSslTls = authCredentials.smtpOpt === SecurityType.Option.SSL_TLS,
        smtpUseStarttls = authCredentials.smtpOpt === SecurityType.Option.STARTLS,
        smtpAuthMechanisms = if (authCredentials.useOAuth2) JavaEmailConstants.AUTH_MECHANISMS_XOAUTH2 else null,
        smtpUseCustomSign = authCredentials.hasCustomSignInForSmtp,
        smtpUsername = authCredentials.smtpSigInUsername,
        smtpPassword = authCredentials.smtpSignInPassword,
        contactsLoaded = false,
        showOnlyEncrypted = false,
        uuid = uuid,
        clientConfiguration = orgRules,
        useAPI = false
      )

  constructor(email: String) :
      this(
        email = email,
        accountType = null,
        displayName = null,
        givenName = null,
        familyName = null,
        photoUrl = null,
        isEnabled = true,
        isActive = false,
        username = "",
        password = "",
        imapServer = "",
        imapPort = 0,
        imapUseSslTls = true,
        imapUseStarttls = false,
        imapAuthMechanisms = null,
        smtpServer = "",
        smtpPort = 0,
        smtpUseSslTls = true,
        smtpUseStarttls = false,
        smtpAuthMechanisms = "",
        smtpUseCustomSign = false,
        smtpUsername = null,
        smtpPassword = null,
        contactsLoaded = false,
        showOnlyEncrypted = false,
        uuid = null,
        clientConfiguration = null,
        useAPI = false
      )

  constructor(source: Parcel) : this(
    source.readValue(Long::class.java.classLoader) as Long?,
    source.readString()!!,
    source.readString(),
    source.readString(),
    source.readString(),
    source.readString(),
    source.readString(),
    source.readValue(Boolean::class.java.classLoader) as Boolean?,
    source.readValue(Boolean::class.java.classLoader) as Boolean?,
    source.readString()!!,
    source.readString()!!,
    source.readString()!!,
    source.readValue(Int::class.java.classLoader) as Int,
    source.readValue(Boolean::class.java.classLoader) as Boolean?,
    source.readValue(Boolean::class.java.classLoader) as Boolean?,
    source.readString(),
    source.readString()!!,
    source.readValue(Int::class.java.classLoader) as Int,
    source.readValue(Boolean::class.java.classLoader) as Boolean?,
    source.readValue(Boolean::class.java.classLoader) as Boolean?,
    source.readString(),
    source.readValue(Boolean::class.java.classLoader) as Boolean?,
    source.readString(),
    source.readString(),
    source.readValue(Boolean::class.java.classLoader) as Boolean?,
    source.readValue(Boolean::class.java.classLoader) as Boolean?,
    source.readString(),
    source.readParcelable(OrgRules::class.java.classLoader),
    source.readValue(Boolean::class.java.classLoader) as Boolean
  )

  fun imapOpt(): SecurityType.Option {
    return when {
      imapUseSslTls == true -> {
        SecurityType.Option.SSL_TLS
      }

      imapUseStarttls == true -> {
        SecurityType.Option.STARTLS
      }

      else -> SecurityType.Option.NONE
    }
  }

  fun smtpOpt(): SecurityType.Option {
    return when {
      smtpUseSslTls == true -> {
        SecurityType.Option.SSL_TLS
      }

      smtpUseStarttls == true -> {
        SecurityType.Option.STARTLS
      }

      else -> SecurityType.Option.NONE
    }
  }

  fun isRuleExist(domainRule: OrgRules.DomainRule): Boolean {
    return clientConfiguration?.hasRule(domainRule) ?: false
  }

  override fun describeContents() = 0

  override fun writeToParcel(dest: Parcel, flags: Int) = with(dest) {
    writeValue(id)
    writeString(email)
    writeString(accountType)
    writeString(displayName)
    writeString(givenName)
    writeString(familyName)
    writeString(photoUrl)
    writeValue(isEnabled)
    writeValue(isActive)
    writeString(username)
    writeString(password)
    writeString(imapServer)
    writeValue(imapPort)
    writeValue(imapUseSslTls)
    writeValue(imapUseStarttls)
    writeString(imapAuthMechanisms)
    writeString(smtpServer)
    writeValue(smtpPort)
    writeValue(smtpUseSslTls)
    writeValue(smtpUseStarttls)
    writeString(smtpAuthMechanisms)
    writeValue(smtpUseCustomSign)
    writeString(smtpUsername)
    writeString(smtpPassword)
    writeValue(contactsLoaded)
    writeValue(showOnlyEncrypted)
    writeString(uuid)
    writeParcelable(clientConfiguration, flags)
    writeValue(useAPI)
  }

  companion object {
    const val ACCOUNT_TYPE_GOOGLE = "com.google"
    const val ACCOUNT_TYPE_OUTLOOK = "outlook.com"

    @JvmField
    val CREATOR: Parcelable.Creator<AccountEntity> = object : Parcelable.Creator<AccountEntity> {
      override fun createFromParcel(source: Parcel): AccountEntity = AccountEntity(source)
      override fun newArray(size: Int): Array<AccountEntity?> = arrayOfNulls(size)
    }
  }
}
