package cr.alsamatours.digitalid

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import cr.alsamatours.digitalid.model.Employee
import cr.alsamatours.digitalid.net.ApiClient
import cr.alsamatours.digitalid.net.InvitationCode
import kotlinx.coroutines.launch
import java.net.URLEncoder

private val Forest = Color(0xFF123D30)
private val Mint = Color(0xFFD5EADD)
private val Light = Color(0xFFF4F8F5)
private val Leaf = Color(0xFF327453)

class MainActivity : ComponentActivity() {
    private val preferences by lazy { getSharedPreferences("alsama_config", MODE_PRIVATE) }
    private val api by lazy { ApiClient() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(18,61,48)
        setContent {
            var page by remember { mutableStateOf("login") }
            var employee by remember { mutableStateOf(Employee()) }
            var ownProfile by remember { mutableStateOf<Employee?>(null) }
            var signedInEmail by remember { mutableStateOf("") }
            var role by remember { mutableStateOf("") }
            var invitation by remember { mutableStateOf<InvitationCode?>(null) }
            var verified by remember { mutableStateOf(false) }
            var employees by remember { mutableStateOf<List<Employee>>(emptyList()) }
            var editing by remember { mutableStateOf(Employee()) }
            var loggedIn by remember { mutableStateOf(false) }
            var error by remember { mutableStateOf("") }
            var busy by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            val apiOrigin = BuildConfig.API_BASE_URL.trimEnd('/')

            fun syncProfile(slug: String = "sebastian-melendez-rodriguez") {
                scope.launch {
                    busy = true; error = ""; verified = false
                    try {
                        employee = if (role == "admin" && employee.id.isNotBlank() && employee.id != ownProfile?.id) api.getEmployee(employee.id) else api.myCredential()
                        verified = true
                    }
                    catch (t: Exception) { error = t.message ?: "No se puede contactar la API" }
                    finally { busy = false }
                }
            }
            fun syncDirectory() {
                scope.launch {
                    busy = true; error = ""
                    try { employees = api.listEmployees() }
                    catch (t: Exception) { error = t.message ?: "Error al consultar empleados" }
                    finally { busy = false }
                }
            }
            fun openProfile(item: Employee, serverVerified: Boolean = false) {
                employee = item; verified = serverVerified; page = "home"
                syncProfile(item.slug)
            }

            MaterialTheme(colorScheme = lightColorScheme(primary = Forest, secondary = Leaf, background = Light, surface = Color.White)) {
                Scaffold(
                    topBar = {
                        Surface(color = Forest) {
                            Row(Modifier.fillMaxWidth().statusBarsPadding().height(60.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("ALSAMA", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp, letterSpacing = 2.sp)
                                Text("  DIGITAL ID", color = Mint, fontSize = 12.sp)
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    },
                    bottomBar = {
                        NavigationBar(containerColor = Color.White) {
                            NavigationBarItem(selected = page == "home", onClick = {
                                if (role.isBlank()) page = "login"
                                else if (ownProfile == null) page = if(role=="admin") "directory" else "login"
                                else { employee = ownProfile!!; page = "home"; syncProfile(ownProfile!!.slug) }
                            }, icon = { Icon(Icons.Default.Badge, null) }, label = { Text("Mi credencial") })
                            NavigationBarItem(selected = page in listOf("login","account","activation"), onClick = { page = if(role.isNotBlank()) "account" else "login" }, icon = { Icon(Icons.Default.Person, null) }, label = { Text(if(role.isNotBlank()) "Mi cuenta" else "Acceso") })
                            if (role == "admin") NavigationBarItem(selected = page in listOf("directory","editor"), onClick = { page = "directory"; syncDirectory() }, icon = { Icon(Icons.Default.SupervisorAccount,null) }, label = { Text("Administración") })
                        }
                    }
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).background(Light)) {
                        if (error.isNotBlank()) {
                            Surface(color = Color(0xFFFFE9E5), modifier = Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(10.dp)) {
                                    Text(error, color = Color(0xFF9E322A), modifier = Modifier.weight(1f))
                                    IconButton(onClick = { error = "" }) { Icon(Icons.Default.Close, "Cerrar") }
                                }
                            }
                        }
                        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        when (page) {
                            "home" -> if (role.isNotBlank()) CredentialScreen(employee, verified, apiOrigin,
                                onRefresh = { syncProfile(employee.slug) },
                                onShare = { link -> startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type="text/plain"; putExtra(Intent.EXTRA_TEXT, link) }, "Compartir credencial")) },
                                onSaveContact = { e ->
                                    val profile = "$apiOrigin/v1/public/staff/${e.slug}/contact.vcf"
                                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(profile)))
                                },
                                onNfc = { link ->
                                    preferences.edit().putString("nfc_url", link).apply()
                                    error = "Enlace configurado para lectores NFC compatibles. No funciona con todos los teléfonos."
                                }
                            )
                            "login" -> LoginScreen(onLogin = { email, password ->
                                scope.launch {
                                    busy = true;error = ""
                                    try {
                                        val session=api.login(email,password)
                                        role=if(session.isAdmin) "admin" else "employee"
                                        loggedIn=session.isAdmin
                                        signedInEmail=email.trim()
                                        ownProfile=session.employee
                                        employee=session.employee ?: Employee()
                                        verified=session.employee!=null
                                        page=if(session.employee!=null) "home" else if(session.isAdmin) "directory" else "login"
                                        if(session.isAdmin) syncDirectory()
                                    }
                                    catch (t:Exception){error = t.message ?: "Error de inicio de sesión"}
                                    finally {busy=false}
                                }
                            }, onActivate = { page = "activation" })
                            "activation" -> ActivationScreen(onBack = { page = "login" }, onActivate = { email, code, password ->
                                scope.launch {
                                    busy=true; error=""
                                    try { api.activate(email,code,password); page="login"; error="Cuenta activada. Ahora inicia sesión con tu correo y contraseña." }
                                    catch(t:Exception){error=t.message?:"Error al activar cuenta"}
                                    finally { busy=false }
                                }
                            })
                            "account" -> if(role.isNotBlank()) AccountScreen(
                                email=signedInEmail,
                                onChangePassword={current,newPassword ->
                                    scope.launch {
                                        busy=true; error=""
                                        try { api.changePassword(current,newPassword); role=""; verified=false; employee=Employee(); ownProfile=null; signedInEmail=""; page="login"; error="Contraseña cambiada. Inicia sesión nuevamente." }
                                        catch(t:Exception){error=t.message?:"No se pudo cambiar la contraseña"}
                                        finally {busy=false}
                                    }
                                },
                                onLogout={scope.launch{try{api.logout()}catch(_:Exception){};role="";loggedIn=false;verified=false;employee=Employee();ownProfile=null;signedInEmail="";page="login"}}
                            ) else LoginScreen(onLogin={_,_->},onActivate={page="activation"})
                            "directory" -> if(role=="admin") DirectoryScreen(employees, onReload = ::syncDirectory,
                                onNew = { editing = Employee(); page = "editor" },
                                onEdit = { editing = it; page = "editor" },
                                onOpen = { openProfile(it) },
                                onActive = { e,active -> scope.launch { busy=true;error="";try { api.changeActive(e, active); syncDirectory() } catch(t:Exception){error=t.message ?: "No se pudo actualizar"} finally {busy=false} } },
                                onInvite = { e -> scope.launch {busy=true;error="";try { invitation=api.inviteEmployee(e) } catch(t:Exception){error=t.message?:"Error al crear acceso"} finally {busy=false}} },
                                onLogout = { scope.launch { try { api.logout() } catch(_:Exception){}; role=""; loggedIn=false; verified=false; employee=Employee(); ownProfile=null; signedInEmail=""; employees=emptyList(); page="login" } })
                            else LoginScreen(onLogin={_,_->},onActivate={page="activation"})
                            "editor" -> if(role=="admin") EmployeeEditor(editing, onCancel={page="directory"}, onSave={ e ->
                                scope.launch { busy=true; error=""; try { api.save(e);page="directory";syncDirectory() } catch(t:Exception){error=t.message ?: "No se pudo guardar"} finally {busy=false} }
                            }) else LoginScreen(onLogin={_,_->},onActivate={page="activation"})
                        }
                    }
                }
                invitation?.let { invite ->
                    AlertDialog(
                        onDismissRequest={invitation=null},
                        title={Text("Activación de empleado")},
                        text={
                            Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                                Text("Correo: ${invite.email}")
                                Text("Comparte el código de activación por un canal privado. Vence en 24 horas y solo sirve una vez.")
                                androidx.compose.foundation.text.selection.SelectionContainer {
                                    Text(invite.code, fontWeight=FontWeight.Bold)
                                }
                                Text("El empleado abre Acceso → Activar cuenta e introduce su correo, este código y una contraseña nueva.")
                            }
                        },
                        confirmButton={TextButton(onClick={invitation=null}){Text("Cerrar")}}
                    )
                }
            }
        }
    }
}

