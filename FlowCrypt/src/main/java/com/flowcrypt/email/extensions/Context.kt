/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.extensions

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.os.Build
import android.widget.Toast

/**
 * @author Denis Bondarenko
 *         Date: 6/23/20
 *         Time: 10:52 AM
 *         E-mail: DenBond7@gmail.com
 */
fun Context.toast(text: String?, duration: Int = Toast.LENGTH_SHORT) {
  Toast.makeText(this, text ?: "", duration).show()
}

fun Context.toast(resId: Int, duration: Int = Toast.LENGTH_SHORT) {
  Toast.makeText(this, resId, duration).show()
}

@SuppressWarnings("deprecation")
@Suppress("DEPRECATION")
fun Context?.hasActiveConnection(): Boolean {
  return this?.let {
    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val cap = cm?.getNetworkCapabilities(cm.activeNetwork) ?: return false
      return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } else {
      val activeNetwork: NetworkInfo? = cm?.activeNetworkInfo
      activeNetwork?.isConnectedOrConnecting == true
    }
  } ?: false
}
