package com.thep.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thep.core.data.Preferences
import com.thep.core.data.WorkoutRepository
import com.thep.core.model.*
import com.thep.feature.workout.MoveInput
import com.thep.feature.workout.SetInput
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@HiltViewModel
class MainViewModel
@Inject
constructor(private val repository: WorkoutRepository, private val preferences: Preferences) :
    ViewModel() {
    val data =
        repository.dashboard.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            Dashboard(),
        )
    val autoRest =
        preferences.autoRest.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _initialized = MutableStateFlow(false)
    val initialized = _initialized.asStateFlow()
    private val _preview = MutableStateFlow<ImportPreview?>(null)
    val preview = _preview.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val mutex = Mutex()
    private var pendingImport: String? = null

    init {
        perform {
            repository.initialize()
            _initialized.value = true
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun notify(message: String) {
        _message.value = message
    }

    private fun perform(block: suspend () -> Unit) {
        viewModelScope.launch {
            mutex.withLock {
                _busy.value = true
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _message.value =
                        when (e) {
                            is IllegalArgumentException,
                            is IllegalStateException -> e.message?.take(180) ?: "输入或状态无效"
                            else -> "操作失败，数据未提交。请检查文件或重试。"
                        }
                } finally {
                    _busy.value = false
                }
            }
        }
    }

    fun activatePlan(id: String) = perform { repository.activatePlan(id) }

    fun createPlan(name: String) = perform { repository.createPlan(name) }

    fun saveDay(id: String, title: String, weekday: Int) = perform {
        repository.saveDay(id, title, weekday)
    }

    fun start(dayId: String, onStarted: (String) -> Unit) = perform {
        onStarted(repository.start(dayId))
    }

    fun saveSet(sessionId: String, value: SetInput) = perform {
        repository.saveSet(
            sessionId,
            value.id,
            value.weight,
            value.reps,
            value.rir,
            value.completed,
            value.warmup,
            autoRest.value,
        )
        _message.value = "本组已保存"
    }

    fun status(id: String, target: SessionStatus) = perform {
        repository.status(id, target)
        _message.value = "训练状态已保存"
    }

    fun timer(id: String, seconds: Int) = perform { repository.timer(id, seconds) }

    fun warmup(id: String, exerciseId: String) = perform { repository.addWarmup(id, exerciseId) }

    fun note(id: String, note: String) = perform {
        repository.saveNote(id, note)
        _message.value = "备注已保存"
    }

    fun saveMove(dayId: String, value: MoveInput) = perform {
        repository.saveMove(
            dayId,
            value.id,
            value.name,
            value.sets,
            value.min,
            value.max,
            value.rest,
        )
        _message.value = "课表已保存"
    }

    fun removeMove(dayId: String, moveId: String) = perform { repository.removeMove(dayId, moveId) }

    fun reorder(dayId: String, moveId: String, direction: Int) = perform {
        repository.reorder(dayId, moveId, direction)
    }

    fun autoRest(value: Boolean) = perform { preferences.autoRest(value) }

    fun body(weight: String, waist: String) = perform {
        repository.addBody(weight, waist)
        _message.value = "身体记录已保存"
    }

    fun cardio(date: String, kind: String, minutes: String, stairs: String) = perform {
        repository.addCardio(
            date,
            kind,
            if (minutes.isBlank()) 0 else minutes.toInt(),
            if (stairs.isBlank()) 0 else stairs.toInt(),
        )
        _message.value = "有氧记录已保存"
    }

    fun preview(read: suspend () -> String) = perform {
        val text = withContext(Dispatchers.IO) { read() }
        val result = repository.preview(text)
        pendingImport = text
        _preview.value = result
    }

    fun cancelImport() {
        _preview.value = null
        pendingImport = null
    }

    fun confirmImport() = perform {
        val text = pendingImport ?: error("请重新选择文件")
        val result = repository.importJson(text)
        cancelImport()
        _message.value =
            "导入完成：新增 ${result.sessions} 场，跳过 ${result.skipped} 场；身体 ${result.body} 条，有氧 ${result.cardio} 条。请核对进度页。"
    }

    fun export(write: suspend (String) -> Unit) = perform {
        val text = repository.exportJson()
        withContext(Dispatchers.IO) { write(text) }
        _message.value = "备份已导出"
    }
}
