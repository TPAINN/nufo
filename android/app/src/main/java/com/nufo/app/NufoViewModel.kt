package com.nufo.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nufo.app.data.Diet
import com.nufo.app.data.Identified
import com.nufo.app.data.Lookup
import com.nufo.app.data.OffParser
import com.nufo.app.data.PriceReport
import com.nufo.app.data.Product
import com.nufo.app.data.SearchFilters
import com.nufo.app.data.SearchHit
import com.nufo.app.data.Settings
import com.nufo.app.data.ThemeMode
import com.nufo.app.data.Units
import com.nufo.app.data.LookupStep
import com.nufo.app.data.onlineFlow
import com.nufo.app.ui.dataLang
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

sealed interface ResultState {
    /** [preview] is what the tapped row already showed, painted while the full record loads. */
    data class Loading(val preview: SearchHit? = null, val step: LookupStep? = null) : ResultState
    /** [cachedAt] is set when this is a copy saved on the phone because the network was unavailable. */
    data class Loaded(val product: Product, val cachedAt: Long? = null) : ResultState
    data class NotFound(val barcode: String?, val identified: Identified? = null) : ResultState
    data class Error(val offline: Boolean, val retry: () -> Unit) : ResultState
}

sealed interface PricesState {
    data object Loading : PricesState
    data class Loaded(val reports: List<PriceReport>) : PricesState
    data object Failed : PricesState
}

data class SearchState(
    val query: String = "",
    val filters: SearchFilters = SearchFilters(),
    val loading: Boolean = false,
    val hits: List<SearchHit> = emptyList(),
    /** Non-null when every source failed and nothing was saved offline either. */
    val error: String? = null,
    val searched: Boolean = false,
    /** Results come from products saved on this phone because the databases were unreachable. */
    val offline: Boolean = false,
)

class NufoViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as NufoApp).repository
    private val store = (app as NufoApp).settings

    val settings: StateFlow<Settings?> = store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    /** Null until the database has answered, so screens show loading rather than a false "empty". */
    val history: StateFlow<List<Product>?> = repo.history.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val online: StateFlow<Boolean> = app.onlineFlow().stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _result = MutableStateFlow<ResultState>(ResultState.Loading())
    val result = _result.asStateFlow()

    private val _prices = MutableStateFlow<PricesState>(PricesState.Loading)
    val prices = _prices.asStateFlow()

    private val _search = MutableStateFlow(SearchState())
    val search = _search.asStateFlow()
    private var searchJob: Job? = null

    init {
        // Coming back online: refresh whatever was answered from the offline fallback.
        viewModelScope.launch {
            online.drop(1).filter { it }.collect {
                val s = _search.value
                if (s.query.isNotBlank() && (s.offline || s.error != null)) retrySearch()
                (_result.value as? ResultState.Error)?.takeIf { e -> e.offline }?.retry?.invoke()
            }
        }
    }

    private fun show(lookup: Lookup, barcode: String?, retry: () -> Unit) {
        _result.value = when (lookup) {
            is Lookup.Found -> {
                viewModelScope.launch { repo.save(lookup.product) }
                loadPrices(lookup.product)
                ResultState.Loaded(lookup.product, lookup.cachedAt)
            }
            is Lookup.NotFound -> ResultState.NotFound(barcode, lookup.identified)
            is Lookup.Failed -> ResultState.Error(lookup.offline, retry)
        }
    }

    fun openBarcode(barcode: String) {
        _result.value = ResultState.Loading()
        viewModelScope.launch { show(repo.lookupBarcode(barcode, dataLang(), ::onStep), barcode) { openBarcode(barcode) } }
    }

    fun openHit(hit: SearchHit) {
        _result.value = ResultState.Loading(hit)
        viewModelScope.launch { show(repo.product(hit, dataLang(), ::onStep), hit.barcode) { openHit(hit) } }
    }

    private fun onStep(step: LookupStep) = _result.update { if (it is ResultState.Loading) it.copy(step = step) else it }

    /** A photographed dish with bundled generic nutrition: opens it and saves it to history. False if none. */
    fun openDish(key: String, name: String, photo: android.graphics.Bitmap?): Boolean {
        val dish = repo.dish(key, name) ?: return false
        openProduct(dish)
        // The user's own photo becomes the dish image once it is stored (off the main thread).
        viewModelScope.launch {
            val withPhoto = photo?.let { withContext(Dispatchers.IO) { savePhoto(it) } }?.let { dish.copy(imageUrl = it) } ?: dish
            _result.update { if (it is ResultState.Loaded && it.product.key == dish.key) ResultState.Loaded(withPhoto) else it }
            repo.save(withPhoto)
        }
        return true
    }

    /** Keeps a photographed plate in app storage (never uploaded), downscaled; returns its file URI. */
    private fun savePhoto(bitmap: android.graphics.Bitmap): String? = runCatching {
        val dir = java.io.File(getApplication<android.app.Application>().filesDir, "photos").apply { mkdirs() }
        val scale = minOf(1f, 1080f / maxOf(bitmap.width, bitmap.height))
        val small = android.graphics.Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        val file = java.io.File(dir, "${System.currentTimeMillis()}.jpg")
        file.outputStream().use { small.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
        android.net.Uri.fromFile(file).toString()
    }.getOrNull()

    fun openProduct(p: Product) {
        _result.value = ResultState.Loaded(p)
        loadPrices(p)
    }

    private fun loadPrices(p: Product) {
        val code = p.barcode
        if (code == null || p.source != OffParser.SOURCE) { _prices.value = PricesState.Loaded(emptyList()); return }
        _prices.value = PricesState.Loading
        viewModelScope.launch {
            _prices.value = repo.prices(code).fold({ PricesState.Loaded(it) }, { PricesState.Failed })
        }
    }

    fun setQuery(q: String) {
        _search.update { it.copy(query = q) }
        searchJob?.cancel()
        if (q.isBlank()) { _search.update { it.copy(hits = emptyList(), loading = false, error = null, searched = false, offline = false) }; return }
        searchJob = viewModelScope.launch {
            delay(450) // debounce typing; a Greek query fans out into several requests
            runSearch()
        }
    }

    fun retrySearch() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch { runSearch() }
    }

    fun setFilters(f: SearchFilters) {
        _search.update { it.copy(filters = f) }
        if (_search.value.query.isNotBlank()) retrySearch()
    }

    private suspend fun runSearch() {
        val s = _search.value
        _search.update { it.copy(loading = true, error = null) }
        val r = repo.search(s.query.trim(), s.filters, dataLang())
        _search.update {
            val outcome = r.getOrNull()
            it.copy(
                loading = false, searched = true,
                hits = outcome?.hits.orEmpty(), offline = outcome?.offline ?: false,
                error = r.exceptionOrNull()?.let { e -> e.message ?: e.javaClass.simpleName },
            )
        }
    }

    fun toggleSaved(p: Product) = viewModelScope.launch {
        if (history.value.orEmpty().any { it.key == p.key }) repo.delete(p) else repo.save(p)
    }
    fun delete(p: Product) = viewModelScope.launch { repo.delete(p) }
    fun restore(p: Product) = viewModelScope.launch { repo.save(p) }
    fun clearHistory() = viewModelScope.launch {
        repo.clearHistory()
        // Photographed plates are part of the history: they go too.
        withContext(Dispatchers.IO) { java.io.File(getApplication<android.app.Application>().filesDir, "photos").deleteRecursively() }
    }

    fun finishOnboarding() = viewModelScope.launch { store.setOnboarded() }
    fun setUnits(u: Units) = viewModelScope.launch { store.setUnits(u) }
    fun setTheme(t: ThemeMode) = viewModelScope.launch { store.setTheme(t) }
    fun setDiet(d: Diet) = viewModelScope.launch { store.setDiet(d) }
    fun toggleAllergen(tag: String) = viewModelScope.launch { store.toggleAllergen(tag) }
}