package eu.kanade.tachiyomi.ui.catalogue.global_search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import com.jakewharton.rxbinding.support.v7.widget.queryTextChangeEvents
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.ui.base.controller.NucleusController
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.view.RecyclerWindowInsetsListener
import kotlinx.android.synthetic.main.catalogue_global_search_controller.*

/**
 * This controller shows and manages the different search result in global search.
 * This controller should only handle UI actions, IO actions should be done by [CatalogueSearchPresenter]
 * [CatalogueSearchCardAdapter.OnMangaClickListener] called when manga is clicked in global search
 */
open class CatalogueSearchController(
        protected val initialQuery: String? = null,
        protected val extensionFilter: String? = null
) : NucleusController<CatalogueSearchPresenter>(),
        CatalogueSearchCardAdapter.OnMangaClickListener {

    /**
     * Adapter containing search results grouped by lang.
     */
    protected var adapter: CatalogueSearchAdapter? = null

    private var customTitle:String? = null

    /**
     * Called when controller is initialized.
     */
    init {
        setHasOptionsMenu(true)
    }

    /**
     * Initiate the view with [R.layout.catalogue_global_search_controller].
     *
     * @param inflater used to load the layout xml.
     * @param container containing parent views.
     * @return inflated view
     */
    override fun inflateView(inflater: LayoutInflater, container: ViewGroup): android.view.View {
        return inflater.inflate(R.layout.catalogue_global_search_controller, container, false)
    }

    /**
     * Set the title of controller.
     *
     * @return title.
     */
    override fun getTitle(): String? {
        return customTitle ?: presenter.query
    }

    /**
     * Create the [CatalogueSearchPresenter] used in controller.
     *
     * @return instance of [CatalogueSearchPresenter]
     */
    override fun createPresenter(): CatalogueSearchPresenter {
        return CatalogueSearchPresenter(initialQuery, extensionFilter)
    }

    /**
     * Called when manga in global search is clicked, opens manga.
     *
     * @param manga clicked item containing manga information.
     */
    override fun onMangaClick(manga: Manga) {
        // Open MangaController.
        router.pushController(MangaController(manga, true).withFadeTransaction())
    }

    /**
     * Called when manga in global search is long clicked.
     *
     * @param manga clicked item containing manga information.
     */
    override fun onMangaLongClick(manga: Manga) {
        // Delegate to single click by default.
        onMangaClick(manga)
    }

    /**
     * Adds items to the options menu.
     *
     * @param menu menu containing options.
     * @param inflater used to load the menu xml.
     */
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        // Inflate menu.
        inflater.inflate(R.menu.catalogue_new_list, menu)

        // Initialize search menu
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView

        searchItem.isVisible = customTitle == null
        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                searchView.onActionViewExpanded() // Required to show the query in the view
                searchView.setQuery(presenter.query, false)
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                return true
            }
        })

        searchView.queryTextChangeEvents()
                .filter { it.isSubmitted }
                .subscribeUntilDestroy {
                    presenter.search(it.queryText().toString())
                    searchItem.collapseActionView()
                    setTitle() // Update toolbar title
                }
    }

    /**
     * Called when the view is created
     *
     * @param view view of controller
     */
    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        adapter = CatalogueSearchAdapter(this)

        // Create recycler and set adapter.
        recycler.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(view.context)
        recycler.adapter = adapter
        recycler.setOnApplyWindowInsetsListener(RecyclerWindowInsetsListener)
        if (extensionFilter != null)
        {
            customTitle = view.context?.getString(R.string.loading)
            setTitle()
        }
    }

    override fun onDestroyView(view: View) {
        adapter = null
        super.onDestroyView(view)
    }

    override fun onSaveViewState(view: View, outState: Bundle) {
        super.onSaveViewState(view, outState)
        adapter?.onSaveInstanceState(outState)
    }

    override fun onRestoreViewState(view: View, savedViewState: Bundle) {
        super.onRestoreViewState(view, savedViewState)
        adapter?.onRestoreInstanceState(savedViewState)
    }

    /**
     * Returns the view holder for the given manga.
     *
     * @param source used to find holder containing source
     * @return the holder of the manga or null if it's not bound.
     */
    private fun getHolder(source: CatalogueSource): CatalogueSearchHolder? {
        val adapter = adapter ?: return null

        adapter.allBoundViewHolders.forEach { holder ->
            val item = adapter.getItem(holder.adapterPosition)
            if (item != null && source.id == item.source.id) {
                return holder as CatalogueSearchHolder
            }
        }

        return null
    }

    /**
     * Add search result to adapter.
     *
     * @param searchResult result of search.
     */
    fun setItems(searchResult: List<CatalogueSearchItem>) {
        if (extensionFilter != null) {
            val results = searchResult.first().results
            if (results != null && results.size == 1) {
                val manga = results.first().manga
                router.replaceTopController(MangaController(manga,true,fromExtension = true)
                    .withFadeTransaction()
                )
                return
            }
            else if (results != null) {
                customTitle = null
                setTitle()
                activity?.invalidateOptionsMenu()
            }
        }
        adapter?.updateDataSet(searchResult)
    }

    /**
     * Called from the presenter when a manga is initialized.
     *
     * @param manga the initialized manga.
     */
    fun onMangaInitialized(source: CatalogueSource, manga: Manga) {
        getHolder(source)?.setImage(manga)
    }

}
