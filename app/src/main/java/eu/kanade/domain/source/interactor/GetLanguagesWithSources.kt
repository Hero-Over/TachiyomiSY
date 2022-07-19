package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.model.Source
import eu.kanade.domain.source.repository.SourceRepository
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.system.LocaleHelper
import exh.source.BlacklistedSources
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class GetLanguagesWithSources(
    private val repository: SourceRepository,
    private val preferences: PreferencesHelper,
) {

    fun subscribe(): Flow<Map<String, List<Source>>> {
        return combine(
            preferences.enabledLanguages().asFlow(),
            preferences.disabledSources().asFlow(),
            repository.getOnlineSources(),
        ) { enabledLanguage, disabledSource, onlineSources ->
            val sortedSources = onlineSources.filterNot { it.id in BlacklistedSources.HIDDEN_SOURCES }.sortedWith(
                compareBy<Source> { it.id.toString() in disabledSource }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
            )

            sortedSources.groupBy { it.lang }
                .toSortedMap(
                    compareBy(
                        { it !in enabledLanguage },
                        { LocaleHelper.getDisplayName(it) },
                    ),
                )
        }
    }
}
