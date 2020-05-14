package org.godotengine.godot;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Set;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.app.Activity;
import android.widget.FrameLayout;
import android.view.ViewGroup.LayoutParams;
import android.provider.Settings;
import android.graphics.Color;
import android.util.Log;
import java.util.Locale;
import android.view.Gravity;
import android.view.View;
import android.os.Bundle;

import com.mopub.mobileads.MoPubView;
import com.mopub.mobileads.MoPubView.MoPubAdSize;
import com.mopub.mobileads.MoPubInterstitial;
import com.mopub.mobileads.MoPubRewardedVideoListener;
import com.mopub.mobileads.MoPubErrorCode;
import com.mopub.mobileads.MoPubRewardedVideos;
import com.mopub.common.SdkConfiguration;
import com.mopub.common.logging.MoPubLog.LogLevel;
import com.mopub.common.SdkInitializationListener;
import com.mopub.common.MoPub;
import com.mopub.common.MoPubReward;

public class GodotMopub extends Godot.SingletonBase
{

    private final String TAG = GodotMopub.class.getName();
    private Activity activity = null; // The main activity of the game
    private boolean _inited = false;

    private HashMap<String, MoPubInterstitial> interstitials = new HashMap<>();
    private HashMap<String, MoPubView> banners = new HashMap<>();
    private HashMap<String, Integer> rewardedCallbacks = new HashMap<>();

    private boolean ProductionMode = true; // Store if is real or not

    private FrameLayout layout = null; // Store the layout
    private long failRateDelay = 0L;

    /* Init
     * ********************************************************************** */

    /**
     * Prepare for work with MoPub
     * @param boolean ProductionMode Tell if the enviroment is for real or test
     * @param int gdscript instance id
     */
    public void init(final String adUnit, final boolean ProductionMode) {
        this.ProductionMode = ProductionMode;

        activity.runOnUiThread(new Runnable() {
            @Override public void run() {

                SdkConfiguration sdkConfiguration = null;
                if(ProductionMode)
                    sdkConfiguration = new SdkConfiguration.Builder(adUnit).withLogLevel(LogLevel.NONE).build();
                else
                    sdkConfiguration = new SdkConfiguration.Builder(adUnit).withLogLevel(LogLevel.DEBUG).build();

                MoPub.initializeSdk(activity, sdkConfiguration, new SdkInitializationListener() {
                        @Override
                        public void onInitializationFinished() {
                            /* MoPub SDK initialized.
                               Check if you should show the consent dialog here, and make your ad requests. */
                            if(MoPub.isSdkInitialized()) {
                                Log.d(TAG, "MoPub: inited");
                                _inited = true;
                                initRewardedVideoListener();
                            } else
                                Log.e(TAG, "MoPub init error!");
                        }
                    });
            }
        });

    }

