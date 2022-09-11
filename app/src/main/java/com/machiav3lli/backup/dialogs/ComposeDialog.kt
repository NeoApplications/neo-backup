package com.machiav3lli.backup.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.machiav3lli.backup.OABX
import com.machiav3lli.backup.R
import com.machiav3lli.backup.ui.compose.item.ActionButton
import com.machiav3lli.backup.ui.compose.item.ElevatedActionButton
import com.machiav3lli.backup.ui.compose.item.SelectableRow
import com.machiav3lli.backup.ui.item.Pref
import kotlinx.coroutines.delay

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun BaseDialog(
    openDialogCustom: MutableState<Boolean>,
    dialogUI: @Composable (() -> Unit)
) {
    Dialog(
        onDismissRequest = { openDialogCustom.value = false },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        dialogUI()
    }
}

@Composable
fun ActionsDialogUI(
    mainText: String,
    openDialogCustom: MutableState<Boolean>,
    primaryText: String,
    primaryAction: (() -> Unit) = {},
    secondaryText: String = "",
    secondaryAction: (() -> Unit)? = null
) {
    val context = LocalContext.current

    Card(
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.padding(8.dp),
        elevation = CardDefaults.elevatedCardElevation(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = mainText, style = MaterialTheme.typography.titleLarge)

            Row(
                Modifier.fillMaxWidth()
            ) {
                ActionButton(text = stringResource(id = R.string.dialogCancel)) {
                    openDialogCustom.value = false
                }
                Spacer(Modifier.weight(1f))
                if (secondaryAction != null) {
                    ElevatedActionButton(text = secondaryText, positive = false) {
                        secondaryAction()
                        openDialogCustom.value = false
                    }
                    Spacer(Modifier.requiredWidth(8.dp))
                }
                ElevatedActionButton(text = primaryText) {
                    primaryAction()
                    openDialogCustom.value = false
                }
            }
        }
    }
}

@Composable
fun EnumDialogUI(
    pref: Pref.EnumPref,
    openDialogCustom: MutableState<Boolean>,
    onChanged: (() -> Unit) = {}
) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(OABX.prefInt(pref.key, pref.defaultValue)) }
    val entryPairs = pref.entries.toList()

    Card(
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.padding(8.dp),
        elevation = CardDefaults.elevatedCardElevation(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = stringResource(pref.titleId), style = MaterialTheme.typography.titleLarge)
            LazyColumn(
                modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)
            ) {
                items(items = entryPairs) {
                    val isSelected = rememberSaveable(selected) {
                        mutableStateOf(selected == it.first)
                    }
                    SelectableRow(
                        modifier = Modifier.clip(MaterialTheme.shapes.medium),
                        title = stringResource(id = it.second),
                        selectedState = isSelected
                    ) {
                        selected = it.first
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth()
            ) {
                ActionButton(text = stringResource(id = R.string.dialogCancel)) {
                    openDialogCustom.value = false
                }
                Spacer(Modifier.weight(1f))
                ElevatedActionButton(text = stringResource(id = R.string.dialogSave)) {
                    if (OABX.prefInt(pref.key, pref.defaultValue) != selected) {
                        OABX.setPrefInt(pref.key, selected)
                        onChanged()
                    }
                    openDialogCustom.value = false
                }
            }
        }
    }
}

@Composable
fun ListDialogUI(
    pref: Pref.ListPref,
    openDialogCustom: MutableState<Boolean>,
    onChanged: (() -> Unit) = {}
) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(OABX.prefString(pref.key, pref.defaultValue)) }
    val entryPairs = pref.entries.toList()

    Card(
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.padding(8.dp),
        elevation = CardDefaults.elevatedCardElevation(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = stringResource(pref.titleId), style = MaterialTheme.typography.titleLarge)
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 16.dp, bottom = 16.dp)
            ) {
                items(items = entryPairs) {
                    val isSelected = rememberSaveable(selected) {
                        mutableStateOf(selected == it.first)
                    }
                    SelectableRow(
                        modifier = Modifier.clip(MaterialTheme.shapes.medium),
                        title = it.second,
                        selectedState = isSelected
                    ) {
                        selected = it.first
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth()
            ) {
                ActionButton(text = stringResource(id = R.string.dialogCancel)) {
                    openDialogCustom.value = false
                }
                Spacer(Modifier.weight(1f))
                ElevatedActionButton(text = stringResource(id = R.string.dialogSave)) {
                    if (OABX.prefString(pref.key, pref.defaultValue) != selected) {
                        OABX.setPrefString(pref.key, selected)
                        onChanged()
                    }
                    openDialogCustom.value = false
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StringDialogUI(
    pref: Pref.StringPref,
    isPrivate: Boolean = false,
    openDialogCustom: MutableState<Boolean>,
    onChanged: (() -> Unit) = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val textFieldFocusRequester = remember { FocusRequester() }

    LaunchedEffect(textFieldFocusRequester) {
        delay(100)
        textFieldFocusRequester.requestFocus()
    }
    var savedValue by remember {
        mutableStateOf(
            if (isPrivate) OABX.prefPrivateString(pref.key, pref.defaultValue)
            else OABX.prefString(pref.key, pref.defaultValue)
        )
    }

    Card(
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.padding(8.dp),
        elevation = CardDefaults.elevatedCardElevation(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = stringResource(pref.titleId), style = MaterialTheme.typography.titleLarge)
            TextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(textFieldFocusRequester),
                value = savedValue,
                colors = TextFieldDefaults.textFieldColors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                shape = MaterialTheme.shapes.medium,
                singleLine = true,
                onValueChange = { savedValue = it },
                visualTransformation = if (isPrivate) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            )

            Row(
                Modifier.fillMaxWidth()
            ) {
                ActionButton(text = stringResource(id = R.string.dialogCancel)) {
                    openDialogCustom.value = false
                }
                Spacer(Modifier.weight(1f))
                ElevatedActionButton(text = stringResource(id = R.string.dialogSave)) {
                    if ((isPrivate &&
                                OABX.prefPrivateString(pref.key, pref.defaultValue) != savedValue)
                        || OABX.prefString(pref.key, pref.defaultValue) != savedValue
                    ) {
                        if (isPrivate) OABX.setPrefPrivateString(pref.key, savedValue)
                        else OABX.setPrefString(pref.key, savedValue)
                        onChanged()
                    }
                    openDialogCustom.value = false
                }
            }
        }
    }
}