/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.jetpack.workmanager.sync

import android.content.Context
import androidx.work.WorkerParameters
import com.flowcrypt.email.api.email.EmailUtil
import com.flowcrypt.email.api.email.model.LocalFolder
import com.flowcrypt.email.database.entity.AccountEntity
import com.flowcrypt.email.database.entity.MessageEntity
import com.flowcrypt.email.extensions.kotlin.toHex
import com.flowcrypt.email.service.MessagesNotificationManager
import com.flowcrypt.email.util.GeneralUtil
import com.sun.mail.imap.IMAPFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.mail.Flags
import javax.mail.Message

/**
 * @author Denis Bondarenko
 *         Date: 12/10/20
 *         Time: 11:29 AM
 *         E-mail: DenBond7@gmail.com
 */
abstract class BaseIdleWorker(context: Context, params: WorkerParameters) :
  BaseSyncWorker(context, params) {
  private val notificationManager = MessagesNotificationManager(applicationContext)

  protected suspend fun processDeletedMsgs(
    cachedUIDSet: Set<Long>, remoteFolder: IMAPFolder,
    msgs: Array<Message>, accountEntity: AccountEntity,
    folderFullName: String
  ) = withContext(Dispatchers.IO) {
    val deleteCandidatesUIDs = EmailUtil.genDeleteCandidates(cachedUIDSet, remoteFolder, msgs)
    processDeletedMsgs(accountEntity, folderFullName, deleteCandidatesUIDs)
  }

  protected suspend fun processDeletedMsgs(
    accountEntity: AccountEntity, folderFullName: String,
    deleteCandidatesUIDs: Collection<Long>
  ) {
    roomDatabase.msgDao()
      .deleteByUIDsSuspend(accountEntity.email, folderFullName, deleteCandidatesUIDs)
    if (!GeneralUtil.isAppForegrounded()) {
      for (uid in deleteCandidatesUIDs) {
        notificationManager.cancel(uid.toHex())
      }
    }
  }

  protected suspend fun processUpdatedMsgs(
    mapOfUIDAndMsgFlags: Map<Long, String?>,
    remoteFolder: IMAPFolder,
    msgs: Array<Message>,
    accountEntity: AccountEntity, folderFullName: String
  ) = withContext(Dispatchers.IO) {
    val updateCandidates = EmailUtil.genUpdateCandidates(mapOfUIDAndMsgFlags, remoteFolder, msgs)
      .map { remoteFolder.getUID(it) to it.flags }.toMap()
    processUpdatedMsgs(accountEntity, folderFullName, updateCandidates)
  }

  protected suspend fun processUpdatedMsgs(
    accountEntity: AccountEntity,
    folderFullName: String,
    updateCandidates: Map<Long, Flags>
  ) {
    roomDatabase.msgDao().updateFlagsSuspend(accountEntity.email, folderFullName, updateCandidates)

    if (!GeneralUtil.isAppForegrounded()) {
      for (item in updateCandidates) {
        val uid = item.key
        if (item.value.contains(Flags.Flag.SEEN)) {
          notificationManager.cancel(uid.toHex())
        }
      }
    }
  }

  protected suspend fun processNewMsgs(
    newMsgs: Array<Message>, accountEntity: AccountEntity,
    localFolder: LocalFolder, remoteFolder: IMAPFolder
  ) = withContext(Dispatchers.IO) {
    if (newMsgs.isNotEmpty()) {
      EmailUtil.fetchMsgs(remoteFolder, newMsgs)
      val msgsEncryptionStates =
        EmailUtil.getMsgsEncryptionInfo(accountEntity.showOnlyEncrypted, remoteFolder, newMsgs)
      val msgEntities = MessageEntity.genMessageEntities(
        context = applicationContext,
        email = accountEntity.email,
        label = localFolder.fullName,
        folder = remoteFolder,
        msgs = newMsgs,
        msgsEncryptionStates = msgsEncryptionStates,
        isNew = !GeneralUtil.isAppForegrounded(),
        areAllMsgsEncrypted = false
      )

      processNewMsgs(accountEntity, localFolder, msgEntities)
    }
  }

  protected suspend fun processNewMsgs(
    accountEntity: AccountEntity,
    localFolder: LocalFolder,
    msgEntities: List<MessageEntity>
  ) {
    roomDatabase.msgDao().insertWithReplaceSuspend(msgEntities)

    if (!GeneralUtil.isAppForegrounded()) {
      val detailsList =
        roomDatabase.msgDao().getNewMsgsSuspend(accountEntity.email, localFolder.fullName)
      notificationManager.notify(applicationContext, accountEntity, localFolder, detailsList)
    }
  }
}
