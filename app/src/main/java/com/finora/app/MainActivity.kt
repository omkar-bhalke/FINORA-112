package com.finora.app

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.*
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch
import java.time.LocalDate

val accents=linkedMapOf("Emerald Finance" to 0xff076653,"Midnight Black" to 0xff303539,"Ocean Blue" to 0xff17679b,"Royal Purple" to 0xff7349a0,"Graphite Pro" to 0xff495862,"Minimal White" to 0xff506065,"Sunset Gradient" to 0xffab5034,"Premium Gold" to 0xff876713,"Cyber Neon" to 0xff00765f,"Soft Lavender" to 0xff805da0,"Arctic Silver" to 0xff526979,"Deep Navy" to 0xff28476b)
@Composable fun FinoraTheme(theme:String="Emerald Finance",mode:String="System",content:@Composable ()->Unit) {
    val dark=mode=="Dark" || (mode=="System" && isSystemInDarkTheme())
    val accent=Color(accents[theme] ?: 0xff076653)
    val colors=if(dark) darkColorScheme(primary=androidx.compose.ui.graphics.lerp(accent,Color.White,.58f),background=Color(0xff101b18),surface=Color(0xff192720))
        else lightColorScheme(primary=accent,background=Color(0xfff7f9f6),surface=Color.White,onBackground=Color(0xff1d2a25))
    MaterialTheme(colorScheme=colors,content=content)
}
@Composable fun Panel(content:@Composable ColumnScope.()->Unit) {
    Surface(shape=RoundedCornerShape(15.dp),color=MaterialTheme.colorScheme.surface,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)
    }
}
@Composable fun Heading(s:String){Text(s,style=MaterialTheme.typography.headlineSmall)}
@Composable fun Field(label:String,value:String,change:(String)->Unit){OutlinedTextField(value,change,Modifier.fillMaxWidth(),label={Text(label)},singleLine=true)}
@Composable fun Choice(label:String,value:String,options:List<String>,select:(String)->Unit){
    var open by remember { mutableStateOf(false) }
    Box { OutlinedButton({open=true}){Text("$label · $value")};DropdownMenu(open,{open=false}) { options.forEach { DropdownMenuItem({Text(it)},{select(it);open=false}) } } }
}
class MainActivity:ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
        val firebase=FirebaseApp.initializeApp(this)
        if(firebase!=null) FirebaseAppCheck.getInstance().installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
        setContent { FinoraTheme { Surface(Modifier.fillMaxSize().systemBarsPadding(),color=MaterialTheme.colorScheme.background) {
            if(firebase==null) Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.Center){Heading("FINORA");Text("Understand Your Money. Control Your Spending.");Panel { Text("Firebase setup required");Text("This build needs the Android registration for com.finora.app in finoraaaaaa. See the repository setup instructions.") }}
            else Session()
        } } }
    }
}
@Composable private fun Session(){
    val auth=remember { FirebaseAuth.getInstance() }
    var user by remember { mutableStateOf(auth.currentUser) }
    DisposableEffect(auth){val listener=FirebaseAuth.AuthStateListener { user=it.currentUser };auth.addAuthStateListener(listener);onDispose{auth.removeAuthStateListener(listener)}}
    val account=user
    if(account==null) Login(auth) else key(account.uid){val model=viewModel<LedgerModel>(key=account.uid){LedgerModel(account.uid)};Account(model,account)}
}
@Composable private fun Login(auth:FirebaseAuth){
    val context=LocalContext.current;val scope= rememberCoroutineScope()
    var email by remember {mutableStateOf("")};var password by remember {mutableStateOf("")}
    var err by remember {mutableStateOf("")};var busy by remember {mutableStateOf(false)}
    fun submit(action:suspend ()->Unit){if(busy)return;busy=true;scope.launch { try{ action() }catch(e:Exception){err=e.localizedMessage.orEmpty()}finally{busy=false} }}
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){result->
        submit { val token=GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java).idToken ?: error("Google ID token unavailable")
            auth.signInWithCredential(GoogleAuthProvider.getCredential(token,null)).await() } }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Heading("FINORA");Text("Understand Your Money. Control Your Spending.");Heading("Welcome")
        Button({val id=context.resources.getIdentifier("default_web_client_id","string",context.packageName)
            if(id==0) err="Enable Google sign-in and download the updated Firebase Android configuration."
            else launcher.launch(GoogleSignIn.getClient(context,GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestEmail().requestIdToken(context.getString(id)).build()).signInIntent)},enabled=!busy){Text("Continue with Google")}
        Field("Email",email){email=it};OutlinedTextField(password,{password=it},label={Text("Password")},visualTransformation=PasswordVisualTransformation(),singleLine=true)
        Button({submit{auth.signInWithEmailAndPassword(email.trim(),password).await()}},enabled=!busy){Text("Sign in")}
        OutlinedButton({submit{auth.createUserWithEmailAndPassword(email.trim(),password).await()}},enabled=!busy){Text("Create account")}
        if(busy) CircularProgressIndicator();Text(err,color=MaterialTheme.colorScheme.error)
    }
}
@Composable private fun Account(model:LedgerModel,user:FirebaseUser){
    val p by model.profile.collectAsState();val data by model.entries.collectAsState();val error by model.error.collectAsState()
    val profile=p
    if(profile==null){Column(Modifier.padding(24.dp)){CircularProgressIndicator();Text(error)};return}
    FinoraTheme(profile.theme,profile.mode){
        val nav= rememberNavController()
        val tabs=listOf("Home","Transactions","Analytics","Assistant","Profile")
        val route=nav.currentBackStackEntryAsState().value?.destination?.route
        val ctx=LocalContext.current
        LaunchedEffect(profile.banks){ctx.inbox(user.uid).edit().putStringSet("selected",profile.banks.toSet()).apply()}
        if(!profile.ready){Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)){Banks(profile,model,true){};Text(error)};return@FinoraTheme}
        Scaffold(bottomBar={if(route in tabs) NavigationBar { tabs.forEachIndexed { i,t->
            NavigationBarItem(selected=route==t,onClick={nav.navigate(t){popUpTo(nav.graph.findStartDestination().id){saveState=true};launchSingleTop=true;restoreState=true}},
                icon={Text(listOf("⌂","⇄","▥","✦","◎")[i])},label={Text(t,style=MaterialTheme.typography.labelSmall,maxLines=1)}) } } }){pad->
            Column(Modifier.padding(pad).padding(horizontal=16.dp).fillMaxSize()){
                if(error.isNotBlank()) TextButton({model.error.value=""}){Text(error,color=MaterialTheme.colorScheme.error)}
                if(route !in tabs && route!=null) TextButton({nav.popBackStack()}){Text("‹  Back")}
                NavHost(nav,"Home"){(tabs+listOf("Add","Edit/{id}","Banks","Access","Planning","Parent","Settings","Control")).forEach { dest->
                    composable(dest){back->Column(Modifier.verticalScroll(rememberScrollState()).fillMaxWidth().padding(vertical=14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                        val go:(String)->Unit={nav.navigate(it)}
                        when(dest){
                            "Home"->Home(profile,data,user,go)
                            "Transactions"->History(data,go)
                            "Analytics","Assistant"->Analysis(data,profile,dest=="Assistant")
                            "Profile"->{Heading(profile.name.ifBlank{user.displayName ?: "Your profile"});Text(user.email.orEmpty());listOf("Settings" to "Personal information & budgets","Banks" to "Selected banks","Access" to "Bank notification access","Planning" to "Bills & savings goals","Control" to "Spending control","Parent" to "Parent Connect").forEach { (r,t)->Panel { TextButton({go(r)}){Text(t)} } }
                                OutlinedButton({ctx.inbox(user.uid).edit().putBoolean("enabled",false).apply();FirebaseAuth.getInstance().signOut()}){Text("Sign out")}}
                            "Add"->Editor(null,model){nav.popBackStack()}
                            "Edit/{id}"->data.find{it.id==back.arguments?.getString("id")}?.let{Editor(it,model){nav.popBackStack()}}
                            "Settings"->Settings(profile,model){nav.popBackStack()}
                            "Banks"->Banks(profile,model,false){nav.popBackStack()}
                            "Access"->Access(model,profile)
                            "Planning"->Planning(model)
                            "Control"->{Heading("Spending control");val spent=data.filter{it.date()==LocalDate.now()&&!it.fixed}.spent();Panel{Text("Today's spending · "+money(spent));Text(if(profile.daily<=0)"Set a daily limit in Settings" else if(spent>=profile.daily)"Your daily spending limit has been reached." else "Remaining · "+money(profile.daily-spent));TextButton({go("Settings")}){Text("Edit daily limit")}}}
                            "Parent"->Parent(model)
                        }
                    }}
                }}
            }
        }
    }
}
