/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.ui.activity.fragment

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.fragment.app.viewModels
import com.flowcrypt.email.Constants
import com.flowcrypt.email.NavGraphDirections
import com.flowcrypt.email.R
import com.flowcrypt.email.api.retrofit.response.base.Result
import com.flowcrypt.email.api.retrofit.response.model.OrgRules
import com.flowcrypt.email.database.entity.AccountEntity
import com.flowcrypt.email.databinding.FragmentBackupKeysBinding
import com.flowcrypt.email.extensions.decrementSafely
import com.flowcrypt.email.extensions.getNavigationResult
import com.flowcrypt.email.extensions.gone
import com.flowcrypt.email.extensions.incrementSafely
import com.flowcrypt.email.extensions.navController
import com.flowcrypt.email.extensions.toast
import com.flowcrypt.email.jetpack.viewmodel.BackupsViewModel
import com.flowcrypt.email.jetpack.viewmodel.PrivateKeysViewModel
import com.flowcrypt.email.security.KeysStorageImpl
import com.flowcrypt.email.security.SecurityUtils
import com.flowcrypt.email.ui.activity.fragment.base.BaseFragment
import com.flowcrypt.email.ui.activity.fragment.base.ProgressBehaviour
import com.flowcrypt.email.ui.activity.fragment.dialog.FixNeedPassphraseIssueDialogFragment
import com.flowcrypt.email.util.GeneralUtil
import com.flowcrypt.email.util.exception.DifferentPassPhrasesException
import com.flowcrypt.email.util.exception.EmptyPassphraseException
import com.flowcrypt.email.util.exception.ExceptionUtil
import com.flowcrypt.email.util.exception.NoPrivateKeysAvailableException
import com.flowcrypt.email.util.exception.PrivateKeyStrengthException
import com.google.android.material.snackbar.Snackbar

/**
 * This activity helps to backup private keys
 *
 * @author Denis Bondarenko
 * Date: 07.08.2018
 * Time: 15:06
 * E-mail: DenBond7@gmail.com
 */
class BackupKeysFragment : BaseFragment(), ProgressBehaviour {
  private var binding: FragmentBackupKeysBinding? = null

  override val progressView: View?
    get() = binding?.iProgress?.root
  override val contentView: View?
    get() = binding?.gContent
  override val statusView: View?
    get() = binding?.iStatus?.root

  private val backupsViewModel: BackupsViewModel by viewModels()
  private val privateKeysViewModel: PrivateKeysViewModel by viewModels()

  private var isPrivateKeySendingNow: Boolean = false
  private var areBackupsSavingNow: Boolean = false
  private var destinationUri: Uri? = null

