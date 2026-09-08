package com.v16studio.serviceloop.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import com.v16studio.serviceloop.ServiceLoopApplication
import com.v16studio.serviceloop.data.DispatchCustomer
import com.v16studio.serviceloop.data.DispatchEquipment
import com.v16studio.serviceloop.data.DispatchPackage
import com.v16studio.serviceloop.data.DispatchPackageCodec
import com.v16studio.serviceloop.data.DispatchPackageService
import com.v16studio.serviceloop.data.DispatchSite
import com.v16studio.serviceloop.data.DispatchVisit
import com.v16studio.serviceloop.data.DispatchWork
import com.v16studio.serviceloop.data.CustomerEntity
import com.v16studio.serviceloop.data.EquipmentEntity
import com.v16studio.serviceloop.data.SiteEntity
import com.v16studio.serviceloop.data.WORK_PACKAGE_MIME
import java.io.File
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal const val DISPATCH_PREFS = "dispatch_prototype"
internal const val COORDINATOR_ENABLED = "coordinator_enabled"
internal const val OFFICE_EMAIL = "office_email"

internal fun reportShareEligible(filePresent:Boolean,voided:Boolean,renditionKind:String,historical:Boolean,acknowledged:Boolean)=filePresent&&(!voided||renditionKind=="VOID_NOTICE")&&(!historical||voided||acknowledged)
internal fun reportShareIntent(uri:Uri,officeEmail:String?=null)=Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply{officeEmail?.takeIf{it.isNotBlank()}?.let{putExtra(Intent.EXTRA_EMAIL,arrayOf(it))}}
private fun InputStream.readBoundedPackage():ByteArray { val buffer=ByteArray(8192);val out=java.io.ByteArrayOutputStream();while(true){val count=read(buffer);if(count<0)break;require(out.size()+count<=DispatchPackageCodec.MAX_BYTES){"Work package exceeds 1 MiB"};out.write(buffer,0,count)};return out.toByteArray() }

@Composable
internal fun DispatchSettings(padding: PaddingValues) {
    val context=LocalContext.current; val prefs=remember{context.getSharedPreferences(DISPATCH_PREFS,0)}
    var enabled by rememberSaveable{mutableStateOf(prefs.getBoolean(COORDINATOR_ENABLED,false))}; var email by rememberSaveable{mutableStateOf(prefs.getString(OFFICE_EMAIL,"").orEmpty())}
    LazyColumn(Modifier.padding(padding),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Text("Experimental coordinator conveniences",style=MaterialTheme.typography.titleLarge); Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Column(Modifier.weight(1f)){Text("Coordinator tools");Text("Create work packages for other ServiceLoop users. This does not enable accounts, synchronization, or shared data.",style=MaterialTheme.typography.bodySmall)};Checkbox(enabled,{enabled=it;prefs.edit().putBoolean(COORDINATOR_ENABLED,it).apply()},Modifier.testTag("coordinator-tools-switch"))} }
        item { OutlinedTextField(email,{email=it},label={Text("Office report recipient")},supportingText={Text("Optional email convenience; sending still opens Android sharing.")},modifier=Modifier.fillMaxWidth().testTag("office-recipient")); Button(onClick={prefs.edit().putString(OFFICE_EMAIL,email.trim()).apply()},modifier=Modifier.fillMaxWidth()){Text("Save recipient")} }
    }
}

