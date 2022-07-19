package eu.kanade.tachiyomi.data.backup.full.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/*
* SY saved searches class
 */
@Serializable
data class BackupSavedSearch(
    @ProtoNumber(1) val name: String,
    @ProtoNumber(2) val query: String = "",
    @ProtoNumber(3) val filterList: String = "",
    @ProtoNumber(4) val source: Long = 0,
)

val backupSavedSearchMapper = {
        _: Long,
        source: Long,
        name: String,
        query: String?,
        filtersJson: String?,  ->
    BackupSavedSearch(
        source = source,
        name = name,
        query = query.orEmpty(),
        filterList = filtersJson ?: "[]",
    )
}
