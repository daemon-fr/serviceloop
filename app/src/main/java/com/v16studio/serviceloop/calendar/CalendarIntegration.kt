package com.v16studio.serviceloop.calendar

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.v16studio.serviceloop.data.ServiceLoopDatabase
import com.v16studio.serviceloop.data.WorkingVisitEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

data class WritableCalendar(val id: Long, val label: String, val accountLabel: String?)
data class ManagedCalendarEvent(val title: String, val location: String?, val description: String, val startMillis: Long, val endMillis: Long, val zoneId: String)
enum class CalendarLinkState { SYNCED, MISSING, DELETE_PENDING }
data class CalendarLink(val visitId: String, val calendarId: Long, val eventId: Long, val state: CalendarLinkState, val fingerprint: String, val lastConfirmedAt: Long)
data class CalendarDeviceState(
    val datasetId: String,
    val enabled: Boolean = false,
    val selectedCalendarId: Long? = null,
    val selectedCalendarLabel: String? = null,
    val links: Map<String, CalendarLink> = emptyMap(),
    val suppressedVisitIds: Set<String> = emptySet(),
    val storeProblem: Boolean = false,
)
data class CalendarRuntimeState(
    val enabled: Boolean = false,
    val hasPermissions: Boolean = false,
    val selectedCalendarLabel: String? = null,
    val linkedFutureCount: Int = 0,
    val problemCount: Int = 0,
    val writableCalendars: List<WritableCalendar> = emptyList(),
    val needsAttention: Boolean = false,
    val selectedCalendarUnavailable: Boolean = false,
) {
    val label: String get() = when {
        needsAttention -> "Needs attention"
        !enabled -> "Off"
        !hasPermissions -> "Permission needed"
        selectedCalendarLabel == null -> "Choose calendar"
        selectedCalendarUnavailable -> "Selected calendar unavailable"
        else -> "Active"
    }
}
data class VisitCalendarState(val label: String, val action: String? = null, val eventId: Long? = null)

interface CalendarGateway {
    fun hasPermissions(): Boolean
    fun writableCalendars(): List<WritableCalendar>
    fun eventExists(calendarId: Long, eventId: Long): Boolean
    fun insert(calendarId: Long, event: ManagedCalendarEvent): Long
    fun update(calendarId: Long, eventId: Long, event: ManagedCalendarEvent): Boolean
    fun delete(calendarId: Long, eventId: Long): Boolean
}

class AndroidCalendarGateway(private val context: Context, private val resolver: ContentResolver = context.contentResolver) : CalendarGateway {
    override fun hasPermissions() = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
    private fun requirePermissions() = check(hasPermissions()) { "Calendar permission is required" }
    override fun writableCalendars(): List<WritableCalendar> {
        requirePermissions()
        val projection = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.VISIBLE)
        return resolver.query(CalendarContract.Calendars.CONTENT_URI, projection, "${CalendarContract.Calendars.VISIBLE}=1 AND ${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL}>=?", arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()), null)?.use { cursor ->
            buildList { while (cursor.moveToNext()) add(WritableCalendar(cursor.getLong(0), cursor.getString(1).orEmpty().ifBlank { "Calendar ${cursor.getLong(0)}" }, cursor.getString(2)?.takeIf(String::isNotBlank))) }
        }.orEmpty()
    }
    override fun eventExists(calendarId: Long, eventId: Long): Boolean {
        requirePermissions(); val uri=ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,eventId)
        return resolver.query(uri,arrayOf(CalendarContract.Events._ID),"${CalendarContract.Events.CALENDAR_ID}=?",arrayOf(calendarId.toString()),null)?.use{it.moveToFirst()}==true
    }
    override fun insert(calendarId: Long, event: ManagedCalendarEvent): Long {
        requirePermissions(); val uri=resolver.insert(CalendarContract.Events.CONTENT_URI,event.values(calendarId))?:error("Calendar provider did not create the event")
        return ContentUris.parseId(uri)
    }
    override fun update(calendarId: Long, eventId: Long, event: ManagedCalendarEvent): Boolean {
        requirePermissions(); val uri=ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,eventId)
        return resolver.update(uri,event.values(calendarId),"${CalendarContract.Events.CALENDAR_ID}=?",arrayOf(calendarId.toString()))==1
    }
    override fun delete(calendarId: Long, eventId: Long): Boolean {
        requirePermissions(); val uri=ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,eventId)
        return resolver.delete(uri,"${CalendarContract.Events.CALENDAR_ID}=?",arrayOf(calendarId.toString()))==1
    }
    private fun ManagedCalendarEvent.values(calendarId: Long)=ContentValues().apply { put(CalendarContract.Events.CALENDAR_ID,calendarId);put(CalendarContract.Events.TITLE,title);put(CalendarContract.Events.EVENT_LOCATION,location);put(CalendarContract.Events.DESCRIPTION,description);put(CalendarContract.Events.DTSTART,startMillis);put(CalendarContract.Events.DTEND,endMillis);put(CalendarContract.Events.EVENT_TIMEZONE,zoneId) }
}

