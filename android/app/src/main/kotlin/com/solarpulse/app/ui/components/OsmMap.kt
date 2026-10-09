package com.solarpulse.app.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.solarpulse.app.ui.color
import com.solarpulse.core.model.Site
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.TilesOverlay
import kotlin.math.sqrt

/** Pin radius by capacity (sqrt scale like the web map), in dp. */
fun pinRadiusDp(capacityKw: Double): Float = (7 + sqrt(capacityKw.coerceAtLeast(0.0)) * 0.55).toFloat().coerceIn(7f, 24f)

/** A status-coloured circular pin with a white ring, sized by capacity. */
private fun pinDrawable(context: Context, site: Site, selected: Boolean): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val r = pinRadiusDp(site.capacityKw) * density
    val ring = (if (selected) 4f else 2.5f) * density
    val size = ((r + ring) * 2 + 2 * density).toInt().coerceAtLeast(8)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val cx = size / 2f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = 0x33000000
    c.drawCircle(cx, cx + density, r + ring, paint)
    paint.color = android.graphics.Color.WHITE
    c.drawCircle(cx, cx, r + ring, paint)
    paint.color = site.status.color.toArgb()
    c.drawCircle(cx, cx, r, paint)
    if (selected) {
        paint.color = android.graphics.Color.WHITE
        c.drawCircle(cx, cx, r * 0.35f, paint)
    }
    return BitmapDrawable(context.resources, bmp)
}

/**
 * OpenStreetMap (osmdroid, no API key) with one marker per site. Tapping a marker calls
 * [onSiteClick]. With [interactive] = false it is a static mini map (site detail).
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun OsmMap(
    sites: List<Site>,
    modifier: Modifier = Modifier,
    selectedId: String? = null,
    focusId: String? = null,
    interactive: Boolean = true,
    dark: Boolean = false,
    onSiteClick: (Site) -> Unit = {},
) {
    val context = LocalContext.current
    val onClick = rememberUpdatedState(onSiteClick)
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(interactive)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            setHorizontalMapRepetitionEnabled(false)
            setTilesScaledToDpi(true)
            setMinZoomLevel(2.0)
            if (!interactive) setOnTouchListener { _, _ -> true }
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }
    val fitted = remember { booleanArrayOf(false) }
    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { map ->
            map.overlayManager.tilesOverlay.setColorFilter(if (dark) TilesOverlay.INVERT_COLORS else null)
            map.overlays.removeAll { it is Marker }
            // Larger pins first so small ones stay tappable on top.
            sites.sortedByDescending { it.capacityKw }.forEach { site ->
                val marker = Marker(map).apply {
                    position = GeoPoint(site.lat, site.lng)
                    icon = pinDrawable(context, site, site.id == selectedId)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = site.name
                    setOnMarkerClickListener { _, _ ->
                        onClick.value(site)
                        true
                    }
                }
                map.overlays.add(marker)
            }
            val focus = sites.firstOrNull { it.id == focusId }
            if (!fitted[0] && sites.isNotEmpty()) {
                fitted[0] = true
                map.post {
                    if (focus != null) {
                        map.controller.setZoom(if (interactive) 9.0 else 7.0)
                        map.controller.setCenter(GeoPoint(focus.lat, focus.lng))
                    } else if (sites.size == 1) {
                        map.controller.setZoom(8.0)
                        map.controller.setCenter(GeoPoint(sites[0].lat, sites[0].lng))
                    } else {
                        val box = BoundingBox.fromGeoPoints(sites.map { GeoPoint(it.lat, it.lng) })
                        map.zoomToBoundingBox(box.increaseByScale(1.25f), false, 48)
                    }
                }
            }
            map.invalidate()
        },
    )
}
