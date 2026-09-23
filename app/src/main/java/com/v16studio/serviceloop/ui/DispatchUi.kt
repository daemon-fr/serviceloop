package com.v16studio.serviceloop.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopCheckbox as Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopButtonAdapter as Button
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopOutlinedButtonAdapter as OutlinedButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopTextButtonAdapter as TextButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopTextFieldAdapter as OutlinedTextField
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopCardAdapter as Card
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopUiTokens
import com.v16studio.serviceloop.ui.designsystem.LocalServiceLoopTokens
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopChoiceGroup
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopSelectionOption
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopActionStack
import com.v16studio.serviceloop.ui.icons.ServiceLoopIcon
import com.v16studio.serviceloop.ui.icons.ServiceLoopIcons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import com.v16studio.serviceloop.ServiceLoopApplication
import com.v16studio.serviceloop.data.DispatchItemBindingEntity
import com.v16studio.serviceloop.data.DispatchPackageCodec
import com.v16studio.serviceloop.data.DispatchPackageService
import com.v16studio.serviceloop.data.DispatchEquipment
import com.v16studio.serviceloop.data.DispatchWork
import com.v16studio.serviceloop.data.outboxStatus
import com.v16studio.serviceloop.data.DispatchDuplicateDecision
import com.v16studio.serviceloop.data.HandoffReview
import com.v16studio.serviceloop.data.DispatchTechnicianSnapshot
import com.v16studio.serviceloop.data.DispatchVisitBindingEntity
import com.v16studio.serviceloop.data.TechnicianIdentity
import com.v16studio.serviceloop.data.TechnicianIdCodec
import com.v16studio.serviceloop.data.SERVICE_LOOP_SYNC_MIME
import com.v16studio.serviceloop.data.ServiceLoopSyncEnvelopeCodec
import com.v16studio.serviceloop.data.ServiceLoopPeerTrustStore
import com.v16studio.serviceloop.data.ServiceLoopTrustDecision
import com.v16studio.serviceloop.data.ServiceLoopSourceTrust
import com.v16studio.serviceloop.domain.VisitCancellationOrigin
import com.v16studio.serviceloop.domain.VisitDetail
import com.v16studio.serviceloop.domain.WorkSubjectType
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal const val DISPATCH_PREFS = "dispatch_prototype"
internal const val COORDINATOR_ENABLED = "coordinator_enabled"
internal const val TEAM_ROLE = "team_role"
internal const val OFFICE_EMAIL = "office_email"

internal fun reportShareEligible(filePresent:Boolean,voided:Boolean,renditionKind:String,historical:Boolean,acknowledged:Boolean)=filePresent&&(!voided||renditionKind=="VOID_NOTICE")&&(!historical||voided||acknowledged)
internal fun reportShareIntent(uri:Uri,officeEmail:String?=null,subject:String?=null,body:String?=null)=Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply{officeEmail?.takeIf{it.isNotBlank()}?.let{putExtra(Intent.EXTRA_EMAIL,arrayOf(it))};subject?.let{putExtra(Intent.EXTRA_SUBJECT,it)};body?.let{putExtra(Intent.EXTRA_TEXT,it)}}
internal fun canceledDispatchVisitMessage(origin:String?):String=when(VisitCancellationOrigin.fromCode(origin)){
    VisitCancellationOrigin.LOCAL->"Canceled on this device."
    VisitCancellationOrigin.COORDINATOR->"Canceled by Coordinator."
    VisitCancellationOrigin.ASSIGNMENT_REMOVAL->"Canceled because this Technician assignment was removed."
    null->"Canceled."
}

