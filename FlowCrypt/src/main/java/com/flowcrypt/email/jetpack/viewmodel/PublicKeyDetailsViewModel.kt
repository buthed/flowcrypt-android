/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.jetpack.viewmodel

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.flowcrypt.email.api.retrofit.response.base.Result
import com.flowcrypt.email.database.entity.PublicKeyEntity
import com.flowcrypt.email.security.pgp.PgpKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/**
 * @author Denis Bondarenko
 *         Date: 11/19/21
 *         Time: 6:48 PM
 *         E-mail: DenBond7@gmail.com
 */
class PublicKeyDetailsViewModel(
  publicKeyEntity: PublicKeyEntity, application: Application
) : AccountViewModel(application) {

  private val publicKeyEntityFlow =
    roomDatabase.pubKeyDao().getPublicKeyByIdFlow(publicKeyEntity.id ?: -1)

  @ExperimentalCoroutinesApi
  val publicKeyEntityWithPgpDetailFlow: StateFlow<Result<PublicKeyEntity?>> =
    publicKeyEntityFlow.flatMapLatest { publicKeyEntity ->
      flow {
        emit(Result.loading())
        try {
          if (publicKeyEntity != null) {
            withContext(Dispatchers.IO) {
              publicKeyEntity.pgpKeyDetails =
                PgpKey.parseKeys(publicKeyEntity.publicKey, false).pgpKeyDetailsList.firstOrNull()
            }
          }
          emit(Result.success(publicKeyEntity))
        } catch (e: Exception) {
          emit(Result.exception(e))
        }
      }
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = Result.none()
    )
}