@Composable
private fun CredentialScreen(employee: Employee, verified: Boolean, origin: String, onRefresh: () -> Unit, onShare: (String) -> Unit, onSaveContact: (Employee) -> Unit, onNfc: (String) -> Unit) {
    val link = "$origin/p/${employee.slug}"
    val canShare = verified && employee.active && employee.publicVisible && origin.startsWith("https://")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(15.dp)) {
        Text("Identificación corporativa", color = Forest, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Text("Alsama Tours · Costa Rica", color=Leaf)
        Card(colors=CardDefaults.cardColors(containerColor=Forest), shape=RoundedCornerShape(24.dp), modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.padding(23.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                BrandLogo()
                Text("ALSAMA TOURS", fontSize=17.sp, fontWeight=FontWeight.Black, letterSpacing=3.sp, color=Mint)
                Spacer(Modifier.height(22.dp))
                Portrait(employee)
                Spacer(Modifier.height(14.dp))
                Text(employee.fullName, fontSize=22.sp, textAlign=TextAlign.Center, fontWeight=FontWeight.Bold, color=Color.White)
                Text(employee.jobTitle, fontSize=16.sp, textAlign=TextAlign.Center, color=Mint)
                Spacer(Modifier.height(17.dp))
                HorizontalDivider(color=Color.White.copy(alpha=0.2f))
                Spacer(Modifier.height(16.dp))
                Text(if (verified && employee.active) "EMPLEADO ACTIVO · VERIFICADO" else "VISTA PREVIA · SIN VERIFICAR", color=if(verified) Mint else Color(0xFFFFD59A), fontSize=12.sp, fontWeight=FontWeight.Bold)
                if (canShare) {
                    Spacer(Modifier.height(16.dp))
                    QrCode(link)
                    Spacer(Modifier.height(8.dp))
                    Text("Escanea para verificar y guardar contacto", fontSize=12.sp, color=Mint, textAlign=TextAlign.Center)
                }
            }
        }
        if (!verified) {
            Text("Esta tarjeta es una vista previa. Sin conexión con la API no se puede verificar la relación laboral ni emitir un QR válido.",fontSize=13.sp,color=Color(0xFF855B14),textAlign=TextAlign.Center)
        }
        if (employee.email.isNotBlank()) Text(employee.email, color=Forest, fontSize=14.sp)
        if (employee.phone.isNotBlank()) Text(employee.phone, color=Leaf)
        Button(onClick=onRefresh, modifier=Modifier.fillMaxWidth()) { Icon(Icons.Default.Refresh,null); Spacer(Modifier.width(8.dp)); Text("Actualizar credencial") }
        if (canShare) {
            OutlinedButton(onClick={onShare(link)}, modifier=Modifier.fillMaxWidth()) { Icon(Icons.Default.Share,null); Spacer(Modifier.width(8.dp));Text("Compartir enlace") }
            OutlinedButton(onClick={onSaveContact(employee)},modifier=Modifier.fillMaxWidth()){ Icon(Icons.Default.PersonAdd,null);Spacer(Modifier.width(8.dp));Text("Guardar contacto") }
            OutlinedButton(onClick={onNfc(link)},modifier=Modifier.fillMaxWidth()){ Icon(Icons.Default.Nfc,null);Spacer(Modifier.width(8.dp));Text("Habilitar enlace NFC experimental") }
        }
    }
}

