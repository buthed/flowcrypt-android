/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.ui.activity.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.navigation.fragment.navArgs
import com.flowcrypt.email.R
import com.flowcrypt.email.databinding.FragmentRecheckProvidedPassphraseBinding
import com.flowcrypt.email.extensions.getOnResultSavedStateHandle
import com.flowcrypt.email.extensions.navController
import com.flowcrypt.email.ui.activity.fragment.base.BaseFragment
import com.flowcrypt.email.ui.notifications.SystemNotificationManager
import com.flowcrypt.email.util.GeneralUtil
import com.flowcrypt.email.util.UIUtil
import com.google.android.material.snackbar.Snackbar

/**
 * @author Denis Bondarenko
 *         Date: 6/28/21
 *         Time: 6:10 PM
 *         E-mail: DenBond7@gmail.com
 */
class RecheckProvidedPassphraseFragment : BaseFragment() {
  private val args by navArgs<RecheckProvidedPassphraseFragmentArgs>()
  private var binding: FragmentRecheckProvidedPassphraseBinding? = null

  override val contentResourceId: Int = R.layout.fragment_recheck_provided_passphrase

  override fun onAttach(context: Context) {
    super.onAttach(context)
    SystemNotificationManager(context)
      .cancel(SystemNotificationManager.NOTIFICATION_ID_PASSPHRASE_TOO_WEAK)
  }

  override fun onCreateView(
    inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
  ): View? {
    binding = FragmentRecheckProvidedPassphraseBinding.inflate(inflater, container, false)
    return binding?.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    supportActionBar?.title = getString(R.string.security)
    initViews()
  }

  private fun initViews() {
    binding?.tVTitle?.text = args.title
    binding?.eTRepeatedPassphrase?.setOnEditorActionListener { v, actionId, _ ->
      return@setOnEditorActionListener when (actionId) {
        EditorInfo.IME_ACTION_DONE -> {
          checkAndReturnResult()
          UIUtil.hideSoftInput(requireContext(), v)
          true
        }
        else -> false
      }
    }
    binding?.btConfirmPassphrase?.setOnClickListener {
      checkAndReturnResult()
    }
    binding?.btUseAnotherPassphrase?.setOnClickListener {
      navController?.navigateUp()
    }
  }

  private fun checkAndReturnResult() {
    if (binding?.eTRepeatedPassphrase?.text?.isEmpty() == true) {
      showInfoSnackbar(
        view = binding?.root,
        msgText = getString(R.string.passphrase_must_be_non_empty),
        duration = Snackbar.LENGTH_LONG
      )
    } else {
      snackBar?.dismiss()
      if (binding?.eTRepeatedPassphrase?.text.toString() == args.passphrase) {
        getOnResultSavedStateHandle(args.popBackStackIdIfSuccess)?.set(
          KEY_ACCEPTED_PASSPHRASE_RESULT,
          Result.success(binding?.eTRepeatedPassphrase?.text?.toString()?.toCharArray())
        )

        navController?.popBackStack(args.popBackStackIdIfSuccess, false)
      } else {
        showInfoSnackbar(
          view = binding?.root,
          msgText = getString(R.string.pass_phrases_do_not_match),
          duration = Snackbar.LENGTH_LONG
        )
      }
    }
  }

  companion object {
    val KEY_ACCEPTED_PASSPHRASE_RESULT = GeneralUtil.generateUniqueExtraKey(
      "KEY_ACCEPTED_PASSPHRASE_RESULT",
      RecheckProvidedPassphraseFragment::class.java
    )
  }
}

