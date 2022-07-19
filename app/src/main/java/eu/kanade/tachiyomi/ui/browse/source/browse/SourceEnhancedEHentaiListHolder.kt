package eu.kanade.tachiyomi.ui.browse.source.browse

import android.view.View
import androidx.core.view.isVisible
import coil.dispose
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.coil.MangaCoverFetcher
import eu.kanade.tachiyomi.databinding.SourceEnhancedEhentaiListItemBinding
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.view.loadAutoPause
import exh.metadata.MetadataUtil
import exh.metadata.metadata.EHentaiSearchMetadata
import exh.metadata.metadata.base.RaisedSearchMetadata
import exh.util.SourceTagsUtil
import java.util.Date

/**
 * Class used to hold the displayed data of a manga in the catalogue, like the cover or the title.
 * All the elements from the layout file "item_catalogue_list" are available in this class.
 *
 * @param view the inflated view for this holder.
 * @param adapter the adapter handling this holder.
 * @constructor creates a new catalogue holder.
 */
class SourceEnhancedEHentaiListHolder(view: View, adapter: FlexibleAdapter<*>) :
    SourceHolder<SourceEnhancedEhentaiListItemBinding>(view, adapter) {

    override val binding = SourceEnhancedEhentaiListItemBinding.bind(itemView)

    private val favoriteColor = itemView.context.getResourceColor(R.attr.colorOnSurface, 0.38f)
    private val unfavoriteColor = itemView.context.getResourceColor(R.attr.colorOnSurface)

    /**
     * Method called from [CatalogueAdapter.onBindViewHolder]. It updates the data for this
     * holder with the given manga.
     *
     * @param manga the manga to bind.
     */
    override fun onSetValues(manga: Manga) {
        binding.title.text = manga.title
        binding.title.setTextColor(if (manga.favorite) favoriteColor else unfavoriteColor)

        // Set alpha of thumbnail.
        binding.thumbnail.alpha = if (manga.favorite) 0.3f else 1.0f

        // For rounded corners
        binding.badges.leftBadges.clipToOutline = true
        binding.badges.rightBadges.clipToOutline = true

        // Set favorite badge
        binding.badges.favoriteText.isVisible = manga.favorite

        setImage(manga)
    }

    override fun onSetMetadataValues(manga: Manga, metadata: RaisedSearchMetadata) {
        if (metadata !is EHentaiSearchMetadata) return

        if (metadata.uploader != null) {
            binding.uploader.text = metadata.uploader
        }

        val pair = when (metadata.genre) {
            "doujinshi" -> SourceTagsUtil.GenreColor.DOUJINSHI_COLOR to R.string.doujinshi
            "manga" -> SourceTagsUtil.GenreColor.MANGA_COLOR to R.string.manga
            "artistcg" -> SourceTagsUtil.GenreColor.ARTIST_CG_COLOR to R.string.artist_cg
            "gamecg" -> SourceTagsUtil.GenreColor.GAME_CG_COLOR to R.string.game_cg
            "western" -> SourceTagsUtil.GenreColor.WESTERN_COLOR to R.string.western
            "non-h" -> SourceTagsUtil.GenreColor.NON_H_COLOR to R.string.non_h
            "imageset" -> SourceTagsUtil.GenreColor.IMAGE_SET_COLOR to R.string.image_set
            "cosplay" -> SourceTagsUtil.GenreColor.COSPLAY_COLOR to R.string.cosplay
            "asianporn" -> SourceTagsUtil.GenreColor.ASIAN_PORN_COLOR to R.string.asian_porn
            "misc" -> SourceTagsUtil.GenreColor.MISC_COLOR to R.string.misc
            else -> null
        }

        if (pair != null) {
            binding.genre.setBackgroundColor(pair.first.color)
            binding.genre.text = itemView.context.getString(pair.second)
        } else binding.genre.text = metadata.genre

        metadata.datePosted?.let { binding.datePosted.text = MetadataUtil.EX_DATE_FORMAT.format(Date(it)) }

        metadata.averageRating?.let { binding.ratingBar.rating = it.toFloat() }

        val locale = SourceTagsUtil.getLocaleSourceUtil(
            metadata.tags
                .firstOrNull { it.namespace == EHentaiSearchMetadata.EH_LANGUAGE_NAMESPACE }
                ?.name,
        )
        val pageCount = metadata.length

        binding.language.text = if (locale != null && pageCount != null) {
            itemView.resources.getQuantityString(R.plurals.browse_language_and_pages, pageCount, pageCount, locale.toLanguageTag().uppercase())
        } else if (pageCount != null) {
            itemView.resources.getQuantityString(R.plurals.num_pages, pageCount, pageCount)
        } else locale?.toLanguageTag()?.uppercase()
    }

    override fun setImage(manga: Manga) {
        binding.thumbnail.dispose()
        binding.thumbnail.loadAutoPause(manga) {
            setParameter(MangaCoverFetcher.USE_CUSTOM_COVER, false)
        }
    }
}
