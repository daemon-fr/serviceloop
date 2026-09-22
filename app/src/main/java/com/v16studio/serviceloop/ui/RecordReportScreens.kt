package com.v16studio.serviceloop.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import com.v16studio.serviceloop.domain.FinalRecordDetail
import com.v16studio.serviceloop.domain.RecordVersionSummary
import com.v16studio.serviceloop.domain.ReportVersionSummary
import com.v16studio.serviceloop.ui.designsystem.LocalServiceLoopTokens
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopActionStack
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopContentTabs
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopUiTokens
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopVersionRow
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopPrivateLabel
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopSurfaceCard
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopButtonAdapter as Button
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopOutlinedButtonAdapter as OutlinedButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopTextButtonAdapter as TextButton
import com.v16studio.serviceloop.ui.icons.ServiceLoopIcon
import com.v16studio.serviceloop.ui.icons.ServiceLoopIcons
import com.v16studio.serviceloop.ui.theme.LocalServiceLoopColors
import java.io.File
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun FinalRecordScreen(detail: FinalRecordDetail?, recordVersions: List<RecordVersionSummary>, reportVersions: List<ReportVersionSummary>, historical: Boolean, generating: Boolean, error: String?, padding: PaddingValues, viewModel: ServiceLoopViewModel, nav: NavHostController) {
    if (detail == null) return HonestPlaceholder(padding, "Reading final service record")
    val report = detail.public
    val context = LocalContext.current
    val reportPresent = detail.report?.let { File(context.filesDir, it.relativePath).isFile } == true
    var voidReason by rememberSaveable(report.recordId) { mutableStateOf("") }
    LazyColumn(Modifier.padding(padding).testTag("final-record-list"), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.section)) {
        item { SectionTitle("${report.visitReference} · ${if(detail.voided) "VOIDED" else "Finalized"}"); detail.publicVoidReason?.let{Text("Customer explanation: $it",color=MaterialTheme.colorScheme.error)}; Text("Service date ${report.actualServiceDate} · Revision ${report.revisionNumber}"); Text("Recorded on ${formatRecordedOn(report.recordedAtEpochMillis)}"); Text("${report.customerReference.orEmpty()} · ${report.customerName}\n${report.siteReference.orEmpty()} · ${report.siteName}\n${report.siteAddress.orEmpty()}"); report.publicNote?.let { Text("Record note: $it") } }
        items(report.lines) { line -> AccentCard { Text(serviceLoopSubjectLabel(line.subjectType, line.equipmentName, line.equipmentReference, line.equipmentDescription), style = MaterialTheme.typography.titleMedium); Text("${line.planReference?.let { "$it · " }.orEmpty()}${line.serviceName}"); Text("Outcome: ${line.outcome.replace('_', ' ')}"); line.publicWorkNote?.let { Text(it) }; line.notPerformedReason?.let { Text("Reason: $it") }; Text(dueEffect(line)); line.parts.forEach { Text("Part: ${it.description} · ${it.quantity} ${it.unit}") }; line.photos.forEachIndexed { index, photo -> ReportPhotoThumbnail(photo, index, context) }; line.checklist.forEach { Text("${it.position}. ${it.label}: ${it.value ?: it.disposition.replace('_', ' ')}${it.reason?.let { reason -> " — $reason" }.orEmpty()}") } } }
        if (detail.privateNotes.isNotEmpty()) item { AccentCard { ServiceLoopPrivateLabel("Internal / Not in customer report", style = MaterialTheme.typography.titleMedium); detail.privateNotes.forEach { Text(it) } } }
        item {
            Text("Customer PDF", style = MaterialTheme.typography.titleMedium)
            Text(when (detail.report?.status) { "READY" -> if (reportPresent) "Ready · Version ${detail.report.versionNumber} · ${detail.report.byteSize} bytes" else "File missing · Version ${detail.report.versionNumber}"; "MISSING" -> "File missing · Version ${detail.report.versionNumber}"; "FAILED" -> "Generation failed · Version ${detail.report.versionNumber}"; "GENERATING" -> "Generating…"; else -> "Not generated" })
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (detail.report?.status == "READY") Button(onClick = { nav.navigate(if (historical) "report-version/${report.recordId}/${report.revisionId}/${detail.report.id}" else if (reportPresent) "report/${report.recordId}" else "report-text/${report.recordId}") }, modifier = Modifier.fillMaxWidth()) { Text(if (reportPresent) "View report" else "View report text") }
            else Button(onClick = { viewModel.generateReport(report.recordId, if (historical) report.revisionId else null) }, enabled = !generating, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Generate customer PDF" }) { Text(if (generating) "Generating…" else if (historical) "Recreate this historical PDF" else if (detail.report?.status == "FAILED") "Retry customer PDF" else "Generate customer PDF") }
        }
        if (historical) item { Text("Historical revision · The current record may be newer.", color = MaterialTheme.colorScheme.tertiary) }
        if (recordVersions.isNotEmpty()) item { Text("Record revisions", style = MaterialTheme.typography.titleMedium); recordVersions.forEach { version -> ServiceLoopVersionRow("Revision ${version.revisionNumber}",if(version.current)"Current" else "Superseded",listOfNotNull(version.correctionReason?.takeIf(String::isNotBlank),"Recorded ${Instant.ofEpochMilli(version.recordedAtEpochMillis)}").joinToString(" · ")) { nav.navigate("record-version/${report.recordId}/${version.id}") } } }
        if (reportVersions.isNotEmpty()) item { Text("Retained report renditions", style = MaterialTheme.typography.titleMedium); reportVersions.forEach { version -> ServiceLoopVersionRow("PDF v${version.versionNumber}",version.status,"${version.kind.replace('_',' ').lowercase().replaceFirstChar(Char::uppercase)}${version.generatedAtEpochMillis?.let { " · Generated ${Instant.ofEpochMilli(it)}" }.orEmpty()}") { nav.navigate("report-version/${report.recordId}/${version.revisionId}/${version.id}") } } }
        if (!historical) item { if(!detail.voided) { Button(onClick={nav.navigate("correction/${report.recordId}")},modifier=Modifier.fillMaxWidth().testTag("correct-record")){Text("Correct record / Resume correction")}; OutlinedTextField(voidReason,{voidReason=it},label={Text("Customer-facing void explanation · Required")},modifier=Modifier.fillMaxWidth()); TextButton(onClick={viewModel.voidRecord(report.recordId,voidReason,""){viewModel.loadFinalRecord(report.recordId)}},enabled=voidReason.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text("Void this record")} } else { Text("Ordinary customer Share is disabled for a voided original. Previously shared files cannot be revoked."); if (detail.report?.kind != "VOID_NOTICE") Button(onClick = { viewModel.generateReport(report.recordId) }, enabled = !generating, modifier = Modifier.fillMaxWidth().testTag("generate-void-notice")) { Text("Generate customer void notice") } else Text("Current VOID NOTICE is ready for customer handoff.") } }
    }
}