@Composable
internal fun CreateDispatchPackageScreen(padding:PaddingValues) {
    val context=LocalContext.current; val app=context.applicationContext as ServiceLoopApplication; val dao=app.container.database.serviceLoopDao()
    var customers by remember{mutableStateOf<List<CustomerEntity>>(emptyList())}; var sites by remember{mutableStateOf<List<SiteEntity>>(emptyList())}; var equipment by remember{mutableStateOf<List<EquipmentEntity>>(emptyList())}; var selectedSite by remember{mutableStateOf<SiteEntity?>(null)}; var selectedEquipment by remember{mutableStateOf<EquipmentEntity?>(null)}
    var sender by rememberSaveable{mutableStateOf("")};var recipient by rememberSaveable{mutableStateOf("")};var date by rememberSaveable{mutableStateOf(LocalDate.now().toString())};var task by rememberSaveable{mutableStateOf("")};var instructions by rememberSaveable{mutableStateOf("")};var drafts by remember{mutableStateOf<List<DispatchVisit>>(emptyList())};var error by remember{mutableStateOf<String?>(null)}
    LaunchedEffect(Unit){withContext(Dispatchers.IO){customers=dao.allCustomers().filter{it.state=="ACTIVE"};sites=dao.allSites().filter{it.state=="ACTIVE"};equipment=dao.allEquipment().filter{it.state=="ACTIVE"}}}
    LazyColumn(Modifier.padding(padding).testTag("dispatch-composer"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Text("Create work package",style=MaterialTheme.typography.titleLarge);Text("Work packages may contain customer and site information. Anyone who receives the file may be able to read it.",color=MaterialTheme.colorScheme.error);OutlinedTextField(sender,{sender=it},label={Text("Sender label · Required")},modifier=Modifier.fillMaxWidth().testTag("dispatch-sender"));OutlinedTextField(recipient,{recipient=it},label={Text("Optional recipient")},modifier=Modifier.fillMaxWidth()) }
        item { Text("Visit draft",style=MaterialTheme.typography.titleMedium);OutlinedTextField(date,{date=it},label={Text("Service date · YYYY-MM-DD")},modifier=Modifier.fillMaxWidth());Text("Choose site");sites.forEach{s->OutlinedButton(onClick={selectedSite=s;selectedEquipment=null},modifier=Modifier.fillMaxWidth().testTag("dispatch-site-${s.id}")){Text("${if(selectedSite?.id==s.id) "✓ " else ""}${s.reference} · ${s.name}")}} }
        item { Text("Choose equipment");equipment.filter{it.siteId==selectedSite?.id}.forEach{e->OutlinedButton(onClick={selectedEquipment=e},modifier=Modifier.fillMaxWidth().testTag("dispatch-equipment-${e.id}")){Text("${if(selectedEquipment?.id==e.id) "✓ " else ""}${e.reference} · ${e.name}")}};OutlinedTextField(task,{task=it},label={Text("Task name · Required")},modifier=Modifier.fillMaxWidth().testTag("dispatch-task"));OutlinedTextField(instructions,{instructions=it},label={Text("Instructions")},modifier=Modifier.fillMaxWidth()) }
        item { Button(onClick={runCatching{LocalDate.parse(date);val s=checkNotNull(selectedSite);val e=checkNotNull(selectedEquipment);drafts= drafts+DispatchVisit(UUID.randomUUID().toString(),null,date,null,s.reference,instructions.takeIf{it.isNotBlank()},listOf(DispatchWork(e.reference,task)));task="";instructions=""}.onFailure{error=it.message}.let{{}}},enabled=selectedSite!=null&&selectedEquipment!=null&&task.isNotBlank(),modifier=Modifier.fillMaxWidth().testTag("add-dispatch-visit")){Text("Add visit")};Text("${drafts.size} visit${if(drafts.size==1)"" else "s"} in package");error?.let{Text(it,color=MaterialTheme.colorScheme.error)} }
        items(drafts){v->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text("${v.serviceDate} · ${v.siteReference}");v.work.forEach{Text("${it.equipmentReference} · ${it.taskName}")}}}}
        item { Button(onClick={runCatching{val selectedSites=sites.filter{s->drafts.any{it.siteReference==s.reference}};val selectedEquipment=equipment.filter{e->drafts.any{v->v.work.any{it.equipmentReference==e.reference}}};val customerIds=selectedSites.map{it.customerId}.toSet();val selectedCustomers=customers.filter{it.id in customerIds};val pkg=DispatchPackage(UUID.randomUUID().toString(),Instant.now().toString(),sender.trim(),recipient.trim().takeIf{it.isNotBlank()},selectedCustomers.map{DispatchCustomer(it.reference,it.name)},selectedSites.map{s->DispatchSite(s.reference,selectedCustomers.single{it.id==s.customerId}.reference,s.name,s.address)},selectedEquipment.map{DispatchEquipment(it.reference,sites.single{s->s.id==it.siteId}.reference,it.name,it.technicianIdentifier,it.make,it.model,it.serialNumber)},drafts);val dir=File(context.cacheDir,"work-packages").apply{mkdirs();listFiles()?.filter{it.lastModified()<System.currentTimeMillis()-86_400_000}?.forEach(File::delete)};val file=File(dir,"serviceloop-${pkg.packageId.take(8)}.slwork");file.writeBytes(DispatchPackageCodec.encode(pkg));val uri=FileProvider.getUriForFile(context,"${context.packageName}.reports",file);context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(WORK_PACKAGE_MIME).putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),"Share ServiceLoop work package"))}.onFailure{error=it.message}.let{{}}},enabled=sender.isNotBlank()&&drafts.isNotEmpty(),modifier=Modifier.fillMaxWidth().testTag("share-work-package")){Text("Share work package")};Text("Android sharing hands off the file; ServiceLoop cannot confirm delivery.",style=MaterialTheme.typography.bodySmall) }
    }
}