    /* Rewarded Video
     * ********************************************************************** */
    private void initRewardedVideoListener()
    {
        MoPubRewardedVideoListener rewardedVideoListener = new MoPubRewardedVideoListener() {
            @Override
            public void onRewardedVideoLoadSuccess(String adUnitId) {
                // Called when the video for the given adUnitId has loaded. At this point you should be able to call MoPubRewardedVideos.showRewardedVideo(String) to show the video.
                Log.w(TAG, "MoPub: onRewardedVideoLoadSuccess");
                int callback_id = rewardedCallbacks.get(adUnitId);
                GodotLib.calldeferred(callback_id, "_on_rewarded_video_ad_loaded", new Object[] { adUnitId });
            }
            @Override
            public void onRewardedVideoLoadFailure(String adUnitId, MoPubErrorCode errorCode) {
                // Called when a video fails to load for the given adUnitId. The provided error code will provide more insight into the reason for the failure to load.
                Log.w(TAG, "MoPub: onRewardedVideoLoadFailure. error: " + errorCode.toString());
                int callback_id = rewardedCallbacks.get(adUnitId);
                GodotLib.calldeferred(callback_id, "_on_rewarded_video_ad_failed_to_load", new Object[] { adUnitId, errorCode.toString() });
                if(errorCode == MoPubErrorCode.WARMUP)
                    setFailRateDelay(30);
                else if(errorCode == MoPubErrorCode.NO_FILL)
                    setFailRateDelay(10);
            }

            @Override
            public void onRewardedVideoStarted(String adUnitId) {
                // Called when a rewarded video starts playing.
                Log.w(TAG, "MoPub: onRewardedVideoStarted");
                int callback_id = rewardedCallbacks.get(adUnitId);
                GodotLib.calldeferred(callback_id, "_on_rewarded_video_started", new Object[] { adUnitId });
            }

            @Override
            public void onRewardedVideoPlaybackError(String adUnitId, MoPubErrorCode errorCode) {
                //  Called when there is an error during video playback.
                Log.w(TAG, "MoPub: onRewardedVideoPlaybackError. error: " + errorCode.toString());
                int callback_id = rewardedCallbacks.get(adUnitId);
                GodotLib.calldeferred(callback_id, "_on_rewarded_video_ad_failed_to_load", new Object[] { adUnitId, errorCode.toString() });
            }
            
            @Override
            public void onRewardedVideoClicked(String adUnitId) {
                //  Called when a rewarded video is clicked.
                Log.w(TAG, "MoPub: onRewardedVideoClicked");
                int callback_id = rewardedCallbacks.get(adUnitId);
                GodotLib.calldeferred(callback_id, "_on_rewarded_video_ad_left_application", new Object[] { adUnitId });
            }

            @Override
            public void onRewardedVideoClosed(String adUnitId) {
                // Called when a rewarded video is closed. At this point your application should resume.
                Log.w(TAG, "MoPub: onRewardedVideoClosed");
                int callback_id = rewardedCallbacks.get(adUnitId);
                GodotLib.calldeferred(callback_id, "_on_rewarded_video_ad_closed", new Object[] { adUnitId });
            }

            @Override
            public void onRewardedVideoCompleted(Set<String> adUnitIds, MoPubReward reward) {
                // Called when a rewarded video is completed and the user should be rewarded.
                // You can query the reward object with boolean isSuccessful(), String getLabel(), and int getAmount().
                Log.w(TAG, "MoPub: " + String.format(" onRewarded! %s amount %d", reward.getLabel(), reward.getAmount()));
                for(String adUnitId: adUnitIds) {
                    int callback_id = rewardedCallbacks.get(adUnitId);
                    GodotLib.calldeferred(callback_id, "_on_rewarded", new Object[] { adUnitId, reward.getLabel(), reward.getAmount() });
                }
            }
        };

        MoPubRewardedVideos.setRewardedVideoListener(rewardedVideoListener);
    }

    /**
     * Load a Rewarded Video
     * @param String id AdMod Rewarded video ID
     */
    public void loadRewardedVideo(final String id, final int callback_id) {
        if(checkFailRateDelay() && _inited) {
            rewardedCallbacks.put(id, callback_id);
            MoPubRewardedVideos.loadRewardedVideo(id);
        } else {
            GodotLib.calldeferred(callback_id, "_on_rewarded_video_ad_failed_to_load", new Object[] { id, "Fail rate limit" });
        }
    }

    /**
     * Show a Rewarded Video
     */
    public void showRewardedVideo(final String id) {
        MoPubRewardedVideos.showRewardedVideo(id);
    }

    public boolean isRewardedVideoLoaded(final String id) {
        return MoPubRewardedVideos.hasRewardedVideo(id);
    }

    /* Banner
     * ********************************************************************** */