private fun service(context:Context)=DispatchPackageService((context.applicationContext as ServiceLoopApplication).container.database,context.filesDir)
private fun java.io.InputStream.readBounded(limit:Int):ByteArray{val out=ByteArrayOutputStream();val b=ByteArray(8192);while(true){val n=read(b);if(n<0)break;require(out.size()+n<=limit){"Selected file is too large"};out.write(b,0,n)};return out.toByteArray()}
internal fun shareFile(context:Context,dirName:String,fileName:String,mime:String,bytes:ByteArray,title:String,subject:String?=null,body:String?=null){val dir=File(context.cacheDir,dirName).apply{mkdirs();listFiles()?.filter{it.lastModified()<System.currentTimeMillis()-86_400_000}?.forEach(File::delete)};val file=File(dir,fileName).apply{writeBytes(bytes)};val uri=FileProvider.getUriForFile(context,"${context.packageName}.reports",file);context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply{subject?.let{putExtra(Intent.EXTRA_SUBJECT,it)};body?.let{putExtra(Intent.EXTRA_TEXT,it)}},title))}
internal fun shareExistingFile(context:Context,file:File,mime:String,title:String,subject:String?=null,body:String?=null){val uri=FileProvider.getUriForFile(context,"${context.packageName}.reports",file);context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply{subject?.let{putExtra(Intent.EXTRA_SUBJECT,it)};body?.let{putExtra(Intent.EXTRA_TEXT,it)}},title))}

@Composable
internal fun DispatchSettings(padding: PaddingValues, nav: NavHostController) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(DISPATCH_PREFS, 0) }
    var role by rememberSaveable { mutableStateOf(context.teamRole()) }
    val choices = TEAM_ROLE_OPTIONS
    LazyColumn(
        Modifier.padding(padding).testTag("team-role-settings"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            val roleExplanation = buildString {
                append("Roles only control local file-based workflows. No account, synchronization, or shared database is created.")
                if (role.workspaceCapabilities.canUseCoordinatorTools) append(" Coordinator tools are available from Home.")
            }
            Column(verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Layout.bodyGap)) {
                Text("What is your role?", style = MaterialTheme.typography.titleLarge)
                Column(verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.sm)) {
                    choices.forEach { option ->
                        TeamRoleChoice(
                            option = option,
                            selected = role == option.role,
                            onSelected = { role = option.role; context.setTeamRole(option.role) },
                        )
                    }
                }
                Text(roleExplanation, style = ServiceLoopUiTokens.Type.supporting, modifier = Modifier.testTag("team-role-helper"))
            }
        }
    }
}

@Composable
private fun TeamRoleChoice(
    option: TeamRoleOption,
    selected: Boolean,
    onSelected: () -> Unit,
) {
    val colors = LocalServiceLoopTokens.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ServiceLoopUiTokens.Size.touchMin)
            .selectable(selected = selected, enabled = true, role = Role.RadioButton, onClick = onSelected)
            .testTag("team-role-${option.role.name}"),
        shape = RoundedCornerShape(ServiceLoopUiTokens.Radius.field),
        color = if (selected) colors.selection else colors.surface,
        border = BorderStroke(ServiceLoopUiTokens.Stroke.outline, if (selected) colors.selectionOutline else colors.outlineControl),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = ServiceLoopUiTokens.Space.md, vertical = ServiceLoopUiTokens.Space.sm),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Box(Modifier.size(32.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                if (selected) ServiceLoopIcon(ServiceLoopIcons.CheckFat, null, Modifier.size(24.dp), tint = colors.action)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.xs)) {
            Text(
                option.title,
                style = ServiceLoopUiTokens.Type.itemTitle,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                modifier = Modifier.testTag("team-role-${option.role.name}-title"),
            )
            Text(
                option.description,
                style = ServiceLoopUiTokens.Type.supporting,
                color = colors.textSecondary,
                modifier = Modifier.testTag("team-role-${option.role.name}-description"),
            )
            }
        }
    }
}