  override val contentResourceId: Int = R.layout.fragment_backup_keys

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    requireActivity().onBackPressedDispatcher.addCallback(this) {
      when {
        areBackupsSavingNow -> toast(R.string.please_wait_while_backup_will_be_saved)
        isPrivateKeySendingNow -> toast(R.string.please_wait_while_message_will_be_sent)
        else -> navController?.navigateUp()
      }
    }
  }

  override fun onCreateView(
    inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
  ): View? {
    binding = FragmentBackupKeysBinding.inflate(inflater, container, false)
    return binding?.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    supportActionBar?.title = getString(R.string.backup_options)
    initViews()
    setupPrivateKeysViewModel()
    setupBackupsViewModel()
    observeOnResultLiveData()
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    when (requestCode) {
      REQUEST_CODE_GET_URI_FOR_SAVING_PRIVATE_KEY -> when (resultCode) {
        Activity.RESULT_OK -> if (data != null && data.data != null) {
          try {
            destinationUri = data.data
            destinationUri?.let { privateKeysViewModel.saveBackupsAsFile(it) }
          } catch (e: Exception) {
            e.printStackTrace()
            ExceptionUtil.handleError(e)
            showInfoSnackbar(binding?.root, e.message ?: "")
          }
        }
      }
    }
  }

  override fun onAccountInfoRefreshed(accountEntity: AccountEntity?) {
    super.onAccountInfoRefreshed(accountEntity)
    updateBackupButtonVisibility(accountEntity)
  }

  private fun initViews() {
    binding?.rGBackupOptions?.setOnCheckedChangeListener { group, checkedId ->
      when (group.id) {
        R.id.rGBackupOptions -> when (checkedId) {
          R.id.rBEmailOption -> {
            binding?.tVHint?.text = getString(R.string.backup_as_email_hint)
            binding?.btBackup?.text = getString(R.string.backup_as_email)
            updateBackupButtonVisibility(account)
          }

          R.id.rBDownloadOption -> {
            binding?.tVHint?.text = getString(R.string.backup_as_download_hint)
            binding?.btBackup?.text = getString(R.string.backup_as_a_file)
            updateBackupButtonVisibility(account)
          }
        }
      }
    }

    binding?.btBackup?.setOnClickListener {
      if (account?.isRuleExist(OrgRules.DomainRule.NO_PRV_BACKUP)?.not() == true) {
        if (KeysStorageImpl.getInstance(requireContext()).getRawKeys().isNullOrEmpty()) {
          showInfoSnackbar(
            view = binding?.root,
            msgText = getString(
              R.string.there_are_no_private_keys,
              account?.email
            ), duration = Snackbar.LENGTH_LONG
          )
        } else {
          when (binding?.rGBackupOptions?.checkedRadioButtonId) {
            R.id.rBEmailOption -> {
              dismissCurrentSnackBar()
              checkForEmptyPassphraseOrRunAction {
                if (GeneralUtil.isConnected(requireContext())) {
                  backupsViewModel.postBackup()
                } else {
                  showInfoSnackbar(
                    view = binding?.root,
                    msgText = getString(R.string.internet_connection_is_not_available)
                  )
                }
              }
            }

            R.id.rBDownloadOption -> {
              dismissCurrentSnackBar()
              destinationUri = null
              checkForEmptyPassphraseOrRunAction {
                chooseDestForExportedKey()
              }
            }
          }
        }
      }
    }
  }

  private fun setupPrivateKeysViewModel() {
    privateKeysViewModel.saveBackupAsFileLiveData.observe(viewLifecycleOwner, {
      it?.let {
        when (it.status) {
          Result.Status.LOADING -> {
            baseActivity.countingIdlingResource.incrementSafely()
            showProgress(getString(R.string.processing))
            areBackupsSavingNow = true
          }

          Result.Status.SUCCESS -> {
            areBackupsSavingNow = false
            val result = it.data
            if (result == true) {
              toast(R.string.backed_up_successfully)
              navController?.popBackStack(R.id.mainSettingsFragment, false)
            } else {
              showContent()
              showInfoSnackbar(binding?.root, getString(R.string.error_occurred_please_try_again))
            }
            baseActivity.countingIdlingResource.decrementSafely()
          }

          Result.Status.ERROR, Result.Status.EXCEPTION -> {
            showContent()
            areBackupsSavingNow = false

            if (!handleKnownException(it.exception)) {
              navController?.navigate(
                NavGraphDirections.actionGlobalInfoDialogFragment(
                  requestCode = 0,
                  dialogTitle = "",
                  dialogMsg = it.exception?.message
                    ?: getString(R.string.error_could_not_save_private_keys)
                )
              )
            }
            privateKeysViewModel.saveBackupAsFileLiveData.value = Result.none()
            baseActivity.countingIdlingResource.decrementSafely()
          }

          else -> {
          }
        }
      }
    })
  }

  private fun setupBackupsViewModel() {
    backupsViewModel.postBackupLiveData.observe(viewLifecycleOwner, {
      when (it.status) {
        Result.Status.LOADING -> {
          baseActivity.countingIdlingResource.incrementSafely()
          isPrivateKeySendingNow = true
          showProgress(getString(R.string.processing))
        }

        Result.Status.SUCCESS -> {
          isPrivateKeySendingNow = false
          toast(R.string.backed_up_successfully)
          navController?.popBackStack(R.id.mainSettingsFragment, false)
          baseActivity.countingIdlingResource.decrementSafely()
        }

        Result.Status.EXCEPTION -> {
          showContent()
          isPrivateKeySendingNow = false

          if (!handleKnownException(it.exception)) {
            showBackupingErrorHint()
          }

          backupsViewModel.postBackupLiveData.value = Result.none()
          baseActivity.countingIdlingResource.decrementSafely()
        }

        else -> {
        }
      }
    })
  }

  private fun handleKnownException(e: Throwable?): Boolean {
    when (e) {
      is EmptyPassphraseException -> {
        navController?.navigate(
          NavGraphDirections.actionGlobalFixNeedPassphraseIssueDialogFragment(
            fingerprints = e.fingerprints.toTypedArray(),
            logicType = FixNeedPassphraseIssueDialogFragment.LogicType.ALL
          )
        )
      }

      is PrivateKeyStrengthException -> {
        showFixPassphraseIssueHint(
          msgText = getString(R.string.pass_phrase_is_too_weak),
          btnName = getString(R.string.change_pass_phrase)
        )
      }

      is DifferentPassPhrasesException -> {
        showFixPassphraseIssueHint(
          msgText = getString(R.string.different_pass_phrases),
          btnName = getString(R.string.fix)
        )
      }

      is NoPrivateKeysAvailableException -> {
        showInfoSnackbar(binding?.root, e.message, Snackbar.LENGTH_LONG)
      }

      else -> {
        return false
      }
    }

    return true
  }

  private fun showBackupingErrorHint() {
    showSnackbar(
      view = binding?.root,
      msgText = getString(R.string.backup_was_not_sent),
      btnName = getString(R.string.retry),
      duration = Snackbar.LENGTH_LONG
    ) {
      backupsViewModel.postBackup()
    }
  }

  private fun showFixPassphraseIssueHint(msgText: String, btnName: String) {
    showSnackbar(
      view = binding?.root,
      msgText = msgText,
      btnName = btnName,
      duration = Snackbar.LENGTH_LONG
    ) {
      navController?.navigate(
        BackupKeysFragmentDirections
          .actionBackupKeysFragmentToCheckPassphraseStrengthFragment(
            popBackStackIdIfSuccess = R.id.backupKeysFragment,
            title = getString(R.string.change_pass_phrase),
            lostPassphraseTitle = getString(R.string.loss_of_this_pass_phrase_cannot_be_recovered)
          )
      )
    }
  }

  /**
   * Start a new Activity with return results to choose a destination for an exported key.
   */
  private fun chooseDestForExportedKey() {
    account?.let {
      val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
      intent.addCategory(Intent.CATEGORY_OPENABLE)
      intent.type = Constants.MIME_TYPE_PGP_KEY
      intent.putExtra(Intent.EXTRA_TITLE, SecurityUtils.genPrivateKeyName(it.email))
      startActivityForResult(intent, REQUEST_CODE_GET_URI_FOR_SAVING_PRIVATE_KEY)
    } ?: ExceptionUtil.handleError(NullPointerException("account is null"))
  }

  private fun checkForEmptyPassphraseOrRunAction(action: () -> Unit) {
    val keysStorage = KeysStorageImpl.getInstance(requireContext())
    val fingerprints = keysStorage.getFingerprintsWithEmptyPassphrase()
    if (fingerprints.isNotEmpty()) {
      navController?.navigate(
        NavGraphDirections.actionGlobalFixNeedPassphraseIssueDialogFragment(
          fingerprints = fingerprints.toTypedArray(),
          logicType = FixNeedPassphraseIssueDialogFragment.LogicType.ALL
        )
      )
    } else {
      action.invoke()
    }
  }

  private fun observeOnResultLiveData() {
    getNavigationResult<kotlin.Result<*>>(RecheckProvidedPassphraseFragment.KEY_ACCEPTED_PASSPHRASE_RESULT) {
      if (it.isSuccess) {
        val passphrase = it.getOrNull() as? CharArray ?: return@getNavigationResult
        account?.let { accountEntity ->
          navController?.navigate(
            BackupKeysFragmentDirections
              .actionBackupKeysFragmentToChangePassphraseOfImportedKeysFragment(
                popBackStackIdIfSuccess = R.id.backupKeysFragment,
                title = getString(R.string.pass_phrase_changed),
                subTitle = getString(R.string.passphrase_was_changed),
                passphrase = String(passphrase),
                accountEntity = accountEntity
              )
          )
        }
      }
    }
  }

  private fun updateBackupButtonVisibility(accountEntity: AccountEntity?) {
    if (accountEntity?.isRuleExist(OrgRules.DomainRule.NO_PRV_BACKUP) == true) {
      binding?.btBackup?.gone()
    }
  }

  companion object {
    private const val REQUEST_CODE_GET_URI_FOR_SAVING_PRIVATE_KEY = 10
  }
}
