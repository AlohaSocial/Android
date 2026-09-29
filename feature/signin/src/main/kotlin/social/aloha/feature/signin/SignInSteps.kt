// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.signin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import java.text.NumberFormat
import social.aloha.core.data.SignInProblem
import social.aloha.core.data.TriedBecause
import social.aloha.core.designsystem.AlohaSpacing

/** A progress indicator with a status line that screen readers announce when it changes. */
@Composable
internal fun StatusMessage(text: String, detail: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator()
        Column {
            Text(text, style = MaterialTheme.typography.titleMedium)
            detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
internal fun InstanceCardContent(card: InstanceCard, actions: SignInActions) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AlohaSpacing.m), verticalArrangement = Arrangement.spacedBy(AlohaSpacing.xs)) {
            Text(
                card.title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics {
                    heading()
                },
            )
            Text(
                card.domain,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val software = stringResource(R.string.signin_nextcloud_social)
            // the software is named only where the server's own title does not already say it
            if (card.isNextcloudSocial && !card.title.contains(software, ignoreCase = true)) {
                Text(software, style = MaterialTheme.typography.labelMedium)
            }
            card.userCount?.let {
                Text(
                    pluralStringResource(R.plurals.signin_user_count, it, NumberFormat.getIntegerInstance().format(it)),
                )
            }
            if (card.description.isNotBlank()) Text(card.description, style = MaterialTheme.typography.bodyMedium)
            if (card.rules.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = AlohaSpacing.xs))
                Text(
                    stringResource(R.string.signin_rules),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.semantics { heading() },
                )
                card.rules.forEachIndexed { index, rule ->
                    Text("${index + 1}. $rule", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
    Button(onClick = actions::onSignIn, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.signin_sign_in))
    }
    TextButton(onClick = actions::onOtherServer, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.signin_other_server))
    }
}

@Composable
internal fun WaitingForBrowser(actions: SignInActions) {
    StatusMessage(stringResource(R.string.signin_waiting), stringResource(R.string.signin_waiting_detail))
    OutlinedButton(onClick = actions::onOpenBrowserAgain, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.signin_open_browser_again))
    }
    TextButton(onClick = actions::onOtherServer, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.signin_other_server))
    }
}

@Composable
internal fun FailureContent(state: SignInUiState, failure: SignInFailure, actions: SignInActions) {
    Text(
        failureMessage(failure),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    if (failure is SignInFailure.NothingAnswered && failure.tried.isNotEmpty()) {
        Text(
            stringResource(R.string.signin_attempted),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics {
                heading()
            },
        )
        failure.tried.forEach { attempt ->
            Column(Modifier.semantics(mergeDescendants = true) {}) {
                Text(attempt.url, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                Text(stringResource(attempt.reason.label()), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    Button(onClick = actions::onTryAgain, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.signin_try_again))
    }
    if (failure is SignInFailure.NothingAnswered) {
        OutlinedButton(onClick = actions::onCopyInstructions, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.signin_copy_instructions))
        }
        if (state.copied) {
            Text(
                stringResource(R.string.signin_copied),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
    TextButton(onClick = actions::onOtherServer, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.signin_other_server))
    }
    ManualApiAddress(state, actions)
}

@Composable
private fun failureMessage(failure: SignInFailure): String = when (failure) {
    is SignInFailure.NothingAnswered -> stringResource(R.string.signin_failure_nothing_answered)
    is SignInFailure.Problem -> problemMessage(failure.problem)
}

@Composable
private fun problemMessage(problem: SignInProblem): String = when (problem) {
    is SignInProblem.Refused -> problem.reason?.let { stringResource(R.string.signin_failure_refused, it) }
        ?: stringResource(R.string.signin_failure_refused_no_reason)

    // an untrusted certificate has its own step; reaching here means it could not be described
    SignInProblem.Offline, is SignInProblem.UntrustedCertificate -> stringResource(R.string.signin_failure_offline)

    is SignInProblem.ServerProblem -> problem.detail?.let { stringResource(R.string.signin_failure_server, it) }
        ?: stringResource(R.string.signin_failure_server_no_detail)
}

private fun TriedBecause.label(): Int = when (this) {
    TriedBecause.Discovery -> R.string.signin_attempt_discovery
    TriedBecause.DomainRoot -> R.string.signin_attempt_domain_root
    TriedBecause.AppPath -> R.string.signin_attempt_app_path
    TriedBecause.PrettyAppPath -> R.string.signin_attempt_pretty_app_path
    TriedBecause.TypedPath -> R.string.signin_attempt_typed_path
    TriedBecause.TypedAppPath -> R.string.signin_attempt_typed_app_path
    TriedBecause.Manual -> R.string.signin_attempt_manual
}

/** The disclosure where a person who knows their API address types it by hand. */
@Composable
internal fun ManualApiAddress(state: SignInUiState, actions: SignInActions) {
    val expanded = state.showManualApiAddress
    val status = stringResource(if (expanded) R.string.signin_expanded else R.string.signin_collapsed)
    TextButton(
        onClick = actions::onToggleManualApiAddress,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                stateDescription = status
                val toggle = {
                    actions.onToggleManualApiAddress()
                    true
                }
                if (expanded) collapse(action = toggle) else expand(action = toggle)
            },
    ) {
        Text(stringResource(R.string.signin_advanced))
    }
    if (!expanded) return
    OutlinedTextField(
        value = state.manualApiAddress,
        onValueChange = actions::onManualApiAddressChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.signin_api_address_label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Done,
            autoCorrectEnabled = false,
        ),
        keyboardActions = KeyboardActions(onDone = {
            if (state.manualApiAddress.isNotBlank()) actions.onUseManualApiAddress()
        }),
    )
    OutlinedButton(
        onClick = actions::onUseManualApiAddress,
        enabled = state.manualApiAddress.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.signin_use_api_address)) }
    OutlinedButton(
        onClick = actions::onChooseClientCertificate,
        enabled = state.server.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.signin_client_certificate)) }
    state.clientCertificate?.let {
        Text(stringResource(R.string.signin_client_certificate_chosen, it), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun CertificatePrompt(certificate: CertificateSummary, actions: SignInActions) {
    Text(
        stringResource(R.string.signin_certificate_title),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.semantics { heading() },
    )
    Text(
        stringResource(R.string.signin_certificate_detail, certificate.host),
        style = MaterialTheme.typography.bodyMedium,
    )
    CertificateField(R.string.signin_certificate_subject, certificate.subject)
    CertificateField(R.string.signin_certificate_issuer, certificate.issuer)
    CertificateField(R.string.signin_certificate_valid_until, certificate.validUntil)
    CertificateField(R.string.signin_certificate_fingerprint, certificate.sha256, monospace = true)
    Button(onClick = actions::onRejectCertificate, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.signin_certificate_cancel))
    }
    OutlinedButton(onClick = actions::onTrustCertificate, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.signin_certificate_trust))
    }
}

@Composable
private fun CertificateField(label: Int, value: String, monospace: Boolean = false) {
    Column(Modifier.semantics(mergeDescendants = true) {}) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (monospace) FontFamily.Monospace else null,
        )
    }
}