@Composable
internal fun ReportPreviewScreen(detail: FinalRecordDetail?, padding: PaddingValues, initialTextView: Boolean, historical: Boolean = false, viewModel: ServiceLoopViewModel? = null) {
    val rendition = detail?.report
    if (detail == null || rendition == null || rendition.status !in setOf("READY", "MISSING")) return HonestPlaceholder(padding, "Report file is not ready")
    val context = LocalContext.current; val file = remember(rendition.relativePath) { File(context.filesDir, rendition.relativePath) }
     val colors = LocalServiceLoopTokens.current
     var textView by rememberSaveable(rendition.id) { mutableStateOf(initialTextView || !file.isFile) }; var pageIndex by rememberSaveable { mutableStateOf(0) }; var bitmap by remember { mutableStateOf<Bitmap?>(null) }; var pageCount by remember { mutableStateOf(rendition.pageCount ?: 1) }; var missing by remember { mutableStateOf(!file.isFile) }; var supersededShareAcknowledged by rememberSaveable(rendition.id) { mutableStateOf(false) }
     var pageScale by remember(pageIndex) { mutableStateOf(1f) }
     var pageOffsetX by remember(pageIndex) { mutableStateOf(0f) }
     var pageOffsetY by remember(pageIndex) { mutableStateOf(0f) }
     val transformState = rememberTransformableState { scaleChange, panChange, _ ->
         pageScale = (pageScale * scaleChange).coerceIn(1f, 4f)
         pageOffsetX = (pageOffsetX + panChange.x).coerceIn(-900f, 900f)
         pageOffsetY = (pageOffsetY + panChange.y).coerceIn(-1400f, 1400f)
     }
    LaunchedEffect(file, pageIndex, textView) {
        if (!textView && file.isFile) withContext(Dispatchers.IO) { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use { renderer -> pageCount = renderer.pageCount; val page = renderer.openPage(pageIndex.coerceIn(0, renderer.pageCount - 1)); bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888).also { page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }; page.close() } } } else missing = !file.isFile
    }
    Column(Modifier.padding(padding).fillMaxSize()) {
    Text(if (missing) "File missing · Structured report remains available" else "PDF file ready", modifier = Modifier.fillMaxWidth().background(if(missing) LocalServiceLoopColors.current.errorTint else LocalServiceLoopColors.current.confirmedTint).padding(10.dp))
    LazyColumn(Modifier.weight(1f).testTag("report-preview-list"), contentPadding = PaddingValues(0.dp, 8.dp, 0.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        item { Column(Modifier.fillMaxWidth().background(colors.surface)) { Column(Modifier.padding(horizontal = 12.dp)) { Text("${detail.public.visitReference} · Revision ${detail.public.revisionNumber} · PDF v${rendition.versionNumber} · ${rendition.id.take(8)}"); Text("Service ${detail.public.actualServiceDate} · ${if (missing) "Structured text only" else "Ready"}"); rendition.generatedAtEpochMillis?.let { Text("Generated ${Instant.ofEpochMilli(it)}") } }; ServiceLoopContentTabs(listOf(false to "PDF view",true to "Text view"),textView,{textView=it},Modifier.testTag("report-view-tabs")) } }
        if (textView) item { Box(Modifier.padding(horizontal = 12.dp)) { StructuredReportText(detail) } }
        else if (missing) item { Text("File missing", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 12.dp)) }
         else { item { Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) { bitmap?.let { Image(it.asImageBitmap(), "Rendered customer report page ${pageIndex + 1}", Modifier.fillMaxWidth().graphicsLayer(scaleX = pageScale, scaleY = pageScale, translationX = pageOffsetX, translationY = pageOffsetY).transformable(transformState)) } } }; item { Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { OutlinedButton(onClick = { pageIndex-- }, enabled = pageIndex > 0) { Text("Previous page") }; Text("Page ${pageIndex + 1} of $pageCount"); OutlinedButton(onClick = { pageIndex++ }, enabled = pageIndex + 1 < pageCount) { Text("Next page") } } } }
        if (historical && !detail.voided) item { Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) { com.v16studio.serviceloop.ui.designsystem.ServiceLoopCheckbox(supersededShareAcknowledged, { supersededShareAcknowledged = it }, contentDescription = "Acknowledge superseded historical report"); Text("I understand this is a superseded historical report") } }
        if (missing && historical && viewModel != null) item { Button(onClick = { viewModel.generateReport(detail.public.recordId, detail.public.revisionId) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) { Text("Recreate from this fixed revision") } }
     item { Column(Modifier.padding(horizontal = 12.dp)) { val eligible=reportShareEligible(file.isFile,detail.voided,rendition.kind,historical,supersededShareAcknowledged); fun share(email:String?){val uri=FileProvider.getUriForFile(context,"${context.packageName}.reports",file);val subject="Service report ${detail.public.visitReference} · ${detail.public.businessName}";val body="Attached is the service report for ${detail.public.customerName} · ${detail.public.siteName} dated ${detail.public.actualServiceDate}.\n\nGenerated with ServiceLoop";context.startActivity(Intent.createChooser(reportShareIntent(uri,email,subject,body),if(email==null)"Share customer service record" else "Send service report to office"))}; ServiceLoopActionStack { Button(onClick={share(null)},enabled=eligible,modifier=Modifier.fillMaxWidth().testTag("share-pdf").semantics{contentDescription="Share PDF"}){ServiceLoopIcon(ServiceLoopIcons.Share,null,Modifier.size(ServiceLoopUiTokens.Size.icon));Spacer(Modifier.width(ServiceLoopUiTokens.Space.sm));Text(if(detail.voided&&rendition.kind!="VOID_NOTICE")"Share disabled for voided original" else "Share PDF")}; val office=context.getSharedPreferences(DISPATCH_PREFS,0).getString(OFFICE_EMAIL,"").orEmpty().trim(); if(office.isNotBlank()) OutlinedButton(onClick={share(office)},enabled=eligible,modifier=Modifier.fillMaxWidth().testTag("send-to-office")){ServiceLoopIcon(ServiceLoopIcons.Mail,null,Modifier.size(ServiceLoopUiTokens.Size.icon));Spacer(Modifier.width(ServiceLoopUiTokens.Space.sm));Text("Send to office")} }; if(detail.voided&&rendition.kind!="VOID_NOTICE") Text("Generate and share the current void notice. Previously shared files cannot be revoked.",style=MaterialTheme.typography.bodySmall) else if(historical) Text("Superseded report: confirm before customer handoff. Sharing does not prove delivery.",style=MaterialTheme.typography.bodySmall) } }
    }
    }
}

