/*
 * © 2016-2019 FlowCrypt Limited. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.jetpack.viewmodel

import android.app.Application
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.Transformations
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import androidx.paging.PagedList
import androidx.paging.toLiveData
import com.flowcrypt.email.api.email.JavaEmailConstants
import com.flowcrypt.email.api.email.model.LocalFolder
import com.flowcrypt.email.database.FlowCryptRoomDatabase
import com.flowcrypt.email.database.entity.AccountEntity
import com.flowcrypt.email.database.entity.MessageEntity
import kotlinx.coroutines.launch


/**
 * @author Denis Bondarenko
 *         Date: 12/17/19
 *         Time: 4:37 PM
 *         E-mail: DenBond7@gmail.com
 */
class MessagesViewModel(application: Application) : BaseAndroidViewModel(application) {
  private var currentLocalFolder: LocalFolder? = null
  private val roomDatabase = FlowCryptRoomDatabase.getDatabase(application)

  val accountLiveData: LiveData<AccountEntity?> = liveData {
    val accountEntity = roomDatabase.accountDao().getActiveAccount()
    emit(accountEntity)
  }

  var msgsLiveData: LiveData<PagedList<MessageEntity>>? = null

  fun loadMsgs(lifecycleOwner: LifecycleOwner, localFolder: LocalFolder?, observer:
  Observer<PagedList<MessageEntity>>, boundaryCallback: PagedList.BoundaryCallback<MessageEntity>) {
    if ((this.currentLocalFolder?.fullName == localFolder?.fullName).not()) {
      this.currentLocalFolder = localFolder
      msgsLiveData?.removeObserver(observer)
      msgsLiveData = Transformations.switchMap(accountLiveData) {
        val account = it?.email ?: ""
        val label = currentLocalFolder?.fullName ?: ""
        roomDatabase.msgDao().getMessagesDataSourceFactory(account, label)
            .toLiveData(pageSize = JavaEmailConstants.COUNT_OF_LOADED_EMAILS_BY_STEP / 3,
                boundaryCallback = boundaryCallback)
      }

      msgsLiveData?.observe(lifecycleOwner, observer)
    }
  }

  fun getActiveAccount(): AccountEntity? {
    return accountLiveData.value
  }

  fun cleanFolderCache(folderName: String?) {
    viewModelScope.launch {
      roomDatabase.msgDao().delete(accountLiveData.value?.email, folderName)
    }
  }
}