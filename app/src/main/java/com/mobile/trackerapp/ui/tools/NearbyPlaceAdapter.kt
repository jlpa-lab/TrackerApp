package com.location.and.phone.tracker.locationnphonetracker.ui.tools

import android.content.Context
import android.graphics.Color
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.location.and.phone.tracker.locationnphonetracker.R
import java.util.Locale
import kotlin.math.roundToInt

internal class NearbyPlaceAdapter(
    context: Context,
    private val openDirections: (NearbyPlace) -> Unit
) : ListAdapter<NearbyPlace, NearbyPlaceAdapter.PlaceViewHolder>(DIFF) {
    private val appContext = context
    private val ink = Color.rgb(17, 24, 39)
    private val muted = Color.rgb(126, 137, 153)
    private val green = Color.rgb(0, 164, 76)
    private val pale = Color.rgb(237, 249, 244)

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): PlaceViewHolder =
        PlaceViewHolder(createCard(parent.context).apply {
            // RecyclerView rows must use the full content width; otherwise long
            // and short place names produce different card widths.
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            )
        })

    override fun onBindViewHolder(holder: PlaceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private fun createCard(context: Context): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(11), dp(10), dp(10), dp(10))
            background = bordered(Color.WHITE, Color.rgb(229, 237, 233), 16)
        }
        val icon = ImageView(context).apply {
            id = R.id.nearby_place_icon
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(pale, 8)
            clipToOutline = true
        }
        val photo = ImageView(context).apply {
            id = R.id.nearby_place_photo
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(pale, 8)
            clipToOutline = true
            contentDescription = appContext.getString(R.string.nearby_place_photo)
            visibility = View.GONE
        }
        val thumbnail = FrameLayout(context).apply {
            addView(photo, FrameLayout.LayoutParams(-1, -1))
            addView(icon, FrameLayout.LayoutParams(-1, -1))
        }
        root.addView(thumbnail, LinearLayout.LayoutParams(dp(52), dp(60)).apply { marginEnd = dp(10) })
        val details = LinearLayout(context).apply {
            id = R.id.nearby_place_details
            orientation = LinearLayout.VERTICAL
        }
        details.addView(TextView(context).apply { id = R.id.nearby_place_name })
        details.addView(TextView(context).apply { id = R.id.nearby_place_meta })
        details.addView(TextView(context).apply { id = R.id.nearby_place_status })
        root.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(ImageView(context).apply {
            id = R.id.nearby_place_direction
            setImageResource(R.drawable.ic_nearby_direction)
            setPadding(dp(9), dp(9), dp(9), dp(9))
            background = rounded(pale, 12)
            isClickable = true
            isFocusable = true
            contentDescription = appContext.getString(R.string.nearby_directions)
        }, LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginStart = dp(8) })
        return root
    }

    private fun NearbyPlaceAdapter.PlaceViewHolder.bind(place: NearbyPlace) {
        val icon = itemView.findViewById<ImageView>(R.id.nearby_place_icon)
        val photo = itemView.findViewById<ImageView>(R.id.nearby_place_photo)
        icon.apply {
            setImageResource(NearbyCategoryCatalog.iconFor(place.kind))
            imageTintList = ColorStateList.valueOf(green)
            contentDescription = appContext.getString(R.string.nearby_category_accessibility, place.name)
        }
        photo.setImageDrawable(null)
        if (place.imageUrl.isNullOrBlank()) {
            photo.visibility = View.GONE
            icon.visibility = View.VISIBLE
        } else {
            photo.visibility = View.VISIBLE
            icon.visibility = View.GONE
            Glide.with(itemView).load(place.imageUrl).into(photo)
        }
        itemView.findViewById<TextView>(R.id.nearby_place_name).apply {
            text = place.name
            textSize = 15f
            setTextColor(ink)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        itemView.findViewById<TextView>(R.id.nearby_place_meta).apply {
            val rating = place.rating?.let { rating ->
                "★ $rating${place.reviewCount?.let { count -> " ($count)" } ?: ""}"
            }
            text = listOfNotNull(rating, place.kind.takeIf { it.isNotBlank() }).joinToString("  · ")
            textSize = 12f
            setTextColor(muted)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(3), 0, 0)
        }
        itemView.findViewById<TextView>(R.id.nearby_place_status).apply {
            val distance = "${formatDistance(place.distanceMeters)}  ·  ${appContext.getString(R.string.nearby_walking_time, walkingTime(place.distanceMeters))}"
            text = listOfNotNull(place.openingHours, distance).joinToString("  · ")
            textSize = 12f
            setTextColor(if (place.openingHours != null) green else muted)
            typeface = if (place.openingHours != null) Typeface.create("sans-serif", Typeface.BOLD) else Typeface.DEFAULT
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(3), 0, 0)
        }
        itemView.findViewById<ImageView>(R.id.nearby_place_direction).apply {
            contentDescription = appContext.getString(R.string.nearby_directions_to, place.name)
            setOnClickListener { openDirections(place) }
        }
        itemView.setOnClickListener { openDirections(place) }
        (itemView.layoutParams as? RecyclerView.LayoutParams)?.let { params ->
            params.bottomMargin = dp(8)
            itemView.layoutParams = params
        }
    }

    private fun formatDistance(meters: Float): String = if (meters < 1000) "${meters.roundToInt()} m" else String.format(Locale.getDefault(), "%.1f km", meters / 1000f)
    private fun walkingTime(meters: Float): Int = maxOf(1, (meters / 80f).roundToInt())
    private fun dp(value: Int): Int = (value * appContext.resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radiusDp).toFloat() }
    private fun bordered(color: Int, stroke: Int, radiusDp: Int) = rounded(color, radiusDp).apply { setStroke(dp(1), stroke) }

    internal class PlaceViewHolder(view: View) : RecyclerView.ViewHolder(view)

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<NearbyPlace>() {
            override fun areItemsTheSame(oldItem: NearbyPlace, newItem: NearbyPlace) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: NearbyPlace, newItem: NearbyPlace) = oldItem == newItem
        }
    }
}
