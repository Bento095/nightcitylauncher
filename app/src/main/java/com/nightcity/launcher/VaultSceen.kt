package com.nightcity.launcher

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID

@Composable
fun VaultScreen(
    entries: List<Prefs.VaultEntry>,
    onSaveEntry: (Prefs.VaultEntry) -> Unit,
    onDeleteEntry: (String) -> Unit,
    onBackHome: () -> Unit,
) {
    val nc = LocalNc.current
    val ctx = LocalContext.current
    val clipboard = remember { ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

    var editingId by remember { mutableStateOf<String?>(null) }
    var titleInput by remember { mutableStateOf("") }
    var bodyInput by remember { mutableStateOf("") }
    var statusMsg by remember { mutableStateOf("PRONTO // COFRE ATIVO") }

    fun startNewNote(prefillBody: String = "") {
        editingId = null
        titleInput = ""
        bodyInput = prefillBody
        statusMsg = "NOVO REGISTRO"
    }

    fun editExisting(item: Prefs.VaultEntry) {
        editingId = item.id
        titleInput = item.title
        bodyInput = item.body
        statusMsg = "EDITANDO // ${item.title.ifBlank { "SEM TÍTULO" }.uppercase()}"
    }

    fun readClipboardText(): String? {
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(ctx)?.toString()
    }

    fun copyToClipboard(text: String, label: String = "vault") {
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        statusMsg = "COPIADO PARA ÁREA DE TRANSFERÊNCIA"
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(nc.bg)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 11.dp, end = 11.dp, top = 8.dp, bottom = 92.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Cabeçalho da segunda página (VAULT)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Tx("COFRE // VAULT", nc.y, 24, true, spacing = 2f)
                Tx(statusMsg, nc.c, 10, mono = true)
            }
            Box(
                Modifier
                    .clip(CutShape(8.dp))
                    .background(nc.bg)
                    .border(1.dp, nc.y, CutShape(8.dp))
                    .clickable { onBackHome() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Tx("◂ HOME", nc.y, 11, true, mono = true)
            }
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(nc.y))

        // Editor / Área de colar e gerenciar conteúdo
        Panel("BLOCO DE NOTAS // EDITOR", if (editingId == null) "NOVO" else "EDITAR") {
            Tx("TÍTULO", nc.dim, 10, mono = true)
            Spacer(Modifier.height(3.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .border(1.dp, nc.c.copy(alpha = 0.45f))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                if (titleInput.isEmpty()) Tx("ex.: nota rápida, comando, lembrete...", nc.off, 12, mono = true)
                BasicTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    singleLine = true,
                    textStyle = TextStyle(color = nc.y, fontSize = 13.sp, fontFamily = Mono),
                    cursorBrush = SolidColor(nc.c),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Tx("CONTEÚDO", nc.dim, 10, mono = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Colar direto no editor atual
                    Box(
                        Modifier
                            .border(1.dp, nc.c)
                            .clickable {
                                val pasted = readClipboardText()
                                if (!pasted.isNullOrEmpty()) {
                                    bodyInput = if (bodyInput.isEmpty()) pasted else "$bodyInput\n$pasted"
                                    statusMsg = "CONTEÚDO COLADO NO EDITOR"
                                } else {
                                    statusMsg = "ÁREA DE TRANSFERÊNCIA VAZIA"
                                }
                            }
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Tx("COLAR", nc.c, 10, true, mono = true)
                    }
                    // Colar e já salvar como novo item no cofre
                    Box(
                        Modifier
                            .border(1.dp, nc.y)
                            .clickable {
                                val pasted = readClipboardText()
                                if (!pasted.isNullOrEmpty()) {
                                    val entry = Prefs.VaultEntry(
                                        id = UUID.randomUUID().toString(),
                                        title = titleInput.trim().ifBlank { "CLIPBOARD" },
                                        body = pasted,
                                        updatedAt = System.currentTimeMillis(),
                                    )
                                    onSaveEntry(entry)
                                    statusMsg = "COLADO E SALVO NO COFRE"
                                } else {
                                    statusMsg = "NADA PARA COLAR"
                                }
                            }
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Tx("COLAR+SALVAR", nc.y, 10, true, mono = true)
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(Color.Black)
                    .border(1.dp, nc.y.copy(alpha = 0.45f))
                    .padding(8.dp),
            ) {
                if (bodyInput.isEmpty()) Tx("> digite ou cole textos, links, snippets ou anotações...", nc.off, 11, mono = true)
                BasicTextField(
                    value = bodyInput,
                    onValueChange = { bodyInput = it },
                    textStyle = TextStyle(color = Color.White, fontSize = 12.sp, fontFamily = Mono),
                    cursorBrush = SolidColor(nc.y),
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .background(nc.y)
                        .clickable {
                            if (titleInput.isNotBlank() || bodyInput.isNotBlank()) {
                                val entry = Prefs.VaultEntry(
                                    id = editingId ?: UUID.randomUUID().toString(),
                                    title = titleInput.trim().ifBlank { "NOTA" },
                                    body = bodyInput,
                                    updatedAt = System.currentTimeMillis(),
                                )
                                onSaveEntry(entry)
                                startNewNote()
                                statusMsg = "REGISTRO SALVO"
                            } else {
                                statusMsg = "PREENCHA TÍTULO OU CONTEÚDO"
                            }
                        }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Tx("SALVAR", Color.Black, 11, true, mono = true)
                }

                Box(
                    Modifier
                        .weight(1f)
                        .border(1.dp, nc.c)
                        .clickable {
                            val combined = if (titleInput.isNotBlank()) "$titleInput\n$bodyInput" else bodyInput
                            if (combined.isNotBlank()) copyToClipboard(combined, titleInput.ifBlank { "vault" })
                            else statusMsg = "NADA PARA COPIAR"
                        }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Tx("COPIAR", nc.c, 11, true, mono = true)
                }

                Box(
                    Modifier
                        .weight(1f)
                        .border(1.dp, nc.r)
                        .clickable {
                            val idToDelete = editingId
                            startNewNote()
                            if (idToDelete != null) {
                                onDeleteEntry(idToDelete)
                                statusMsg = "REGISTRO REMOVIDO"
                            } else {
                                statusMsg = "CAMPOS LIMPOS"
                            }
                        }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Tx(if (editingId != null) "EXCLUIR" else "LIMPAR", nc.r, 11, true, mono = true)
                }
            }
        }

        // Lista gerenciável de notas salvas no cofre
        Panel("ITENS ARMAZENADOS", "${entries.size} SALVOS", accent = nc.c, tagColor = nc.y) {
            if (entries.isEmpty()) {
                Tx("> cofre vazio. cole ou escreva algo acima.", nc.dim, 11, mono = true, modifier = Modifier.padding(4.dp))
            }
            entries.forEach { item ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .border(1.dp, nc.y.copy(alpha = 0.25f))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Tx(item.title.uppercase(), nc.y, 14, true, spacing = 1f, modifier = Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Tx("EDITAR", nc.c, 10, true, mono = true, modifier = Modifier.clickable { editExisting(item) })
                            Tx("COPIAR", nc.y, 10, true, mono = true, modifier = Modifier.clickable {
                                copyToClipboard(item.body, item.title)
                            })
                            Tx("✕", nc.r, 11, true, mono = true, modifier = Modifier.clickable {
                                if (editingId == item.id) startNewNote()
                                onDeleteEntry(item.id)
                                statusMsg = "ITEM EXCLUÍDO"
                            })
                        }
                    }
                    if (item.body.isNotBlank()) {
                        Tx(
                            item.body,
                            nc.dim,
                            11,
                            mono = true,
                            maxLines = 4,
                        )
                    }
                }
            }
        }
    }
}