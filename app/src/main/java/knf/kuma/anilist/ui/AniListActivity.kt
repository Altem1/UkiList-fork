package knf.kuma.anilist.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.google.android.material.tabs.TabLayout
import knf.kuma.R
import knf.kuma.anilist.AniListApi
import knf.kuma.anilist.AniListAuth
import knf.kuma.commons.EAHelper
import knf.kuma.commons.toast
import knf.kuma.database.CacheDB
import knf.kuma.custom.GenericActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Sección AniList tipo AniHyou: historial y listas del usuario. */
class AniListActivity : GenericActivity() {

    private val tabs = listOf(
        "Historial" to null, "Viendo" to "CURRENT", "Pendientes" to "PLANNING",
        "Completado" to "COMPLETED", "En pausa" to "PAUSED", "Abandonado" to "DROPPED", "Repitiendo" to "REPEATING"
    )
    private var all: List<AniListApi.ListItem> = emptyList()
    private val adapter = Adapter()
    private lateinit var refresh: SwipeRefreshLayout
    private lateinit var empty: TextView
    private lateinit var tabLayout: TabLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(EAHelper.getTheme())
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_anilist)
        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = AniListAuth.userName.value?.let { "AniList · $it" } ?: "AniList"
        refresh = findViewById(R.id.refresh)
        empty = findViewById(R.id.empty)
        tabLayout = findViewById(R.id.tabs)
        findViewById<RecyclerView>(R.id.recycler).adapter = adapter
        tabs.forEach { tabLayout.addTab(tabLayout.newTab().setText(it.first)) }
        tabLayout.getTabAt((savedInstanceState?.getInt(EXTRA_TAB) ?: intent.getIntExtra(EXTRA_TAB, 1)).coerceIn(0, tabs.size - 1))?.select()
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = filter()
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        refresh.setOnRefreshListener { load() }
        load()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(EXTRA_TAB, tabLayout.selectedTabPosition.coerceAtLeast(0))
    }

    private fun load() {
        if (!AniListAuth.isLoggedIn) {
            refresh.isRefreshing = false
            empty.text = "Conecta tu cuenta de AniList en Ajustes > Sincronización"
            empty.visibility = View.VISIBLE
            return
        }
        refresh.isRefreshing = true
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) { runCatching { AniListApi.userListDetailed(AniListAuth.userId) }.getOrNull() }
            refresh.isRefreshing = false
            if (list == null) {
                adapter.submit(emptyList())
                empty.text = "No se pudo cargar tu lista de AniList"
                empty.visibility = View.VISIBLE
            } else {
                all = list
                filter()
            }
        }
    }

    private fun filter() {
        val status = tabs[tabLayout.selectedTabPosition.coerceAtLeast(0)].second
        val items = if (status == null) all.filter { it.updatedAt > 0 }.take(100) else all.filter { it.status == status }
        adapter.submit(items)
        empty.text = "Nada por aquí"
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "Perfil")
        menu.add(0, 2, 0, "Estadísticas")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val user = AniListAuth.userName.value ?: ""
        when (item.itemId) {
            android.R.id.home -> finish()
            1 -> AniListDrawer.openUrl(this, "https://anilist.co/user/$user/")
            2 -> AniListDrawer.openUrl(this, "https://anilist.co/user/$user/stats/anime/overview")
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private inner class Adapter : RecyclerView.Adapter<Adapter.Holder>() {
        private var items: List<AniListApi.ListItem> = emptyList()

        fun submit(list: List<AniListApi.ListItem>) {
            items = list
            notifyDataSetChanged()
        }

        inner class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val cover: ImageView = v.findViewById(R.id.cover)
            val title: TextView = v.findViewById(R.id.title)
            val text: TextView = v.findViewById(R.id.progress_text)
            val bar: ProgressBar = v.findViewById(R.id.progress_bar)
            val openAnime: View = v.findViewById(R.id.open_anime)
            val openWeb: View = v.findViewById(R.id.open_web)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_anilist_entry, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            holder.title.text = item.title
            val total = item.episodes?.toString() ?: "?"
            val score = if (item.score > 0) " · ★ ${item.score}" else ""
            holder.text.text = "Ep. ${item.progress}/$total$score"
            holder.bar.max = item.episodes ?: maxOf(item.progress, 1)
            holder.bar.progress = item.progress
            Glide.with(holder.cover).load(item.cover).into(holder.cover)
            holder.openWeb.setOnClickListener {
                AniListDrawer.openUrl(this@AniListActivity, "https://anilist.co/anime/${item.mediaId}/")
            }
            val openAnime = View.OnClickListener { openInApp(item) }
            holder.openAnime.setOnClickListener(openAnime)
            holder.itemView.setOnClickListener(openAnime)
        }
    }

    /** Abre la ficha del anime en la app para seguir viendo; la relación sale del mapeo AniList ↔ catálogo. */
    private fun openInApp(item: AniListApi.ListItem) {
        lifecycleScope.launch {
            val mapping = withContext(Dispatchers.IO) { CacheDB.INSTANCE.aniListMappingDAO().byAniList(item.mediaId) }
            if (mapping == null) "${item.title} no está en el catálogo de la app".toast()
            else knf.kuma.animeinfo.ActivityAnimeMaterial.open(this@AniListActivity, "https://animeav1.com/media/${mapping.slug}")
        }
    }

    companion object {
        private const val EXTRA_TAB = "tab"
        const val TAB_HISTORY = 0
        const val TAB_PLANNING = 2

        fun open(context: Context, tab: Int = 1) {
            context.startActivity(Intent(context, AniListActivity::class.java).putExtra(EXTRA_TAB, tab))
        }
    }
}
