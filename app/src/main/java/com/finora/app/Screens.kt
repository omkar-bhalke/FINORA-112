package com.finora.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseUser
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.tasks.await

private val today get()=LocalDate.now()
private fun dateTime(t:Long)=Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).toLocalDateTime()
@Composable fun Home(p:Profile,rows:List<Entry>,u:FirebaseUser,go:(String)->Unit){
    Text(today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")));Heading("Hello, "+p.name.ifBlank{u.displayName ?: "there"})
    Column(Modifier.fillMaxWidth().background(Color(0xff154f42),RoundedCornerShape(16.dp)).padding(20.dp)){
        Text("Calculated balance",color=Color.White)
        var show by rememberSaveable {mutableStateOf(true)}
        Text(if(!show)"••••" else p.opening?.let {money(it+rows.filter{r->r.credit}.sumOf{r->r.amount}-rows.spent())} ?: "Opening balance needed",color=Color.White,style=MaterialTheme.typography.headlineSmall)
        TextButton({show=!show}){Text(if(show)"Hide amount" else "Show amount",color=Color.White)}
        Text(p.banks.firstOrNull() ?: "No primary bank selected",color=Color.White)
    }
    val daily=rows.filter{it.date()==today && !it.fixed}.spent()
    val monthly=rows.filter{YearMonth.from(it.date())==YearMonth.from(today)}.spent()
    listOf(Triple("Daily budget",daily,p.daily),Triple("Monthly budget",monthly,p.monthly)).forEach{(name,value,limit)->Panel{
        Heading(name);Text(money(value)+" spent");if(limit>0){LinearProgressIndicator(progress={(value.toFloat()/limit).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth());Text(money(limit-value)+" remaining")}else TextButton({go("Settings")}){Text("Set budget")}
    }}
    Button({go("Add")}){Text("Add transaction")}
    listOf("Planning" to "Bills & savings goals","Access" to "Review bank notifications","Parent" to "Parent Connect").forEach{(r,t)->OutlinedButton({go(r)}){Text(t)}}
    Heading("Recent transactions");if(rows.isEmpty())Text("Your saved transactions will appear here.")
    rows.take(5).forEach{RowEntry(it,go)}
}
@Composable private fun RowEntry(e:Entry,go:(String)->Unit){Panel{
    Text(e.title,style=MaterialTheme.typography.titleMedium);Text((if(e.credit)"+" else "−")+money(e.amount))
    Text(listOf(e.category,e.source,e.date().toString(),e.bank).filter{it.isNotBlank()}.joinToString(" · "))
    TextButton({go("Edit/"+e.id)}){Text("Details")}
}}
@Composable fun History(rows:List<Entry>,go:(String)->Unit){
    Heading("Transactions");var q by rememberSaveable{mutableStateOf("")};var kind by rememberSaveable{mutableStateOf("All")};var source by rememberSaveable{mutableStateOf("All")};var oldest by rememberSaveable{mutableStateOf(false)}
    Field("Search title, account, category or date",q){q=it};Choice("Type",kind,listOf("All","Debits","Credits")){kind=it}
    Choice("Source",source,listOf("All","Manual","Bank Notification","Reminder")){source=it}
    TextButton({oldest=!oldest}){Text(if(oldest)"Oldest first" else "Newest first")};Button({go("Add")}){Text("Add transaction")}
    val found=rows.filter{(kind=="All" || it.credit==(kind=="Credits")) && (source=="All"||it.source==source) && listOf(it.title,it.category,it.bank,it.account,it.date().toString()).any{v->v.contains(q,true)}}
    if(found.isEmpty())Text("No matching transactions")
    (if(oldest)found.reversed() else found).groupBy{YearMonth.from(it.date())}.forEach{(m,list)->Heading(m.toString());list.forEach{RowEntry(it,go)}}
}
@Composable fun Editor(original:Entry?,model:LedgerModel,done:()->Unit){
    val id=rememberSaveable{original?.id ?: UUID.randomUUID().toString()}
    var title by rememberSaveable{mutableStateOf(original?.title.orEmpty())};var amt by rememberSaveable{mutableStateOf(original?.amount?.let{java.math.BigDecimal.valueOf(it,2).toPlainString()}.orEmpty())}
    var cat by rememberSaveable{mutableStateOf(original?.category ?: categories.first())};var credit by rememberSaveable{mutableStateOf(original?.credit ?: false)}
    var date by rememberSaveable{mutableStateOf((original?.let{dateTime(it.time)} ?: LocalDateTime.now()).toLocalDate().toString())}
    var time by rememberSaveable{mutableStateOf((original?.let{dateTime(it.time)} ?: LocalDateTime.now()).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")))}
    var notes by rememberSaveable{mutableStateOf(original?.notes.orEmpty())};var method by rememberSaveable{mutableStateOf(original?.method.orEmpty())}
    var fixed by rememberSaveable{mutableStateOf(original?.fixed ?: false)};var deleting by remember{mutableStateOf(false)}
    Heading(if(original==null)"Add transaction" else "Transaction details");Field("Title",title){title=it};Field("Amount in rupees",amt){amt=it}
    Choice("Category",cat,categories){cat=it};Choice("Direction",if(credit)"Credit" else "Debit",listOf("Debit","Credit")){credit=it=="Credit"}
    Field("Date (YYYY-MM-DD)",date){date=it};Field("Time (HH:mm)",time){time=it};Field("Payment method",method){method=it};Field("Notes",notes){notes=it}
    Row{Checkbox(fixed,{fixed=it});Text("Fixed expense")}
    val ts=runCatching{LocalDateTime.parse(date+"T"+time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()}.getOrNull();val value=paise(amt)
    Button({model.write(done){model.save((original ?: Entry()).copy(id=id,title=title.trim(),amount=value!!,credit=credit,category=cat,time=ts!!,fixed=fixed,method=method,notes=notes))}},enabled=title.isNotBlank() && (value ?: 0)>0 && ts!=null){Text("Save")}
    OutlinedButton(done){Text("Cancel")}
    if(original!=null) TextButton({deleting=true}){Text("Delete")}
    if(deleting)AlertDialog({deleting=false},title={Text("Delete this transaction?")},confirmButton={TextButton({deleting=false;model.write(done){model.root.collection("transactions").document(id).delete().await()}}){Text("Delete")}},dismissButton={TextButton({deleting=false}){Text("Cancel")}})
}
@Composable fun Analysis(rows:List<Entry>,p:Profile,assistant:Boolean){
    Heading(if(assistant)"Financial assistant" else "Analytics")
    var period by rememberSaveable{mutableStateOf("Monthly")};Choice("Period",period,listOf("Daily","Weekly","Monthly","Yearly")){period=it}
    val from=when(period){"Daily"->today;"Weekly"->today.minusDays(today.dayOfWeek.value.toLong()-1);"Yearly"->today.withDayOfYear(1);else->today.withDayOfMonth(1)}
    val slice=rows.filter{!it.date().isBefore(from)&& !it.date().isAfter(today)}
    val sums=slice.filterNot{it.credit}.groupBy{it.category}.mapValues{it.value.spent()}.toList().sortedByDescending{it.second}
    Panel{Text("Recorded expenses");Heading(money(slice.spent()));Text("Income · "+money(slice.filter{it.credit}.sumOf{it.amount}));Text("${slice.size} recorded transactions · $from – $today")}
    if(assistant){
        var question by rememberSaveable{mutableStateOf("Highest spending category")}
        Choice("Ask",question,listOf("Highest spending category","Food spending","Daily budget remaining","Highest spending bank")){question=it}
        Panel{Text(when(question){"Highest spending category"->sums.firstOrNull()?.let{it.first+" · "+money(it.second)} ?: "No recorded expenses";"Food spending"->money(slice.filter{it.category=="Food"}.spent());"Daily budget remaining"->if(p.daily>0)money(p.daily-rows.filter{it.date()==today && !it.fixed}.spent())else "Daily budget unset";else->slice.filter{it.bank.isNotBlank() && !it.credit}.groupBy{it.bank}.maxByOrNull{it.value.spent()}?.key ?: "No bank-attributed expenses"})};Text("Calculated from your FINORA records. Bank coverage may be incomplete.")
    }else{Heading("Category spending");sums.forEach{(c,v)->Panel{Text(c+" · "+money(v));LinearProgressIndicator(progress={v.toFloat()/slice.spent().coerceAtLeast(1)},modifier=Modifier.fillMaxWidth())}}}
}
