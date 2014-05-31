package com.thirthydaylabs.tvapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ListAdapter;
import android.widget.RelativeLayout;

import com.android.vending.billing.IInAppBillingService;
import com.google.analytics.tracking.android.EasyTracker;
import com.google.analytics.tracking.android.MapBuilder;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.InterstitialAd;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;


public class MainActivity extends Activity {

    public final static String TAG = "TVApp";
    static final String KEY_ID = "id";
    static final String KEY_TITLE = "title";


    GridView videoList;
    ArrayList<HashMap<String, String>> itemsList = new ArrayList<HashMap<String,String>>();

    ListAdapter adapter;

    final Context context = this;
    View rootView;

    String[] channelUrlArray;
    String[] channelTitles;

    //In App Purchase
    boolean premium_status;
    private final static int DAYS_UNTIL_UPGRADE_PROMPT = 2;
    private final static int LAUNCHES_UNTIL_UPGRADE_PROMPT = 2;

    //Admob
    private InterstitialAd interstitial;
    private AdView adView;
    private RelativeLayout.LayoutParams rLParams;
    private RelativeLayout rLayout;

    //Dialogs
    Dialog dialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        channelUrlArray = getResources().getStringArray(R.array.urls);
        channelTitles = getResources().getStringArray(R.array.titles);
        adapter = new ListViewAdapter(this, itemsList, this);
        videoList = (GridView) findViewById(R.id.list);
        videoList.setAdapter(adapter);


        for(int i = 0; i < channelUrlArray.length ; i++){
            HashMap<String, String> map = new HashMap<String, String>();
            map.put(KEY_ID, Integer.toString(i) );
            map.put(KEY_TITLE, channelTitles[i]);
            itemsList.add(map);
        }

        videoList.invalidateViews();


