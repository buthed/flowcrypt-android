/*
 * © 2016-2019 FlowCrypt Limited. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.ui.activity.base

import android.Manifest
import android.app.Activity
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.text.TextUtils
import android.view.View
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import com.flowcrypt.email.R
import com.flowcrypt.email.api.retrofit.response.model.node.NodeKeyDetails
import com.flowcrypt.email.model.KeyDetails
import com.flowcrypt.email.model.KeyImportModel
import com.flowcrypt.email.model.results.LoaderResult
import com.flowcrypt.email.service.CheckClipboardToFindKeyService
import com.flowcrypt.email.ui.activity.fragment.dialog.InfoDialogFragment
import com.flowcrypt.email.ui.loader.ParseKeysFromResourceAsyncTaskLoader
import com.flowcrypt.email.util.GeneralUtil
import com.flowcrypt.email.util.UIUtil
import com.flowcrypt.email.util.exception.NodeException
import com.google.android.material.snackbar.Snackbar
import java.io.FileNotFoundException
import java.util.*

/**
 * The base import key activity. This activity defines a logic of import a key (private or
 * public) via select a file or using clipboard.
 *
 * @author Denis Bondarenko
 * Date: 03.08.2017
 * Time: 12:35
 * E-mail: DenBond7@gmail.com
 */

