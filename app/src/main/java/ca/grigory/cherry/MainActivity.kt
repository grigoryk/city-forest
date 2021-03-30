package ca.grigory.cherry

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import com.mapbox.android.core.location.*
import com.mapbox.android.core.permissions.PermissionsListener
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.plugins.building.BuildingPlugin
import com.mapbox.mapboxsdk.style.expressions.Expression.*
import com.mapbox.mapboxsdk.style.layers.CircleLayer
import com.mapbox.mapboxsdk.style.layers.Property.TEXT_ANCHOR_BOTTOM
import com.mapbox.mapboxsdk.style.layers.Property.TEXT_JUSTIFY_CENTER
import com.mapbox.mapboxsdk.style.layers.PropertyFactory.*
import com.mapbox.mapboxsdk.style.layers.SymbolLayer
import com.mapbox.mapboxsdk.style.sources.VectorSource
import java.lang.Exception
import java.lang.ref.WeakReference

class MainActivity : AppCompatActivity(), PermissionsListener {
    companion object {
        const val DEFAULT_INTERVAL_IN_MILLISECONDS = 1000L
        const val DEFAULT_MAX_WAIT_TIME = DEFAULT_INTERVAL_IN_MILLISECONDS * 5
    }

    private var mapView: MapView? = null
    private var mapboxMap: MapboxMap? = null

    private var permissionsManager: PermissionsManager? = null

    private var locationEngine: LocationEngine? = null

    private data class Tileset(
        val id: String,
        val layerName: String,
        val commonNameKey: String,
        val diameterKey: String,
        val speciesNameKey: String
    )

    private val dataLayers = listOf(
        // Vancouver
        Tileset(
            id = "grigoryk.c63otp8s",
            layerName = "out-a881k8",
            commonNameKey = "COMMON_NAME",
            diameterKey = "DIAMETER",
            speciesNameKey = "SPECIES_NAME"
        ),
        // Victoria
        Tileset(
            id = "grigoryk.dc2yc3dz",
            layerName = "Tree_Species-dnift9",
            commonNameKey = "CommonName",
            diameterKey = "DiameterAt",
            speciesNameKey = "Species"
        )
    )

    private val callback = MainActivityLocationCallback(this)

    class MainActivityLocationCallback(activity: MainActivity) : LocationEngineCallback<LocationEngineResult> {
        private val weakActivity = WeakReference<MainActivity>(activity)

        override fun onSuccess(result: LocationEngineResult?) {
            val activity = weakActivity.get()
            activity?.mapboxMap?.locationComponent?.forceLocationUpdate(result?.lastLocation)
        }

        override fun onFailure(exception: Exception) {}
    }

    @SuppressWarnings("MissingPermission")
    private fun enableLocationComponent(loadedMapStyle: Style) {
        if (!PermissionsManager.areLocationPermissionsGranted(this)) {
            permissionsManager = PermissionsManager(this)
            permissionsManager?.requestLocationPermissions(this)
            return
        }

        val locationComponent = mapboxMap!!.locationComponent
        val locationComponentActivationOptions =
            LocationComponentActivationOptions.builder(this, loadedMapStyle)
                .useDefaultLocationEngine(false)
                .build()

        locationComponent.activateLocationComponent(locationComponentActivationOptions)

        locationComponent.isLocationComponentEnabled = true
        locationComponent.cameraMode = CameraMode.TRACKING;
        locationComponent.renderMode = RenderMode.COMPASS;

        initLocationEngine();
    }

