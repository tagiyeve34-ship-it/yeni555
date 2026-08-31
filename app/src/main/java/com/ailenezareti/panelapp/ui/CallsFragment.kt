package com.ailenezareti.panelapp.ui

import android.app.DatePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ailenezareti.panelapp.R
import com.ailenezareti.panelapp.api.ApiClient
import com.ailenezareti.panelapp.databinding.DialogCallFilterBinding
import com.ailenezareti.panelapp.databinding.FragmentCallsBinding
import com.ailenezareti.panelapp.model.CallEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

data class CallGroup(val number:String,val name:String?,val calls:List<CallEntry>)

class CallsFragment:Fragment(),Refreshable{
 private var _b:FragmentCallsBinding?=null; private val b get()=_b!!
 private val adapter=GroupAdapter({showHistory(it)},{copyNumber(it)})
 private var from:String?=null;private var to:String?=null;private var type="all";private var search:String?=null;private var limit=100
 override fun onCreateView(i:LayoutInflater,c:ViewGroup?,s:Bundle?):View{_b=FragmentCallsBinding.inflate(i,c,false);return b.root}
 override fun onViewCreated(v:View,s:Bundle?){b.callsRecycler.layoutManager=LinearLayoutManager(requireContext());b.callsRecycler.adapter=adapter;b.filterButton.setOnClickListener{showFilter()};refresh()}
 override fun refresh(){load()}
 private fun load(){val ch=(activity as? MainActivity)?.activeChild()?:return;lifecycleScope.launch(Dispatchers.IO){try{val all=mutableListOf<CallEntry>();var offset=0;val page=if(limit==0)500 else limit;var more:Boolean;do{val r=ApiClient.get(requireContext()).getCalls(ch.id,from,to,type,search,page,offset).body()?:break;all+=r.calls;offset+=r.calls.size;more=limit==0&&r.has_more}while(more);val groups=all.groupBy{normalize(it.phone_number)}.map{(_,v)->CallGroup(v.first().phone_number,v.firstNotNullOfOrNull{it.contact_name?.takeIf(String::isNotBlank)},v.sortedByDescending{it.occurred_at})}.sortedByDescending{it.calls.firstOrNull()?.occurred_at};launch(Dispatchers.Main){if(_b==null)return@launch;adapter.items=groups;adapter.notifyDataSetChanged();b.countText.text="${groups.size} nömrə · ${all.size} zəng"}}catch(_:Exception){}}}
 private fun normalize(s:String)=s.filter{it.isDigit()}.takeLast(12)
 private fun showFilter(){val db=DialogCallFilterBinding.inflate(layoutInflater);val dlg=AlertDialog.Builder(requireContext()).setView(db.root).create();val types=listOf("Hamısı","Gələn","Gedən","Buraxılmış");db.typeSpinner.adapter=ArrayAdapter(requireContext(),android.R.layout.simple_spinner_dropdown_item,types);db.typeSpinner.setSelection(listOf("all","incoming","outgoing","missed").indexOf(type).coerceAtLeast(0));val limits=listOf("100 qeyd","250 qeyd","500 qeyd","Hamısı");db.limitSpinner.adapter=ArrayAdapter(requireContext(),android.R.layout.simple_spinner_dropdown_item,limits);db.limitSpinner.setSelection(when(limit){250->1;500->2;0->3;else->0});db.searchInput.setText(search.orEmpty());db.fromDateButton.text=from?:"Başlanğıc tarix";db.toDateButton.text=to?:"Son tarix";db.fromDateButton.setOnClickListener{pickDate{from=it;db.fromDateButton.text=it}};db.toDateButton.setOnClickListener{pickDate{to=it;db.toDateButton.text=it}};db.resetButton.setOnClickListener{from=null;to=null;type="all";search=null;limit=100;dlg.dismiss();load()};db.applyButton.setOnClickListener{search=db.searchInput.text.toString().trim().ifBlank{null};type=listOf("all","incoming","outgoing","missed")[db.typeSpinner.selectedItemPosition];limit=listOf(100,250,500,0)[db.limitSpinner.selectedItemPosition];dlg.dismiss();load()};dlg.show()}
 private fun pickDate(done:(String)->Unit){val c=Calendar.getInstance();DatePickerDialog(requireContext(),{_,y,m,d->done("%04d-%02d-%02d".format(y,m+1,d))},c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show()}
 private fun showHistory(g:CallGroup){val box=LinearLayout(requireContext()).apply{orientation=LinearLayout.VERTICAL;setPadding(32,20,32,20)};val title=TextView(requireContext()).apply{text=(g.name?.let{"$it\n"}?:"")+g.number;textSize=20f;setTextColor(resources.getColor(R.color.text,null));setOnClickListener{copyNumber(g.number)}};box.addView(title);val incoming=g.calls.count{it.call_type.contains("in",true)&&!it.call_type.contains("miss",true)};val outgoing=g.calls.count{it.call_type.contains("out",true)};val missed=g.calls.count{it.call_type.contains("miss",true)};val dur=g.calls.sumOf{it.duration_sec};box.addView(TextView(requireContext()).apply{text="${g.calls.size} zəng · $incoming gələn · $outgoing gedən · $missed buraxılmış · ${formatDur(dur)}";setPadding(0,16,0,16)});g.calls.take(300).forEach{x->box.addView(TextView(requireContext()).apply{text="${icon(x.call_type)}  ${x.occurred_at}       ${formatDur(x.duration_sec)}";textSize=15f;setPadding(0,12,0,12)})};val scroll=ScrollView(requireContext()).apply{addView(box)};AlertDialog.Builder(requireContext()).setView(scroll).setPositiveButton("Bağla",null).show()}
 private fun icon(t:String)=when{t.contains("miss",true)->"✕";t.contains("out",true)->"↗";else->"↙"}
 private fun formatDur(s:Int)=if(s<60)"${s}s" else "${s/60}d ${s%60}s"
 private fun copyNumber(n:String){(requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Nömrə",n));Toast.makeText(requireContext(),"Nömrə kopyalandı",Toast.LENGTH_SHORT).show()}
 override fun onDestroyView(){_b=null;super.onDestroyView()}
 class GroupAdapter(val open:(CallGroup)->Unit,val copy:(String)->Unit):RecyclerView.Adapter<GroupAdapter.H>(){var items:List<CallGroup> = emptyList();override fun onCreateViewHolder(p:ViewGroup,v:Int)=H(LayoutInflater.from(p.context).inflate(R.layout.item_call_group,p,false));override fun getItemCount()=items.size;override fun onBindViewHolder(h:H,p:Int){val g=items[p];h.name.text=g.name?:g.number;h.num.text=g.number;h.meta.text="${g.calls.size} zəng · Son: ${g.calls.firstOrNull()?.occurred_at.orEmpty()}";h.num.setOnClickListener{copy(g.number)};h.itemView.setOnClickListener{open(g)}}class H(v:View):RecyclerView.ViewHolder(v){val name:TextView=v.findViewById(R.id.nameText);val num:TextView=v.findViewById(R.id.numberText);val meta:TextView=v.findViewById(R.id.metaText)}}
}
