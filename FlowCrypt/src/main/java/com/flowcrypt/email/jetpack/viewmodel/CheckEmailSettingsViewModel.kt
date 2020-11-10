/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.jetpack.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowcrypt.email.R
import com.flowcrypt.email.api.email.EmailUtil
import com.flowcrypt.email.api.email.JavaEmailConstants
import com.flowcrypt.email.api.email.model.AuthCredentials
import com.flowcrypt.email.api.email.protocol.OpenStoreHelper
import com.flowcrypt.email.api.email.protocol.PropertiesHelper
import com.flowcrypt.email.api.email.protocol.SmtpProtocolUtil
import com.flowcrypt.email.api.retrofit.response.base.Result
import com.flowcrypt.email.database.FlowCryptRoomDatabase
import com.flowcrypt.email.database.entity.AccountEntity
import com.flowcrypt.email.util.exception.AccountAlreadyAddedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import javax.mail.Folder
import javax.mail.MessagingException
import javax.mail.Session

/**
 * This [ViewModel] checks [AuthCredentials]. If incoming [AuthCredentials] is valid
 * then this it sends `true`, otherwise sends false.
 *
 * @author DenBond7
 * Date: 14.09.2017.
 * Time: 15:08.
 * E-mail: DenBond7@gmail.com
 */
class CheckEmailSettingsViewModel(application: Application) : BaseAndroidViewModel(application) {
  val checkEmailSettingsLiveData = MutableLiveData<Result<Boolean?>>()

  fun checkAccount(accountEntity: AccountEntity) {
    viewModelScope.launch {
      val context: Context = getApplication()
      val existedAccount = FlowCryptRoomDatabase.getDatabase(context).accountDao()
          .getAccountSuspend(accountEntity.email.toLowerCase(Locale.US))

      if (existedAccount == null) {
        checkEmailSettingsLiveData.postValue(Result.loading(progressMsg = context.getString(R.string.connection)))
        val result = checkAuthCreds(accountEntity)
        checkEmailSettingsLiveData.postValue(result)
      } else {
        checkEmailSettingsLiveData.postValue(Result.exception(
            AccountAlreadyAddedException(context.getString(R.string.template_email_alredy_added, accountEntity.email))))
      }
    }
  }

  private suspend fun checkAuthCreds(accountEntity: AccountEntity): Result<Boolean?> =
      withContext(Dispatchers.IO) {
        val context: Context = getApplication()
        val authCreds = getAuthCredentials(accountEntity)
        val props = PropertiesHelper.genProps(accountEntity)
        props[JavaEmailConstants.PROPERTY_NAME_MAIL_IMAP_CONNECTIONTIMEOUT] = 1000 * 10
        props[JavaEmailConstants.PROPERTY_NAME_MAIL_SMTP_CONNECTIONTIMEOUT] = 1000 * 10

        val session = OpenStoreHelper.getAccountSess(getApplication(), accountEntity)

        try {
          checkEmailSettingsLiveData.postValue(Result.loading(progressMsg = context.getString(R.string.checking_imap_settings)))
          testImapConn(accountEntity, authCreds, session)
        } catch (e: Exception) {
          e.printStackTrace()
          return@withContext Result.exception(Exception("IMAP: " + e.message, e), null as Boolean?)
        }

        try {
          checkEmailSettingsLiveData.postValue(Result.loading(progressMsg = context.getString(R.string.checking_smtp_settings)))
          testSmtpConn(accountEntity, authCreds, session)
        } catch (e: Exception) {
          e.printStackTrace()
          return@withContext Result.exception(Exception("SMTP: " + e.message, e), null as Boolean?)
        }

        return@withContext Result.success(true)
      }

  private suspend fun getAuthCredentials(accountEntity: AccountEntity): AuthCredentials = withContext(Dispatchers.IO) {
    return@withContext when (accountEntity.accountType) {
      AccountEntity.ACCOUNT_TYPE_GOOGLE -> {
        if (JavaEmailConstants.AUTH_MECHANISMS_XOAUTH2.equals(accountEntity.imapAuthMechanisms, true)) {
          val token = EmailUtil.getGmailAccountToken(getApplication(), accountEntity)
          AuthCredentials.from(accountEntity.copy(password = token, smtpPassword = token))
        } else {
          AuthCredentials.from(accountEntity)
        }
      }

      else -> {
        AuthCredentials.from(accountEntity)
      }
    }
  }

  /**
   * Trying to connect to an IMAP server. If an exception will occur than that exception will be throw up.
   *
   * @param authCreds The [AuthCredentials] which will be used for the connection.
   * @param session The [Session] which will be used for the connection.
   * @throws MessagingException This operation can throw some exception.
   */
  private fun testImapConn(accountEntity: AccountEntity, authCreds: AuthCredentials, session: Session) {
    val store = OpenStoreHelper.openStore(accountEntity, authCreds, session)
    val folder = store.getFolder(JavaEmailConstants.FOLDER_INBOX)
    folder.open(Folder.READ_ONLY)
    folder.close(false)
    store.close()
  }

  /**
   * Trying to connect to the SMTP server. If an exception will occur than that exception will be throw up.
   *
   * @param authCreds The [AuthCredentials] which will be used for the connection.
   * @param session The [Session] which will be used for the connection.
   * @throws MessagingException This operation can throw some exception.
   */
  private fun testSmtpConn(accountEntity: AccountEntity, authCreds: AuthCredentials, session: Session) {
    val transport = SmtpProtocolUtil.prepareSmtpTransport(session, accountEntity, authCreds)
    transport.close()
  }
}