        videoList.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            HashMap<String, String> item;

            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
                item = itemsList.get(position);
                String id = item.get("id");
                String title = item.get("title");
                Intent youtubeActivity = new Intent(context, PlayerActivity.class);
                youtubeActivity.putExtra("id", id);
                youtubeActivity.putExtra("title", title);
                startActivity(youtubeActivity);
            }

        });

        //App Upgrade and rating Dialog
        appOfferDialog();


        //Initiate the admob banner
        adMobBannerInitiate();
        adMobInterstitialInitiate();


        //Bind the in-app service
        bindService(new Intent("com.android.vending.billing.InAppBillingService.BIND"),
                mServiceConn, Context.BIND_AUTO_CREATE);


    }



    /**
     * In-App purchase service
     */
    IInAppBillingService mService;

    ServiceConnection mServiceConn = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }

        @Override
        public void onServiceConnected(ComponentName name,
                                       IBinder service) {
            mService = IInAppBillingService.Stub.asInterface(service);
            try {
                int response = mService.isBillingSupported(3,getPackageName(),"inapp");
                if(response == 0) {
                    //has billing
                    checkPurchases();
                }else{
                    // no billing V3
//                    MenuItem item = mMenu.findItem(R.id.action_upgrade);
//                    if (item != null) {
//                        item.setVisible(false);
//                    }
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }

        }
    };




    //Activity life cycles
    @Override
    protected void onStart() {
        super.onStart();
        EasyTracker.getInstance(this).activityStart(this);  // Add this method.
    }

    @Override
    public void onStop() {
        super.onStop();
        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }
        EasyTracker.getInstance(this).activityStop(this);  // Add this method.
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {

        // Destroy the AdView.
        if (adView != null) {
            adView.destroy();
        }

        super.onDestroy();

        if (mService != null) {
            unbindService(mServiceConn);
        }
    }






    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.player, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    /**
     * Initiate the adMob Banner
     */
    private void adMobBannerInitiate(){

        rLayout = (RelativeLayout) findViewById(R.id.main_layout);

        rLParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        rLParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 1);

        rLayout = (RelativeLayout) findViewById(R.id.main_layout);

        //Remove the current banner
        AdView oldAdView = (AdView) findViewById(1);

        // Add the AdView to the view hierarchy. The view will have no size
        // until the ad is loaded.

        // Destroy the old AdView.
        if (oldAdView != null) {
            rLayout.removeView(oldAdView);
            oldAdView.destroy();
        }


        SharedPreferences settings = getSharedPreferences(TAG, MODE_MULTI_PROCESS);
        premium_status = settings.getBoolean("premiumStatus", false);

        if(!premium_status && !getString(R.string.admob_id_home).equals("")) {
            adView = new AdView(this);
            adView.setAdSize(AdSize.SMART_BANNER);
            adView.setAdUnitId(getString(R.string.admob_id_home));
            adView.setId(1);

            rLayout.addView(adView, rLParams);

            // Create an ad request. Check logcat output for the hashed device ID to
            // get test ads on a physical device.
            AdRequest adRequest = new AdRequest.Builder().build();

            // Start loading the ad in the background.
            adView.loadAd(adRequest);
            final EasyTracker easyTracker = EasyTracker.getInstance(context);
            adView.setAdListener(new AdListener() {

                @Override
                public void onAdOpened() {
                    //Send the ad open event to google analytics
                    easyTracker.send(MapBuilder
                                    .createEvent("ui_event",     // Event category (required)
                                            "button_press",  // Event action (required)
                                            "banner_ad",   // Event label
                                            null)            // Event value
                                    .build()
                    );
                }

                @Override
                public void onAdFailedToLoad(int errorCode) {
                    super.onAdFailedToLoad(errorCode);
                    //Send the on ad fail to load event to google analytics
                    easyTracker.send(MapBuilder
                                    .createEvent("ui_event",     // Event category (required)
                                            "ui_load_fail",  // Event action (required)
                                            "banner_ad",   // Event label
                                            null)            // Event value
                                    .build()
                    );
                }

            });
        }

    }



    /**
     * Override the back button
     */
    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent event)
    {
        if (keyCode== KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0)
        {

            if(!getString(R.string.admob_id_interstitials).equals("")){
                //RevMob Full Screen Ad
                displayInterstitial();
            }
            // return true;
        }
        return super.onKeyDown(keyCode, event);
    }



    // Invoke displayInterstitial() when you are ready to display an interstitial.
    public void displayInterstitial() {

        SharedPreferences settings = getSharedPreferences(TAG, MODE_MULTI_PROCESS);
        premium_status = settings.getBoolean("premiumStatus", false);
        if (interstitial.isLoaded() && !premium_status) {
            interstitial.show();
        }

    }


    /**
     *  Initiate the interstitial adMob
     */
    private void adMobInterstitialInitiate(){

        if(!getString(R.string.admob_id_interstitials).equals("")){
            //AdMob Full Screen
            //Create the interstitial
            interstitial = new InterstitialAd(this);
            interstitial.setAdUnitId(getString(R.string.admob_id_interstitials));
            // Create ad request
            AdRequest interAdRequest = new AdRequest.Builder().build();
            // Begin loading your interstitial
            interstitial.loadAd(interAdRequest);

            final Activity act = this;
            interstitial.setAdListener(new AdListener() {
                @Override
                public void onAdClosed() {
                    // Save app state before going to the ad overlay.
                    act.finish();
                }
            });
        }

    }



    /**
     * appOfferDialog Dialog
     */
    public void appOfferDialog() {

        SharedPreferences prefs = getSharedPreferences(MainActivity.TAG, MODE_MULTI_PROCESS);
        premium_status = prefs.getBoolean("premiumStatus", false);
        Boolean dont_show_rate_again  = prefs.getBoolean("dontshowrateagain", false);


        SharedPreferences.Editor editor = prefs.edit();

        // Increment launch counter
        long launch_count = prefs.getLong("launch_count", 0) + 1;
        editor.putLong("launch_count", launch_count);

        // Get date of first launch
        Long date_firstLaunch = prefs.getLong("date_firstlaunch", 0);
        if (date_firstLaunch == 0) {
            date_firstLaunch = System.currentTimeMillis();
            editor.putLong("date_firstlaunch", date_firstLaunch);
        }


        // Wait at least n days before opening
        if (launch_count >= LAUNCHES_UNTIL_UPGRADE_PROMPT ) {
            if (System.currentTimeMillis() >= date_firstLaunch +
                    (DAYS_UNTIL_UPGRADE_PROMPT * 24 * 60 * 60 * 1000)) {

                //generate a random number [1,2]
                int randomDay = 1 + (int)(Math.random()*3);
                if(!premium_status && randomDay == 1) {
                    //Upgrade offer
                    upgradeDialog();
                }else if(!dont_show_rate_again && randomDay == 2){
                    rateDialog(editor);
                }

            }
        }

        editor.commit();

    }



    private void upgradeDialog(){

        //Create the upgrade dialog
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.upgrade_offer_title))
                .setMessage(getString(R.string.upgrade_offer_text))
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // continue with upgrade
                        purchase();
                    }
                })
                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // do nothing
                    }
                })
                .setIcon(R.drawable.ic_action_dark_important)
                .show();

    }


    private void rateDialog(final SharedPreferences.Editor editor){

        final String APP_PNAME = getPackageName();

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.rate_offer_title))
                .setItems(R.array.rating_response, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // The 'which' argument contains the index position
                        // of the selected item
                        switch (which) {
                            case 0:
                                //Rate Now
                                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + APP_PNAME)));
                            case 1:
                                //Rate Later
                            case 2:
                                //Never
                                if (editor != null) {
                                    editor.putBoolean("dontshowrateagain", true);
                                    editor.commit();
                                }
                        }

                    }

                })
                .setIcon(R.drawable.ic_action_dark_important)
                .show();

    }


    /**
     * Purchase Item
     */
    private void purchase(){


        try {
            int response = mService.isBillingSupported(3,getPackageName(),"inapp");
            if(response == 0) {
                //has billing
                String SKU = getString(R.string.premium_product_id);
                try {
                    Bundle buyIntentBundle = mService.getBuyIntent(3, context.getPackageName(),
                            SKU, "inapp", null);
                    PendingIntent pendingIntent = buyIntentBundle.getParcelable("BUY_INTENT");
                    assert pendingIntent != null;
                    startIntentSenderForResult(pendingIntent.getIntentSender(),
                            1001, new Intent(), 0, 0, 0);
                } catch (RemoteException e) {
                    e.printStackTrace();
                } catch (IntentSender.SendIntentException e) {
                    e.printStackTrace();
                } catch (Exception e){
                    purchaseErrorDialog();
                }
            }else{
                // no billing V3
//                MenuItem item = mMenu.findItem(R.id.action_upgrade);
//                assert item != null;
//                item.setVisible(false);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        } catch (Exception e){
            purchaseErrorDialog();
        }


    }



    /**
     * on in-app purchase listener
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1001) {
            String purchaseData = data.getStringExtra("INAPP_PURCHASE_DATA");
            int responseCode = data.getIntExtra("RESPONSE_CODE", 0);
            String dataSignature = data.getStringExtra("INAPP_DATA_SIGNATURE");

            if (resultCode == RESULT_OK) {
                try {

                    JSONObject jo = new JSONObject(purchaseData);
                    String sku = jo.getString("productId");
                    Log.i(TAG, "You have bought the " + sku + ". Excellent choice,adventurer!");

                    //Upgrade the app if the sku matched the upgrade package
                    if(sku.equals(getString(R.string.premium_product_id))) {

                        // May return null if EasyTracker has not yet been initialized with a
                        // property ID.
                        EasyTracker easyTracker = EasyTracker.getInstance(this);

                        easyTracker.send(MapBuilder
                                        .createTransaction(jo.getString("orderId"), // (String) Transaction ID
                                                "In-app Store",   // (String) Affiliation
                                                0.99d,            // (Double) Order revenue
                                                0.0d,            // (Double) Tax
                                                0.0d,             // (Double) Shipping
                                                "USD")            // (String) Currency code
                                        .build()
                        );

                        easyTracker.send(MapBuilder
                                        .createItem(jo.getString("orderId"),               // (String) Transaction ID
                                                "Premium Upgrade",      // (String) Product name
                                                "premium",                  // (String) Product SKU
                                                "Paid Upgrade",        // (String) Product category
                                                0.99d,                    // (Double) Product price
                                                1L,                       // (Long) Product quantity
                                                "USD")                    // (String) Currency code
                                        .build()
                        );

                        //Set the premium flag
                        SharedPreferences settings = getSharedPreferences(TAG, MODE_MULTI_PROCESS);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putBoolean("premiumStatus", true);
                        editor.commit();

                        //Remove the upgrade menu
//                        MenuItem item = mMenu.findItem(R.id.action_upgrade);
//                        assert item != null;
//                        item.setVisible(false);

                        //Remove the ads
                        adMobBannerInitiate();

                        //Show complete dialog
                        purchaseCompleteDialog();
                    }

                }
                catch (JSONException e) {
                    Log.i(TAG,"Failed to parse purchase data.");
                    purchaseErrorDialog();
                    e.printStackTrace();
                }
                catch (Exception e){
                    purchaseErrorDialog();
                }
            }
        }
    }







    private void purchaseCompleteDialog(){

        //Create the upgrade dialog
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.upgrade_complete_dialog_title))
                .setMessage(getString(R.string.upgrade_complete_dialog_body_text))
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // continue with upgrade

                    }
                })
                .setIcon(R.drawable.ic_action_dark_important)
                .show();

    }


    private void purchaseErrorDialog(){

        //Create the upgrade dialog
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.upgrade_error_dialog_title))
                .setMessage(getString(R.string.upgrade_error_dialog_body_text))
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // continue with upgrade

                    }
                })
                .setIcon(R.drawable.ic_action_dark_important)
                .show();

    }




    /**
     * check purchased items
     */
    private void checkPurchases() {

        Bundle ownedItems = null;
        try {
            ownedItems = mService.getPurchases(3, getPackageName(), "inapp", null);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        assert ownedItems != null;
        int response = ownedItems.getInt("RESPONSE_CODE");
        if (response == 0) {
            ArrayList<String> ownedSkus =
                    ownedItems.getStringArrayList("INAPP_PURCHASE_ITEM_LIST");
            ArrayList<String>  purchaseDataList =
                    ownedItems.getStringArrayList("INAPP_PURCHASE_DATA_LIST");

            assert purchaseDataList != null;

            SharedPreferences settings =
                    getSharedPreferences(TAG, MODE_MULTI_PROCESS);

            for (int i = 0; i < purchaseDataList.size(); ++i) {

                String sku = ownedSkus.get(i);
                Log.i(TAG,sku);

                // String purchaseData = purchaseDataList.get(i);
                // Log.i(TAG,purchaseData);

                //check and apply the premium purchase
                if(sku.equals(getString(R.string.premium_product_id))) {

                    //Check if the flag is not set, if true set the flag and reinit AdMob
                    if(!settings.getBoolean("premiumStatus", false)) {
                        //Set the premium flag
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putBoolean("premiumStatus", true);
                        editor.commit();

                        //Remove the ads
                        adMobBannerInitiate();
                    }

                }

                //Check and remove the test package
//                if(sku.equals("android.test.purchased")){
//                    try {
//
//                        //Consume the order
//                        int cResponse = mService.consumePurchase(3, getPackageName(), "inapp:com.mardox.mathtricks:android.test.purchased");
//                        Log.i(TAG,"consume response: "+cResponse);
//
//                        if(cResponse==0) {
//                            //reset the sharedprefrences
//                            SharedPreferences settings = getSharedPreferences(CollectionActivity.PREFS_NAME, MODE_MULTI_PROCESS);
//                            SharedPreferences.Editor editor = settings.edit();
//                            editor.putBoolean("premiumStatus", false);
//                            editor.commit();
//
//                            //Enable the ads
//                            adMobBannerInitiate();
//                            adMobInterstitialInitiate();
//                        }
//
//                    } catch (RemoteException e) {
//                        e.printStackTrace();
//                    }
//                }

                // do something with this purchase information
                // e.g. display the updated list of products owned by user
            }

            // if continuationToken != null, call getPurchases again
            // and pass in the token to retrieve more items
        }

    }




}
