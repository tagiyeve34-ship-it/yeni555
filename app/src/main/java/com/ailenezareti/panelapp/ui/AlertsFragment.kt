package com.ailenezareti.panelapp.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ailenezareti.panelapp.Prefs
import com.ailenezareti.panelapp.R
import com.ailenezareti.panelapp.api.ApiClient
import com.ailenezareti.panelapp.databinding.FragmentAlertsBinding
import com.ailenezareti.panelapp.model.AlertEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlertsFragment : Fragment(), Refreshable {
    private var _b: FragmentAlertsBinding? = null
    private val b get() = _b!!
    private val adapter = AlertAdapter()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentAlertsBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        b.alertsRecycler.layoutManager = LinearLayoutManager(requireContext())
        b.alertsRecycler.adapter = adapter
        b.incomingSwitch.isChecked = Prefs.notifyIncoming(requireContext())
        b.outgoingSwitch.isChecked = Prefs.notifyOutgoing(requireContext())
        b.missedSwitch.isChecked = Prefs.notifyMissed(requireContext())
        val listener = android.widget.CompoundButton.OnCheckedChangeListener { _, _ ->
            Prefs.setCallNotifyPrefs(requireContext(), b.incomingSwitch.isChecked, b.outgoingSwitch.isChecked, b.missedSwitch.isChecked)
        }
        b.incomingSwitch.setOnCheckedChangeListener(listener)
        b.outgoingSwitch.setOnCheckedChangeListener(listener)
        b.missedSwitch.setOnCheckedChangeListener(listener)
        refresh()
    }

    override fun refresh() {
        val ch = (activity as? MainActivity)?.activeChild() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val items = ApiClient.get(requireContext()).getAlerts(ch.id).body()?.alerts.orEmpty()
                launch(Dispatchers.Main) { if (_b != null) { adapter.items = items; adapter.notifyDataSetChanged() } }
            } catch (_: Exception) { }
        }
    }

    override fun onDestroyView() { _b = null; super.onDestroyView() }

    class AlertAdapter : RecyclerView.Adapter<AlertAdapter.H>() {
        var items: List<AlertEntry> = emptyList()
        override fun onCreateViewHolder(p: ViewGroup, v: Int) = H(LayoutInflater.from(p.context).inflate(R.layout.item_alert, p, false))
        override fun getItemCount() = items.size
        override fun onBindViewHolder(h: H, p: Int) { val x = items[p]; h.m.text = x.message; h.t.text = x.created_at }
        class H(v: View) : RecyclerView.ViewHolder(v) {
            val m: android.widget.TextView = v.findViewById(R.id.messageText)
            val t: android.widget.TextView = v.findViewById(R.id.timeText)
        }
    }
}
