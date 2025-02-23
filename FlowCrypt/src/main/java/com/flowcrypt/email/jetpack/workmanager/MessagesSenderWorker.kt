/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.jetpack.workmanager

import android.accounts.AuthenticatorException
import android.content.Context
import android.net.Uri
import android.text.TextUtils
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.flowcrypt.email.BuildConfig
import com.flowcrypt.email.Constants
import com.flowcrypt.email.R
import com.flowcrypt.email.api.email.EmailUtil
import com.flowcrypt.email.api.email.FoldersManager
import com.flowcrypt.email.api.email.JavaEmailConstants
import com.flowcrypt.email.api.email.gmail.GmailApiHelper
import com.flowcrypt.email.api.email.model.AttachmentInfo
import com.flowcrypt.email.api.email.model.GeneralMessageDetails
import com.flowcrypt.email.api.email.protocol.OpenStoreHelper
import com.flowcrypt.email.api.email.protocol.SmtpProtocolUtil
import com.flowcrypt.email.database.FlowCryptRoomDatabase
import com.flowcrypt.email.database.MessageState
import com.flowcrypt.email.database.entity.AccountEntity
import com.flowcrypt.email.database.entity.AttachmentEntity
import com.flowcrypt.email.database.entity.MessageEntity
import com.flowcrypt.email.jetpack.viewmodel.AccountViewModel
import com.flowcrypt.email.security.KeysStorageImpl
import com.flowcrypt.email.security.SecurityUtils
import com.flowcrypt.email.security.pgp.PgpDecryptAndOrVerify
import com.flowcrypt.email.security.pgp.PgpEncryptAndOrSign
import com.flowcrypt.email.ui.notifications.NotificationChannelManager
import com.flowcrypt.email.util.FileAndDirectoryUtils
import com.flowcrypt.email.util.GeneralUtil
import com.flowcrypt.email.util.LogsUtil
import com.flowcrypt.email.util.exception.CopyNotSavedInSentFolderException
import com.flowcrypt.email.util.exception.ExceptionUtil
import com.flowcrypt.email.util.exception.ForceHandlingException
import com.flowcrypt.email.util.exception.ManualHandledException
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.common.util.CollectionUtils
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.FileContent
import com.sun.mail.imap.IMAPFolder
import com.sun.mail.util.MailConnectException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.apache.commons.io.IOUtils
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection
import org.pgpainless.key.protection.SecretKeyRingProtector
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.util.*
import javax.activation.DataHandler
import javax.activation.DataSource
import javax.mail.AuthenticationFailedException
import javax.mail.BodyPart
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.Session
import javax.mail.Store
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.net.ssl.SSLException

/**
 * @author Denis Bondarenko
 *
 * Date: 11.09.2018
 * Time: 18:43
 * E-mail: DenBond7@gmail.com
 */
