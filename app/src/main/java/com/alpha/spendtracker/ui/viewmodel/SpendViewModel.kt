/**
 * ViewModel that manages spend data and analytics for the UI components.
 */
package com.alpha.spendtracker.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alpha.spendtracker.data.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

enum class TimeFilter {
    DAY, WEEK, MONTH, YEAR, ALL
}

/**
 * Main ViewModel to manage Spending Tracker operations, analytics, and states
 */
class SpendViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: SpendRepository
    private val auth = FirebaseAuth.getInstance()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = SpendRepository(database.spendDao())
        
        // Listen to auth changes
        auth.addAuthStateListener { firebaseAuth ->
            _userId.value = firebaseAuth.currentUser?.uid ?: "anonymous"
        }
    }

    private val _userId = MutableStateFlow(auth.currentUser?.uid ?: "anonymous")

    // Raw spends flow from Room database filtered by current user
    @OptIn(ExperimentalCoroutinesApi::class)
    val allSpendsFlow: StateFlow<List<Spend>> = _userId.flatMapLatest { userId ->
        repository.getAllSpends(userId)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val selectedFilter = MutableStateFlow(TimeFilter.MONTH)

    val uiState: StateFlow<SpendingAnalytics> = combine(
        allSpendsFlow,
        selectedFilter
    ) { spends, filter ->
        calculateAnalytics(filterSpendsByTime(spends, filter), filter)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SpendingAnalytics()
    )

    fun addSpend(
        appName: String,
        amount: Double,
        purpose: String,
        category: String,
        notes: String = "",
        timestamp: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch {
            val spend = Spend(
                userId = _userId.value,
                appName = appName,
                amount = amount,
                purpose = purpose,
                category = category,
                timestamp = timestamp,
                notes = notes
            )
            repository.insert(spend)
        }
    }

    fun deleteSpend(spend: Spend) {
        viewModelScope.launch {
            repository.delete(spend)
        }
    }

    fun deleteSpendById(id: Int) {
        viewModelScope.launch {
            repository.deleteById(id, _userId.value)
        }
    }

    fun setFilter(filter: TimeFilter) {
        selectedFilter.value = filter
    }

    // Helper: Filter spends mathematically based on selected TimeFilter
    private fun filterSpendsByTime(spends: List<Spend>, filter: TimeFilter): List<Spend> {
        if (filter == TimeFilter.ALL) return spends

        val now = Calendar.getInstance()
        val startOfPeriod = Calendar.getInstance().apply {
            // Reset fields
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        when (filter) {
            TimeFilter.DAY -> {
                // Today 00:00 to 23:59:59
                // Already set to start of today.
            }
            TimeFilter.WEEK -> {
                // First day of current week (e.g., Sunday or Monday)
                startOfPeriod.set(Calendar.DAY_OF_WEEK, startOfPeriod.firstDayOfWeek)
            }
            TimeFilter.MONTH -> {
                // Start of the calendar month
                startOfPeriod.set(Calendar.DAY_OF_MONTH, 1)
            }
            TimeFilter.YEAR -> {
                // Jan 1
                startOfPeriod.set(Calendar.DAY_OF_YEAR, 1)
            }
            TimeFilter.ALL -> {}
        }

        val startMillis = startOfPeriod.timeInMillis
        return spends.filter { it.timestamp >= startMillis }
    }

    // Helper: Calculate advanced metrics and grouping data categories for high-fidelity dashboards
    private fun calculateAnalytics(spends: List<Spend>, filter: TimeFilter): SpendingAnalytics {
        if (spends.isEmpty()) {
            return SpendingAnalytics(totalAmount = 0.0, filterType = filter)
        }

        val total = spends.sumOf { it.amount }

        // Category breakdown
        val categoryTotals = spends.groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }

        // Purpose (Lending, Groceries etc.) breakdown
        val purposeTotals = spends.groupBy { it.purpose }
            .mapValues { entry -> entry.value.sumOf { it.amount } }

        // App/Platform-wise breakdown
        val appTotals = spends.groupBy { it.appName }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }

        // Trend Breakdown for beautiful graph (bar/line charts based on day index or calendar buckets)
        val trendPoints = calculateTrendPoints(spends, filter)

        // Count for friend lending specifically to display metrics/badge
        val friendLendingTotal = spends.filter { 
            it.purpose == "Friend Lending" || it.appName == "Friend Lending" 
        }.sumOf { it.amount }

        return SpendingAnalytics(
            totalAmount = total,
            categoryBreakdown = categoryTotals,
            purposeBreakdown = purposeTotals,
            appBreakdown = appTotals,
            trendPoints = trendPoints,
            friendLendingTotal = friendLendingTotal,
            transactionCount = spends.size,
            filterType = filter
        )
    }

    private fun calculateTrendPoints(spends: List<Spend>, filter: TimeFilter): List<TrendPoint> {
        val calendar = Calendar.getInstance()
        
        return when (filter) {
            TimeFilter.DAY -> {
                // Group by hour
                spends.groupBy {
                    calendar.timeInMillis = it.timestamp
                    calendar.get(Calendar.HOUR_OF_DAY)
                }.map { (hour, items) ->
                    val total = items.sumOf { it.amount }
                    val hourStr = String.format(Locale.getDefault(), "%02d:00", hour)
                    TrendPoint(label = hourStr, amount = total, sortKey = hour)
                }.sortedBy { it.sortKey }
            }
            TimeFilter.WEEK -> {
                // Group by Day of Week
                val dayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                spends.groupBy {
                    calendar.timeInMillis = it.timestamp
                    calendar.get(Calendar.DAY_OF_WEEK)
                }.map { (dayOfWeek, items) ->
                    val total = items.sumOf { it.amount }
                    val name = dayNames.getOrElse(dayOfWeek - 1) { "Day" }
                    TrendPoint(label = name, amount = total, sortKey = dayOfWeek)
                }.sortedBy { it.sortKey }
            }
            TimeFilter.MONTH -> {
                // Group by day of month (1 to 31)
                spends.groupBy {
                    calendar.timeInMillis = it.timestamp
                    calendar.get(Calendar.DAY_OF_MONTH)
                }.map { (dayOfMonth, items) ->
                    val total = items.sumOf { it.amount }
                    TrendPoint(label = dayOfMonth.toString(), amount = total, sortKey = dayOfMonth)
                }.sortedBy { it.sortKey }
            }
            TimeFilter.YEAR -> {
                // Group by code of month (0 to 11)
                val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                spends.groupBy {
                    calendar.timeInMillis = it.timestamp
                    calendar.get(Calendar.MONTH)
                }.map { (monthNum, items) ->
                    val total = items.sumOf { it.amount }
                    val name = monthNames.getOrElse(monthNum) { "Month" }
                    TrendPoint(label = name, amount = total, sortKey = monthNum)
                }.sortedBy { it.sortKey }
            }
            TimeFilter.ALL -> {
                // Group by month-year or simply calendar year
                spends.groupBy {
                    calendar.timeInMillis = it.timestamp
                    calendar.get(Calendar.YEAR)
                }.map { (yr, items) ->
                    val total = items.sumOf { it.amount }
                    TrendPoint(label = yr.toString(), amount = total, sortKey = yr)
                }.sortedBy { it.sortKey }
            }
        }
    }
}

/**
 * Encapsulates spending metrics, groups, and breakdown reports for user interface rendering
 */
data class SpendingAnalytics(
    val totalAmount: Double = 0.0,
    val categoryBreakdown: Map<String, Double> = emptyMap(),
    val purposeBreakdown: Map<String, Double> = emptyMap(),
    val appBreakdown: List<Pair<String, Double>> = emptyList(),
    val trendPoints: List<TrendPoint> = emptyList(),
    val friendLendingTotal: Double = 0.0,
    val transactionCount: Int = 0,
    val filterType: TimeFilter = TimeFilter.MONTH
)

/**
 * Representation of one interval aggregate in trend visualization
 */
data class TrendPoint(
    val label: String,
    val amount: Double,
    val sortKey: Int
)

/**
 * Factory class to instantiate Android ViewModel with local context setup
 */
class SpendViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SpendViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SpendViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
