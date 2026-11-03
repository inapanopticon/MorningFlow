package com.morningflow.app.feature.templates

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.morningflow.app.R
import com.morningflow.app.core.designsystem.component.common.ConfirmationDialog
import com.morningflow.app.core.designsystem.component.navigation.MorningFlowNavigationDestination

private enum class TemplatesPage { LIST, DETAIL, EDITOR }

/** Feature-local coordinator. START returns the selected id to the caller and never starts the session itself. */
@Composable
fun TemplatesRoute(
    viewModel: TemplatesViewModel,
    onStart: (templateId: String) -> Unit,
    onNavigationSelected: (MorningFlowNavigationDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val editor by viewModel.editor.collectAsState()
    val editorClosedBySave by viewModel.editorClosedBySave.collectAsState()
    var page by remember { mutableStateOf(TemplatesPage.LIST) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var editorReturnPage by remember { mutableStateOf(TemplatesPage.LIST) }

    LaunchedEffect(editor, page, editorClosedBySave) {
        if (page == TemplatesPage.EDITOR && editor == null) {
            page = if (editorClosedBySave) TemplatesPage.LIST else editorReturnPage
        }
    }
    LaunchedEffect(state.templates, selectedId, page) {
        if (page == TemplatesPage.DETAIL && selectedId != null && state.templates.none { it.id == selectedId }) {
            page = TemplatesPage.LIST
        }
    }

    BackHandler {
        when (page) {
            TemplatesPage.EDITOR -> if (editor?.showDiscardConfirmation == true) {
                viewModel.keepEditing()
            } else {
                viewModel.requestCloseEditor()
            }
            TemplatesPage.DETAIL -> if (state.pendingDeleteId != null) {
                viewModel.dismissDelete()
            } else {
                page = TemplatesPage.LIST
            }
            TemplatesPage.LIST -> onNavigationSelected(MorningFlowNavigationDestination.Home)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (state.isLoading) {
            Text(
                text = "Loading templates…",
                modifier = Modifier.align(Alignment.Center),
            )
        } else AnimatedContent(
            targetState = page,
            transitionSpec = { fadeIn(tween(200, delayMillis = 50)).togetherWith(fadeOut(tween(150))) },
            label = "templatesPage",
        ) { target ->
        when (target) {
        TemplatesPage.LIST -> TemplatesListScreen(
            templates = state.displayTemplates,
            onCreateTemplate = {
                viewModel.openEditor()
                editorReturnPage = TemplatesPage.LIST
                page = TemplatesPage.EDITOR
            },
            onStart = { onStart(it.id) },
            onDetails = {
                selectedId = it.id
                page = TemplatesPage.DETAIL
            },
            onSetDefault = { viewModel.setDefault(it.id) },
            onNavigationSelected = onNavigationSelected,
            modifier = modifier,
        )

        TemplatesPage.DETAIL -> {
            val template = state.templates.firstOrNull { it.id == selectedId }
            if (template == null) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "Loading template…",
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            } else {
                val summary = template.toTemplateSummaryDisplayData()
                TemplateDetailScreen(
                    template = summary,
                    variant = if (state.pendingDeleteId == template.id) {
                        TemplateDetailVariant.DeleteConfirmation
                    } else TemplateDetailVariant.Normal,
                    dialog = TemplateDeleteDialogDisplayData(
                        title = R.string.template_delete_dialog_title,
                        confirmLabel = R.string.template_delete_dialog_confirm,
                        cancelLabel = R.string.template_delete_dialog_cancel,
                    ),
                    onBack = { page = TemplatesPage.LIST },
                    onEdit = {
                        viewModel.openEditor(template.id)
                        editorReturnPage = TemplatesPage.DETAIL
                        page = TemplatesPage.EDITOR
                    },
                    onStart = { onStart(template.id) },
                    onDelete = { viewModel.requestDelete(template.id) },
                    onConfirmDelete = {
                        viewModel.confirmDelete()
                    },
                    onDismissDelete = viewModel::dismissDelete,
                    modifier = modifier,
                )
            }
        }

        TemplatesPage.EDITOR -> {
            val currentEditor = editor
            if (currentEditor != null) {
                Box(modifier = Modifier.fillMaxSize()) {
                    TemplateEditorScreen(
                        data = currentEditor.toDisplayData(),
                        variant = if (currentEditor.errors.isEmpty()) TemplateEditorVariant.Normal else TemplateEditorVariant.Error,
                        onBack = viewModel::requestCloseEditor,
                        onSave = viewModel::saveEditor,
                        onTemplateNameChange = viewModel::updateName,
                        onPriorityChange = viewModel::updatePriority,
                        onRecommendedFromChange = viewModel::updateRecommendedFrom,
                        onDefaultChange = viewModel::updateDefault,
                        onDayClick = viewModel::toggleDay,
                        onModuleToggle = viewModel::toggleModule,
                        onBehaviourClick = viewModel::cycleBehaviour,
                        onBehaviourSelected = { type, key, option ->
                            viewModel.updateBehaviour(type, key, when (option) {
                                "Short" -> com.morningflow.app.core.model.BehaviourMode.SHORT
                                "Long" -> com.morningflow.app.core.model.BehaviourMode.LONG
                                "On" -> com.morningflow.app.core.model.BehaviourMode.ON
                                "Off" -> com.morningflow.app.core.model.BehaviourMode.OFF
                                else -> com.morningflow.app.core.model.BehaviourMode.MEDIUM
                            })
                        },
                        onTargetChange = viewModel::adjustTarget,
                    )
                    if (currentEditor.showDiscardConfirmation) {
                        ConfirmationDialog(
                            title = androidx.compose.ui.res.stringResource(R.string.template_discard_dialog_title),
                            confirmLabel = androidx.compose.ui.res.stringResource(R.string.template_discard_dialog_confirm),
                            cancelLabel = androidx.compose.ui.res.stringResource(R.string.template_discard_dialog_cancel),
                            onConfirm = viewModel::discardEditor,
                            onCancel = viewModel::keepEditing,
                        )
                    }
                }
            }
        }
        }
        }
        state.operationError?.let { error ->
            Text(
                text = error,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .background(Color(0xFF8A1C1C))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}