    private MoPubView initBanner(final String id, final boolean isOnTop, final int callback_id)
    {
        FrameLayout.LayoutParams adParams = new FrameLayout.LayoutParams(
                                                FrameLayout.LayoutParams.MATCH_PARENT,
                                                FrameLayout.LayoutParams.WRAP_CONTENT
                                                );
        if(isOnTop) adParams.gravity = Gravity.TOP;
        else adParams.gravity = Gravity.BOTTOM;
                
        MoPubView banner = new MoPubView(activity);
        banner.setAdUnitId(id);
        banner.setBackgroundColor(/* Color.WHITE */Color.TRANSPARENT);
        MoPubView.MoPubAdSize adSize = MoPubAdSize.HEIGHT_50;

        banner.setBannerAdListener(new MoPubView.BannerAdListener() {
                public void onBannerClicked(MoPubView banner) {
                    Log.w(TAG, "MoPub: onBannerClicked");
                }
                public void onBannerCollapsed(MoPubView banner) {
                    Log.w(TAG, "MoPub: onBannerCollapsed");
                }
                public void onBannerExpanded(MoPubView banner) {
                    Log.w(TAG, "MoPub: onBannerExpanded");
                }
                public void onBannerFailed(MoPubView banner, MoPubErrorCode errorCode) {
                    Log.w(TAG, "MoPub: onBannerFailed -> " + errorCode.toString());
                    GodotLib.calldeferred(callback_id, "_on_banner_failed_to_load", new Object[]{ id, errorCode.toString() });
                    if(errorCode == MoPubErrorCode.WARMUP)
                        setFailRateDelay(30);
                    else if(errorCode == MoPubErrorCode.NO_FILL)
                        setFailRateDelay(10);
                }
                public void onBannerLoaded(MoPubView banner) {
                    Log.w(TAG, "MoPub: onBannerLoaded");
                    GodotLib.calldeferred(callback_id, "_on_banner_loaded", new Object[]{ id });
                }
            });
        layout.addView(banner, adParams);

        // Request
        banner.loadAd(adSize);
        return banner;
    }

    /**
     * Load a banner
     * @param String id AdMod Banner ID
     * @param boolean isOnTop To made the banner top or bottom
     */
    public void loadBanner(final String id, final boolean isOnTop, final int callback_id)
    {
        activity.runOnUiThread(new Runnable()
        {
            @Override public void run()
            {
                if(checkFailRateDelay() && _inited) {
                    if(!banners.containsKey(id)) {
                        MoPubView b = initBanner(id, isOnTop, callback_id);
                        banners.put(id, b);
                    } else {
                        MoPubView b = banners.get(id);
                        b.setAdUnitId(id);
                        b.loadAd(MoPubAdSize.HEIGHT_50);
                    }
                } else {
                    GodotLib.calldeferred(callback_id, "_on_banner_failed_to_load", new Object[]{ id, "Fail rate limit" });
                }
            }
        });
    }

    /**
     * Show the banner
     */
    public void showBanner(final String id)
    {
        activity.runOnUiThread(new Runnable()
        {
            @Override public void run()
            {
                if(banners.containsKey(id)) {
                    MoPubView b = banners.get(id);
                    b.setVisibility(View.VISIBLE);
                    for (String key : banners.keySet()) {
                        if(!key.equals(id)) {
                            MoPubView b2 = banners.get(key);
                            b2.setVisibility(View.GONE);
                        }
                    }
                    Log.d(TAG, "Show Banner");
                } else {
                    Log.w(TAG, "Banner not found: "+id);
                }
            }
        });
    }

    public void removeBanner(final String id)
    {
        activity.runOnUiThread(new Runnable()
        {
            @Override public void run()
            {
                if(banners.containsKey(id)) {
                    MoPubView b = banners.get(id);
                    banners.remove(id);
                    layout.removeView(b); // Remove the banner
                    Log.d(TAG, "Remove Banner");
                } else {
                    Log.w(TAG, "Banner not found: "+id);
                }
            }
        });
    }


    /**
     * Hide the banner
     */
    public void hideBanner(final String id)
    {
        activity.runOnUiThread(new Runnable()
        {
            @Override public void run()
            {
                if(banners.containsKey(id)) {
                    MoPubView b = banners.get(id);
                    b.setVisibility(View.GONE);
                    Log.d(TAG, "Hide Banner");
                } else {
                    Log.w(TAG, "Banner not found: "+id);
                }
            }
        });
    }

    /**
     * Get the banner width
     * @return int Banner width
     */
    public int getBannerWidth(final String id)
    {
        if(banners.containsKey(id)) {
            MoPubView b = banners.get(id);
            if(b != null)
                return 320;
            else
                return 0;
        } else {
            return 0;
        }
    }

    /**
     * Get the banner height
     * @return int Banner height
     */
    public int getBannerHeight(final String id)
    {
        if(banners.containsKey(id)) {
            MoPubView b = banners.get(id);
            if(b != null)
                return 50;
            else
                return 0;
        } else {
            return 0;
        }
    }

    /* Interstitial
     * ********************************************************************** */

