package com.milan.game.ui.tutorial

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milan.game.services.GameService
import com.milan.game.services.WriteOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 新手引导 ViewModel（第 8 节）。
 *
 * 只负责：读当前步、跳过、开场确认。业务完成步由 GameService 在
 * pull / setFormation / levelUp / 战斗胜利路径自动写入，本 VM 订阅 snapshot 刷新。
 */
class TutorialViewModel(
    private val service: GameService,
) : ViewModel() {

    private val _currentStep = MutableStateFlow(service.tutorialCurrentStep())
    val currentStep: StateFlow<String?> = _currentStep.asStateFlow()

    private val _finished = MutableStateFlow(service.tutorialFinished())
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    init {
        // snapshot revision 变化时重读（写操作成功后 refreshSnapshot）
        viewModelScope.launch {
            service.snapshot.collect {
                _currentStep.value = service.tutorialCurrentStep()
                _finished.value = service.tutorialFinished()
            }
        }
    }

    fun refresh() {
        _currentStep.value = service.tutorialCurrentStep()
        _finished.value = service.tutorialFinished()
    }

    suspend fun completeStep(step: String): WriteOutcome {
        val r = service.completeTutorialStep(step)
        refresh()
        return r
    }

    suspend fun skip(): WriteOutcome {
        val r = service.skipTutorial()
        refresh()
        return r
    }
}
