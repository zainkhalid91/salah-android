package com.zainkhalid.salah.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationSheet(vm: SalahViewModel) {
    val context = LocalContext.current
    val c = palette
    var query by remember { mutableStateOf("") }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.locate()
    }

    ModalBottomSheet(onDismissRequest = { vm.showLocationSheet = false }, containerColor = c.background) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr("Location"), color = c.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Button(
                onClick = {
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (granted) vm.locate() else askLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                },
                enabled = !vm.locating,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (vm.locating) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = c.onAccent, strokeWidth = 2.dp)
                    Text("  " + tr("Finding you…"))
                } else {
                    Icon(Icons.Filled.LocationOn, contentDescription = null)
                    Text("  " + tr("Use my location"))
                }
            }
            vm.locationMessage?.let { Text(tr(it), color = c.secondary, fontSize = 13.sp) }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(tr("Search for a city")) },
                singleLine = true,
                trailingIcon = {
                    if (vm.searching) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Filled.Search, contentDescription = tr("Search"), modifier = Modifier.clickable { vm.search(query) })
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.search(query) }),
                modifier = Modifier.fillMaxWidth(),
            )
            vm.searchMessage?.let { Text(it, color = c.secondary, fontSize = 13.sp) }
            for (loc in vm.results) {
                Row(
                    Modifier.fillMaxWidth().clickable { vm.setLocation(loc) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(loc.name, color = c.text, fontSize = 15.sp)
                        Text(listOfNotNull(vm.regionOf(loc), loc.timeZone).joinToString(" · "), color = c.secondary, fontSize = 12.sp)
                    }
                }
                HorizontalDivider(color = c.line)
            }
        }
    }
}
