package eu.kanade.tachiyomi.ui.browse.source.browse

import android.view.View
import androidx.core.view.isVisible
import coil.dispose
import coil.load
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.coil.MangaCoverFetcher
import eu.kanade.tachiyomi.databinding.SourceListItemBinding
import eu.kanade.tachiyomi.util.system.getResourceColor
import exh.metadata.metadata.MangaDexSearchMetadata
import exh.metadata.metadata.base.RaisedSearchMetadata

/**
 * Class used to hold the displayed data of a manga in the catalogue, like the cover or the title.
 * All the elements from the layout file "item_catalogue_list" are available in this class.
 *
 * @param view the inflated view for this holder.
 * @param adapter the adapter handling this holder.
 * @constructor creates a new catalogue holder.
 */
class SourceListHolder(private val view: View, adapter: FlexibleAdapter<*>) :
    SourceHolder<SourceListItemBinding>(view, adapter) {

    override val binding = SourceListItemBinding.bind(view)

    private val favoriteColor = view.context.getResourceColor(R.attr.colorOnSurface, 0.38f)
    private val unfavoriteColor = view.context.getResourceColor(R.attr.colorOnSurface)

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
        binding.badges.clipToOutline = true

        // Set favorite badge
        binding.favoriteText.isVisible = manga.favorite

        setImage(manga)
    }

    // SY -->
    override fun onSetMetadataValues(manga: Manga, metadata: RaisedSearchMetadata) {
        if (metadata is MangaDexSearchMetadata) {
            metadata.followStatus?.let {
                binding.localText.text = itemView.context.resources.getStringArray(R.array.md_follows_options).asList()[it]
                binding.localText.isVisible = true
            }
            metadata.relation?.let {
                binding.localText.setText(it.resId)
                binding.localText.isVisible = true
            }
        }
    }
    // SY <--

    override fun setImage(manga: Manga) {
        binding.thumbnail.dispose()
        if (!manga.thumbnailUrl.isNullOrEmpty()) {
            binding.thumbnail.load(manga) {
                setParameter(MangaCoverFetcher.USE_CUSTOM_COVER, false)
            }
        }
    }
}
