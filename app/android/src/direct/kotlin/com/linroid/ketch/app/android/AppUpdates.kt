package com.linroid.ketch.app.android

import com.linroid.ketch.app.platform.AppUpdates
import com.linroid.ketch.app.state.AppController
import kotlinx.coroutines.CoroutineScope

@Suppress("UNUSED_PARAMETER")
internal fun createAppUpdates(
  scope: CoroutineScope,
  app: KetchApplication,
  controller: AppController,
): AppUpdates? = null
