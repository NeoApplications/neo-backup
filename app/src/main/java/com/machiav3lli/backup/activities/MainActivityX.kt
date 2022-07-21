/*
 * OAndBackupX: open-source apps backup and restore app.
 * Copyright (C) 2020  Antonios Hazim
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.machiav3lli.backup.activities

import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Looper
import android.os.Process
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import com.machiav3lli.backup.BuildConfig
import com.machiav3lli.backup.OABX
import com.machiav3lli.backup.OABX.Companion.addInfoText
import com.machiav3lli.backup.PREFS_CATCHUNCAUGHTEXCEPTION
import com.machiav3lli.backup.PREFS_MAXCRASHLINES
import com.machiav3lli.backup.PREFS_SKIPPEDENCRYPTION
import com.machiav3lli.backup.R
import com.machiav3lli.backup.databinding.ActivityMainXBinding
import com.machiav3lli.backup.dbs.ODatabase
import com.machiav3lli.backup.fragments.ProgressViewController
import com.machiav3lli.backup.fragments.RefreshViewController
import com.machiav3lli.backup.handler.LogsHandler
import com.machiav3lli.backup.handler.ShellHandler.Companion.runAsRoot
import com.machiav3lli.backup.items.SortFilterModel
import com.machiav3lli.backup.utils.FileUtils.invalidateBackupLocation
import com.machiav3lli.backup.utils.getPrivateSharedPrefs
import com.machiav3lli.backup.utils.isEncryptionEnabled
import com.machiav3lli.backup.utils.isRememberFiltering
import com.machiav3lli.backup.utils.itemIdToOrder
import com.machiav3lli.backup.utils.navigateLeft
import com.machiav3lli.backup.utils.navigateRight
import com.machiav3lli.backup.utils.setCustomTheme
import com.machiav3lli.backup.utils.sortFilterModel
import com.machiav3lli.backup.viewmodels.MainViewModel
import com.topjohnwu.superuser.Shell
import timber.log.Timber
import kotlin.system.exitProcess

class MainActivityX : BaseActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var refreshViewController: RefreshViewController
    private lateinit var progressViewController: ProgressViewController

    lateinit var binding: ActivityMainXBinding
    lateinit var viewModel: MainViewModel

    var needRefresh: Boolean
        get() = viewModel.isNeedRefresh.value ?: false
        set(value) = viewModel.isNeedRefresh.postValue(value)

    override fun onCreate(savedInstanceState: Bundle?) {
        val context = this
        OABX.activity = this
        OABX.main = this

        setCustomTheme()
        super.onCreate(savedInstanceState)

        OABX.appsSuspendedChecked = false

        if (OABX.prefFlag(PREFS_CATCHUNCAUGHTEXCEPTION, false)) {
            Thread.setDefaultUncaughtExceptionHandler { _, e ->
                try {
                    val maxCrashLines = OABX.prefInt(PREFS_MAXCRASHLINES, 50)
                    LogsHandler.unhandledException(e)
                    LogsHandler(context).writeToLogFile(
                        "uncaught exception happened:\n\n" +
                                "\n${BuildConfig.APPLICATION_ID} ${BuildConfig.VERSION_NAME}"
                                + "\n" +
                                runAsRoot(
                                    "logcat -d -t $maxCrashLines --pid=${Process.myPid()}"  // -d = dump and exit
                                ).out.joinToString("\n")
                    )
                    val longToastTime = 3000
                    val showTime = 12000
                    object : Thread() {
                        override fun run() {
                            Looper.prepare()
                            repeat(showTime / longToastTime) {
                                Toast.makeText(
                                    context,
                                    "Uncaught Exception\n${e.message ?: ""}\n${e.cause ?: ""}",
                                    Toast.LENGTH_LONG
                                ).show()
                                sleep(longToastTime.toLong())
                            }
                            Toast.makeText(
                                context,
                                "restarting application...",
                                Toast.LENGTH_LONG
                            ).show()
                            Looper.loop()
                        }
                    }.start()
                    Thread.sleep(showTime.toLong())
                } catch (e: Throwable) {
                    // ignore
                } finally {
                    exitProcess(2)
                }
            }
        }

        Shell.getShell()

        binding = ActivityMainXBinding.inflate(layoutInflater)
        binding.lifecycleOwner = this
        val database = ODatabase.getInstance(this)
        prefs = getPrivateSharedPrefs()

        val viewModelFactory = MainViewModel.Factory(database, application)
        viewModel = ViewModelProvider(this, viewModelFactory)[MainViewModel::class.java]
        if (!isRememberFiltering) {
            this.sortFilterModel = SortFilterModel()
        }
        viewModel.blocklist.observe(this) {
            needRefresh = true
        }
        viewModel.packageList.observe(this) { }
        viewModel.backupsMap.observe(this) { }
        viewModel.isNeedRefresh.observe(this) {
            if (it) {
                if (viewModel.refreshing.value == 0) {
                    invalidateBackupLocation()
                }
            }
        }

        runOnUiThread { showEncryptionDialog() }

        setContentView(binding.root)

        if (doIntent(intent))
            return
    }

    override fun onStart() {
        super.onStart()
        setupOnClicks()
        setupNavigation()
    }

    override fun onResume() {
        OABX.activity = this    // just in case 'this' object is recreated
        OABX.main = this
        super.onResume()
    }

    override fun onDestroy() {
        OABX.main = null
        super.onDestroy()
    }

    override fun onBackPressed() {
        finishAffinity()
    }

    override fun onNewIntent(intent: Intent?) {
        doIntent(intent)
        super.onNewIntent(intent)
    }

    fun doIntent(intent: Intent?): Boolean {
        if (intent == null) return false
        val command = intent.action
        Timber.i("Main: command $command")
        when (command) {
            null -> {}
            "android.intent.action.MAIN" -> {}
            else -> {
                addInfoText("Main: command '$command'")
            }
        }
        return false
    }

    private fun setupNavigation() {
        try {
            val navHostFragment =
                supportFragmentManager.findFragmentById(R.id.fragmentContainer) as NavHostFragment
            val navController = navHostFragment.navController
            binding.bottomNavigation.setOnItemSelectedListener { item: MenuItem ->
                if (item.itemId == binding.bottomNavigation.selectedItemId) return@setOnItemSelectedListener false
                if (binding.bottomNavigation.selectedItemId.itemIdToOrder() < item.itemId.itemIdToOrder())
                    navController.navigateRight(item.itemId)
                else
                    navController.navigateLeft(item.itemId)
                true
            }
        } catch (e: ClassCastException) {
            finish()
            startActivity(intent)
        }
    }

    private fun setupOnClicks() {
        binding.buttonSettings.setOnClickListener {
            viewModel.packageList.value?.let { OABX.app.cache.put("appInfoList", it) }
            startActivity(
                Intent(applicationContext, PrefsActivity::class.java)
            )
        }
    }

    private fun showEncryptionDialog() {
        val dontShowAgain = isEncryptionEnabled()
        if (dontShowAgain) return
        val dontShowCounter = prefs.getInt(PREFS_SKIPPEDENCRYPTION, 0)
        prefs.edit().putInt(PREFS_SKIPPEDENCRYPTION, dontShowCounter + 1).apply()
        if (dontShowCounter % 10 == 0 && dontShowCounter <= 30) {
            AlertDialog.Builder(this)
                .setTitle(R.string.enable_encryption_title)
                .setMessage(R.string.enable_encryption_message)
                .setPositiveButton(R.string.dialog_approve) { _: DialogInterface?, _: Int ->
                    startActivity(
                        Intent(applicationContext, PrefsActivity::class.java).putExtra(
                            ".toEncryption",
                            true
                        )
                    )
                }
                .show()
        }
    }

    fun updatePackage(packageName: String) {
        viewModel.updatePackage(packageName)
    }

    fun setRefreshViewController(refreshViewController: RefreshViewController) {
        this.refreshViewController = refreshViewController
    }

    fun refreshView() {
        if (::refreshViewController.isInitialized) refreshViewController.refreshView(viewModel.packageList.value)
    }

    fun setProgressViewController(progressViewController: ProgressViewController) {
        this.progressViewController = progressViewController
    }

    fun updateProgress(progress: Int, max: Int) {
        if (::progressViewController.isInitialized)
            this.progressViewController.updateProgress(progress, max)
    }

    fun hideProgress() {
        if (::progressViewController.isInitialized)
            this.progressViewController.hideProgress()
    }

    fun showSnackBar(message: String) {
        binding.snackbarText.apply {
            text = message
            visibility = View.VISIBLE
        }
    }

    fun dismissSnackBar() {
        binding.snackbarText.visibility = View.GONE
    }

    fun whileShowingSnackBar(message: String, todo: () -> Unit) {
        runOnUiThread {
            showSnackBar(message)
        }
        todo()
        runOnUiThread {
            dismissSnackBar()
        }
    }

}