@Composable
internal fun TechnicianIdentityContent(modifier: Modifier = Modifier, showId: Boolean = true) {
    val context = LocalContext.current
    val svc = remember { service(context) }
    val scope = rememberCoroutineScope()
    var value by remember { mutableStateOf<TechnicianIdentity?>(null) }
    var designation by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) { svc.identity() }
        value = loaded
        designation = loaded.designation.orEmpty()
    }
    Column(modifier.fillMaxWidth().testTag("technician-identity"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showId) {
            Text("Technician identity", style = MaterialTheme.typography.titleLarge)
            Text("Your Technician ID identifies this ServiceLoop installation in dispatch packages. It is not an account or password.")
            Text("Technician ID", style = MaterialTheme.typography.labelLarge)
            Text(TechnicianIdCodec.display(value?.technicianId.orEmpty()), style = ServiceLoopUiTokens.IdentifierStyle, modifier = Modifier.testTag("technician-id-value"))
        }
        OutlinedTextField(designation, { designation = it }, label = { Text("Team designation or badge (optional)") }, modifier = Modifier.fillMaxWidth().testTag("technician-designation"))
        ServiceLoopActionStack {
            Button(
                {
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { svc.updateIdentityDesignation(designation) } }
                            .onSuccess { value = it }
                            .onFailure { error = it.message }
                    }
                },
                enabled = value != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
            if (showId) OutlinedButton(
                { value?.let { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("ServiceLoop Technician ID", it.technicianId)) } },
                Modifier.fillMaxWidth(),
            ) {
                ServiceLoopIcon(ServiceLoopIcons.Copy, null, Modifier.size(ServiceLoopUiTokens.Size.icon))
                Spacer(Modifier.width(ServiceLoopUiTokens.Space.sm))
                Text("Copy ID")
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

private fun dispatchImportSubjectLabel(work:DispatchWork,equipment:DispatchEquipment?):String=when{
    work.subjectType==WorkSubjectType.SITE->"Site"
    equipment!=null->"${equipment.reference} · ${equipment.name}"
    !work.equipmentDescription.isNullOrBlank()->work.equipmentDescription.trim()
    else->"Equipment not specified"
}

@Composable internal fun DispatchTechniciansScreen(padding:PaddingValues){
    val context=LocalContext.current;val svc=remember{service(context)};val scope=rememberCoroutineScope()
    var techs by remember{mutableStateOf(emptyList<com.v16studio.serviceloop.data.DispatchTechnicianEntity>())};var id by rememberSaveable{mutableStateOf("")};var name by rememberSaveable{mutableStateOf("")};var designation by rememberSaveable{mutableStateOf("")};var error by remember{mutableStateOf<String?>(null)};var rename by remember{mutableStateOf<com.v16studio.serviceloop.data.TechnicianRenameReview?>(null)};var edit by remember{mutableStateOf<com.v16studio.serviceloop.data.DispatchTechnicianEntity?>(null)}
    fun reload(){scope.launch{techs=withContext(Dispatchers.IO){svc.technicians()}}}
    fun submit(value:TechnicianIdentity,manual:Boolean=false){scope.launch{runCatching{val normalized=if(manual)value.copy(technicianId=com.v16studio.serviceloop.data.TechnicianIdCodec.normalize(value.technicianId)?:throw IllegalArgumentException("Technician ID is not a valid ServiceLoop Technician ID."))else value;withContext(Dispatchers.IO){svc.technicianRenameReview(normalized)} to normalized}.onSuccess{(review,normalized)->if(review!=null)rename=review else scope.launch{withContext(Dispatchers.IO){if(manual)svc.importTechnicianManually(normalized) else svc.importTechnician(normalized)};id="";name="";designation="";reload()}}.onFailure{error=it.message}}}
    LaunchedEffect(Unit){reload()}
    rename?.let{review->AlertDialog(modifier=Modifier.testTag("technician-rename-dialog"),onDismissRequest={rename=null},title={Text("Technician ID already exists")},text={Text("Old name: ${review.existingName}${review.existingDesignation?.let{" · $it"}.orEmpty()}\nIncoming name: ${review.incoming.name}${review.incoming.designation?.let{" · $it"}.orEmpty()}")},dismissButton={TextButton({rename=null},Modifier.testTag("technician-rename-keep")){Text("Keep existing")}},confirmButton={Button({scope.launch{withContext(Dispatchers.IO){svc.importTechnician(review.incoming,true)};rename=null;id="";name="";designation="";reload()}},Modifier.testTag("technician-rename-update")){Text("Update technician")}})}
    edit?.let { tech ->
        var editName by remember(tech.technicianId) { mutableStateOf(tech.displayName) }
        var editDesignation by remember(tech.technicianId) { mutableStateOf(tech.designation.orEmpty()) }
        var editNotes by remember(tech.technicianId) { mutableStateOf(tech.notes.orEmpty()) }
        AlertDialog(onDismissRequest={edit=null}, title={Text("Edit technician")}, text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Technician ID: ${tech.technicianId}",style=ServiceLoopUiTokens.Type.identifier);OutlinedTextField(editName,{editName=it},label={Text("Actual name")});OutlinedTextField(editDesignation,{editDesignation=it},label={Text("Team designation or badge (optional)")});OutlinedTextField(editNotes,{editNotes=it},label={Text("Notes / contact info (optional)")})}}, dismissButton={TextButton({edit=null}){Text("Cancel")}}, confirmButton={Button({scope.launch{runCatching{withContext(Dispatchers.IO){svc.updateTechnicianMetadata(tech.technicianId,editName,editDesignation,editNotes)}}.onSuccess{edit=null;reload()}.onFailure{error=it.message}}}){Text("Save")}})
    }
    LazyColumn(Modifier.padding(padding).testTag("dispatch-technicians"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{Text("Technicians",style=MaterialTheme.typography.headlineSmall);Text("Technician IDs identify local directory entries; they are not accounts or passwords.");OutlinedTextField(id,{id=it},label={Text("Technician ID")},modifier=Modifier.fillMaxWidth().testTag("manual-technician-id"));OutlinedTextField(name,{name=it},label={Text("Actual name")},modifier=Modifier.fillMaxWidth());OutlinedTextField(designation,{designation=it},label={Text("Team designation or badge (optional)")},modifier=Modifier.fillMaxWidth());OutlinedButton({submit(TechnicianIdentity(id,name.trim(),designation.trim().takeIf{it.isNotEmpty()}),true)},enabled=id.isNotBlank()&&name.isNotBlank(),modifier=Modifier.fillMaxWidth().testTag("manual-technician-add")){Text("Add manually")};error?.let{Text(it,color=MaterialTheme.colorScheme.error)}};items(techs){tech->Column(Modifier.fillMaxWidth().clickable{edit=tech}.padding(vertical=8.dp)){Text(tech.displayName,style=MaterialTheme.typography.titleMedium);tech.designation?.takeIf{it.isNotBlank()}?.let{Text(it,style=MaterialTheme.typography.bodyMedium)};tech.notes?.takeIf{it.isNotBlank()}?.let{Text(it,style=MaterialTheme.typography.bodySmall)};Text(tech.technicianId,style=MaterialTheme.typography.bodySmall)}}}
}
@Composable internal fun DispatchTeamsScreen(padding:PaddingValues){val context=LocalContext.current;val svc=remember{service(context)};val scope=rememberCoroutineScope();var teams by remember{mutableStateOf(emptyList<com.v16studio.serviceloop.data.DispatchTeamDetail>())};var techs by remember{mutableStateOf(emptyList<com.v16studio.serviceloop.data.DispatchTechnicianEntity>())};var name by rememberSaveable{mutableStateOf("")};fun reload(){scope.launch{teams=withContext(Dispatchers.IO){svc.teams()};techs=withContext(Dispatchers.IO){svc.technicians()}}};LaunchedEffect(Unit){reload()};LazyColumn(Modifier.padding(padding).testTag("dispatch-teams"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){item{Text("Teams and leaders",style=MaterialTheme.typography.headlineSmall);OutlinedTextField(name,{name=it},label={Text("New team name")},modifier=Modifier.fillMaxWidth());Button({scope.launch{withContext(Dispatchers.IO){svc.createTeam(name)};name="";reload()}},enabled=name.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text("Create team")}};items(teams){team->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp), horizontalAlignment=androidx.compose.ui.Alignment.CenterHorizontally){Text(team.team.name,style=MaterialTheme.typography.titleLarge, modifier=Modifier.fillMaxWidth(), textAlign=androidx.compose.ui.text.style.TextAlign.Center);Spacer(Modifier.height(8.dp));Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Text("Member",Modifier.padding(horizontal=4.dp));Text("Technician",Modifier.weight(1f));Text("Leader",Modifier.padding(horizontal=4.dp))};techs.forEach{t->val member=team.members.find{it.first.technicianId==t.technicianId};Row(Modifier.fillMaxWidth().heightIn(min=ServiceLoopUiTokens.Size.touchMin),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Checkbox(member!=null,{checked->scope.launch{withContext(Dispatchers.IO){svc.setTeamMember(team.team.id,t.technicianId,checked,false)};reload()}},Modifier.semantics{contentDescription="Member — ${t.displayName}"});Text(t.displayName,Modifier.weight(1f));Checkbox(member?.second==true,{leader->scope.launch{withContext(Dispatchers.IO){svc.setTeamMember(team.team.id,t.technicianId,true,leader)};reload()}},Modifier.semantics{contentDescription="Leader — ${t.displayName}"},enabled=member!=null)}}}}}}}