@Composable
private fun StructuredReportText(detail: FinalRecordDetail) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val r = detail.public
        Text(r.businessName, style = MaterialTheme.typography.titleLarge)
        Text("Service record ${r.visitReference} · Revision ${r.revisionNumber}")
        Text("Service date ${r.actualServiceDate}")
        Text("Technician ${r.technicianName}\n${r.businessContact}")
        Text("${r.customerReference.orEmpty()} · ${r.customerName}\n${r.siteReference.orEmpty()} · ${r.siteName}\n${r.siteAddress.orEmpty()}")
        r.publicNote?.let { Text("Record note: $it") }
        r.dispatch?.let {
            Text("Service coordination", style = MaterialTheme.typography.titleMedium)
            Text("Job ${it.managerReference ?: it.dispatchVisitId} · Generation ${it.generation}\nDocumented by ${it.documentingTechnicianName}\nTechnician reference ${it.documentingTechnicianId.take(8)}")
        }
        r.lines.forEach { line ->
            Text(serviceLoopSubjectLabel(line.subjectType, line.equipmentName, line.equipmentReference, line.equipmentDescription), style = MaterialTheme.typography.titleMedium)
            Text(line.equipmentIdentification.orEmpty())
            Text("${line.planReference?.let { "$it · " }.orEmpty()}${line.serviceName} — ${line.outcome.replace('_', ' ')}")
            line.publicWorkNote?.let { Text(it) }
            line.notPerformedReason?.let { Text("Reason: $it") }
            Text(dueEffect(line))
            line.parts.forEach { Text("Part: ${it.description} · ${it.quantity} ${it.unit}") }
            line.photos.forEachIndexed { index, photo -> Text("Photograph ${index + 1}${photo.caption?.let { caption -> ": $caption" }.orEmpty()}") }
            line.checklist.forEach { Text("${it.position}. ${it.label}: ${it.value ?: it.disposition.replace('_', ' ')}${it.unit?.let { unit -> " $unit" }.orEmpty()}${it.reason?.let { reason -> " — $reason" }.orEmpty()}") }
        }
    }
}