class CalendarDeviceStore(context: Context) {
    private val baseFile=File(context.noBackupFilesDir,"calendar-integration.json")
    @Volatile private var corrupt=false
    @Volatile private var cached:CalendarDeviceState?=null
    fun read(datasetId: String): CalendarDeviceState {
        cached?.takeIf{it.datasetId==datasetId}?.let{return it}
        val parsed=try { if(!baseFile.isFile) null else JSONObject(baseFile.bufferedReader().use{it.readText()}) } catch (_:Exception) { corrupt=true; null }
        if(parsed==null) return CalendarDeviceState(datasetId,storeProblem=corrupt).also{cached=it}
        if(parsed.optString("datasetId")!=datasetId) return CalendarDeviceState(datasetId).also(::write)
        return try { val links=mutableMapOf<String,CalendarLink>();val array=parsed.optJSONArray("links")?:JSONArray();for(i in 0 until array.length()){val o=array.getJSONObject(i);val l=CalendarLink(o.getString("visitId"),o.getLong("calendarId"),o.getLong("eventId"),CalendarLinkState.valueOf(o.getString("state")),o.optString("fingerprint",""),o.optLong("lastConfirmedAt",0));links[l.visitId]=l};val suppressed=mutableSetOf<String>();val s=parsed.optJSONArray("suppressed")?:JSONArray();for(i in 0 until s.length())suppressed+=s.getString(i);val selectedId=if(parsed.has("selectedCalendarId")&&!parsed.isNull("selectedCalendarId"))parsed.getLong("selectedCalendarId")else null;val selectedLabel=if(parsed.has("selectedCalendarLabel")&&!parsed.isNull("selectedCalendarLabel"))parsed.getString("selectedCalendarLabel").takeIf{it.isNotBlank()}else null;CalendarDeviceState(datasetId,parsed.optBoolean("enabled",false),selectedId,selectedLabel,links,suppressed).also{cached=it} } catch (_:Exception){ corrupt=true; CalendarDeviceState(datasetId,storeProblem=true).also{cached=it} }
    }
    fun write(state: CalendarDeviceState) {
        val json=JSONObject().put("version",1).put("datasetId",state.datasetId).put("enabled",state.enabled).put("links",JSONArray(state.links.values.map{JSONObject().put("visitId",it.visitId).put("calendarId",it.calendarId).put("eventId",it.eventId).put("state",it.state.name).put("fingerprint",it.fingerprint).put("lastConfirmedAt",it.lastConfirmedAt)})).put("suppressed",JSONArray(state.suppressedVisitIds.toList()))
        state.selectedCalendarId?.let{json.put("selectedCalendarId",it)};state.selectedCalendarLabel?.let{json.put("selectedCalendarLabel",it)}
        val temporary=File(baseFile.parentFile,"${baseFile.name}.new");try{FileOutputStream(temporary).use{stream->stream.write(json.toString().toByteArray());stream.fd.sync()};Files.move(temporary.toPath(),baseFile.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);corrupt=false;cached=state}catch(e:Exception){temporary.delete();throw e}
    }
    fun resetForDatasetReplacement() { baseFile.delete();File(baseFile.parentFile,"${baseFile.name}.new").delete();corrupt=false;cached=null }
}