    @SuppressLint("MissingPermission")
    private fun initLocationEngine() {
        locationEngine = LocationEngineProvider.getBestLocationEngine(this)
        val request =
            LocationEngineRequest.Builder(DEFAULT_INTERVAL_IN_MILLISECONDS)
                .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
                .setMaxWaitTime(DEFAULT_MAX_WAIT_TIME).build()
        locationEngine?.requestLocationUpdates(request, callback, mainLooper)
        locationEngine?.getLastLocation(callback)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setSupportActionBar(findViewById(R.id.my_toolbar))

        Mapbox.getInstance(this, BuildConfig.MAPBOX_DOWNLOADS_TOKEN)

        setContentView(R.layout.activity_main)

        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.mapView)
        mapView?.onCreate(savedInstanceState)
        mapView?.getMapAsync { mapboxMap ->
            this.mapboxMap = mapboxMap

            mapboxMap.addOnMapClickListener {
                val screenLocation = mapboxMap.projection.toScreenLocation(it)
                val features = mapboxMap.queryRenderedFeatures(
                    screenLocation, "trees-circles", "trees-labels"
                )
                if (features.isNotEmpty()) {
                    val commonName = features[0]?.getStringProperty("COMMON_NAME")
                    val speciesName = features[0]?.getStringProperty("SPECIES_NAME")

                    Toast.makeText(this, "$speciesName - $commonName", Toast.LENGTH_SHORT).show()
                }
                true
            }

            loadMap(findViewById<SwitchMaterial>(R.id.cherry_switch).isChecked, mapboxMap, true)

            findViewById<SwitchMaterial>(R.id.cherry_switch).setOnCheckedChangeListener { _, isChecked ->
                loadMap(isChecked, mapboxMap)
            }
        }
    }

    private fun loadMap(cherryMode: Boolean, mapboxMap: MapboxMap, firstLoad: Boolean = false) {
        if (cherryMode) {
            cherryStyle(mapboxMap)
        } else {
            defaultStyle(mapView!!, mapboxMap, firstLoad)
        }
    }

    private fun fullTreeSource(dataLayer: Tileset) =
        VectorSource("trees-source-${dataLayer.id}", "https://api.mapbox.com/v4/${dataLayer.id}.json?access_token=" + BuildConfig.MAPBOX_DOWNLOADS_TOKEN)

    private val cherryStyle by lazy {
        Style.Builder().fromUri("mapbox://styles/grigoryk/ckmlk57u64pdf17lk7kgh2wvp")
    }

    private fun fullTreesCircleLayer(dataLayer: Tileset) = CircleLayer("trees-circles-${dataLayer.id}", "trees-source-${dataLayer.id}").also {
        it.sourceLayer = dataLayer.layerName
        it.withProperties(
            circleBlur(0.6f),
            circleColor("#48A363"),
            circleRadius(interpolate(exponential(1.0f), get(dataLayer.diameterKey),
                stop(0, 3f),
                stop(1, 6f),
                stop(110, 11f)
            )),
            circleOpacity(1f)
        )
    }

    private fun fullTreesLabelLayer(dataLayer: Tileset) = SymbolLayer("trees-labels-${dataLayer.id}", "trees-source-${dataLayer.id}").also {
        it.sourceLayer = dataLayer.layerName
        it.minZoom = 18f
        it.withProperties(
            textField(get(dataLayer.commonNameKey)),
            textSize(15f),
            textColor("#A34D4D"),
            textJustify(TEXT_JUSTIFY_CENTER),
            textRadialOffset(2f),
            textAnchor(TEXT_ANCHOR_BOTTOM)
        )
    }

    private fun cherryStyle(map: MapboxMap) {
        map.setStyle(cherryStyle)
    }

    private fun defaultStyle(view: MapView, map: MapboxMap, first: Boolean = false) {
        map.setStyle(
            Style.LIGHT
        ) { style ->
            dataLayers.forEach {
                style.addSource(fullTreeSource(it))
                style.addLayer(fullTreesCircleLayer(it))
            }

            if (first) {
                enableLocationComponent(style)

                val buildingPlugin = BuildingPlugin(view, map, style)
                buildingPlugin.setMinZoomLevel(15f)
                buildingPlugin.setVisibility(true)
                buildingPlugin.setOpacity(0.8f)
            }

            dataLayers.forEach {
                style.addLayer(fullTreesLabelLayer(it))
            }
        }
    }

    override fun onStart() {
        super.onStart()
        mapView?.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView?.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView?.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onExplanationNeeded(permissionsToExplain: MutableList<String>?) {}
    override fun onPermissionResult(granted: Boolean) {}
}