@Composable internal fun ImportDispatchPackageScreen(padding:PaddingValues,nav:NavHostController,viewModel:ServiceLoopViewModel,incomingUri:String?=null,openPickerImmediately:Boolean=false){
    val context=LocalContext.current;val svc=remember{service(context)};val scope=rememberCoroutineScope();val database=remember{(context.applicationContext as ServiceLoopApplication).container.database};val trustStore=remember{ServiceLoopPeerTrustStore(database)};var preview by remember{mutableStateOf<com.v16studio.serviceloop.data.DispatchPreview?>(null)};var exporterId by remember{mutableStateOf<String?>(null)};var trust by remember{mutableStateOf<ServiceLoopTrustDecision?>(null)};var trustedName by remember{mutableStateOf("")};var confirmTrust by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)};var busy by remember{mutableStateOf(false)};var result by remember{mutableStateOf<String?>(null)}
    fun load(uri:Uri){scope.launch{busy=true;error=null;runCatching{withContext(Dispatchers.IO){val bytes=context.contentResolver.openInputStream(uri)?.use{x->x.readBounded(ServiceLoopSyncEnvelopeCodec.MAX_PACKAGE_BYTES)}?:throw IllegalArgumentException("Could not read this ServiceLoop file");val envelope=ServiceLoopSyncEnvelopeCodec.decode(bytes);require(envelope.manifest.purpose=="WORK_ASSIGNMENT"){"Choose a work-assignment ServiceLoop file"};val decision=trustStore.assess(envelope.manifest.exporterId);val value=svc.preview(ServiceLoopSyncEnvelopeCodec.unwrapWorkAssignment(bytes));Triple(value,envelope.manifest.exporterId,decision)}}.onSuccess{(value,id,decision)->preview=value;exporterId=id;trust=decision;trustedName=decision.friendlyName.orEmpty()}.onFailure{if(it is CancellationException)throw it else error=it.message};busy=false}}
    val open=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->uri?.let(::load)}
    var pickerLaunched by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(openPickerImmediately) { if (openPickerImmediately && !pickerLaunched) { pickerLaunched = true; open.launch(arrayOf(SERVICE_LOOP_SYNC_MIME,"application/zip","application/octet-stream")) } }
    LaunchedEffect(incomingUri){incomingUri?.let(Uri::parse)?.let(::load)}
    if(confirmTrust)AlertDialog(onDismissRequest={confirmTrust=false},title={Text("Trust this ServiceLoop ID?")},text={Column{Text("${trustedName.trim()} will be able to send files that can be imported on this device. This identifies the source within ServiceLoop; it does not verify who possesses the ID.");Spacer(Modifier.height(8.dp));com.v16studio.serviceloop.ui.designsystem.ServiceLoopTextFieldAdapter(trustedName,{trustedName=it},label={Text("Friendly name")},modifier=Modifier.fillMaxWidth().testTag("dispatch-trusted-source-name"))}},dismissButton={TextButton({confirmTrust=false}){Text("Cancel")}},confirmButton={Button({scope.launch{runCatching{trustStore.add(trust?.sourceId.orEmpty(),trustedName)}.onSuccess{saved->trust=ServiceLoopTrustDecision(saved.peerId,saved.name,ServiceLoopSourceTrust.TRUSTED);confirmTrust=false}.onFailure{error=it.message}}},Modifier.testTag("dispatch-confirm-trusted-source")){Text("Trust ID")}})
    Column(Modifier.padding(padding).testTag("dispatch-import")) {
    preview?.takeIf { p -> p.visits.isNotEmpty() && p.visits.all { it.classification == com.v16studio.serviceloop.data.DispatchVisitClassification.NOT_ASSIGNED } }?.let { p -> Card(Modifier.fillMaxWidth().padding(16.dp, 8.dp, 16.dp, 0.dp).testTag("dispatch-zero-applicability-warning")){Column(Modifier.padding(12.dp)){Text("No work in this package is assigned to this Technician.",style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.error);Text("You may not have been included by your coordinator, or the coordinator may have the wrong Technician ID.");Text("${p.identity.name}\n${p.identity.technicianId}")}} }
    LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{Text("Import",style=MaterialTheme.typography.headlineSmall);Text("Opening only validates and previews. ServiceLoop files are readable and not cryptographically signed.");Button({open.launch(arrayOf(SERVICE_LOOP_SYNC_MIME,"application/zip","application/octet-stream"))},Modifier.fillMaxWidth()){Text("Choose ServiceLoop file")};if(busy)Text("Validating…");error?.let{Text(it,color=MaterialTheme.colorScheme.error)}}
        trust?.let{decision->item{Column(verticalArrangement=Arrangement.spacedBy(6.dp),modifier=Modifier.fillMaxWidth().testTag("dispatch-import-source")){Text("Source",style=MaterialTheme.typography.titleMedium);when(decision.trust){ServiceLoopSourceTrust.TRUSTED->{Text(decision.friendlyName?:"Trusted source");Text(decision.sourceId.orEmpty());Text("Trusted",color=MaterialTheme.colorScheme.primary)};ServiceLoopSourceTrust.NOT_TRUSTED->{Text(decision.sourceId.orEmpty());Text("Not trusted",color=MaterialTheme.colorScheme.error);OutlinedTextField(trustedName,{trustedName=it},label={Text("Friendly name")},modifier=Modifier.fillMaxWidth());OutlinedButton({confirmTrust=true},Modifier.fillMaxWidth().testTag("dispatch-add-trusted-source"),enabled=trustedName.trim().isNotEmpty()&&!busy){Text("Add to trusted IDs")}};ServiceLoopSourceTrust.OLDER_FILE->{Text("Older ServiceLoop file");Text("This file has no source ID. Re-export it from a newer ServiceLoop version.")}}}}}
        preview?.let{p->val counts=p.visits.groupingBy{it.classification}.eachCount();val applicable=p.visits.count{it.classification!=com.v16studio.serviceloop.data.DispatchVisitClassification.NOT_ASSIGNED};val blocked=p.visits.count{it.classification in setOf(com.v16studio.serviceloop.data.DispatchVisitClassification.CONFLICT,com.v16studio.serviceloop.data.DispatchVisitClassification.LOCAL_CONFLICT,com.v16studio.serviceloop.data.DispatchVisitClassification.UPDATE_BLOCKED)||p.directoryBlockers(it).isNotEmpty()};item{Text("${p.value.visits.size} Visits in package",style=MaterialTheme.typography.titleLarge);Text("Assigned/applicable to you: $applicable\n${counts[com.v16studio.serviceloop.data.DispatchVisitClassification.NEW_VISIT]?:0} new · ${counts[com.v16studio.serviceloop.data.DispatchVisitClassification.UPDATE]?:0} updates · ${counts[com.v16studio.serviceloop.data.DispatchVisitClassification.CANCELED]?:0} coordinator canceled · ${counts[com.v16studio.serviceloop.data.DispatchVisitClassification.ASSIGNMENT_REMOVED]?:0} assignment removed\n${counts[com.v16studio.serviceloop.data.DispatchVisitClassification.ALREADY_CURRENT]?:0} already current · $blocked require review · ${counts[com.v16studio.serviceloop.data.DispatchVisitClassification.NOT_ASSIGNED]?:0} not assigned to you",Modifier.testTag("dispatch-import-summary"));Text("From ${p.value.senderLabel}\nPackage ${p.value.packageId}");Text("This Technician: ${p.identity.name}\n${p.identity.technicianId}")};items(p.directory){line->Column{Text("${line.reference} · ${line.classification.name.replace('_',' ').lowercase()} · ${line.detail}");if(line.classification==com.v16studio.serviceloop.data.DispatchClassification.POSSIBLE_DUPLICATE){ServiceLoopChoiceGroup<DispatchDuplicateDecision?>(listOf(DispatchDuplicateDecision.CREATE_SEPARATE to "Create separate",DispatchDuplicateDecision.SKIP_BRANCH to "Skip branch"),line.duplicateDecision,{decision->if(decision!=null)preview=svc.resolveDuplicate(p,line.reference,decision)},testTagPrefix="duplicate-${line.reference}")}}};items(p.visits){v->Card(Modifier.fillMaxWidth().testTag("dispatch-preview-${v.dispatchVisitId}")){Column(Modifier.padding(12.dp)){Text("${v.dispatchVisitId.take(8)} · generation ${v.generation}");Text(v.classification.name.replace('_',' ').lowercase());p.directoryBlockers(v).forEach{Text("Requires review: ${it.reference} · ${it.detail}",color=MaterialTheme.colorScheme.error)};v.changes.forEach{Text("${it.label}: ${it.before} → ${it.after}")};v.items.forEach{itemPreview->val work=p.value.visits.single{it.dispatchVisitId==v.dispatchVisitId}.work.single{it.dispatchItemId==itemPreview.dispatchItemId};Text("${dispatchImportSubjectLabel(work,p.value.equipment.find{it.reference==work.equipmentReference})} · ${itemPreview.taskName} · ${itemPreview.localRole.replace('_',' ').lowercase()} · ${itemPreview.change.replace('_',' ').lowercase()}")};v.reasons.forEach{Text(it,color=MaterialTheme.colorScheme.tertiary)}}}};item{Button({scope.launch{busy=true;runCatching{withContext(Dispatchers.IO){svc.import(p,exporterId)}}.onSuccess{r->result="Applied ${r.createdVisitIds.size+r.updatedVisitIds.size} Visits\n${r.createdVisitIds.size} created · ${r.updatedVisitIds.size-r.withdrawnVisitIds.size} updated · ${r.withdrawnVisitIds.size} assignment removed · ${r.canceledVisitIds.size} coordinator canceled\n${p.visits.size-r.createdVisitIds.size-r.updatedVisitIds.size} not applied";viewModel.loadVisits()}.onFailure{if(it is CancellationException)throw it else error=it.message};busy=false}},enabled=p.canImport&&trust?.canImport==true&&!busy,modifier=Modifier.fillMaxWidth().testTag("import-dispatch-work")){Text("Apply ${p.safeActionableVisits.size} safe Visits")};result?.let{Text(it,Modifier.testTag("dispatch-import-result"));OutlinedButton({nav.navigate(workRoute(WorkTab.VISITS))},Modifier.fillMaxWidth()){Text("View Visits")}}}}
    }}
}