class MessagesSenderWorker(context: Context, params: WorkerParameters) :
  BaseWorker(context, params) {

  override suspend fun doWork(): Result =
    withContext(Dispatchers.IO) {
      LogsUtil.d(TAG, "doWork")
      if (isStopped) {
        return@withContext Result.success()
      }

      try {
        val account = AccountViewModel.getAccountEntityWithDecryptedInfoSuspend(
          roomDatabase.accountDao().getActiveAccountSuspend()
        )
          ?: return@withContext Result.success()

        val attsCacheDir = File(applicationContext.cacheDir, Constants.ATTACHMENTS_CACHE_DIR)

        roomDatabase.msgDao().resetMsgsWithSendingStateSuspend(account.email)

        val queuedMsgs = roomDatabase.msgDao().getOutboxMsgsByStatesSuspend(
          account = account.email,
          msgStates = listOf(MessageState.QUEUED.value)
        )

        val sentButNotSavedMsgs = roomDatabase.msgDao().getOutboxMsgsByStatesSuspend(
          account = account.email,
          msgStates = listOf(
            MessageState.SENT_WITHOUT_LOCAL_COPY.value,
            MessageState.QUEUED_MAKE_COPY_IN_SENT_FOLDER.value
          )
        )

        if (!CollectionUtils.isEmpty(queuedMsgs) || !CollectionUtils.isEmpty(sentButNotSavedMsgs)) {
          setForeground(genForegroundInfo(account))

          if (account.useAPI) {
            when (account.accountType) {
              AccountEntity.ACCOUNT_TYPE_GOOGLE -> {
                if (!CollectionUtils.isEmpty(queuedMsgs)) {
                  sendQueuedMsgs(account, attsCacheDir)
                }
              }

              else -> throw ManualHandledException("Unsupported provider")
            }
          } else {
            val session = OpenStoreHelper.getAccountSess(applicationContext, account)
            OpenStoreHelper.openStore(applicationContext, account, session).use { store ->
              if (!CollectionUtils.isEmpty(queuedMsgs)) {
                sendQueuedMsgs(account, attsCacheDir, session, store)
              }

              if (!CollectionUtils.isEmpty(sentButNotSavedMsgs)) {
                saveCopyOfAlreadySentMsgs(account, session, store, attsCacheDir)
              }
            }
          }
        }

        return@withContext rescheduleIfActiveAccountWasChanged(account)
      } catch (e: Exception) {
        e.printStackTrace()
        when (e) {
          is UserRecoverableAuthException, is UserRecoverableAuthIOException, is AuthenticatorException, is AuthenticationFailedException -> {
            markMsgsWithAuthFailureState(roomDatabase)
          }

          else -> {
            val account = roomDatabase.accountDao().getActiveAccountSuspend()
            account?.email?.let {
              roomDatabase.msgDao().resetMsgsWithSendingStateSuspend(account.email)
            }

            ExceptionUtil.handleError(ForceHandlingException(e))
          }
        }

        return@withContext Result.failure()
      } finally {
        val account = roomDatabase.accountDao().getActiveAccountSuspend()
        account?.email?.let {
          roomDatabase.msgDao().resetMsgsWithSendingStateSuspend(account.email)
        }
        LogsUtil.d(TAG, "work was finished")
      }
    }

  private suspend fun markMsgsWithAuthFailureState(roomDatabase: FlowCryptRoomDatabase) {
    val account = roomDatabase.accountDao().getActiveAccountSuspend()
    roomDatabase.msgDao().changeMsgsStateSuspend(
      account = account?.email,
      label = JavaEmailConstants.FOLDER_OUTBOX,
      oldValue = MessageState.QUEUED.value,
      newValues = MessageState.AUTH_FAILURE.value
    )
  }

  private fun genForegroundInfo(account: AccountEntity): ForegroundInfo {
    val title = applicationContext.getString(R.string.sending_email)
    val notification = NotificationCompat.Builder(
      applicationContext,
      NotificationChannelManager.CHANNEL_ID_SYNC
    )
      .setContentTitle(title)
      .setTicker(title)
      .setSmallIcon(R.drawable.ic_sending_email_grey_24dp)
      .setOngoing(true)
      .setSubText(account.email)
      .setProgress(0, 0, true)
      .build()

    return ForegroundInfo(NOTIFICATION_ID, notification)
  }

  private suspend fun sendQueuedMsgs(
    account: AccountEntity, attsCacheDir: File, sess: Session? =
      null, store: Store? = null
  ) =
    withContext(Dispatchers.IO) {
      var list: List<MessageEntity>
      var lastMsgUID = 0L
      val email = account.email
      val roomDatabase = FlowCryptRoomDatabase.getDatabase(applicationContext)
      while (true) {
        list = roomDatabase.msgDao().getOutboxMsgsByStatesSuspend(
          account = account.email,
          msgStates = listOf(MessageState.QUEUED.value)
        )

        if (CollectionUtils.isEmpty(list)) {
          break
        }

        val iterator = list.iterator()
        var msgEntity: MessageEntity? = null

        while (iterator.hasNext()) {
          val tempMsgDetails = iterator.next()
          if (tempMsgDetails.uid > lastMsgUID) {
            msgEntity = tempMsgDetails
            break
          }
        }

        if (msgEntity == null) {
          msgEntity = list[0]
        }

        lastMsgUID = msgEntity.uid

        try {
          roomDatabase.msgDao().resetMsgsWithSendingStateSuspend(account.email)
          roomDatabase.msgDao().updateSuspend(msgEntity.copy(state = MessageState.SENDING.value))
          delay(2000)

          val attachments = roomDatabase.attachmentDao()
            .getAttachmentsSuspend(email, JavaEmailConstants.FOLDER_OUTBOX, msgEntity.uid)
          val isMsgSent = sendMsg(account, msgEntity, attachments, sess, store)

          if (!isMsgSent) {
            continue
          }

          msgEntity = roomDatabase.msgDao()
            .getMsgSuspend(email, JavaEmailConstants.FOLDER_OUTBOX, msgEntity.uid)

          if (msgEntity != null && msgEntity.msgState === MessageState.SENT) {
            roomDatabase.msgDao().deleteSuspend(msgEntity)

            if (!CollectionUtils.isEmpty(attachments)) {
              deleteMsgAtts(account, attsCacheDir, msgEntity)
            }

            val outgoingMsgCount = roomDatabase.msgDao().getOutboxMsgsSuspend(email).size
            val outboxLabel = roomDatabase.labelDao()
              .getLabelSuspend(email, account.accountType, JavaEmailConstants.FOLDER_OUTBOX)

            outboxLabel?.let {
              roomDatabase.labelDao().updateSuspend(it.copy(messagesTotal = outgoingMsgCount))
            }
          }
        } catch (e: Exception) {
          e.printStackTrace()
          ExceptionUtil.handleError(e)

          if (!GeneralUtil.isConnected(applicationContext)) {
            if (msgEntity.msgState !== MessageState.SENT) {
              roomDatabase.msgDao().updateSuspend(msgEntity.copy(state = MessageState.QUEUED.value))
            }

            throw e
          } else {
            val newMsgState = when (e) {
              is MailConnectException -> {
                MessageState.QUEUED
              }

              is MessagingException -> {
                if (e.cause is SSLException || e.cause is SocketException) {
                  MessageState.QUEUED
                } else {
                  MessageState.ERROR_SENDING_FAILED
                }
              }

              is CopyNotSavedInSentFolderException -> MessageState.ERROR_COPY_NOT_SAVED_IN_SENT_FOLDER

              else -> {
                when (e.cause) {
                  is FileNotFoundException -> MessageState.ERROR_CACHE_PROBLEM

                  else -> MessageState.ERROR_SENDING_FAILED
                }
              }
            }

            roomDatabase.msgDao()
              .updateSuspend(msgEntity.copy(state = newMsgState.value, errorMsg = e.message))
          }

          delay(5000)
        }
      }
    }

  private suspend fun saveCopyOfAlreadySentMsgs(
    account: AccountEntity,
    sess: Session,
    store: Store,
    attsCacheDir: File
  ) =
    withContext(Dispatchers.IO) {
      var list: List<MessageEntity>
      val email = account.email
      val roomDatabase = FlowCryptRoomDatabase.getDatabase(applicationContext)
      while (true) {
        list = roomDatabase.msgDao().getOutboxMsgsByStatesSuspend(
          account = account.email,
          msgStates = listOf(
            MessageState.SENT_WITHOUT_LOCAL_COPY.value,
            MessageState.QUEUED_MAKE_COPY_IN_SENT_FOLDER.value
          )
        )
        if (CollectionUtils.isEmpty(list)) {
          break
        }
        val msgEntity = list.first()
        try {
          val attachments = roomDatabase.attachmentDao()
            .getAttachmentsSuspend(email, JavaEmailConstants.FOLDER_OUTBOX, msgEntity.uid)

          val mimeMsg = createMimeMsg(sess, account, msgEntity, attachments)

          roomDatabase.msgDao().resetMsgsWithSendingStateSuspend(account.email)
          roomDatabase.msgDao().updateSuspend(msgEntity.copy(state = MessageState.SENDING.value))
          delay(2000)

          val isMsgSaved = saveCopyOfSentMsg(account, store, mimeMsg)

          if (!isMsgSaved) {
            continue
          }

          roomDatabase.msgDao().deleteSuspend(msgEntity)

          if (attachments.isNotEmpty()) {
            deleteMsgAtts(account, attsCacheDir, msgEntity)
          }
        } catch (e: Exception) {
          e.printStackTrace()
          ExceptionUtil.handleError(e)

          if (!GeneralUtil.isConnected(applicationContext)) {
            roomDatabase.msgDao().updateSuspend(
              msgEntity.copy(
                state = MessageState.SENT_WITHOUT_LOCAL_COPY.value
              )
            )
            throw e
          }

          when (e) {
            is CopyNotSavedInSentFolderException -> {
              roomDatabase.msgDao().updateSuspend(
                msgEntity.copy(
                  state = MessageState.ERROR_COPY_NOT_SAVED_IN_SENT_FOLDER.value,
                  errorMsg = e.message
                )
              )
            }

            else -> {
              when (e.cause) {
                is FileNotFoundException -> {
                  roomDatabase.msgDao().deleteSuspend(msgEntity)
                }

                else -> {
                  roomDatabase.msgDao().updateSuspend(
                    msgEntity.copy(
                      state = MessageState.ERROR_COPY_NOT_SAVED_IN_SENT_FOLDER.value,
                      errorMsg = e.message
                    )
                  )
                }
              }
            }
          }
        }
      }
    }

  private suspend fun deleteMsgAtts(
    account: AccountEntity,
    attsCacheDir: File,
    details: MessageEntity
  ) =
    withContext(Dispatchers.IO) {
      FlowCryptRoomDatabase.getDatabase(applicationContext).attachmentDao().deleteAttSuspend(
        account = account.email,
        label = JavaEmailConstants.FOLDER_OUTBOX,
        uid = details.uid
      )
      details.attachmentsDirectory?.let { FileAndDirectoryUtils.deleteDir(File(attsCacheDir, it)) }
    }

  private suspend fun sendMsg(
    account: AccountEntity, msgEntity: MessageEntity,
    atts: List<AttachmentEntity>, sess: Session?, store: Store?
  ): Boolean =
    withContext(Dispatchers.IO) {
      val mimeMsg = createMimeMsg(sess, account, msgEntity, atts)
      val roomDatabase = FlowCryptRoomDatabase.getDatabase(applicationContext)

      when (account.accountType) {
        AccountEntity.ACCOUNT_TYPE_GOOGLE -> {
          if (!account.useAPI && account.email.equals(
              msgEntity.from.firstOrNull()?.address,
              ignoreCase = true
            )
          ) {
            sess ?: throw NullPointerException("Session == null")
            val transport = SmtpProtocolUtil.prepareSmtpTransport(applicationContext, sess, account)
            transport.sendMessage(mimeMsg, mimeMsg.allRecipients)
          } else {
            val gmail = GmailApiHelper.generateGmailApiService(applicationContext, account)
            val copyOfMimeMsg = File.createTempFile("tmp", null, applicationContext.cacheDir)
            try {
              //todo-denbond7 it will be a temporary solution until we will migrate to a new logic
              FileOutputStream(copyOfMimeMsg).use { out ->
                mimeMsg.writeTo(out)
              }

              val threadId = msgEntity.threadId
                ?: mimeMsg.getHeader(JavaEmailConstants.HEADER_IN_REPLY_TO, null)
                  ?.let { replyMsgId ->
                    GmailApiHelper.executeWithResult {
                      com.flowcrypt.email.api.retrofit.response.base.Result.success(
                        GmailApiHelper.getGmailMsgThreadID(gmail, replyMsgId)
                      )
                    }.data
                  }

              var gmailMsg = com.google.api.services.gmail.model.Message().apply {
                this.threadId = threadId
              }

              val mediaContent = FileContent("message/rfc822", copyOfMimeMsg)

              gmailMsg = gmail
                .users()
                .messages()
                .send(GmailApiHelper.DEFAULT_USER_ID, gmailMsg, mediaContent)
                .execute()

              if (gmailMsg.id == null) {
                return@withContext false
              }
            } finally {
              if (copyOfMimeMsg.exists()) {
                copyOfMimeMsg.delete()
              }
            }
          }

          roomDatabase.msgDao().updateSuspend(msgEntity.copy(state = MessageState.SENT.value))
          //Gmail automatically save a copy of the sent message.
        }

        AccountEntity.ACCOUNT_TYPE_OUTLOOK -> {
          sess ?: throw NullPointerException("Session == null")
          val outlookTransport =
            SmtpProtocolUtil.prepareSmtpTransport(applicationContext, sess, account)
          outlookTransport.sendMessage(mimeMsg, mimeMsg.allRecipients)
          roomDatabase.msgDao().updateSuspend(msgEntity.copy(state = MessageState.SENT.value))
        }

        else -> {
          sess ?: throw NullPointerException("Session == null")
          val defaultTransport =
            SmtpProtocolUtil.prepareSmtpTransport(applicationContext, sess, account)
          defaultTransport.sendMessage(mimeMsg, mimeMsg.allRecipients)
          roomDatabase.msgDao()
            .updateSuspend(msgEntity.copy(state = MessageState.SENT_WITHOUT_LOCAL_COPY.value))

          store ?: throw NullPointerException("Store == null")
          if (saveCopyOfSentMsg(account, store, mimeMsg)) {
            roomDatabase.msgDao().updateSuspend(msgEntity.copy(state = MessageState.SENT.value))
          }
        }
      }

      return@withContext true
    }

  /**
   * Create [MimeMessage] from the given [GeneralMessageDetails].
   *
   * @param sess    Will be used to create [MimeMessage]
   * @throws IOException
   * @throws MessagingException
   */
  private suspend fun createMimeMsg(
    sess: Session?,
    account: AccountEntity,
    msgEntity: MessageEntity,
    atts: List<AttachmentEntity>
  ): MimeMessage =
    withContext(Dispatchers.IO) {
      val stream =
        IOUtils.toInputStream(msgEntity.rawMessageWithoutAttachments, StandardCharsets.UTF_8)
      val mimeMsg = MimeMessage(sess, stream)

      //https://tools.ietf.org/html/draft-melnikov-email-user-agent-00#:~:text=User%2DAgent%20and%20X%2DMailer%20are%20common%20Email%20header%20fields,use%20of%20different%20email%20clients.
      mimeMsg.addHeader("User-Agent", "FlowCrypt_Android_" + BuildConfig.VERSION_NAME)

      if (mimeMsg.content is MimeMultipart && atts.isNotEmpty()) {
        val mimeMultipart = mimeMsg.content as MimeMultipart
        val keysStorage = KeysStorageImpl.getInstance(applicationContext)
        val secretKeys = PGPSecretKeyRingCollection(keysStorage.getPGPSecretKeyRings())
        val ringProtector = keysStorage.getSecretKeyRingProtector()

        val publicKeys = mutableListOf<String>()
        val senderEmail = EmailUtil.getFirstAddressString(msgEntity.from)
        val recipients = msgEntity.allRecipients.toMutableList()
        publicKeys.addAll(
          SecurityUtils.getRecipientsUsablePubKeys(applicationContext, recipients)
        )
        publicKeys.addAll(
          SecurityUtils.getSenderPgpKeyDetailsList(applicationContext, account, senderEmail)
            .map { it.publicKey })

        for (att in atts) {
          val attBodyPart = genBodyPartWithAtt(
            att = att,
            shouldBeEncrypted = msgEntity.isEncrypted ?: false,
            publicKeys = publicKeys,
            secretKeys = secretKeys,
            ringProtector = ringProtector
          )
          mimeMultipart.addBodyPart(attBodyPart)
        }

        mimeMsg.setContent(mimeMultipart)
        mimeMsg.saveChanges()
      }

      return@withContext mimeMsg
    }

  /**
   * Generate a [BodyPart] with an attachment.
   *
   * @param att     The [AttachmentInfo] object, which contains general information about the
   * attachment.
   * @return Generated [MimeBodyPart] with the attachment.
   * @throws MessagingException
   */
  private fun genBodyPartWithAtt(
    att: AttachmentEntity,
    shouldBeEncrypted: Boolean,
    publicKeys: List<String>?,
    secretKeys: PGPSecretKeyRingCollection,
    ringProtector: SecretKeyRingProtector
  ): BodyPart {
    val attBodyPart = MimeBodyPart()
    val attInfo = att.toAttInfo()
    attBodyPart.dataHandler = if (attInfo.isForwarded) {
      DataHandler(
        ForwardedAttachmentInfoDataSource(
          applicationContext,
          attInfo,
          shouldBeEncrypted,
          publicKeys,
          secretKeys,
          ringProtector
        )
      )
    } else {
      DataHandler(AttachmentInfoDataSource(applicationContext, attInfo))
    }
    attBodyPart.fileName = attInfo.getSafeName()
    attBodyPart.contentID = attInfo.id

    return attBodyPart
  }

  /**
   * Save a copy of the sent message to the account SENT folder.
   *
   * @param account The object which contains information about an email account.
   * @param store   The connected and opened [Store] object.
   * @param mimeMsg The original [MimeMessage] which will be saved to the SENT folder.
   */
  private suspend fun saveCopyOfSentMsg(
    account: AccountEntity,
    store: Store,
    mimeMsg: MimeMessage
  ): Boolean =
    withContext(Dispatchers.IO) {
      val foldersManager = FoldersManager.fromDatabaseSuspend(applicationContext, account)
      val sentLocalFolder = foldersManager.findSentFolder()

      if (sentLocalFolder != null) {
        store.getFolder(sentLocalFolder.fullName).use { folder ->
          val sentRemoteFolder = (folder as IMAPFolder).apply {
            if (exists()) {
              open(Folder.READ_WRITE)
            } else throw IllegalArgumentException("The SENT folder doesn't exists. Can't create a copy of the sent message!")
          }
          mimeMsg.setFlag(Flags.Flag.SEEN, true)
          sentRemoteFolder.appendMessages(arrayOf<Message>(mimeMsg))
          return@withContext true
        }
      } else throw CopyNotSavedInSentFolderException(
        "An error occurred during saving a copy of the outgoing message. " +
            "The SENT folder is not defined. Please contact the support: " +
            applicationContext.getString(R.string.support_email) + "\n\nProvider: "
            + account.email.substring(account.email.indexOf("@") + 1)
      )
    }

  /**
   * The [DataSource] realization for a file which received from [Uri]
   */
  private open class AttachmentInfoDataSource(
    private val context: Context,
    protected val att: AttachmentInfo
  ) : DataSource {

    override fun getInputStream(): InputStream? {
      val inputStream: InputStream? = if (att.uri == null) {
        val rawData = att.rawData ?: return null
        ByteArrayInputStream(rawData)
      } else {
        att.uri?.let { context.contentResolver.openInputStream(it) }
      }

      return if (inputStream == null) null else BufferedInputStream(inputStream)
    }

    override fun getOutputStream(): OutputStream? {
      return null
    }

    /**
     * If a content type is unknown we return "application/octet-stream".
     * http://www.rfc-editor.org/rfc/rfc2046.txt (section 4.5.1.  Octet-Stream Subtype)
     */
    override fun getContentType(): String {
      return if (TextUtils.isEmpty(att.type)) Constants.MIME_TYPE_BINARY_DATA else att.type
    }

    override fun getName(): String {
      return att.getSafeName()
    }
  }

  private class ForwardedAttachmentInfoDataSource(
    context: Context,
    att: AttachmentInfo,
    private val shouldBeEncrypted: Boolean,
    private val publicKeys: List<String>? = null,
    private val secretKeys: PGPSecretKeyRingCollection,
    private val protector: SecretKeyRingProtector
  ) : AttachmentInfoDataSource(context, att) {
    override fun getInputStream(): InputStream? {
      val inputStream = super.getInputStream() ?: return null
      val srcInputStream = if (att.decryptWhenForward) PgpDecryptAndOrVerify.genDecryptionStream(
        srcInputStream = inputStream,
        secretKeys = secretKeys,
        protector = protector
      ) else inputStream

      return if (shouldBeEncrypted) {
        //here we use [ByteArrayOutputStream] as a temp destination of encrypted data.
        //it should be improved in the future for better performance
        val tempByteArrayOutputStream = ByteArrayOutputStream()
        PgpEncryptAndOrSign.encryptAndOrSign(
          srcInputStream = srcInputStream,
          destOutputStream = tempByteArrayOutputStream,
          pubKeys = requireNotNull(publicKeys)
        )

        return ByteArrayInputStream(tempByteArrayOutputStream.toByteArray())
      } else {
        srcInputStream
      }
    }
  }

  companion object {
    private val TAG = MessagesSenderWorker::class.java.simpleName
    private const val NOTIFICATION_ID = -10000
    val NAME = MessagesSenderWorker::class.java.simpleName

    fun enqueue(context: Context, forceSending: Boolean = false) {
      val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

      WorkManager
        .getInstance(context.applicationContext)
        .enqueueUniqueWork(
          NAME,
          if (forceSending) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
          OneTimeWorkRequestBuilder<MessagesSenderWorker>()
            .setConstraints(constraints)
            .build()
        )
    }
  }
}
