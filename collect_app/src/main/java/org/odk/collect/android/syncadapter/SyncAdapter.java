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
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;

import org.odk.collect.android.R;
import org.odk.collect.android.application.Collect;
// import org.odk.collect.android.database.SyncSQLiteContract.SyncFileEntry;
// import org.odk.collect.android.database.SyncSQLiteOpenHelper;
// import org.odk.collect.android.gcm.SendDeviceReport;
import org.odk.collect.android.preferences.PreferencesActivity;
import org.odk.collect.android.preferences.PreferenceKeys;
import org.odk.collect.android.utilities.FileUtils;
// import org.odk.collect.android.utilities.NotificationUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
			URL location;
			String location_url;
	    	Log.i(TAG, "Synchronization started");
			try {
				location_url = sp.getString(PreferenceKeys.KEY_FORM_SYNC_URL, null);
				if (location_url.endsWith(File.separator)){
					location_url=location_url.substring(0,location_url.length()-1);
				}
				location = new URL(location_url + "/");
				
				
				
				syncFileTree(location, SYNCFOLDER, forceSync, syncResult);
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
    }
    
    private void syncFileTree(final URL url, final File folder, boolean forceSync, SyncResult syncResult) throws IOException {
    	String absolutePath;
    	String relativePath;
    	String cleanedRelativePath;
    	String workPath;
    	String urlpath;
    	URL newurl;

		String localMD5;
		String remoteMD5;
    	
    	File[] filelist = folder.listFiles();
    	String msg = "Sync folder cannot be read or is empty";
    	if(filelist == null) {throw new IOException(msg);}
    	
    	for (File file : filelist) {
    		absolutePath = file.getAbsolutePath();
    		relativePath = absolutePath.replaceFirst(SYNCFOLDER.getAbsolutePath(), "");
    		if (relativePath.startsWith(File.separator)) {cleanedRelativePath = relativePath.substring(1);}
    			else {cleanedRelativePath = relativePath;}
    		
    		//fetch new csv file corresponding to the imported file unless corresponding csv exists, in which case continue
    		if (cleanedRelativePath.endsWith(".imported")) {
    			cleanedRelativePath=cleanedRelativePath.replaceAll(".imported", "");
				File csvToImport = new File(absolutePath.replaceAll(".imported", ""));
				if (csvToImport.exists()){
					continue;
				}
    		} else if (cleanedRelativePath.endsWith(".bad")) {
				//fetch new xml file corresponding to the broken file unless corresponding xml exists, in which case continue
				cleanedRelativePath=cleanedRelativePath.replaceAll(".bad", "");
				File xmlToImport = new File(absolutePath.replaceAll(".bad", ""));
				if (xmlToImport.exists()){
					continue;
				}
			}

    		try {

    			workPath=SYNCFOLDER.getAbsolutePath() + File.separator + cleanedRelativePath;
    			urlpath=url.toString() + cleanedRelativePath;

				//Encode the URL to accommodate special characters
				urlpath= URLEncoder.encode(urlpath,"UTF-8");
				urlpath=urlpath.replace("%3A", ":");
				urlpath=urlpath.replace("%2F", "/");


    			newurl = new URL(urlpath);
				Log.i(TAG,"Syncing url " + urlpath);
    			
    			if (file.isFile()) {

					//Get MD5 hash on device
					localMD5= FileUtils.getMd5Hash(file);
					Log.i(TAG, "Local file MD5: " + localMD5);

					//Get MD5 hash on server
					remoteMD5 = remoteMD5OnServer(newurl);
					try {
						remoteMD5 = remoteMD5.replace("\"", ""); // remove double quotes
					} catch (NullPointerException e) {
						Log.w(TAG, "Could not read remote MD5: " + e.getMessage());
						remoteMD5 = null;
					}
					Log.i(TAG, "Remote file MD5: " + remoteMD5);
			
    				//Compare MD5 sums and update if there's a difference
					boolean matchingMD5=false;
					try {
						matchingMD5 = localMD5.equals(remoteMD5);
					} catch (NullPointerException e) {
						Log.e(TAG, "MD5 comparison failed: " + e.getMessage());
						matchingMD5 = false;
					}
    				if (!matchingMD5 && (remoteMD5 != null)) {
    					InputStream stream = downloadUrl(newurl);
						updateLocalData(new File(workPath), stream, syncResult);
    				}
    			} else if (file.isDirectory()) {
    				syncFileTree(url, file, forceSync, syncResult);
    			}
    		} catch (UnsupportedEncodingException e) {
    			Log.e(TAG,"Encoding not supported" + e.getMessage());
    		} catch (MalformedURLException e) {
    			Log.e(TAG,"URL not valid: " + e.getMessage());
    		}
    	}
    }



    public void updateLocalData(final File outputfile, final InputStream stream, final SyncResult syncResult) throws IOException {
    	Log.i(TAG, "Updating file " + outputfile.getAbsolutePath());
    	File cacheDir = this.getContext().getCacheDir();
    	File cacheFile = File.createTempFile(outputfile.getName(), ".tmp", cacheDir);
    	BufferedInputStream bis = null;
    	InputStream is = null;
    	OutputStream os = null;
    	BufferedOutputStream bos = null;
    	try{
    		Log.i(TAG, "Printing stream to cache file");
    		
    		os = new FileOutputStream(cacheFile);
    		bos = new BufferedOutputStream(os);
    		
    		byte[] buffer = new byte[256];
    		int read;
    		while ((read = stream.read(buffer)) != -1) {
    			bos.write(buffer,0,read);
    		}
    	} catch (IOException e) {
    		Log.e(TAG, "Error while reading stream: " + e.getMessage());
    		// TODO: migrate NotificationUtils
//			NotificationUtils.raiseNotification(1, this.getContext().getString(R.string.app_name)
//					+ " " + this.getContext().getString(R.string.sync_warning),
//					this.getContext().getString(R.string.sync_error_local));
    	} finally {
    		try{
    			if (stream != null) {
    				stream.close();
    			}
    			if (bos != null){
    				bos.flush();
    				bos.close();
    			}

    		} catch (IOException e) {
    			Log.e(TAG,"Error while closing streams: " + e.getMessage());
    		}
    	}
    	
    	try{
    		outputfile.delete();
    		Log.i(TAG, "Reading cache file");
    		is = new FileInputStream(cacheFile);
    		os = new FileOutputStream(outputfile);
      		bos = new BufferedOutputStream(os);
    		
    		byte[] buffer = new byte[256];
    		int read;
    		while ((read = is.read(buffer)) != -1) {
    			bos.write(buffer,0,read);
    		}
    		cacheFile.delete();
			Log.i(TAG, "File " + outputfile.getAbsolutePath() + " updated");
//			NotificationUtils.raiseNotification(-1, this.getContext().getString(R.string.app_name)
//							+ " " + this.getContext().getString(R.string.sync_form_info),
//					this.getContext().getString(R.string.sync_form_file) + " " + outputfile.getName() +
//							" " + this.getContext().getString(R.string.sync_form_updated));
    	} catch (IOException e) {
    		Log.e(TAG,"Error while reading from cache file: " + e.getMessage());
    	} finally {
    		try{
    			if (is != null) {
    				is.close();
    			}
    			if (bos != null){
    				bos.flush();
    				bos.close();
    			}

    		} catch (IOException e) {
    			Log.e(TAG,"Error while closing cache stream: " + e.getMessage());
    		}
    	}
    	

    	
    }
    
    public void updateLocalDataFromZip(final InputStream stream, final SyncResult syncResult){
        Log.i(TAG, "Start updating local data from zip on server");
        String outputDir = Environment.getExternalStorageDirectory() + File.separator +
        		"odk";
        ZipInputStream zStream = new ZipInputStream(new BufferedInputStream(stream));
        
        try {
        	Log.i(TAG, "Printing zip stream to file");
        	try {
        		ZipEntry ze;
        		while ((ze = zStream.getNextEntry()) != null) {
        			ByteArrayOutputStream baos = new ByteArrayOutputStream();
        			byte[] buffer = new byte[1024];
        			int count;
        			while ((count = zStream.read(buffer)) != -1) {
        				baos.write(buffer, 0, count);
        			}
        			String filename = ze.getName();
        			byte[] bytes = baos.toByteArray();
        			
        			filename = ze.getName();  
        		    
        			boolean debugIsDirectory = ze.isDirectory();
        		   
        			// if the zip entry is a directory, create the directory and its parent 
        			// directories
        			if (ze.isDirectory()) {  
        				File fmd = new File(outputDir + File.separator + filename);
        				fmd.mkdirs();    
        			}  else {
        				File outputFile = new File(outputDir + File.separator,filename);
        				
        				// create parent directories for file if they are missing
        	            if (!outputFile.getParentFile().exists()) {
        	                outputFile.getParentFile().mkdirs();
        	            }
        	            
        	            // write file on disk
        				FileOutputStream fout = new FileOutputStream(outputFile);
        				fout.write(bytes);
        				fout.close();
        			}
        		    zStream.closeEntry();  
        		}
        		
        	} catch (IOException e) {
        		Log.e(TAG, "Cannot write to file: " + e);
        	} finally {
        		if (zStream != null) {
        			zStream.close();
        		}
        	}
        	
        }
        catch (IOException e) {
        	Log.e(TAG, "Cannot read zip from stream: " + e);
        }
        
        
        Log.i(TAG, "Updating local data complete");
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
    
    private InputStream downloadZipFromUrl(final URL url) throws IOException {
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

	private String remoteMD5OnServer (final URL url) throws IOException {
		String remoteMD5 = "";
		try {
			HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
			conn.setReadTimeout(NET_READ_TIMEOUT_MILLIS /* milliseconds */);
			conn.setConnectTimeout(NET_CONNECT_TIMEOUT_MILLIS /* milliseconds */);
			conn.setRequestMethod("HEAD");
			conn.setDoInput(true);
			conn.connect();
			remoteMD5=conn.getHeaderField("eTag");
			conn.disconnect();
			return remoteMD5;
		} 	catch (IOException e) {
			throw new IOException("Could not fetch remote MD5", e);
		}
	}
}