@Composable
private fun Portrait(employee: Employee) {
    val context=LocalContext.current
    val bitmap = remember(employee.slug) {
        if (employee.slug == "sebastian-melendez-rodriguez") runCatching {
            context.assets.open("sebastian.jpg").use { BitmapFactory.decodeStream(it) }
        }.getOrNull() else null
    }
    Box(Modifier.size(118.dp).background(Mint, CircleShape), contentAlignment=Alignment.Center) {
        when {
            employee.photoUrl.startsWith("https://") -> AsyncImage(model=employee.photoUrl, contentDescription="Fotografía del empleado", contentScale=ContentScale.Crop, modifier=Modifier.fillMaxSize())
            bitmap!=null -> Image(bitmap.asImageBitmap(),contentDescription="Fotografía del empleado", contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())
            else -> Icon(Icons.Default.Person,null,tint=Forest,modifier=Modifier.size(68.dp))
        }
    }
}

@Composable
private fun QrCode(content:String){
    val bitmap=remember(content){runCatching {
        val matrix=QRCodeWriter().encode(content,BarcodeFormat.QR_CODE,320,320)
        val pixels=IntArray(320*320){ i -> if(matrix[i%320,i/320]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
        Bitmap.createBitmap(pixels,320,320,Bitmap.Config.ARGB_8888)
    }.getOrNull()}
    if(bitmap!=null) Image(bitmap.asImageBitmap(),"QR de verificación",modifier=Modifier.size(185.dp).background(Color.White, RoundedCornerShape(8.dp)).padding(9.dp))
}

@Composable
private fun LoginScreen(onLogin:(String,String)->Unit, onActivate:()->Unit) {
    var email by remember { mutableStateOf("") };var password by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        Icon(Icons.Default.AdminPanelSettings, null, tint=Forest,modifier=Modifier.size(48.dp))
        Text("Iniciar sesión",fontSize=25.sp,fontWeight=FontWeight.Bold,color=Forest)
        Text("Entra con tu correo y contraseña. Los permisos de administración se asignan automáticamente.",color=Leaf)
        OutlinedTextField(email,{email=it},label={Text("Correo de tu cuenta")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email),modifier=Modifier.fillMaxWidth())
        OutlinedTextField(password,{password=it},label={Text("Contraseña")},singleLine=true,visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
        Button(onClick={onLogin(email,password)},enabled=email.contains("@")&&password.isNotBlank(),modifier=Modifier.fillMaxWidth()){ Text("Iniciar sesión") }
        OutlinedButton(onClick=onActivate,modifier=Modifier.fillMaxWidth()){Text("Activar cuenta de empleado")}
        Text("El administrador proporciona un código de activación individual. Nunca compartas tu contraseña.",fontSize=12.sp,color=Leaf)
    }
}

@Composable
private fun ActivationScreen(onBack:()->Unit,onActivate:(String,String,String)->Unit) {
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(15.dp)) {
        Text("Activar mi cuenta",fontSize=25.sp,fontWeight=FontWeight.Bold,color=Forest)
        Text("Solicita al administrador un código individual. Tu correo debe coincidir con el registrado en Alsama.",color=Leaf)
        OutlinedTextField(email,{email=it},label={Text("Correo registrado")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email),modifier=Modifier.fillMaxWidth())
        OutlinedTextField(code,{code=it.trim()},label={Text("Código de activación (64 caracteres)")},singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(password,{password=it},label={Text("Nueva contraseña (mínimo 14 caracteres)")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
        OutlinedTextField(repeat,{repeat=it},label={Text("Confirmar contraseña")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
        if(repeat.isNotBlank()&&password!=repeat) Text("Las contraseñas no coinciden",color=Color.Red)
        Button(onClick={onActivate(email,code,password)},enabled=email.contains("@")&&code.length==64&&password.length>=14&&password==repeat,modifier=Modifier.fillMaxWidth()){Text("Activar cuenta")}
        OutlinedButton(onClick=onBack,modifier=Modifier.fillMaxWidth()){Text("Volver al inicio de sesión")}
    }
}

@Composable
private fun AccountScreen(email:String,onChangePassword:(String,String)->Unit,onLogout:()->Unit) {
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(15.dp)) {
        Text("Mi cuenta",fontSize=25.sp,fontWeight=FontWeight.Bold,color=Forest)
        Text(email,color=Leaf)
        HorizontalDivider()
        Text("Cambiar contraseña",fontSize=20.sp,fontWeight=FontWeight.SemiBold,color=Forest)
        OutlinedTextField(current,{current=it},label={Text("Contraseña actual")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
        OutlinedTextField(next,{next=it},label={Text("Contraseña nueva (mínimo 14 caracteres)")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
        OutlinedTextField(repeat,{repeat=it},label={Text("Confirmar contraseña nueva")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
        Button(onClick={onChangePassword(current,next)},enabled=current.isNotBlank()&&next.length>=14&&next==repeat,modifier=Modifier.fillMaxWidth()){Text("Guardar nueva contraseña")}
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick=onLogout,modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Logout,null);Spacer(Modifier.width(8.dp));Text("Cerrar sesión")}
    }
}

@Composable
private fun DirectoryScreen(employees:List<Employee>, onReload:()->Unit, onNew:()->Unit,onEdit:(Employee)->Unit,onOpen:(Employee)->Unit,onActive:(Employee,Boolean)->Unit,onInvite:(Employee)->Unit,onLogout:()->Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically){Text("Empleados",fontSize=26.sp,fontWeight=FontWeight.Bold,color=Forest,modifier=Modifier.weight(1f));IconButton(onClick=onReload){Icon(Icons.Default.Refresh,"Actualizar")};IconButton(onClick=onLogout){Icon(Icons.Default.Logout,"Salir")}}
        Button(onClick=onNew,modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Add,null);Spacer(Modifier.width(8.dp));Text("Agregar empleado")}
        if(employees.isEmpty()) Text("Aún no hay empleados cargados o el directorio no está disponible.",color=Leaf)
        LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            items(employees,key={it.id}){e->
                Card(shape=RoundedCornerShape(14.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){
                    Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                        Text(e.fullName,fontWeight=FontWeight.Bold,color=Forest)
                        Text(e.jobTitle,color=Leaf)
                        Text(if(e.active) "Activo" else "Inactivo",color=if(e.active) Leaf else Color.Red)
                        Row(verticalAlignment=Alignment.CenterVertically){
                            TextButton(onClick={onEdit(e)}){Text("Editar")}
                            TextButton(onClick={onOpen(e)}){Text("Credencial")}
                            TextButton(onClick={onInvite(e)},enabled=e.active){Text("Crear acceso")}
                            Spacer(Modifier.weight(1f))
                            Switch(checked=e.active,onCheckedChange={onActive(e,it)})
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmployeeEditor(initial:Employee,onCancel:()->Unit,onSave:(Employee)->Unit){
    var name by remember(initial.id){mutableStateOf(initial.fullName)}
    var slug by remember(initial.id){mutableStateOf(initial.slug)}
    var job by remember(initial.id){mutableStateOf(initial.jobTitle)}
    var email by remember(initial.id){mutableStateOf(initial.email)}
    var phone by remember(initial.id){mutableStateOf(initial.phone)}
    var photo by remember(initial.id){mutableStateOf(initial.photoUrl)}
    var active by remember(initial.id){mutableStateOf(initial.active)}
    var isPublic by remember(initial.id){mutableStateOf(initial.publicVisible)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text(if(initial.id.isBlank()) "Nuevo empleado" else "Editar empleado",fontSize=25.sp,fontWeight=FontWeight.Bold,color=Forest)
        OutlinedTextField(name,{name=it},label={Text("Nombre completo")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(slug,{slug=it.lowercase().replace(" ", "-")},label={Text("Identificador (slug)")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        OutlinedTextField(job,{job=it},label={Text("Puesto")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(email,{email=it},label={Text("Correo")},modifier=Modifier.fillMaxWidth(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email))
        OutlinedTextField(phone,{phone=it},label={Text("Teléfono")},modifier=Modifier.fillMaxWidth(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone))
        OutlinedTextField(photo,{photo=it},label={Text("URL HTTPS de fotografía")},modifier=Modifier.fillMaxWidth())
        Row(verticalAlignment=Alignment.CenterVertically){Text("Empleado activo",modifier=Modifier.weight(1f));Switch(active,{active=it})}
        Row(verticalAlignment=Alignment.CenterVertically){Text("Contacto visible públicamente",modifier=Modifier.weight(1f));Switch(isPublic,{isPublic=it})}
        Button(onClick={onSave(initial.copy(slug=slug,fullName=name,jobTitle=job,email=email,phone=phone,photoUrl=photo,active=active,publicVisible=isPublic))},
            enabled=name.isNotBlank()&&job.isNotBlank()&&email.contains("@")&&slug.matches(Regex("[a-z0-9-]+")),modifier=Modifier.fillMaxWidth()){Text("Guardar cambios")}
        OutlinedButton(onClick=onCancel,modifier=Modifier.fillMaxWidth()){Text("Cancelar")}
    }
}

@Composable
private fun BrandLogo(){
    val context=LocalContext.current
    val bitmap=remember { runCatching { context.assets.open("tortuga.png").use { BitmapFactory.decodeStream(it) } }.getOrNull() }
    if(bitmap!=null) Image(bitmap.asImageBitmap(),"Logo Alsama Tours",modifier=Modifier.size(88.dp),contentScale=ContentScale.Fit)
}