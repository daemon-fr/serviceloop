package com.v16studio.v16service.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.v16studio.v16service.ui.designsystem.V16ServiceButtonAdapter as Button
import com.v16studio.v16service.ui.designsystem.V16ServiceCardAdapter as Card
import com.v16studio.v16service.ui.designsystem.V16ServiceLongTextEditor
import com.v16studio.v16service.ui.designsystem.V16ServicePrimaryButton
import com.v16studio.v16service.ui.designsystem.V16ServicePrivateLabel
import com.v16studio.v16service.ui.designsystem.V16ServiceTextButtonAdapter as TextButton
import com.v16studio.v16service.ui.designsystem.V16ServiceTextField
import com.v16studio.v16service.ui.designsystem.V16ServiceUiTokens
import java.io.ByteArrayOutputStream
import java.io.InputStream

@Composable
internal fun LongTextEditor(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    private: Boolean,
    compact: Boolean = false,
    enabled: Boolean = true,
) {
    V16ServiceLongTextEditor(value, onValueChange, label, private, compact = compact, enabled = enabled)
}

@Composable
internal fun UnsavedChangesGuard(changed:Boolean,nav:NavHostController) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    val requestBack:()->Unit={if(changed) confirm=true else { nav.popBackStack(); Unit }}
    val interceptor=LocalDetailBackInterceptor.current
    DisposableEffect(requestBack) { interceptor.value=requestBack; onDispose { if(interceptor.value===requestBack) interceptor.value=null } }
    BackHandler(onBack=requestBack)
    if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text("Discard unsaved changes?")},text={Text("This form uses local unsaved input until Save succeeds.")},confirmButton={TextButton({confirm=false;nav.popBackStack()}){Text("Discard changes")}},dismissButton={TextButton({confirm=false}){Text("Keep editing")}})
}

@Composable
internal fun EditorColumn(
    padding: PaddingValues,
    state: UiState,
    tag: String? = null,
    topContentPadding: Dp = 8.dp,
    showOperationMessage: Boolean = true,
    leadingContent: (@Composable () -> Unit)? = null,
    content: EditorColumnScope.() -> Unit,
) {
    val horizontalPadding = if (leadingContent == null) 16.dp else 0.dp
    LazyColumn(
        Modifier.padding(padding).fillMaxWidth().widthIn(max = com.v16studio.v16service.ui.designsystem.V16ServiceUiTokens.Size.formMaxWidth)
            .then(if (tag == null) Modifier else Modifier.testTag(tag)),
        contentPadding = PaddingValues(horizontalPadding, topContentPadding, horizontalPadding, 32.dp),
        verticalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.section),
    ) {
        if (state.error != null) item { Box(Modifier.fillMaxWidth().padding(horizontal = if (leadingContent == null) 0.dp else 16.dp)) { Text("Not saved — ${state.error}", color = MaterialTheme.colorScheme.error) } }
        if (showOperationMessage && state.operationMessage != null) item { Box(Modifier.fillMaxWidth().padding(horizontal = if (leadingContent == null) 0.dp else 16.dp)) { Text(state.operationMessage, color = MaterialTheme.colorScheme.primary) } }
        leadingContent?.let { item { it() } }
        EditorColumnScope(this, if (leadingContent == null) 0.dp else 16.dp).content()
    }
}

internal class EditorColumnScope internal constructor(
    private val listScope: LazyListScope,
    private val horizontalPadding: Dp,
) {
    fun item(
        key: Any? = null,
        contentType: Any? = null,
        content: @Composable LazyItemScope.() -> Unit,
    ) {
        listScope.item(key = key, contentType = contentType) {
            Column(Modifier.fillMaxWidth().padding(horizontal = horizontalPadding)) { content() }
        }
    }

    fun <T> items(
        items: List<T>,
        key: ((item: T) -> Any)? = null,
        contentType: ((item: T) -> Any?)? = null,
        itemContent: @Composable LazyItemScope.(item: T) -> Unit,
    ) {
        listScope.items(
            count = items.size,
            key = if (key == null) null else { index: Int -> key(items[index]) },
            contentType = if (contentType == null) ({ _: Int -> null }) else { index: Int -> contentType(items[index]) },
        ) { index ->
            val item = items[index]
            Column(Modifier.fillMaxWidth().padding(horizontal = horizontalPadding)) { itemContent(item) }
        }
    }
}

@Composable
internal fun DailyField(value:String,onChange:(String)->Unit,label:String,bottomPadding:Dp=V16ServiceUiTokens.Space.lg,enabled:Boolean=true){
    val tag = "field-" + label.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
    V16ServiceTextField(value,onChange,label,enabled=enabled,modifier=Modifier.testTag(tag).padding(bottom=bottomPadding))
}

@Composable
internal fun DailyHeading(value:String){Text(value,style=MaterialTheme.typography.titleLarge)}

@Composable
internal fun DailyEmpty(padding:PaddingValues,value:String,onRetry:(() -> Unit)?=null){Box(Modifier.fillMaxSize().padding(padding),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(V16ServiceUiTokens.Space.md)){Text(value);onRetry?.let{V16ServicePrimaryButton("Retry",it,Modifier.testTag("retry-due-services"))}}}}

@Composable
internal fun PrivateBlock(label:String,value:String){Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant)){Column(Modifier.padding(12.dp)){V16ServicePrivateLabel(label, style = MaterialTheme.typography.labelLarge);Text(value)}}}

internal fun handoff(
    context: Context,
    intent: Intent,
    label: String,
    successMessage: String = "Opened $label · no contact outcome was recorded",
    failureMessage: String = "No compatible $label app is available · copy the saved details manually",
) = if (runCatching { context.startActivity(intent); true }.getOrDefault(false)) successMessage else failureMessage

internal fun visitMapsIntent(capturedAddress: String): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(capturedAddress)}"))

internal fun InputStream.readDailyBounded(limit:Int):ByteArray? { val output=ByteArrayOutputStream(); val buffer=ByteArray(8192); var total=0; while(true){val count=read(buffer);if(count<0)break;total+=count;if(total>limit)return null;output.write(buffer,0,count)};return output.toByteArray() }

internal const val MAX_PHOTO_PICK_BYTES=30*1024*1024
