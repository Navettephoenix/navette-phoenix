package com.phoenix.navette28

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

internal fun appCheckProvider(): AppCheckProviderFactory =
    if (BuildConfig.USE_DEBUG_APP_CHECK) DebugAppCheckProviderFactory.getInstance()
    else PlayIntegrityAppCheckProviderFactory.getInstance()