@Composable internal fun DispatchVisitPanel(detail:VisitDetail,modifier:Modifier=Modifier){
    val context=LocalContext.current;val svc=remember{service(context)};val scope=rememberCoroutineScope();var data by remember(detail.id){mutableStateOf<Pair<DispatchVisitBindingEntity,List<DispatchItemBindingEntity>>?>(null)};var message by remember{mutableStateOf<String?>(null)};var handoffItem by remember{mutableStateOf<DispatchItemBindingEntity?>(null)};var review by remember{mutableStateOf<HandoffReview?>(null)};var target by remember{mutableStateOf<DispatchTechnicianSnapshot?>(null)}
    fun reload(){scope.launch{data=withContext(Dispatchers.IO){svc.dispatchDetail(detail.id)}}};LaunchedEffect(detail.id){reload()}
    if(handoffItem!=null&&review!=null){val r=review!!;AlertDialog(modifier=Modifier.testTag("handoff-picker"),onDismissRequest={handoffItem=null;review=null;target=null},title={Text("Hand off documentation")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Choose an eligible recipient. This records your local documentation handoff. ServiceLoop cannot confirm that the selected Technician accepted it.");r.candidates.forEach{candidate->ServiceLoopSelectionOption(target?.technicianId==candidate.technicianId,{target=candidate},candidate.name,Modifier.testTag("handoff-recipient-${candidate.technicianId}"))};if(r.hasSubstantiveDraft)Text("Your local answers, notes, parts, and draft photographs will be permanently removed from this draft.",color=MaterialTheme.colorScheme.error)}},dismissButton={TextButton({handoffItem=null;review=null;target=null}){Text("Cancel")}},confirmButton={Button({val selected=target?:return@Button;val item=handoffItem?:return@Button;scope.launch{runCatching{withContext(Dispatchers.IO){svc.handoff(detail.id,item.dispatchItemId,selected,r.hasSubstantiveDraft)}}.onSuccess{message="Documentation handed off locally to ${selected.name}; acceptance is not implied"}.onFailure{if(it is CancellationException)throw it else message=it.message};handoffItem=null;review=null;target=null;reload()}},enabled=target!=null,modifier=Modifier.testTag(if(r.hasSubstantiveDraft)"handoff-discard-confirm" else "handoff-confirm")){Text(if(r.hasSubstantiveDraft)"Discard my local documentation and hand off to ${target?.name.orEmpty()}" else "Hand off to ${target?.name.orEmpty()}")}})}
    data?.let{(binding,items)->Card(modifier.fillMaxWidth().testTag("dispatched-visit")){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Dispatched · generation ${binding.appliedGeneration}",style=MaterialTheme.typography.titleMedium);binding.managerReference?.let{Text("Job $it")};binding.instructionsSnapshot?.let{Text("Dispatch instructions\n$it",Modifier.testTag("dispatch-instructions"))};if(detail.state=="COMPLETED")Text("Completed. This records only your local visit lifecycle; it does not mean a final record, PDF, central acceptance, or report was created.");if(detail.state=="CANCELED")Text(canceledDispatchVisitMessage(detail.cancellationOrigin)+" Local evidence and any completed record are preserved.",Modifier.testTag("dispatch-canceled-copy"));items.forEach{i->Text("${i.taskNameSnapshot}\n${if(i.assignmentMeaning=="EVERYONE")"Assigned to everyone" else "Assigned technicians preserved"} · ${i.localRole.replace('_',' ').lowercase()} · ${i.documentationDisposition.replace('_',' ').lowercase()}");if(detail.state=="WORKING"){if(i.documentationDisposition in setOf("PENDING","LEADER_OBSERVE"))Button({scope.launch{message=withContext(Dispatchers.IO){svc.documentLocally(detail.id,i.dispatchItemId)};reload()}},Modifier.fillMaxWidth()){Text("Document this item")};if(i.localRole=="ASSIGNED"&&i.documentationDisposition!="DEFERRED"){OutlinedButton({scope.launch{runCatching{withContext(Dispatchers.IO){svc.handoffReview(detail.id,i.dispatchItemId)}}.onSuccess{r->handoffItem=i;review=r}.onFailure{message=it.message}}},Modifier.fillMaxWidth().testTag("handoff-open-${i.dispatchItemId}")){Text("Hand off documentation…")}};if(i.documentationDisposition=="DEFERRED")OutlinedButton({scope.launch{withContext(Dispatchers.IO){svc.undoHandoff(detail.id,i.dispatchItemId)};reload()}},Modifier.fillMaxWidth()){Text("Document this item myself")}}};if(detail.state=="WORKING"&&items.filter{it.localRole=="ASSIGNED"}.all{it.documentationDisposition=="DEFERRED"}&&items.none{it.documentationDisposition=="DOCUMENT_LOCAL"})Button({scope.launch{withContext(Dispatchers.IO){svc.finishInvolvement(detail.id)};reload()}},Modifier.fillMaxWidth()){Text("Complete visit")};message?.let{Text(it,color=MaterialTheme.colorScheme.primary)}}}}
}
