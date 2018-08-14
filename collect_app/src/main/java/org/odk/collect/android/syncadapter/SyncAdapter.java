/*
 * Copyright 2015 WHO
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.odk.collect.android.syncadapter;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import org.odk.collect.android.application.Collect;
// import org.odk.collect.android.database.SyncSQLiteContract.SyncFileEntry;
// import org.odk.collect.android.database.SyncSQLiteOpenHelper;
// import org.odk.collect.android.gcm.SendDeviceReport;
import org.odk.collect.android.dao.FormsDao;
import org.odk.collect.android.preferences.PreferenceKeys;
import org.odk.collect.android.provider.FormsProviderAPI;
import org.odk.collect.android.utilities.XmlStreamUtils;
import org.odk.collect.android.utilities.XmlStreamUtils.XFormHeader;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

import javax.net.ssl.HttpsURLConnection;

/**
 * Define a sync adapter for the app.
 *
 * <p>This class is instantiated in {@link SyncService}, which also binds SyncAdapter to the system.
 * SyncAdapter should only be initialized in SyncService, never anywhere else.
 *
 * <p>The system calls onPerformSync() via an RPC call through the IBinder object supplied by
 * SyncService.
 */
class SyncAdapter extends AbstractThreadedSyncAdapter {
    public static final String TAG = "SyncAdapter";

	SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(Collect.getInstance());
    
    /**
     * Directory to be synced with the version on the server
     */

    private File SYNCFOLDER;
    
    /**
     * Network connection timeout, in milliseconds.
     */
    private static final int NET_CONNECT_TIMEOUT_MILLIS = 15000;  // 15 seconds

    /**
     * Network read timeout, in milliseconds.
     */
    private static final int NET_READ_TIMEOUT_MILLIS = 10000;  // 10 seconds
    
