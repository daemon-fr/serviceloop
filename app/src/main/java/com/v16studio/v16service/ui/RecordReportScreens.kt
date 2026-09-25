package com.v16studio.v16service.ui

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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import com.v16studio.v16service.domain.FinalRecordDetail
import com.v16studio.v16service.domain.RecordVersionSummary
import com.v16studio.v16service.domain.ReportVersionSummary
import com.v16studio.v16service.ui.designsystem.LocalV16ServiceTokens
import com.v16studio.v16service.ui.designsystem.V16ServiceActionStack
import com.v16studio.v16service.ui.designsystem.V16ServiceContentTabs
import com.v16studio.v16service.ui.designsystem.V16ServiceUiTokens
import com.v16studio.v16service.ui.designsystem.V16ServiceVersionRow
import com.v16studio.v16service.ui.designsystem.V16ServicePrivateLabel
import com.v16studio.v16service.ui.designsystem.V16ServiceSurfaceCard
import com.v16studio.v16service.ui.designsystem.V16ServiceButtonAdapter as Button
import com.v16studio.v16service.ui.designsystem.V16ServiceOutlinedButtonAdapter as OutlinedButton
import com.v16studio.v16service.ui.designsystem.V16ServiceTextButtonAdapter as TextButton
import com.v16studio.v16service.ui.icons.V16ServiceIcon
import com.v16studio.v16service.ui.icons.V16ServiceIcons
import com.v16studio.v16service.ui.theme.LocalV16ServiceColors
import com.v16studio.v16service.report.technicianReportName
import java.io.File
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun FinalRecordScreen(detail: FinalRecordDetail?, recordVersions: List<RecordVersionSummary>, reportVersions: List<ReportVersionSummary>, historical: Boolean, generating: Boolean, error: String?, padding: PaddingValues, viewModel: V16ServiceViewModel, nav: NavHostController) {
    if (detail == null) return HonestPlaceholder(padding, "Reading final service record")
    val report = detail.public
    val context = LocalContext.current
    val reportPresent = detail.report?.let { File(context.filesDir, it.relativePath).isFile } == true
    var voidReason by rememberSaveable(report.recordId) { mutableStateOf("") }
    LazyColumn(Modifier.padding(padding).testTag("final-record-list"), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.section)) {
        item { SectionTitle("${report.visitReference} · ${if(detail.voided) "VOIDED" else "Finalized"}"); detail.publicVoidReason?.let{Text("Customer explanation: $it",color=MaterialTheme.colorScheme.error)}; Text("Service date ${report.actualServiceDate} · Revision ${report.revisionNumber}"); Text("Recorded on ${formatRecordedOn(report.recordedAtEpochMillis)}"); Text("${report.customerReference.orEmpty()} · ${report.customerName}\n${report.siteReference.orEmpty()} · ${report.siteName}\n${report.siteAddress.orEmpty()}"); report.publicNote?.let { Text("Record note: $it") } }
        items(report.lines) { line -> AccentCard { Text(v16ServiceSubjectLabel(line.subjectType, line.equipmentName, line.equipmentReference, line.equipmentDescription), style = MaterialTheme.typography.titleMedium); Text("${line.planReference?.let { "$it · " }.orEmpty()}${line.serviceName}"); Text("Outcome: ${line.outcome.replace('_', ' ')}"); line.publicWorkNote?.let { Text(it) }; line.notPerformedReason?.let { Text("Reason: $it") }; Text(dueEffect(line)); line.parts.forEach { Text("Part: ${it.description} · ${it.quantity} ${it.unit}") }; line.photos.forEachIndexed { index, photo -> ReportPhotoThumbnail(photo, index, context) }; line.checklist.forEach { Text("${it.position}. ${it.label}: ${it.value ?: it.disposition.replace('_', ' ')}${it.reason?.let { reason -> " — $reason" }.orEmpty()}") } } }
        if (detail.privateNotes.isNotEmpty()) item { AccentCard { V16ServicePrivateLabel("Internal / Not in customer report", style = MaterialTheme.typography.titleMedium); detail.privateNotes.forEach { Text(it) } } }
        item {
            Text("Customer PDF", style = MaterialTheme.typography.titleMedium)
            Text(when (detail.report?.status) { "READY" -> if (reportPresent) "Ready · Version ${detail.report.versionNumber} · ${detail.report.byteSize} bytes" else "File missing · Version ${detail.report.versionNumber}"; "MISSING" -> "File missing · Version ${detail.report.versionNumber}"; "FAILED" -> "Generation failed · Version ${detail.report.versionNumber}"; "GENERATING" -> "Generating…"; else -> "Not generated" })
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (detail.report?.status == "READY") Button(onClick = { nav.navigate(if (historical) "report-version/${report.recordId}/${report.revisionId}/${detail.report.id}" else if (reportPresent) "report/${report.recordId}" else "report-text/${report.recordId}") }, modifier = Modifier.fillMaxWidth()) { Text(if (reportPresent) "View report" else "View report text") }
            else Button(onClick = { viewModel.generateReport(report.recordId, if (historical) report.revisionId else null) }, enabled = !generating, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Generate customer PDF" }) { Text(if (generating) "Generating…" else if (historical) "Recreate this historical PDF" else if (detail.report?.status == "FAILED") "Retry customer PDF" else "Generate customer PDF") }
        }
        if (historical) item { Text("Historical revision · The current record may be newer.", color = MaterialTheme.colorScheme.tertiary) }
        if (recordVersions.isNotEmpty()) item { Text("Record revisions", style = MaterialTheme.typography.titleMedium); recordVersions.forEach { version -> V16ServiceVersionRow("Revision ${version.revisionNumber}",if(version.current)"Current" else "Superseded",listOfNotNull(version.correctionReason?.takeIf(String::isNotBlank),"Recorded ${Instant.ofEpochMilli(version.recordedAtEpochMillis)}").joinToString(" · ")) { nav.navigate("record-version/${report.recordId}/${version.id}") } } }
        if (reportVersions.isNotEmpty()) item { Text("Retained report renditions", style = MaterialTheme.typography.titleMedium); reportVersions.forEach { version -> V16ServiceVersionRow("PDF v${version.versionNumber}",version.status,"${version.kind.replace('_',' ').lowercase().replaceFirstChar(Char::uppercase)}${version.generatedAtEpochMillis?.let { " · Generated ${Instant.ofEpochMilli(it)}" }.orEmpty()}") { nav.navigate("report-version/${report.recordId}/${version.revisionId}/${version.id}") } } }
        if (!historical) item { if(!detail.voided) { Button(onClick={nav.navigate("correction/${report.recordId}")},modifier=Modifier.fillMaxWidth().testTag("correct-record")){Text("Correct record / Resume correction")}; OutlinedTextField(voidReason,{voidReason=it},label={Text("Customer-facing void explanation · Required")},modifier=Modifier.fillMaxWidth()); TextButton(onClick={viewModel.voidRecord(report.recordId,voidReason,""){viewModel.loadFinalRecord(report.recordId)}},enabled=voidReason.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text("Void this record")} } else { Text("Ordinary customer Share is disabled for a voided original. Previously shared files cannot be revoked."); if (detail.report?.kind != "VOID_NOTICE") Button(onClick = { viewModel.generateReport(report.recordId) }, enabled = !generating, modifier = Modifier.fillMaxWidth().testTag("generate-void-notice")) { Text("Generate customer void notice") } else Text("Current VOID NOTICE is ready for customer handoff.") } }
    }
}

