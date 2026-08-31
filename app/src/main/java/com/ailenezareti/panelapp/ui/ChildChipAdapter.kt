package com.ailenezareti.panelapp.ui
import android.view.*
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ailenezareti.panelapp.R
import com.ailenezareti.panelapp.model.Child
class ChildChipAdapter(private val click:(Child)->Unit):RecyclerView.Adapter<ChildChipAdapter.H>(){
 private var items:List<Child> = emptyList(); private var active=-1
 fun submit(v:List<Child>, id:Int){items=v;active=id;notifyDataSetChanged()}
 fun setActive(id:Int){active=id;notifyDataSetChanged()}
 override fun onCreateViewHolder(p:ViewGroup,v:Int)=H(LayoutInflater.from(p.context).inflate(R.layout.item_child,p,false) as TextView)
 override fun getItemCount()=items.size
 override fun onBindViewHolder(h:H,p:Int){ val x=items[p]; h.t.text="U   ${x.name}"; h.t.setBackgroundResource(if(x.id==active) R.drawable.bg_teal_soft else R.drawable.bg_chip); h.t.setOnClickListener{click(x)} }
 class H(val t:TextView):RecyclerView.ViewHolder(t)
}
