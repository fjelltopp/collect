package org.odk.collect.android.messaging;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.odk.collect.android.BuildConfig;
import org.odk.collect.android.dao.FormsDao;
import org.odk.collect.android.logic.FormDetails;
import org.odk.collect.android.utilities.DownloadFormListUtils;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.logic.PropertyManager;
import org.odk.collect.android.provider.FormsProviderAPI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import timber.log.Timber;

import static android.content.Context.ACCOUNT_SERVICE;

/**
 * Created by jyri.soppela on 26.12.2015.
 */
public class SendDeviceReport extends AsyncTask<Void,Void,String> {

    //database
    private SQLiteDatabase db;

    private static final String TAG = "SendDeviceReport";
    public static final String LAST_REPORT = "last_report";

    private PropertyManager mPropertyManager = new PropertyManager(Collect.getInstance().getApplicationContext());

    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(Collect.getInstance());
    private String token;
    private String app_version;
    private Integer app_version_code;

    private final String deviceid = mPropertyManager.getSingularProperty(PropertyManager.PROPMGR_DEVICE_ID);
    private final String simid = mPropertyManager.getSingularProperty(PropertyManager.PROPMGR_SIM_SERIAL);

    @Override
    protected String doInBackground(Void... params) {
        String msg = "";

        try {
            PackageInfo pInfo = Collect.getInstance().getApplicationContext().getPackageManager().
                    getPackageInfo(Collect.getInstance().getApplicationContext().getPackageName(), 0);

            //token = sharedPreferences.getString(QuickstartPreferences.REGISTRATION_TOKEN, "");
            app_version = pInfo.versionName;
            app_version_code = pInfo.versionCode;

            AccountManager manager = (AccountManager) Collect.getInstance().getApplicationContext().
                    getSystemService(ACCOUNT_SERVICE);
            Account[] list = manager.getAccounts();
            String gmail = null;
            for(Account account: list)
            {
                if(account.type.equalsIgnoreCase("com.google"))
                {
                    gmail = account.name;
                    break;
                }
            }


            Bundle data = new Bundle();
            JSONObject dataJSONObject = new JSONObject();
            String dataString;

            JSONArray formReports = getFormReports();
            JSONObject location = getLocation();
            dataJSONObject.put(FirebaseAnalyticsParams.KEY_DEVICEID, deviceid);
            dataJSONObject.put(FirebaseAnalyticsParams.KEY_SIMID, simid);
            if(gmail != null) {
                dataJSONObject.put(FirebaseAnalyticsParams.KEY_GMAIL_ACCOUNT, gmail);
            }
            dataJSONObject.put(FirebaseAnalyticsParams.KEY_ORGANIZATION, BuildConfig.FLAVOR);
            dataJSONObject.put(FirebaseAnalyticsParams.KEY_APP_VERSION, app_version);
            dataJSONObject.put(FirebaseAnalyticsParams.KEY_APP_VERSION_CODE, app_version_code);
            dataJSONObject.put(FirebaseAnalyticsParams.KEY_FORM_REPORTS, formReports);
            dataJSONObject.put(FirebaseAnalyticsParams.KEY_MESSAGE_TYPE, "device_report");

            try {
                dataJSONObject.put(FirebaseAnalyticsParams.KEY_LOCATION_LATITUDE, String.valueOf(location.get("latitude")));
                dataJSONObject.put(FirebaseAnalyticsParams.KEY_LOCATION_LONGITUDE, String.valueOf(location.get("longitude")));
            } catch (NullPointerException ex) {
                Log.i(TAG, "No location information available");
            } catch (Exception ex) {
                Log.i(TAG, "Location error:" + ex.getMessage());
            }
            dataString = dataJSONObject.toString();
            data.putString("data", dataString);

            String id = UUID.randomUUID().toString();
            // send Firebase analytics report

            msg = "Sent message";
            sharedPreferences.edit().putLong(LAST_REPORT,System.currentTimeMillis()).apply();
        } catch (PackageManager.NameNotFoundException ex) {
            msg = "Error :" + ex.getMessage();
        } catch (JSONException ex) {
            msg = "Error :" + ex.getMessage();
        }
        return msg;
    }

    private JSONArray getFormReports() {
        //JSONArray formReports = new JSONArray();
        ArrayList<String> formPaths = new ArrayList<String>();
        ArrayList<String> displayNames = new ArrayList<String>();
        ArrayList<String> formFileMD5Sums = new ArrayList<String>();
        ArrayList<String> jrVersions = new ArrayList<String>();

        int n = 0;
        Cursor c = null;
        List formHeaders;
        ArrayList<FormDetails> formReports = new ArrayList<FormDetails>();

        try {
            c = new FormsDao().getFormsCursor();

            if (c.getCount() > 0) {
                c.moveToPosition(-1);

                DownloadFormListUtils mDownloadFOrmListUtils = new DownloadFormListUtils();
                HashMap<String, FormDetails> formDetailsHashMap =
                        mDownloadFOrmListUtils.downloadFormList(true);

                while (c.moveToNext()) {
                    String currentFormId = c.getString(c.getColumnIndex(FormsProviderAPI.FormsColumns.JR_FORM_ID));
                    formReports.add(formDetailsHashMap.get(currentFormId));
                }
            }
        } catch (Exception e) {
            Log.e(TAG,e.getMessage());
        }

        return new JSONArray(formReports);
    }


    private JSONObject getLocation(){
        LocationManager mLocationManager = (LocationManager)
                Collect.getInstance().getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
        List<String> providers = mLocationManager.getProviders(true);
        Location bestLocation = null;
        JSONObject locationJSONObject = new JSONObject();

        for (String provider : providers) {
            try {
                Location l = mLocationManager.getLastKnownLocation(provider);

                if (l == null) {
                    continue;
                }
                if (bestLocation == null || l.getAccuracy() < bestLocation.getAccuracy()) {
                    // Found best last known location: %s", l);
                    bestLocation = l;
                }
            }
            catch (SecurityException e) {
                Timber.e("Location not available: " + e.getMessage());
                return null;
            }
        }

        if (bestLocation != null){
            try {
                locationJSONObject.put("longitude", bestLocation.getLongitude());
                locationJSONObject.put("latitude", bestLocation.getLatitude());
            }
            catch (JSONException e){
                Log.e(TAG,"JSON exception when getting location: " + e.getMessage());
                return null;
            }
        }
        else{
            return null;
        }
        return locationJSONObject;
    }
}