@Composable
internal fun ImportDispatchPackageScreen(padding:PaddingValues,nav:NavHostController,viewModel:ServiceLoopViewModel) {
    val context=LocalContext.current;val service=remember{DispatchPackageService((context.applicationContext as ServiceLoopApplication).container.database)};val scope=rememberCoroutineScope();var preview by remember{mutableStateOf<com.v16studio.serviceloop.data.DispatchPreview?>(null)};var error by remember{mutableStateOf<String?>(null)};var importing by remember{mutableStateOf(false)};var result by remember{mutableStateOf<String?>(null)}
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null){importing=true;error=null;scope.launch{runCatching{withContext(Dispatchers.IO){val pkg=context.contentResolver.openInputStream(uri)?.use{DispatchPackageCodec.decode(it.readBoundedPackage())}?:error("Unable to read selected file");service.preview(pkg)}}.onSuccess{preview=it;importing=false}.onFailure{error=it.message;importing=false}}}}
    LazyColumn(Modifier.padding(padding).testTag("dispatch-import"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Text("Import work package",style=MaterialTheme.typography.titleLarge);Text("Preview first. Opening a file does not change saved data.");Text("Work packages may contain customer and site information. Anyone who receives the file may be able to read it.",color=MaterialTheme.colorScheme.error);Button(onClick={launcher.launch(arrayOf(WORK_PACKAGE_MIME,"application/json","application/octet-stream"))},modifier=Modifier.fillMaxWidth()){Text("Choose .slwork file")};if(importing)Text("Validating…");error?.let{Text(it,color=MaterialTheme.colorScheme.error)} }
        preview?.let{p->item{Text("From ${p.value.senderLabel}");Text("Package ${p.value.packageId}");Text("${p.value.visits.size} visits · ${p.value.visits.minOf{it.serviceDate}} – ${p.value.visits.maxOf{it.serviceDate}}")};items(p.directory){d->Text("${d.reference} · ${d.classification.name.lowercase().replace('_',' ')} · ${d.detail}")};items(p.visits){v->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text("${v.dispatchVisitId.take(8)} · ${v.classification.name.lowercase().replace('_',' ')}");Text("${v.planLinked} plan-linked · ${v.oneOff} one-off fallback");v.reasons.forEach{Text(it,color=MaterialTheme.colorScheme.tertiary)}}}};item{Button(onClick={importing=true;kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch{runCatching{service.import(p)}.onSuccess{r->withContext(Dispatchers.Main){result="Imported ${r.createdVisitIds.size}; already imported ${r.alreadyImportedVisitIds.size}";importing=false;viewModel.loadVisits()}}.onFailure{withContext(Dispatchers.Main){error=it.message;importing=false}}}},enabled=p.canImport&&!importing,modifier=Modifier.fillMaxWidth().testTag("import-dispatch-work")){Text("Import work")};result?.let{Text(it,color=MaterialTheme.colorScheme.primary);OutlinedButton(onClick={nav.navigate(workRoute(WorkTab.VISITS))},modifier=Modifier.fillMaxWidth()){Text("View Visits")}}}}
    }
}
