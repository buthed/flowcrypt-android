/*
 * © 2016-2019 FlowCrypt Limited. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.ui.loader;

import android.content.Context;
import android.net.Uri;

import com.flowcrypt.email.database.dao.source.AccountDao;
import com.flowcrypt.email.js.core.Js;
import com.flowcrypt.email.model.results.LoaderResult;
import com.flowcrypt.email.security.SecurityStorageConnector;
import com.flowcrypt.email.security.SecurityUtils;
import com.flowcrypt.email.util.GeneralUtil;
import com.flowcrypt.email.util.exception.ExceptionUtil;

import androidx.loader.content.AsyncTaskLoader;

/**
 * This loader tries to save the backup of the private key as a file.
 * <p>
 * Return true if the key saved, false otherwise;
 *
 * @author DenBond7
 * Date: 26.07.2017
 * Time: 13:18
 * E-mail: DenBond7@gmail.com
 */

public class SavePrivateKeyAsFileAsyncTaskLoader extends AsyncTaskLoader<LoaderResult> {
  private Uri destinationUri;
  private AccountDao account;

  public SavePrivateKeyAsFileAsyncTaskLoader(Context context, AccountDao account, Uri destinationUri) {
    super(context);
    this.account = account;
    this.destinationUri = destinationUri;
    onContentChanged();
  }

  @Override
  public LoaderResult loadInBackground() {
    try {
      Js js = new Js(getContext(), new SecurityStorageConnector(getContext()));
      String backup = SecurityUtils.genPrivateKeysBackup(getContext(), js, account, false);
      boolean result = GeneralUtil.writeFileFromStringToUri(getContext(), destinationUri, backup) > 0;
      return new LoaderResult(result, null);
    } catch (Exception e) {
      e.printStackTrace();
      ExceptionUtil.handleError(e);
      return new LoaderResult(null, e);
    }
  }

  @Override
  public void onStartLoading() {
    if (takeContentChanged()) {
      forceLoad();
    }
  }

  @Override
  public void onStopLoading() {
    cancelLoad();
  }
}
