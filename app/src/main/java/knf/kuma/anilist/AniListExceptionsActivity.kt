package knf.kuma.anilist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.input.input
import com.afollestad.materialdialogs.list.listItems
import knf.kuma.R
import knf.kuma.commons.EAHelper
import knf.kuma.commons.safeShow
import knf.kuma.commons.toast
import knf.kuma.custom.GenericActivity
import knf.kuma.database.CacheDB
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Episodios que no se pudieron sincronizar con AniList: reintentar, elegir el anime a mano o descartar. */
class AniListExceptionsActivity : GenericActivity() {

    private val dao = CacheDB.INSTANCE.aniListScrobbleDAO()
    private val adapter = Adapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(EAHelper.getTheme())
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_anilist_exceptions)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.title = "Excepciones de AniList"
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
        val empty = findViewById<View>(R.id.empty)
        findViewById<RecyclerView>(R.id.recycler).apply {
            layoutManager = LinearLayoutManager(this@AniListExceptionsActivity)
            adapter = this@AniListExceptionsActivity.adapter
        }
        dao.failedLive().observe(this) {
            adapter.items = it
            empty.visibility = if (it.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun onItem(item: AniListScrobbleState) {
        MaterialDialog(this).safeShow {
            title(text = "${item.name} · Ep ${item.episode}")
            listItems(items = listOf("Reintentar", "Elegir el anime en AniList", "Descartar")) { _, index, _ ->
                when (index) {
                    0 -> retry(item)
                    1 -> pickManually(item)
                    2 -> lifecycleScope.launch(Dispatchers.IO) { dao.remove(item.slug, item.episode) }
                }
            }
        }
    }

    private fun retry(item: AniListScrobbleState, mediaId: Int = 0) {
        AniListScrobbleWorker.enqueue(this, item.aid, item.slug, item.name, item.episode, mediaId)
        "Reintentando...".toast()
    }

    private fun pickManually(item: AniListScrobbleState) {
        MaterialDialog(this).safeShow {
            title(text = "Buscar en AniList")
            input(prefill = item.name) { _, text -> search(item, text.toString()) }
            positiveButton(text = "Buscar")
            negativeButton(text = "Cancelar")
        }
    }

    private fun search(item: AniListScrobbleState, query: String) {
        lifecycleScope.launch {
            val results = withContext(Dispatchers.IO) { AniListApi.search(query) }
            when {
                results == null -> "No se pudo conectar con AniList".toast()
                results.isEmpty() -> "Sin resultados para \"$query\"".toast()
                else -> MaterialDialog(this@AniListExceptionsActivity).safeShow {
                    title(text = "¿Cuál es?")
                    listItems(items = results.map { m -> m.titles.take(2).joinToString(" · ") }) { _, index, _ ->
                        val media = results[index]
                        lifecycleScope.launch(Dispatchers.IO) { AniListMatcher.saveManual(item.aid, item.slug, media.id) }
                        retry(item, media.id)
                    }
                }
            }
        }
    }

    private inner class Adapter : RecyclerView.Adapter<Adapter.Holder>() {
        var items: List<AniListScrobbleState> = emptyList()
            set(value) {
                field = value
                notifyDataSetChanged()
            }

        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(android.R.id.text1)
            val subtitle: TextView = view.findViewById(android.R.id.text2)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            holder.title.text = "${item.name} · Episodio ${item.episode}"
            holder.subtitle.text = item.reason ?: "Error desconocido"
            holder.itemView.setOnClickListener { onItem(item) }
        }
    }
}
