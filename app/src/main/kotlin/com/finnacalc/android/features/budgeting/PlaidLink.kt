//
// PlaidLink.kt
//
// Port of iOS Core/Plaid/PlaidLink.swift onto the Plaid Android SDK. LinkKit's
// `Plaid.create + handler.open` becomes `Plaid.createPlaidLinkSession` launched
// through the SDK's OpenPlaidLink ActivityResult contract; `rememberPlaidLink`
// wires it into Compose. onSuccess receives the public token to exchange
// server-side; onExit fires if the user backs out.
//

package com.finnacalc.android.features.budgeting

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.plaid.link.OpenPlaidLink
import com.plaid.link.Plaid
import com.plaid.link.configuration.LinkTokenConfiguration
import com.plaid.link.result.LinkExit
import com.plaid.link.result.LinkSuccess

class PlaidLinkHandle internal constructor(
    private val launch: (String) -> Unit,
) {
    /** Open Plaid Link for the given link token. */
    fun open(linkToken: String) = launch(linkToken)
}

@Composable
fun rememberPlaidLink(
    onSuccess: (publicToken: String) -> Unit,
    onExit: () -> Unit,
): PlaidLinkHandle {
    val context = LocalContext.current
    val currentOnSuccess by rememberUpdatedState(onSuccess)
    val currentOnExit by rememberUpdatedState(onExit)
    val launcher = rememberLauncherForActivityResult(OpenPlaidLink()) { result ->
        when (result) {
            is LinkSuccess -> currentOnSuccess(result.publicToken)
            is LinkExit -> currentOnExit()
        }
    }
    return remember(launcher, context) {
        PlaidLinkHandle { token ->
            val configuration = LinkTokenConfiguration.Builder().token(token).build()
            launcher.launch(Plaid.createPlaidLinkSession(context, configuration))
        }
    }
}
