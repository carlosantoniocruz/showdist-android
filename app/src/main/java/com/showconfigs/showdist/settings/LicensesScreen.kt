package com.showconfigs.showdist.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.showconfigs.showdist.ui.BaseText
import com.showconfigs.showdist.ui.Palette
import com.showconfigs.showdist.ui.ShowdistAppBar

/** Licenses (SPEC.md §1.7): every shipped component, alphabetical. */
@Composable
fun LicensesScreen(onOpen: (index: Int) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var licenses by remember { mutableStateOf(Licenses.loaded) }
    LaunchedEffect(Unit) { if (licenses == null) licenses = Licenses.load(context) }

    Scaffold(topBar = { ShowdistAppBar("LICENSES", onBack) }, containerColor = Palette.Ink) { padding ->
        val list = licenses
        if (list == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Palette.Peach)
            }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            itemsIndexed(list, key = { _, license -> license.name }) { index, license ->
                if (index > 0) SettingsDivider(Modifier.padding(start = 20.dp))
                SettingsRow(license.name, onClick = { onOpen(index) }) { Chevron() }
            }
        }
    }
}

/** License detail (SPEC.md §1.7): the component name in citrine, then its paragraphs. */
@Composable
fun LicenseDetailScreen(index: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    var licenses by remember { mutableStateOf(Licenses.loaded) }
    LaunchedEffect(Unit) { if (licenses == null) licenses = Licenses.load(context) }
    val license = licenses?.getOrNull(index)

    Scaffold(topBar = { ShowdistAppBar("LICENSE", onBack) }, containerColor = Palette.Ink) { padding ->
        if (license == null) return@Scaffold
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(24.dp),
        ) {
            item {
                Text(
                    license.name,
                    style = BaseText.copy(color = Palette.Citrine, fontSize = 24.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(Modifier.height(20.dp))
            }
            items(license.paragraphs.size) { i ->
                val paragraph = license.paragraphs[i]
                Text(
                    paragraph.text,
                    modifier = Modifier.fillMaxWidth(),
                    style = BaseText.copy(
                        fontSize = 13.sp,
                        lineHeight = 1.5.em,
                        textAlign = if (paragraph.centred) TextAlign.Center else TextAlign.Start,
                    ),
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