abstract class BaseImportKeyActivity : BaseBackStackSyncActivity(), View.OnClickListener,
    LoaderManager.LoaderCallbacks<LoaderResult> {

  protected lateinit var checkClipboardToFindKeyService: CheckClipboardToFindKeyService
  protected lateinit var layoutContentView: View
  protected lateinit var layoutProgress: View
  protected lateinit var textViewTitle: TextView
  protected lateinit var buttonLoadFromFile: View

  @JvmField
  protected var keyImportModel: KeyImportModel? = null
  @JvmField
  protected var isCheckingClipboardEnabled = true
  protected var isClipboardServiceBound: Boolean = false

  private lateinit var clipboardManager: ClipboardManager
  private var isCheckingPrivateKeyNow: Boolean = false
  private var throwErrorIfDuplicateFound: Boolean = false

  private var title: String? = null

  private val clipboardConn = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName, service: IBinder) {
      val binder = service as CheckClipboardToFindKeyService.LocalBinder
      checkClipboardToFindKeyService = binder.service
      checkClipboardToFindKeyService.isPrivateKeyMode = isPrivateKeyMode
      isClipboardServiceBound = true
    }

    override fun onServiceDisconnected(name: ComponentName) {
      isClipboardServiceBound = false
    }
  }

  abstract val isPrivateKeyMode: Boolean

  override val rootView: View
    get() = findViewById(R.id.layoutContent)

  override val isSyncEnabled: Boolean
    get() = intent == null || intent.getBooleanExtra(KEY_EXTRA_IS_SYNC_ENABLE, true)

  abstract fun onKeyFound(type: KeyDetails.Type, keyDetailsList: ArrayList<NodeKeyDetails>)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    bindService(Intent(this, CheckClipboardToFindKeyService::class.java), clipboardConn, Context.BIND_AUTO_CREATE)

    if (intent != null) {
      this.throwErrorIfDuplicateFound = intent.getBooleanExtra(KEY_EXTRA_IS_THROW_ERROR_IF_DUPLICATE_FOUND, false)
      this.keyImportModel = intent.getParcelableExtra(KEY_EXTRA_PRIVATE_KEY_IMPORT_MODEL_FROM_CLIPBOARD)
      this.title = intent.getStringExtra(KEY_EXTRA_TITLE)
    }

    clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    initViews()

    if (keyImportModel != null) {
      LoaderManager.getInstance(this).restartLoader(R.id.loader_id_validate_key_from_clipboard, null, this)
    }
  }

  override fun onResume() {
    super.onResume()
    if (isClipboardServiceBound && !isCheckingPrivateKeyNow && isCheckingClipboardEnabled) {
      keyImportModel = checkClipboardToFindKeyService.keyImportModel
      if (keyImportModel != null) {
        LoaderManager.getInstance(this).restartLoader(R.id.loader_id_validate_key_from_clipboard, null, this)
      }
    }
  }

  public override fun onPause() {
    super.onPause()
    isCheckingClipboardEnabled = true
  }

  override fun onDestroy() {
    super.onDestroy()
    if (isClipboardServiceBound) {
      unbindService(clipboardConn)
      isClipboardServiceBound = false
    }
  }

  public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    when (requestCode) {
      REQUEST_CODE_SELECT_KEYS_FROM_FILES_SYSTEM -> {
        isCheckingClipboardEnabled = false

        when (resultCode) {
          Activity.RESULT_OK -> if (data != null) {
            if (data.data != null) {
              handleSelectedFile(data.data!!)
            } else {
              showInfoSnackbar(rootView, getString(R.string.please_use_another_app_to_choose_file),
                  Snackbar.LENGTH_LONG)
            }
          }
        }
      }

      else -> super.onActivityResult(requestCode, resultCode, data)
    }

  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    when (requestCode) {
      REQUEST_CODE_PERMISSION_READ_EXTERNAL_STORAGE -> {
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
          selectFile()
        } else {
          showAccessDeniedWarning()
        }
      }
    }
  }

  override fun onBackPressed() {
    if (isCheckingPrivateKeyNow) {
      LoaderManager.getInstance(this).destroyLoader(R.id.loader_id_validate_key_from_file)
      LoaderManager.getInstance(this).destroyLoader(R.id.loader_id_validate_key_from_clipboard)
      isCheckingPrivateKeyNow = false
      UIUtil.exchangeViewVisibility(applicationContext, false, layoutProgress, layoutContentView)
    } else {
      super.onBackPressed()
    }
  }

  override fun onClick(v: View) {
    when (v.id) {
      R.id.buttonLoadFromFile -> {
        dismissSnackBar()

        val isPermissionGranted = ContextCompat.checkSelfPermission(this,
            Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

        if (isPermissionGranted) {
          selectFile()
        } else {
          if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                  Manifest.permission.READ_EXTERNAL_STORAGE)) {
            showReadSdCardExplanation()
          } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                REQUEST_CODE_PERMISSION_READ_EXTERNAL_STORAGE)
          }
        }
      }

      R.id.buttonLoadFromClipboard -> {
        dismissSnackBar()

        if (clipboardManager.hasPrimaryClip()) {
          val clipData = clipboardManager.primaryClip
          if (clipData != null) {
            val item = clipData.getItemAt(0)
            val privateKeyFromClipboard = item.text
            if (!TextUtils.isEmpty(privateKeyFromClipboard)) {
              keyImportModel = KeyImportModel(null, privateKeyFromClipboard.toString(),
                  isPrivateKeyMode, KeyDetails.Type.CLIPBOARD)

              LoaderManager.getInstance(this).restartLoader(R.id.loader_id_validate_key_from_clipboard, null, this)
            } else {
              showClipboardIsEmptyInfoDialog()
            }
          }
        } else {
          showClipboardIsEmptyInfoDialog()
        }
      }
    }
  }

  override fun onCreateLoader(id: Int, args: Bundle?): Loader<LoaderResult> {
    return when (id) {
      R.id.loader_id_validate_key_from_file -> {
        isCheckingPrivateKeyNow = true
        UIUtil.exchangeViewVisibility(applicationContext, true, layoutProgress, layoutContentView)
        ParseKeysFromResourceAsyncTaskLoader(applicationContext, keyImportModel, true)
      }

      R.id.loader_id_validate_key_from_clipboard -> {
        isCheckingPrivateKeyNow = true
        UIUtil.exchangeViewVisibility(applicationContext, true, layoutProgress, layoutContentView)
        ParseKeysFromResourceAsyncTaskLoader(applicationContext, keyImportModel, false)
      }

      else -> Loader(this)
    }
  }

  override fun onLoadFinished(loader: Loader<LoaderResult>, loaderResult: LoaderResult) {
    handleLoaderResult(loader, loaderResult)
  }

  override fun onLoaderReset(loader: Loader<LoaderResult>) {
    when (loader.id) {
      R.id.loader_id_validate_key_from_file, R.id.loader_id_validate_key_from_clipboard -> {
        isCheckingPrivateKeyNow = false
        UIUtil.exchangeViewVisibility(applicationContext, false, layoutProgress, layoutContentView)
      }
    }
  }

  @Suppress("UNCHECKED_CAST")
  override fun onSuccess(loaderId: Int, result: Any?) {
    when (loaderId) {
      R.id.loader_id_validate_key_from_file -> {
        isCheckingPrivateKeyNow = false
        UIUtil.exchangeViewVisibility(applicationContext, false, layoutProgress, layoutContentView)
        val keysFromFile = result as ArrayList<NodeKeyDetails>?

        if (keysFromFile!!.isNotEmpty()) {
          onKeyFound(KeyDetails.Type.FILE, keysFromFile)
        } else {
          showInfoSnackbar(rootView, getString(R.string.file_has_wrong_pgp_structure,
              if (isPrivateKeyMode) getString(R.string.private_) else getString(R.string.public_)))
        }
      }

      R.id.loader_id_validate_key_from_clipboard -> {
        isCheckingPrivateKeyNow = false
        UIUtil.exchangeViewVisibility(applicationContext, false, layoutProgress, layoutContentView)
        val keysFromClipboard = result as ArrayList<NodeKeyDetails>?
        if (keysFromClipboard!!.isNotEmpty()) {
          onKeyFound(KeyDetails.Type.CLIPBOARD, keysFromClipboard)
        } else {
          showInfoSnackbar(rootView, getString(R.string.clipboard_has_wrong_structure,
              if (isPrivateKeyMode) getString(R.string.private_) else getString(R.string.public_)))
        }
      }

      else -> super.onSuccess(loaderId, result)
    }
  }

  override fun onError(loaderId: Int, e: Exception?) {
    when (loaderId) {
      R.id.loader_id_validate_key_from_file, R.id.loader_id_validate_key_from_clipboard -> {
        isCheckingPrivateKeyNow = false
        UIUtil.exchangeViewVisibility(applicationContext, false, layoutProgress, layoutContentView)

        var errorMsg = e!!.message

        if (e is FileNotFoundException) {
          errorMsg = getString(R.string.file_not_found)
        }

        if (e is NodeException) {
          val nodeException = e as NodeException?

          if (WRONG_STRUCTURE_ERROR == nodeException!!.nodeError!!.msg) {
            val mode = if (isPrivateKeyMode) getString(R.string.private_) else getString(R.string.public_)
            when (loaderId) {
              R.id.loader_id_validate_key_from_file ->
                errorMsg = getString(R.string.file_has_wrong_pgp_structure, mode)

              R.id.loader_id_validate_key_from_clipboard ->
                errorMsg = getString(R.string.clipboard_has_wrong_structure, mode)
            }
          }
        }

        showInfoSnackbar(rootView, errorMsg)
      }

      else -> super.onError(loaderId, e)
    }
  }

  override fun onReplyReceived(requestCode: Int, resultCode: Int, obj: Any?) {

  }

  override fun onErrorHappened(requestCode: Int, errorType: Int, e: Exception) {

  }

  /**
   * Handle a selected file.
   *
   * @param uri A [Uri] of the selected file.
   */
  protected open fun handleSelectedFile(uri: Uri) {
    keyImportModel = KeyImportModel(uri, null, isPrivateKeyMode, KeyDetails.Type.FILE)
    LoaderManager.getInstance(this).restartLoader(R.id.loader_id_validate_key_from_file, null, this)
  }

  protected open fun initViews() {
    layoutContentView = findViewById(R.id.layoutContentView)
    layoutProgress = findViewById(R.id.layoutProgress)

    textViewTitle = findViewById(R.id.textViewTitle)
    textViewTitle.text = title

    buttonLoadFromFile = findViewById(R.id.buttonLoadFromFile)
    buttonLoadFromFile.setOnClickListener(this)

    if (findViewById<View>(R.id.buttonLoadFromClipboard) != null) {
      findViewById<View>(R.id.buttonLoadFromClipboard).setOnClickListener(this)
    }
  }

  private fun showAccessDeniedWarning() {
    UIUtil.showSnackbar(rootView, getString(R.string.access_to_read_the_sdcard_id_denied),
        getString(R.string.change),
        View.OnClickListener { GeneralUtil.showAppSettingScreen(this@BaseImportKeyActivity) })
  }

  /**
   * Show an explanation to the user for readOption the sdcard.
   * After the user sees the explanation, we try again to request the permission.
   */
  private fun showReadSdCardExplanation() {
    UIUtil.showSnackbar(rootView, getString(R.string.read_sdcard_permission_explanation_text),
        getString(R.string.do_request), View.OnClickListener {
      ActivityCompat.requestPermissions(this@BaseImportKeyActivity,
          arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
          REQUEST_CODE_PERMISSION_READ_EXTERNAL_STORAGE)
    })
  }

  private fun selectFile() {
    val intent = Intent()
    intent.action = Intent.ACTION_OPEN_DOCUMENT
    intent.addCategory(Intent.CATEGORY_OPENABLE)
    intent.type = "*/*"
    startActivityForResult(Intent.createChooser(intent, getString(R.string.select_key_to_import)),
        REQUEST_CODE_SELECT_KEYS_FROM_FILES_SYSTEM)
  }

  private fun showClipboardIsEmptyInfoDialog() {
    val dialogMsg = getString(R.string.hint_clipboard_is_empty, if (isPrivateKeyMode)
      getString(R.string.private_)
    else
      getString(R.string.public_), getString(R.string.app_name))
    val infoDialogFragment = InfoDialogFragment.newInstance(getString(R.string.hint), dialogMsg)
    infoDialogFragment.show(supportFragmentManager, InfoDialogFragment::class.java.simpleName)
  }

  companion object {

    val KEY_EXTRA_IS_SYNC_ENABLE =
        GeneralUtil.generateUniqueExtraKey("KEY_EXTRA_IS_SYNC_ENABLE", BaseImportKeyActivity::class.java)

    val KEY_EXTRA_IS_THROW_ERROR_IF_DUPLICATE_FOUND =
        GeneralUtil.generateUniqueExtraKey("KEY_EXTRA_IS_THROW_ERROR_IF_DUPLICATE_FOUND",
        BaseImportKeyActivity::class.java)

    val KEY_EXTRA_PRIVATE_KEY_IMPORT_MODEL_FROM_CLIPBOARD =
        GeneralUtil.generateUniqueExtraKey("KEY_EXTRA_PRIVATE_KEY_IMPORT_MODEL_FROM_CLIPBOARD",
        BaseImportKeyActivity::class.java)

    val KEY_EXTRA_TITLE =
        GeneralUtil.generateUniqueExtraKey("KEY_EXTRA_TITLE", BaseImportKeyActivity::class.java)
    private const val WRONG_STRUCTURE_ERROR = "Cannot parse key: could not determine pgpType"
    private const val REQUEST_CODE_SELECT_KEYS_FROM_FILES_SYSTEM = 10
    private const val REQUEST_CODE_PERMISSION_READ_EXTERNAL_STORAGE = 11

    @JvmStatic
    fun newIntent(context: Context?, title: String, cls: Class<*>): Intent {
      return newIntent(context, title, false, cls)
    }

    @JvmStatic
    fun newIntent(context: Context?, title: String, isThrowErrorIfDuplicateFoundEnabled: Boolean,
                  cls: Class<*>): Intent {
      return newIntent(context, title, null, isThrowErrorIfDuplicateFoundEnabled, cls)
    }

    @JvmStatic
    fun newIntent(context: Context?, title: String, model: KeyImportModel?,
                  isThrowErrorIfDuplicateFoundEnabled: Boolean, cls: Class<*>): Intent {
      return newIntent(context, true, title, model, isThrowErrorIfDuplicateFoundEnabled, cls)
    }

    @JvmStatic
    fun newIntent(context: Context?, isSyncEnabled: Boolean, title: String, model: KeyImportModel?,
                  isThrowErrorIfDuplicateFoundEnabled: Boolean, cls: Class<*>): Intent {
      val intent = Intent(context, cls)
      intent.putExtra(KEY_EXTRA_IS_SYNC_ENABLE, isSyncEnabled)
      intent.putExtra(KEY_EXTRA_TITLE, title)
      intent.putExtra(KEY_EXTRA_PRIVATE_KEY_IMPORT_MODEL_FROM_CLIPBOARD, model)
      intent.putExtra(KEY_EXTRA_IS_THROW_ERROR_IF_DUPLICATE_FOUND, isThrowErrorIfDuplicateFoundEnabled)
      return intent
    }
  }
}
