/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.api.retrofit

import android.content.Context
import com.flowcrypt.email.api.retrofit.request.api.DomainRulesRequest
import com.flowcrypt.email.api.retrofit.request.api.LoginRequest
import com.flowcrypt.email.api.retrofit.request.model.InitialLegacySubmitModel
import com.flowcrypt.email.api.retrofit.request.model.PostLookUpEmailModel
import com.flowcrypt.email.api.retrofit.request.model.PostLookUpEmailsModel
import com.flowcrypt.email.api.retrofit.request.model.TestWelcomeModel
import com.flowcrypt.email.api.retrofit.response.api.DomainRulesResponse
import com.flowcrypt.email.api.retrofit.response.api.LoginResponse
import com.flowcrypt.email.api.retrofit.response.attester.InitialLegacySubmitResponse
import com.flowcrypt.email.api.retrofit.response.attester.LookUpEmailResponse
import com.flowcrypt.email.api.retrofit.response.attester.LookUpEmailsResponse
import com.flowcrypt.email.api.retrofit.response.attester.PubResponse
import com.flowcrypt.email.api.retrofit.response.attester.TestWelcomeResponse
import com.flowcrypt.email.api.retrofit.response.base.Result
import com.flowcrypt.email.api.retrofit.response.oauth2.MicrosoftOAuth2TokenResponse
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementation of Flowcrypt API
 *
 * @author Denis Bondarenko
 *         Date: 10/24/19
 *         Time: 6:10 PM
 *         E-mail: DenBond7@gmail.com
 */
class FlowcryptApiRepository : ApiRepository {
  override suspend fun login(context: Context, request: LoginRequest): Result<LoginResponse> =
      withContext(Dispatchers.IO) {
        val apiService = ApiHelper.getInstance(context).retrofit.create(ApiService::class.java)
        getResult { apiService.postLogin(request.requestModel, "Bearer ${request.tokenId}") }
      }

  override suspend fun getDomainRules(context: Context, request: DomainRulesRequest): Result<DomainRulesResponse> =
      withContext(Dispatchers.IO) {
        val apiService = ApiHelper.getInstance(context).retrofit.create(ApiService::class.java)
        getResult { apiService.getDomainRules(request.requestModel) }
      }

  override suspend fun submitPubKey(context: Context, model: InitialLegacySubmitModel): Result<InitialLegacySubmitResponse> =
      withContext(Dispatchers.IO) {
        val apiService = ApiHelper.getInstance(context).retrofit.create(ApiService::class.java)
        getResult { apiService.submitPubKey(model) }
      }

  override suspend fun postLookUpEmails(context: Context, model: PostLookUpEmailsModel): Result<LookUpEmailsResponse> =
      withContext(Dispatchers.IO) {
        val apiService = ApiHelper.getInstance(context).retrofit.create(ApiService::class.java)
        getResult { apiService.postLookUpEmails(model) }
      }

  override suspend fun postLookUpEmail(context: Context, model: PostLookUpEmailModel): Result<LookUpEmailResponse> =
      withContext(Dispatchers.IO) {
        val apiService = ApiHelper.getInstance(context).retrofit.create(ApiService::class.java)
        getResult { apiService.postLookUpEmail(model) }
      }

  override suspend fun postInitialLegacySubmit(context: Context, model: InitialLegacySubmitModel): Result<InitialLegacySubmitResponse> =
      withContext(Dispatchers.IO) {
        val apiService = ApiHelper.getInstance(context).retrofit.create(ApiService::class.java)
        getResult { apiService.postInitialLegacySubmitSuspend(model) }
      }

  override suspend fun postTestWelcome(context: Context, model: TestWelcomeModel): Result<TestWelcomeResponse> =
      withContext(Dispatchers.IO) {
        val apiService = ApiHelper.getInstance(context).retrofit.create(ApiService::class.java)
        getResult { apiService.postTestWelcomeSuspend(model) }
      }

  //todo-denbond7 need to ask Tom to improve https://flowcrypt.com/attester/pub to use the common
  // API  response ([ApiResponse])
  override suspend fun getPub(requestCode: Long, context: Context, keyIdOrEmail: String): Result<PubResponse> =
      withContext(Dispatchers.IO) {
        val apiService = ApiHelper.getInstance(context).retrofit.create(ApiService::class.java)
        val result = getResult(requestCode = requestCode) { apiService.getPub(keyIdOrEmail) }
        when (result.status) {
          Result.Status.SUCCESS -> Result.success(requestCode = requestCode, data = PubResponse(null, result.data))

          Result.Status.ERROR -> Result.error(requestCode = requestCode, data = PubResponse(null, null))

          Result.Status.EXCEPTION -> Result.exception(requestCode = requestCode, error = result.exception
              ?: Exception())

          Result.Status.LOADING -> Result.loading(requestCode = requestCode)
        }
      }

  override suspend fun getMicrosoftOAuth2Token(requestCode: Long, context: Context,
                                               authorizeCode: String, scopes: String, codeVerifier: String):
      Result<MicrosoftOAuth2TokenResponse> =
      withContext(Dispatchers.IO) {
        val apiService = ApiHelper.getInstance(context).retrofit.create(ApiService::class.java)
        getResult(
            context = context,
            expectedResultClass = MicrosoftOAuth2TokenResponse::class.java
        ) {
          apiService.getMicrosoftOAuth2Token(authorizeCode, scopes, codeVerifier)
        }
      }

  override suspend fun getOpenIdConfiguration(requestCode: Long, context: Context, url: String): Result<JsonObject> =
      withContext(Dispatchers.IO) {
        val apiService = ApiHelper.getInstance(context).retrofit.create(ApiService::class.java)
        getResult {
          apiService.getOpenIdConfiguration(url)
        }
      }
}