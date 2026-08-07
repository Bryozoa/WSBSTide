import com.example.wsbstide.model.TideEvent
import com.example.wsbstide.model.TidePoint

data class TideUiState(
    val selectedDateMillis: Long,
    val points: List<TidePoint>,
    val events: List<TideEvent>,
    val isLoading: Boolean,
    val error: String?,
)