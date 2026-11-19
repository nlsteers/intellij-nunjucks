package uk.steers.nunjucks.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker

class NunjucksProjectState : BaseState() {
    /** Template search paths, relative to the project root or absolute. Empty means auto-detect. */
    var templateRoots by list<String>()
    var extraFilters by list<String>()
    var extraTests by list<String>()
    var extraGlobals by list<String>()
}

@Service(Service.Level.PROJECT)
@State(name = "NunjucksSettings", storages = [Storage("nunjucks.xml")])
class NunjucksSettings : SimplePersistentStateComponent<NunjucksProjectState>(NunjucksProjectState()) {
    val modificationTracker = SimpleModificationTracker()

    fun update(block: NunjucksProjectState.() -> Unit) {
        state.block()
        modificationTracker.incModificationCount()
    }

    override fun loadState(state: NunjucksProjectState) {
        super.loadState(state)
        modificationTracker.incModificationCount()
    }

    companion object {
        fun getInstance(project: Project): NunjucksSettings = project.service()
    }
}

class NunjucksAppState : BaseState() {
    var detectInHtmlFiles by property(true)
}

@Service(Service.Level.APP)
@State(name = "NunjucksAppSettings", storages = [Storage("nunjucks.xml")])
class NunjucksAppSettings : SimplePersistentStateComponent<NunjucksAppState>(NunjucksAppState()) {
    companion object {
        fun getInstance(): NunjucksAppSettings = service()
    }
}
