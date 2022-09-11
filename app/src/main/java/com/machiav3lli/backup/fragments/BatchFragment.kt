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
package com.machiav3lli.backup.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.machiav3lli.backup.ALT_MODE_APK
import com.machiav3lli.backup.ALT_MODE_BOTH
import com.machiav3lli.backup.ALT_MODE_DATA
import com.machiav3lli.backup.MAIN_FILTER_DEFAULT
import com.machiav3lli.backup.OABX
import com.machiav3lli.backup.R
import com.machiav3lli.backup.dialogs.BatchDialogFragment
import com.machiav3lli.backup.dialogs.PackagesListDialogFragment
import com.machiav3lli.backup.handler.LogsHandler
import com.machiav3lli.backup.items.Package
import com.machiav3lli.backup.ui.compose.item.ActionButton
import com.machiav3lli.backup.ui.compose.item.ElevatedActionButton
import com.machiav3lli.backup.ui.compose.item.ExpandableSearchAction
import com.machiav3lli.backup.ui.compose.item.StateChip
import com.machiav3lli.backup.ui.compose.item.TopBar
import com.machiav3lli.backup.ui.compose.recycler.BatchPackageRecycler
import com.machiav3lli.backup.ui.compose.theme.AppTheme
import com.machiav3lli.backup.ui.compose.theme.ColorAPK
import com.machiav3lli.backup.ui.compose.theme.ColorData
import com.machiav3lli.backup.utils.FileUtils
import com.machiav3lli.backup.utils.StorageLocationNotConfiguredException
import com.machiav3lli.backup.utils.applyFilter
import com.machiav3lli.backup.utils.getStats
import com.machiav3lli.backup.utils.sortFilterModel
import com.machiav3lli.backup.viewmodels.BatchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber

