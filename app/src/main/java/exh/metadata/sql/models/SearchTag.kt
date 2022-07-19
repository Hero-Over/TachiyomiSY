package exh.metadata.sql.models

import kotlinx.serialization.Serializable

@Serializable
data class SearchTag(
    // Tag identifier, unique
    val id: Long?,

    // Metadata this tag is attached to
    val mangaId: Long,

    // Tag namespace
    val namespace: String?,

    // Tag name
    val name: String,

    // Tag type
    val type: Int,
)