    private void initInterstitial(final String id, final int callback_id)
    {
        MoPubInterstitial mInterstitial = new MoPubInterstitial(activity, id);
        interstitials.put(id, mInterstitial);
        mInterstitial.setInterstitialAdListener(new MoPubInterstitial.InterstitialAdListener() {
                public void onInterstitialClicked(MoPubInterstitial interstitial) {
                    Log.w(TAG, "MoPub: onInterstitialClicked");
                }
                public void onInterstitialDismissed(MoPubInterstitial interstitial) {
                    Log.w(TAG, "MoPub: onInterstitialDismissed");
                    GodotLib.calldeferred(callback_id, "_on_interstitial_close", new Object[] { id });
                }
                public void onInterstitialFailed(MoPubInterstitial interstitial, MoPubErrorCode errorCode) {
                    Log.w(TAG, "MoPub: onInterstitialFailed - error: " + errorCode.toString());
                    GodotLib.calldeferred(callback_id, "_on_interstitial_failed_to_load", new Object[] { id, errorCode.toString() });
                    if(errorCode == MoPubErrorCode.WARMUP)
                        setFailRateDelay(30);
                    else if(errorCode == MoPubErrorCode.NO_FILL)
                        setFailRateDelay(10);
                }
                public void onInterstitialLoaded(MoPubInterstitial interstitial) {
                    Log.w(TAG, "MoPub: onInterstitialLoaded");
                    GodotLib.calldeferred(callback_id, "_on_interstitial_loaded", new Object[] { id });
                }
                public void onInterstitialShown(MoPubInterstitial interstitial) {
                    Log.w(TAG, "MoPub: onInterstitialShown");
                }
            });
        mInterstitial.load();
    }

    /**
     * Load a interstitial
     * @param String id AdMod Interstitial ID
     */
    public void loadInterstitial(final String id, final int callback_id)
    {
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                if(checkFailRateDelay() && _inited) {
                    initInterstitial(id, callback_id);
                } else {
                    GodotLib.calldeferred(callback_id, "_on_interstitial_failed_to_load", new Object[] { id, "Fail rate limit" });
                }
            }
        });
    }

    /**
     * Show the interstitial
     */
    public void showInterstitial(final String id)
    {
        activity.runOnUiThread(new Runnable()
        {
            @Override public void run()
            {
                if(interstitials.containsKey(id)) {
                    MoPubInterstitial interstitial = interstitials.get(id);
                    if (interstitial.isReady()) {
                        interstitial.show();
                    } else {
                        Log.w(TAG, "MoPub: showInterstitial - interstitial not loaded");
                    }
                }
            }
        });
    }

    /* Utilities
     * ********************************************************************** */
    void setFailRateDelay() {
        setFailRateDelay(30);
    }

    void setFailRateDelay(long delay) {
        long time = System.currentTimeMillis() / 1000L + delay;
        failRateDelay = Math.max(failRateDelay, time);
        Log.w(TAG, "Set fail rate delay to: " + delay);
    }

    boolean checkFailRateDelay() {
        long unixTime = System.currentTimeMillis() / 1000L;
        return unixTime > failRateDelay;
    }
    
    /* Definitions
     * ********************************************************************** */

    /**
     * Initilization Singleton
     * @param Activity The main activity
     */
    static public Godot.SingletonBase initialize(Activity activity)
    {
        return new GodotMopub(activity);
    }

    /**
     * Constructor
     * @param Activity Main activity
     */
    public GodotMopub(Activity p_activity) {
        registerClass("Mopub", new String[] {
            "init",
            "initWithContentRating",
            // banner
            "loadBanner", "showBanner", "hideBanner", "removeBanner", "getBannerWidth", "getBannerHeight", 
            // Interstitial
            "loadInterstitial", "showInterstitial",
            // Rewarded video
            "loadRewardedVideo", "showRewardedVideo", "isRewardedVideoLoaded"
        });
        activity = p_activity;
        layout = (FrameLayout)activity.getWindow().getDecorView().getRootView();
    }

    protected void onMainPause() {
        if(activity != null)
            MoPub.onPause(activity);
    }
    protected void onMainResume() {
        if(activity != null)
            MoPub.onResume(activity);
    }
    protected void onMainDestroy() {
    }
}