    /**
     * Constructor. Obtains handle to content resolver for later use.
     */
    public SyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        SYNCFOLDER = new File(Collect.FORMS_PATH);
    }
    
    /**
     * Constructor. Obtains handle to content resolver for later use.
     */
    public SyncAdapter(Context context, boolean autoInitialize, boolean allowParallelSyncs) {
        super(context, autoInitialize, allowParallelSyncs);
        SYNCFOLDER = new File(Collect.FORMS_PATH);
    }

  
    /**
     * Called by the Android system in response to a request to run the sync adapter. The work
     * required to read data from the network, parse it, and store it in the content provider is
     * done here. Extending AbstractThreadedSyncAdapter ensures that all methods within SyncAdapter
     * run on a background thread. For this reason, blocking I/O and other long-running tasks can be
     * run <em>in situ</em>, and you don't have to set up a separate thread for them.
     .
     *
     * <p>This is where we actually perform any work required to perform a sync.
     * {@link AbstractThreadedSyncAdapter} guarantees that this will be called on a non-UI thread,
     * so it is safe to perform blocking I/O here.
     *
     * <p>The syncResult argument allows you to pass information back to the method that triggered
     * the sync.
     */
    @Override
    public void onPerformSync(Account account, Bundle extras, String authority,
                              ContentProviderClient provider, SyncResult syncResult) {

		Log.i(TAG, "Checking for sync");
    	SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this.getContext());

    	boolean allowSync = sp.getBoolean(PreferenceKeys.KEY_ALLOW_FORM_SYNC, true);
    	boolean forceSync = extras.getBoolean(PreferenceKeys.KEY_FORCE_UPDATE, false);


    	if (allowSync || forceSync) {
			URL aggregate_url;
	    	Log.i(TAG, "Synchronization started");
			try {

				aggregate_url = new URL(
				        sp.getString(PreferenceKeys.KEY_SERVER_URL, null) + "/") ;
                aggregate_url = new URL("https://som.emro.info");
				syncForms(aggregate_url, forceSync, syncResult);

			} catch (MalformedURLException e) {
				Log.e(TAG,"Malformed URL");
			} catch (IOException e) {
				Log.e(TAG,"Could not synchronize folder tree");
			} catch (NullPointerException e) {
				Log.e(TAG, "URL read error");
			}

			// send Device report if last report sent over a day ago
			// Long last_report = sharedPreferences.getLong(SendDeviceReport.LAST_REPORT,0);
			// if ((System.currentTimeMillis() >= last_report + (24 * 60 * 60 * 1000)) || forceSync){
			//	new SendDeviceReport().execute();
			// }

	    	Log.i(TAG, "Synchronization finished");
    	}
    	else {
    	    Log.i(TAG, "Sync disabled");
        }
    }

    private void syncForms(final URL url, boolean forceSync, SyncResult syncResult) throws IOException {

        Cursor c = null;
        List formHeaders;

        try {
            c = new FormsDao().getFormsCursor();

            if (c.getCount() > 0) {
                c.moveToPosition(-1);
                formHeaders = getFormHeaders(url);
                while (c.moveToNext()) {
                    XFormHeader currentFormHeader = getCurrentFormHeader(
                            formHeaders,
                            c.getString(c.getColumnIndex(FormsProviderAPI.FormsColumns.JR_FORM_ID)));
                    String remoteFormMD5 = currentFormHeader.hash;
                    String localFormMD5 = "md5:" + c.getString(c.getColumnIndex(
                            FormsProviderAPI.FormsColumns.MD5_HASH));
                    if (!remoteFormMD5.equals(localFormMD5)) {
                        downloadUrl(new URL(""));
                    }

                    ArrayList<String> mediaFiles = getMediaFiles(c.getString(
                            c.getColumnIndex(FormsProviderAPI.FormsColumns.FORM_MEDIA_PATH)));
                    for (String mediaFile : mediaFiles) {
                        //String remoteMediaFileMD5 = remoteMediaFileMD5OnAggregate(form);
                        //String localMediaFileMD5 = localMediaFileMD5OnAggregate(form);
                        //if (!remoteMediaFileMD5.equals(localMediaFileMD5)) {
                        //    downloadUrl(new URL(""));
                        //}

                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG,e.getMessage());
        }
    }


    private ArrayList<String> getMediaFiles(String formMediaPath) {
        String[] array = new String[]{"demo_register","demo_case"};
        return new ArrayList<>(Arrays.asList(array));


    }

    private InputStream downloadUrl(final URL url) throws IOException {
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setReadTimeout(NET_READ_TIMEOUT_MILLIS /* milliseconds */);
        conn.setConnectTimeout(NET_CONNECT_TIMEOUT_MILLIS /* milliseconds */);
        conn.setRequestMethod("GET");
        conn.setDoInput(true);
        
        // Starts the query
        try {
        conn.connect();
        } catch (IOException e) {
        	Log.e(TAG, "Error connecting to network: " + e.toString());
        }
        return conn.getInputStream();
    }

    private List getFormHeaders(URL aggregate_url) throws IOException {
        String aggregate_url_text = aggregate_url.toString();
        List<XFormHeader> formHeaderList;
        InputStream inputStream;
        URL url = new URL(aggregate_url_text + "/xformsList");
        try {
            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
            conn.setReadTimeout(NET_READ_TIMEOUT_MILLIS /* milliseconds */);
            conn.setConnectTimeout(NET_CONNECT_TIMEOUT_MILLIS /* milliseconds */);
            conn.setRequestMethod("GET");
            inputStream = conn.getInputStream();
            formHeaderList = XmlStreamUtils.readFormHeaders(inputStream);

            return formHeaderList;
        }
        catch (IOException e) {
            Log.e(TAG, "Could not fetch form headers from Aggregate");
            //throw new IOException("Could not fetch Aggregate form MD5", e);
            return null;
        } catch (XmlPullParserException e) {
            throw new IOException("Broken form manifest in Aggregate", e);
        }
    }

    private XFormHeader getCurrentFormHeader(List<XFormHeader> formHeaderList, String formID) {
        try {
            for (XFormHeader header : formHeaderList) {
                if (header.formId.equals(formID)) {
                    return header;
                }
            }
            Log.w(TAG, "No remote header found for form " + formID);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Unexpected form header structure: " + e.getMessage());
            return null;
        }
}

    private String remoteMediaFileMD5OnAggregate (String formID) throws IOException {
        String server_response = "";
        String remoteMD5 = "";
        URL url = new URL("");
        try {
            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
            conn.setReadTimeout(NET_READ_TIMEOUT_MILLIS /* milliseconds */);
            conn.setConnectTimeout(NET_CONNECT_TIMEOUT_MILLIS /* milliseconds */);
            conn.setRequestMethod("GET");
            server_response = readStream(conn.getInputStream());
        }
        catch (IOException e) {
            throw new IOException("Could not fetch Aggregate form MD5", e);
        }
        return "";
    }

	private String readStream(InputStream in) {
		BufferedReader reader = null;
		StringBuffer response = new StringBuffer();
		try {
			reader = new BufferedReader(new InputStreamReader(in));
			String line;
			while ((line = reader.readLine()) != null) {
				response.append(line);
			}
		} catch (IOException e) {
			Log.e(TAG, "Error reading remote stream: " + e.getMessage());
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					Log.e(TAG, "Error closing remote stream: " + e.getMessage());
				}
			}
		}
		return response.toString();
	}
}