class CalendarCoordinator(private val context:Context, private val database:ServiceLoopDatabase, private val gateway:CalendarGateway, private val store:CalendarDeviceStore, private val scope:CoroutineScope) {
    private val mutex=Mutex()
    private val started=AtomicBoolean(false)
    fun start() {
        if (!started.compareAndSet(false,true)) return
        scope.launch(Dispatchers.IO) {
            database.invalidationTracker.createFlow("working_visits","customers","sites",emitInitialState=true).collect {
                try { reconcile() } catch (failure:Throwable) { if (failure is CancellationException) throw failure }
            }
        }
    }
    fun reconcileAsync() { scope.launch(Dispatchers.IO){ reconcile() } }
    suspend fun runtimeState():CalendarRuntimeState=withContext(Dispatchers.IO){ mutex.withLock { val (state,visits)=current();val permitted=gateway.hasPermissions();val calendars=if(permitted) runCatching{gateway.writableCalendars()}.getOrDefault(emptyList()) else emptyList();CalendarRuntimeState(state.enabled,permitted,state.selectedCalendarLabel,state.links.values.count{it.state==CalendarLinkState.SYNCED&&visits[it.visitId]?.state=="BOOKED"},state.links.values.count{it.state!=CalendarLinkState.SYNCED},calendars,state.storeProblem,state.selectedCalendarId!=null&&calendars.none{it.id==state.selectedCalendarId}) } }
    suspend fun setEnabled(enabled:Boolean){ mutex.withLock{val (s,_)=current();store.write(s.copy(enabled=enabled));};if(enabled)reconcile() }
    suspend fun select(calendar:WritableCalendar){mutex.withLock{val(s,_)=current();store.write(s.copy(selectedCalendarId=calendar.id,selectedCalendarLabel=calendar.label))};reconcile()}
    suspend fun visitState(visitId:String):VisitCalendarState=mutex.withLock{val(s,visits)=current();val v=visits[visitId]?:return@withLock VisitCalendarState("Unavailable");val l=s.links[visitId];when{!s.enabled->VisitCalendarState("Calendar integration Off");!gateway.hasPermissions()->VisitCalendarState("Calendar permission needed");v.state!="BOOKED"->l?.let{VisitCalendarState("Calendar event retained",eventId=it.eventId)}?:VisitCalendarState("Not applicable");v.scheduledAtEpochMillis==null->VisitCalendarState("Calendar event will be added when an appointment time is set");l?.state==CalendarLinkState.MISSING->VisitCalendarState("Calendar event missing","Recreate event");l?.state==CalendarLinkState.DELETE_PENDING->VisitCalendarState("Calendar removal needs attention","Remove from Calendar");l!=null->VisitCalendarState("Synced to ${runCatching{gateway.writableCalendars().firstOrNull{it.id==l.calendarId}?.label}.getOrNull()?:"calendar"}","Remove from Calendar",l.eventId);visitId in s.suppressedVisitIds->VisitCalendarState("Removed from Calendar","Add to Calendar");else->VisitCalendarState("Not added","Add to Calendar")}}
    suspend fun add(visitId:String){mutex.withLock{val(s,visits)=current();val v=visits[visitId]?:error("Visit unavailable");val clean=s.copy(suppressedVisitIds=s.suppressedVisitIds-visitId,links=s.links-visitId);store.write(clean);create(clean,v)}}
    suspend fun remove(visitId:String){mutex.withLock{val(s,_)=current();val l=s.links[visitId];if(l!=null){if(!gateway.hasPermissions()||!gateway.delete(l.calendarId,l.eventId)){store.write(s.copy(links=s.links+(visitId to l.copy(state=CalendarLinkState.DELETE_PENDING))));error("Calendar event could not be removed")}};store.write(s.copy(links=s.links-visitId,suppressedVisitIds=s.suppressedVisitIds+visitId))}}
    suspend fun reconcile()=withContext(Dispatchers.IO){mutex.withLock{val(s,visits)=current();if(s.storeProblem||!s.enabled||!gateway.hasPermissions())return@withLock;var next=s;for(l in s.links.values){val v=visits[l.visitId];if(v==null||v.state in setOf("CANCELLED","DISPATCH_WITHDRAWN")){val deleted=runCatching{gateway.delete(l.calendarId,l.eventId)}.getOrDefault(false);next=if(deleted)next.copy(links=next.links-l.visitId)else next.copy(links=next.links+(l.visitId to l.copy(state=CalendarLinkState.DELETE_PENDING)));continue};if(v.state!="BOOKED")continue;val exists=runCatching{gateway.eventExists(l.calendarId,l.eventId)}.getOrDefault(false);if(!exists){next=next.copy(links=next.links+(l.visitId to l.copy(state=CalendarLinkState.MISSING)));continue};val event=event(v);val fp=fingerprint(event);if(l.state==CalendarLinkState.SYNCED&&l.fingerprint!=fp&&runCatching{gateway.update(l.calendarId,l.eventId,event)}.getOrDefault(false))next=next.copy(links=next.links+(l.visitId to l.copy(fingerprint=fp,lastConfirmedAt=System.currentTimeMillis())))};store.write(next);val writable=runCatching{gateway.writableCalendars().any{it.id==next.selectedCalendarId}}.getOrDefault(false);if(writable)visits.values.filter{it.state=="BOOKED"&&it.scheduledAtEpochMillis!=null&&it.id !in next.links&&it.id !in next.suppressedVisitIds}.forEach{v->next=create(next,v)};store.write(next)}}
    fun eventIntent(eventId:Long)=Intent(Intent.ACTION_VIEW,ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,eventId)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    fun resetForDatasetReplacement(){store.resetForDatasetReplacement()}
    private suspend fun current():Pair<CalendarDeviceState,Map<String,WorkingVisitEntity>>{val dao=database.serviceLoopDao();val dataset=dao.recoveryMetadata()?.datasetId?:"uninitialized";val customers=dao.allCustomers().associateBy{it.id};val sites=dao.allSites().associateBy{it.id};val visits=dao.allVisits().associateBy({it.id}){v->val customer=customers[v.customerId];val site=sites[v.siteId];v.copy(customerNameSnapshot=customer?.name?:v.customerNameSnapshot,siteNameSnapshot=site?.name?:v.siteNameSnapshot,siteAddressSnapshot=if(site!=null)site.address else v.siteAddressSnapshot)};return store.read(dataset) to visits}
    private fun create(state:CalendarDeviceState,v:WorkingVisitEntity):CalendarDeviceState{check(state.enabled&&gateway.hasPermissions());val calendarId=state.selectedCalendarId?:error("Choose a writable calendar");check(gateway.writableCalendars().any{it.id==calendarId}){"Selected calendar is unavailable"};val event=event(v);val id=gateway.insert(calendarId,event);val link=CalendarLink(v.id,calendarId,id,CalendarLinkState.SYNCED,fingerprint(event),System.currentTimeMillis());val next=state.copy(links=state.links+(v.id to link));try{store.write(next)}catch(e:Exception){runCatching{gateway.delete(calendarId,id)};throw e};return next}
    companion object { fun event(v:WorkingVisitEntity):ManagedCalendarEvent{val start=requireNotNull(v.scheduledAtEpochMillis);return ManagedCalendarEvent("ServiceLoop · ${v.siteNameSnapshot}",v.siteAddressSnapshot?.takeIf(String::isNotBlank),"Visit ${v.reference}\n${v.customerNameSnapshot}",start,start+60*60*1000,requireNotNull(v.appointmentZoneId))};fun fingerprint(e:ManagedCalendarEvent)=MessageDigest.getInstance("SHA-256").digest(listOf(e.title,e.location,e.description,e.startMillis,e.endMillis,e.zoneId).joinToString("\u0000").toByteArray()).joinToString(""){"%02x".format(it)}}
}
