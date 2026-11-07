package app.frame.studio.pro

import app.frame.studio.telemetry.AppEvents
import android.app.Activity
import android.content.Context
import android.util.Log
import app.frame.studio.BuildConfig
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Features unlocked by Frame Pro (or once by watching a rewarded ad). */
enum class ProFeature(val title: String, val pitch: String) {
    MAGIC_MOVIE("Magic Movie", "Pick photos and videos — Frame makes the movie for you."),
    ANIMATED_CAPTIONS("Animated captions", "Word-by-word captions that pop, like top Reels."),
    HIGH_RES_EXPORT("4K & 60 fps", "Export in the highest quality your phone supports."),
    PREMIUM_TEMPLATES("Premium templates", "Every frame, invitation and collage design."),
    PREMIUM_FILTERS("Premium filters", "The full filter library, including film and cinematic looks."),
    TEXT_ANIMATIONS("Text animations", "Typewriter, bounce, spin, zoom and slide-ins for titles and stickers."),
    NOISE_REDUCTION("Voice clean-up", "Removes hiss, hum and room noise from voices and recordings."),
    PREMIUM_TRANSITIONS("Premium transitions", "Zoom, spin, glitch, iris, film burn and more between your clips."),
}

/**
 * Frame Pro entitlement: a one-time Google Play purchase ([PRODUCT_ID]), checked on the device.
 * The unlock is cached so the app works offline; Play is re-queried on every launch.
 */
class ProStore(private val context: Context) : PurchasesUpdatedListener {

    private val prefs = context.getSharedPreferences("pro", Context.MODE_PRIVATE)

    private var debugOverride = prefs.getBoolean(KEY_DEBUG, false)

    private val _isPro = MutableStateFlow(prefs.getBoolean(KEY_PRO, false))
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    private val _price = MutableStateFlow<String?>(null)
    /** Localized price from Play, or null while unknown / unavailable. */
    val price: StateFlow<String?> = _price.asStateFlow()

    private val _billingAvailable = MutableStateFlow(false)
    val billingAvailable: StateFlow<Boolean> = _billingAvailable.asStateFlow()

    private var details: ProductDetails? = null

    private val _passes = MutableStateFlow<Set<ProFeature>>(emptySet())
    /** One-time unlocks earned by watching a rewarded ad. Kept in memory only. */
    val passes: StateFlow<Set<ProFeature>> = _passes.asStateFlow()

    fun hasAccess(feature: ProFeature): Boolean = _isPro.value || feature in _passes.value

    /** A rewarded ad was watched: allow [feature] once. */
    fun grantPass(feature: ProFeature) {
        _passes.value = _passes.value + feature
    }

    /** Uses up the one-time pass, if any. Pro users never need one. */
    fun consumePass(feature: ProFeature) {
        if (!_isPro.value) _passes.value = _passes.value - feature
    }

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    fun connect() {
        if (client.isReady) return
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                val ok = result.responseCode == BillingClient.BillingResponseCode.OK
                _billingAvailable.value = ok
                if (ok) {
                    loadProduct()
                    restore()
                }
            }

            override fun onBillingServiceDisconnected() {
                _billingAvailable.value = false
            }
        })
    }

    private fun loadProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()
        client.queryProductDetailsAsync(params) { result, list ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                details = list.firstOrNull()
                _price.value = details?.oneTimePurchaseOfferDetails?.formattedPrice
            }
        }
    }

    /** Re-reads purchases from Play (also the "Restore purchase" action). */
    fun restore() {
        if (!client.isReady) {
            connect()
            return
        }
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) handle(purchases, fromRestore = true)
        }
    }

    /** Opens Play's purchase sheet. Returns false when the product isn't available. */
    fun purchase(activity: Activity): Boolean {
        val product = details ?: return false
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product).build()))
            .build()
        val launched = client.launchBillingFlow(activity, params).responseCode == BillingClient.BillingResponseCode.OK
        if (launched) AppEvents.proPurchaseStarted()
        return launched
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) handle(purchases, fromRestore = false)
    }

    private fun handle(purchases: List<Purchase>, fromRestore: Boolean) {
        val pro = purchases.firstOrNull { PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED }
        if (pro != null) {
            if (!fromRestore && !pro.isAcknowledged) AppEvents.proPurchased()
            setPro(true)
            if (!pro.isAcknowledged) {
                client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(pro.purchaseToken).build()) { r ->
                    if (r.responseCode != BillingClient.BillingResponseCode.OK) Log.w(TAG, "Acknowledge failed: ${r.debugMessage}")
                }
            }
        } else if (fromRestore && !(debugOverride && BuildConfig.DEBUG)) {
            // Refunds and revoked purchases disappear from Play's list.
            setPro(false)
        }
    }


    /** Debug builds only: toggle Pro without a purchase, for testing. */
    fun setDebugPro(on: Boolean) {
        if (!BuildConfig.DEBUG) return
        debugOverride = on
        prefs.edit().putBoolean(KEY_DEBUG, on).apply()
        setPro(on)
    }

    private fun setPro(on: Boolean) {
        _isPro.value = on
        prefs.edit().putBoolean(KEY_PRO, on).apply()
    }

    companion object {
        const val PRODUCT_ID = "frame_pro"
        private const val KEY_PRO = "is_pro"
        private const val KEY_DEBUG = "debug_pro"
        private const val TAG = "ProStore"
    }
}