@Composable
internal fun ReportPreviewScreen(detail: FinalRecordDetail?, padding: PaddingValues, initialTextView: Boolean, historical: Boolean = false, viewModel: V16ServiceViewModel? = null) {
    val rendition = detail?.report
    if (detail == null || rendition == null || rendition.status !in setOf("READY", "MISSING")) return HonestPlaceholder(padding, "Report file is not ready")
    val context = LocalContext.current
    val file = remember(rendition.id, rendition.relativePath) { File(context.filesDir, rendition.relativePath) }
    val colors = LocalV16ServiceTokens.current
    var textView by rememberSaveable(rendition.id) { mutableStateOf(initialTextView || !file.isFile) }
    var pageIndex by rememberSaveable(rendition.id) { mutableStateOf(0) }
    val bitmapState = remember(rendition.id) { mutableStateOf<Bitmap?>(null) }
    val bitmap by bitmapState
    var pageCount by remember(rendition.id) { mutableStateOf(rendition.pageCount ?: 1) }
    var missing by remember(rendition.id) { mutableStateOf(!file.isFile) }
    var supersededShareAcknowledged by rememberSaveable(rendition.id) { mutableStateOf(false) }
    var transform by remember(rendition.id) { mutableStateOf(PdfPageZoomMath.reset()) }
    var viewportSize by remember(rendition.id) { mutableStateOf(IntSize.Zero) }
    var displayedPageSize by remember(rendition.id, pageIndex) { mutableStateOf(IntSize.Zero) }

    DisposableEffect(bitmapState) {
        onDispose { bitmapState.value?.takeUnless(Bitmap::isRecycled)?.recycle() }
    }

    val transformState = rememberTransformableState { scaleChange, panChange, _ ->
        val nextScale = PdfPageZoomMath.clampScale(transform.scale * scaleChange)
        val bounds = PdfPageZoomMath.translationBounds(
            displayedPageWidth = displayedPageSize.width.toFloat(),
            displayedPageHeight = displayedPageSize.height.toFloat(),
            viewportWidth = viewportSize.width.toFloat(),
            viewportHeight = viewportSize.height.toFloat(),
            scale = nextScale,
        )
        transform = PdfPageZoomMath.clampTranslation(
            offsetX = transform.offsetX + panChange.x,
            offsetY = transform.offsetY + panChange.y,
            bounds = bounds,
            scale = nextScale,
        )
    }

    LaunchedEffect(rendition.id, pageIndex, textView) {
        transform = PdfPageZoomMath.reset()
    }

    LaunchedEffect(displayedPageSize, viewportSize, transform.scale) {
        val bounds = PdfPageZoomMath.translationBounds(
            displayedPageSize.width.toFloat(), displayedPageSize.height.toFloat(),
            viewportSize.width.toFloat(), viewportSize.height.toFloat(), transform.scale,
        )
        transform = PdfPageZoomMath.clampTranslation(transform.offsetX, transform.offsetY, bounds, transform.scale)
    }

    LaunchedEffect(file, pageIndex) {
        missing = !file.isFile
        if (!file.isFile) {
            bitmapState.value?.takeUnless(Bitmap::isRecycled)?.recycle()
            bitmapState.value = null
            return@LaunchedEffect
        }
        val rendered = withContext(Dispatchers.IO) {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    val count = renderer.pageCount
                    val pageNumber = pageIndex.coerceIn(0, count - 1)
                    val pageBitmap = renderer.openPage(pageNumber).use { page ->
                        Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888).also {
                            page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                    count to pageBitmap
                }
            }
        }
        pageCount = rendered.first
        val previous = bitmapState.value
        bitmapState.value = rendered.second
        if (previous !== rendered.second) previous?.takeUnless(Bitmap::isRecycled)?.recycle()
    }
    Column(Modifier.padding(padding).fillMaxSize()) {
    Text(if (missing) "File missing · Structured report remains available" else "PDF file ready", modifier = Modifier.fillMaxWidth().background(if(missing) LocalV16ServiceColors.current.errorTint else LocalV16ServiceColors.current.confirmedTint).padding(10.dp))
    LazyColumn(Modifier.weight(1f).clipToBounds().onSizeChanged { viewportSize = it }.testTag("report-preview-list"), contentPadding = PaddingValues(0.dp, 8.dp, 0.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        item { Column(Modifier.fillMaxWidth().background(colors.surface)) { Column(Modifier.padding(horizontal = 12.dp)) { Text("${detail.public.visitReference} · Revision ${detail.public.revisionNumber} · PDF v${rendition.versionNumber} · ${rendition.id.take(8)}"); Text("Service ${detail.public.actualServiceDate} · ${if (missing) "Structured text only" else "Ready"}"); rendition.generatedAtEpochMillis?.let { Text("Generated ${Instant.ofEpochMilli(it)}") } }; V16ServiceContentTabs(listOf(false to "PDF view",true to "Text view"),textView,{ textView = it; transform = PdfPageZoomMath.reset() },Modifier.testTag("report-view-tabs")) } }
        if (textView) item { Box(Modifier.padding(horizontal = 12.dp)) { StructuredReportText(detail) } }
        else if (missing) item { Text("File missing", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 12.dp)) }
         else {
             item {
                 Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                     bitmap?.let { pageBitmap ->
                         Image(
                             pageBitmap.asImageBitmap(),
                             "Rendered customer report page ${pageIndex + 1}",
                             Modifier.fillMaxWidth()
                                 .onSizeChanged { displayedPageSize = it }
                                 .graphicsLayer(scaleX = transform.scale, scaleY = transform.scale, translationX = transform.offsetX, translationY = transform.offsetY)
                                 .transformable(transformState, canPan = { transform.scale > PdfPageTransform.MIN_PDF_PAGE_SCALE })
                                 .testTag("pdf-page-image"),
                             contentScale = ContentScale.Fit,
                         )
                     }
                 }
             }
             item {
                 Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                     OutlinedButton(onClick = { bitmapState.value?.takeUnless(Bitmap::isRecycled)?.recycle(); bitmapState.value = null; transform = PdfPageZoomMath.reset(); pageIndex-- }, enabled = pageIndex > 0, modifier = Modifier.testTag("pdf-page-previous")) { Text("Previous page") }
                     Text("Page ${pageIndex + 1} of $pageCount")
                     OutlinedButton(onClick = { bitmapState.value?.takeUnless(Bitmap::isRecycled)?.recycle(); bitmapState.value = null; transform = PdfPageZoomMath.reset(); pageIndex++ }, enabled = pageIndex + 1 < pageCount, modifier = Modifier.testTag("pdf-page-next")) { Text("Next page") }
                 }
             }
         }
        if (historical && !detail.voided) item { Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) { com.v16studio.v16service.ui.designsystem.V16ServiceCheckbox(supersededShareAcknowledged, { supersededShareAcknowledged = it }, contentDescription = "Acknowledge superseded historical report"); Text("I understand this is a superseded historical report") } }
        if (missing && historical && viewModel != null) item { Button(onClick = { viewModel.generateReport(detail.public.recordId, detail.public.revisionId) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) { Text("Recreate from this fixed revision") } }
     item { Column(Modifier.padding(horizontal = 12.dp)) { val eligible=reportShareEligible(file.isFile,detail.voided,rendition.kind,historical,supersededShareAcknowledged); fun share(email:String?){val uri=FileProvider.getUriForFile(context,"${context.packageName}.reports",file);val subject="Service report ${detail.public.visitReference} · ${detail.public.businessName}";val body="Attached is the service report for ${detail.public.customerName} · ${detail.public.siteName} dated ${detail.public.actualServiceDate}.\n\nGenerated with V16 Service";context.startActivity(Intent.createChooser(reportShareIntent(uri,email,subject,body),if(email==null)"Share customer service record" else "Send service report to office"))}; V16ServiceActionStack { Button(onClick={share(null)},enabled=eligible,modifier=Modifier.fillMaxWidth().testTag("share-pdf").semantics{contentDescription="Share PDF"}){V16ServiceIcon(V16ServiceIcons.Share,null,Modifier.size(V16ServiceUiTokens.Size.icon));Spacer(Modifier.width(V16ServiceUiTokens.Space.sm));Text(if(detail.voided&&rendition.kind!="VOID_NOTICE")"Share disabled for voided original" else "Share PDF")}; val office=context.getSharedPreferences(DISPATCH_PREFS,0).getString(OFFICE_EMAIL,"").orEmpty().trim(); if(office.isNotBlank()) OutlinedButton(onClick={share(office)},enabled=eligible,modifier=Modifier.fillMaxWidth().testTag("send-to-office")){V16ServiceIcon(V16ServiceIcons.Mail,null,Modifier.size(V16ServiceUiTokens.Size.icon));Spacer(Modifier.width(V16ServiceUiTokens.Space.sm));Text("Send to office")} }; if(detail.voided&&rendition.kind!="VOID_NOTICE") Text("Generate and share the current void notice. Previously shared files cannot be revoked.",style=MaterialTheme.typography.bodySmall) else if(historical) Text("Superseded report: confirm before customer handoff. Sharing does not prove delivery.",style=MaterialTheme.typography.bodySmall) } }
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
        Text("Technician ${technicianReportName(r.technicianName, r.technicianDesignation)}\n${r.businessContact}")
        Text("${r.customerReference.orEmpty()} · ${r.customerName}\n${r.siteReference.orEmpty()} · ${r.siteName}\n${r.siteAddress.orEmpty()}")
        r.publicNote?.let { Text("Record note: $it") }
        r.dispatch?.let {
            Text("Service coordination", style = MaterialTheme.typography.titleMedium)
            Text("Job ${it.managerReference ?: it.dispatchVisitId} · Generation ${it.generation}\nDocumented by ${it.documentingTechnicianName}\nTechnician reference ${it.documentingTechnicianId.take(8)}")
        }
        r.lines.forEach { line ->
            Text(v16ServiceSubjectLabel(line.subjectType, line.equipmentName, line.equipmentReference, line.equipmentDescription), style = MaterialTheme.typography.titleMedium)
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