open class BatchFragment(private val backupBoolean: Boolean) : NavigationFragment(),
    BatchDialogFragment.ConfirmListener, RefreshViewController {
    lateinit var viewModel: BatchViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreate(savedInstanceState)
        val viewModelFactory = BatchViewModel.Factory(requireActivity().application)
        viewModel = ViewModelProvider(this, viewModelFactory)[BatchViewModel::class.java]
        return ComposeView(requireContext()).apply {
            setContent { BatchPage() }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        OABX.main?.viewModel?.isNeedRefresh?.observe(viewLifecycleOwner) {
            viewModel.refreshing.postValue(it)
        }
        packageList.observe(requireActivity()) { refreshView(it) }
    }

    override fun onResume() {
        super.onResume()
        requireMainActivity().setRefreshViewController(this)
    }

    private fun onClickBatchAction(backupBoolean: Boolean) {
        val checkedPackages = viewModel.filteredList.value
            ?.filter { it.packageName in viewModel.apkCheckedList.union(viewModel.dataCheckedList) }
            ?: listOf()
        val selectedList = checkedPackages.map(Package::packageInfo).toCollection(ArrayList())
        val selectedListModes = checkedPackages
            .map {
                when (it.packageName) {
                    in viewModel.apkCheckedList.intersect(viewModel.dataCheckedList) -> ALT_MODE_BOTH
                    in viewModel.apkCheckedList -> ALT_MODE_APK
                    else -> ALT_MODE_DATA
                }
            }
            .toCollection(ArrayList())
        if (selectedList.isNotEmpty()) {
            BatchDialogFragment(backupBoolean, selectedList, selectedListModes, this)
                .show(requireActivity().supportFragmentManager, "DialogFragment")
        }
    }

    override fun onConfirmed(
        selectedPackages: List<String?>,
        selectedModes: List<Int>
    ) {
        startBatchAction(backupBoolean, selectedPackages, selectedModes) {
            //viewModel.refreshNow.value = true
            // TODO refresh only the influenced packages
            it.removeObserver(this)
        }
    }

    override fun refreshView(list: MutableList<Package>?) {
        Timber.d("refreshing")
        sheetSortFilter =
            SortFilterSheet(requireActivity().sortFilterModel, getStats(list ?: mutableListOf()))
        try {
            viewModel.filteredList.value =
                list?.applyFilter(requireActivity().sortFilterModel, requireContext())
        } catch (e: FileUtils.BackupLocationInAccessibleException) {
            Timber.e("Could not update application list: $e")
        } catch (e: StorageLocationNotConfiguredException) {
            Timber.e("Could not update application list: $e")
        } catch (e: Throwable) {
            LogsHandler.unhandledException(e)
        }
    }

    override fun updateProgress(progress: Int, max: Int) {
        viewModel.progress.postValue(Pair(true, progress.toFloat() / max.toFloat()))
    }

    override fun hideProgress() {
        viewModel.progress.postValue(Pair(false, 0f))
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun BatchPage() {

        // TODO include tags in search
        val list by viewModel.filteredList.observeAsState(null)
        val query by viewModel.searchQuery.observeAsState("")

        val filterPredicate = { item: Package ->
            val includedBoolean = if (backupBoolean) item.isInstalled else item.hasBackups
            val queryBoolean =
                query.isNullOrEmpty() || listOf(item.packageName, item.packageLabel)
                    .find { it.contains(query, true) } != null
            includedBoolean && queryBoolean
        }
        val filteredList = list?.filter(filterPredicate)
        var allApkChecked by remember(filteredList) {
            mutableStateOf(
                viewModel.apkCheckedList.size ==
                        filteredList
                            ?.filter { !it.isSpecial && (backupBoolean || it.hasApk) }
                            ?.size
            )
        }
        var allDataChecked by remember(filteredList) {
            mutableStateOf(
                viewModel.dataCheckedList.size ==
                        filteredList
                            ?.filter { backupBoolean || it.hasData }
                            ?.size
            )
        }

        val refreshing by viewModel.refreshing.observeAsState()
        val progress by viewModel.progress.observeAsState(Pair(false, 0f))

        AppTheme(
            darkTheme = isSystemInDarkTheme()
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    TopBar(
                        title = stringResource(id = if (backupBoolean) R.string.backup else R.string.restore)
                    ) {
                        ExpandableSearchAction(
                            query = viewModel.searchQuery.value.orEmpty(),
                            onQueryChanged = { new ->
                                viewModel.searchQuery.value = new
                            },
                            onClose = {
                                viewModel.searchQuery.value = ""
                            }
                        )
                    }
                }
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .padding(paddingValues)
                        .fillMaxSize()
                ) {
                    Row(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ElevatedActionButton(
                            icon = painterResource(id = R.drawable.ic_blocklist),
                            text = stringResource(id = R.string.sched_blocklist),
                            positive = false
                        ) {
                            GlobalScope.launch(Dispatchers.IO) {
                                val blocklistedPackages =
                                    requireMainActivity().viewModel.blocklist.value
                                        ?.mapNotNull { it.packageName }.orEmpty()

                                PackagesListDialogFragment(
                                    blocklistedPackages,
                                    MAIN_FILTER_DEFAULT,
                                    true
                                ) { newList: Set<String> ->
                                    requireMainActivity().viewModel.updateBlocklist(newList)
                                }.show(
                                    requireActivity().supportFragmentManager,
                                    "BLOCKLIST_DIALOG"
                                )
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        ElevatedActionButton(
                            icon = painterResource(id = R.drawable.ic_filter),
                            text = stringResource(id = R.string.sort_and_filter),
                            positive = true
                        ) {
                            if (sheetSortFilter == null) sheetSortFilter = SortFilterSheet(
                                requireActivity().sortFilterModel,
                                getStats(packageList.value ?: mutableListOf())
                            )
                            sheetSortFilter?.showNow(
                                requireActivity().supportFragmentManager,
                                "SORTFILTER_SHEET"
                            )
                        }
                    }
                    AnimatedVisibility(visible = refreshing ?: false) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    AnimatedVisibility(visible = progress?.first == true) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            progress = progress.second
                        )
                    }
                    BatchPackageRecycler(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        productsList = filteredList,
                        restore = !backupBoolean,
                        apkCheckedList = viewModel.apkCheckedList,
                        dataCheckedList = viewModel.dataCheckedList,
                        onApkClick = { item: Package, b: Boolean ->
                            if (b) viewModel.apkCheckedList.add(item.packageName)
                            else viewModel.apkCheckedList.remove(item.packageName)
                            allApkChecked =
                                viewModel.apkCheckedList.size == filteredList?.filter { ai -> !ai.isSpecial && (backupBoolean || ai.hasApk) }?.size
                        }, onDataClick = { item: Package, b: Boolean ->
                            if (b) viewModel.dataCheckedList.add(item.packageName)
                            else viewModel.dataCheckedList.remove(item.packageName)
                            allDataChecked =
                                viewModel.dataCheckedList.size == filteredList
                                    ?.filter { ai -> backupBoolean || ai.hasData }?.size
                        }) { item, checkApk, checkData ->
                        when (checkApk) {
                            true -> viewModel.apkCheckedList.add(item.packageName)
                            else -> viewModel.apkCheckedList.remove(item.packageName)
                        }
                        when (checkData) {
                            true -> viewModel.dataCheckedList.add(item.packageName)
                            else -> viewModel.dataCheckedList.remove(item.packageName)
                        }
                        allApkChecked =
                            viewModel.apkCheckedList.size == filteredList
                                ?.filter { ai -> !ai.isSpecial && (backupBoolean || ai.hasApk) }?.size
                        allDataChecked =
                            viewModel.dataCheckedList.size == filteredList
                                ?.filter { ai -> backupBoolean || ai.hasData }?.size
                    }
                    Row(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        StateChip(
                            modifier = Modifier.padding(start = 8.dp, end = 4.dp),
                            icon = painterResource(id = R.drawable.ic_apk),
                            text = stringResource(id = R.string.all_apk),
                            checked = allApkChecked,
                            color = ColorAPK
                        ) {
                            val checkBoolean = !allApkChecked
                            allApkChecked = checkBoolean
                            if (checkBoolean)
                                viewModel.apkCheckedList.addAll(
                                    filteredList
                                        ?.filter { ai -> !ai.isSpecial && (backupBoolean || ai.hasApk) }
                                        ?.mapNotNull(Package::packageName).orEmpty()
                                )
                            else
                                viewModel.apkCheckedList.clear()
                        }
                        StateChip(
                            icon = painterResource(id = R.drawable.ic_data),
                            text = stringResource(id = R.string.all_data),
                            checked = allDataChecked,
                            color = ColorData
                        ) {
                            val checkBoolean = !allDataChecked
                            allDataChecked = checkBoolean
                            if (checkBoolean)
                                viewModel.dataCheckedList.addAll(
                                    filteredList
                                        ?.filter { ai -> backupBoolean || ai.hasData }
                                        ?.mapNotNull(Package::packageName).orEmpty()
                                )
                            else
                                viewModel.dataCheckedList.clear()
                        }
                        ActionButton(
                            modifier = Modifier.weight(1f),
                            text = stringResource(id = if (backupBoolean) R.string.backup else R.string.restore),
                            positive = true
                        ) {
                            onClickBatchAction(backupBoolean)
                        }
                    }
                }
            }
        }
    }

    class BackupFragment : BatchFragment(true)
    class RestoreFragment : BatchFragment(false)
}
