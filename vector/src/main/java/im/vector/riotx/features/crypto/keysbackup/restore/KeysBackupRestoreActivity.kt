/*
 * Copyright 2019 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package im.vector.riotx.features.crypto.keysbackup.restore

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Observer
import im.vector.matrix.android.api.session.crypto.crosssigning.KEYBACKUP_SECRET_SSSS_NAME
import im.vector.riotx.R
import im.vector.riotx.core.extensions.addFragmentToBackstack
import im.vector.riotx.core.extensions.observeEvent
import im.vector.riotx.core.extensions.replaceFragment
import im.vector.riotx.core.platform.SimpleFragmentActivity
import im.vector.riotx.core.ui.views.KeysBackupBanner
import im.vector.riotx.features.crypto.quads.SharedSecureStorageActivity

class KeysBackupRestoreActivity : SimpleFragmentActivity() {

    companion object {

        private const val REQUEST_4S_SECRET = 100
        const val SECRET_ALIAS = SharedSecureStorageActivity.DEFAULT_RESULT_KEYSTORE_ALIAS

        fun intent(context: Context): Intent {
            return Intent(context, KeysBackupRestoreActivity::class.java)
        }
    }

    override fun getTitleRes() = R.string.title_activity_keys_backup_restore

    private lateinit var viewModel: KeysBackupRestoreSharedViewModel

    override fun onBackPressed() {
        hideWaitingView()
        super.onBackPressed()
    }

    override fun initUiAndData() {
        super.initUiAndData()
        viewModel = viewModelProvider.get(KeysBackupRestoreSharedViewModel::class.java)
        viewModel.initSession(session)

        viewModel.keySourceModel.observe(this, Observer { keySource ->
            if (keySource != null && !keySource.isInQuadS && supportFragmentManager.fragments.isEmpty()) {
                val isBackupCreatedFromPassphrase =
                        viewModel.keyVersionResult.value?.getAuthDataAsMegolmBackupAuthData()?.privateKeySalt != null
                if (isBackupCreatedFromPassphrase) {
                    replaceFragment(R.id.container, KeysBackupRestoreFromPassphraseFragment::class.java)
                } else {
                    replaceFragment(R.id.container, KeysBackupRestoreFromKeyFragment::class.java)
                }
            }
        })

        viewModel.keyVersionResultError.observeEvent(this) { message ->
            AlertDialog.Builder(this)
                    .setTitle(R.string.unknown_error)
                    .setMessage(message)
                    .setCancelable(false)
                    .setPositiveButton(R.string.ok) { _, _ ->
                        // nop
                        finish()
                    }
                    .show()
        }

        if (viewModel.keyVersionResult.value == null) {
            // We need to fetch from API
            viewModel.getLatestVersion()
        }

        viewModel.navigateEvent.observeEvent(this) { uxStateEvent ->
            when (uxStateEvent) {
                KeysBackupRestoreSharedViewModel.NAVIGATE_TO_RECOVER_WITH_KEY -> {
                    addFragmentToBackstack(R.id.container, KeysBackupRestoreFromKeyFragment::class.java)
                }
                KeysBackupRestoreSharedViewModel.NAVIGATE_TO_SUCCESS          -> {
                    viewModel.keyVersionResult.value?.version?.let {
                        KeysBackupBanner.onRecoverDoneForVersion(this, it)
                    }
                    replaceFragment(R.id.container, KeysBackupRestoreSuccessFragment::class.java)
                }
                KeysBackupRestoreSharedViewModel.NAVIGATE_TO_4S               -> {
                    launch4SActivity()
                }
                KeysBackupRestoreSharedViewModel.NAVIGATE_FAILED_TO_LOAD_4S   -> {
                    AlertDialog.Builder(this)
                            .setTitle(R.string.unknown_error)
                            .setMessage(R.string.error_failed_to_import_keys)
                            .setCancelable(false)
                            .setPositiveButton(R.string.ok) { _, _ ->
                                // nop
                                launch4SActivity()
                            }
                            .show()
                }
            }
        }

        viewModel.loadingEvent.observe(this, Observer {
            updateWaitingView(it)
        })

        viewModel.importRoomKeysFinishWithResult.observeEvent(this) {
            // set data?
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    private fun launch4SActivity() {
        SharedSecureStorageActivity.newIntent(
                context = this,
                keyId = null, // default key
                requestedSecrets = listOf(KEYBACKUP_SECRET_SSSS_NAME),
                resultKeyStoreAlias = SECRET_ALIAS
        ).let {
            startActivityForResult(it, REQUEST_4S_SECRET)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_4S_SECRET) {
            val extraResult = data?.getStringExtra(SharedSecureStorageActivity.EXTRA_DATA_RESULT)
            if (resultCode == Activity.RESULT_OK && extraResult != null) {
                viewModel.handleGotSecretFromSSSS(
                        extraResult,
                        SECRET_ALIAS
                )
            } else {
                finish()
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
}
