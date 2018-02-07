/*
 * Copyright (C) 2012 University of Washington
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.odk.collect.android.tasks;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.util.Log;


import org.odk.collect.android.BuildConfig;
import org.odk.collect.android.R;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.listeners.DeleteFormsListener;
import org.odk.collect.android.provider.FormsProviderAPI.FormsColumns;
import org.odk.collect.android.utilities.AssetHandler;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;


/**
 * Task that fetches all form id:s from the app database and sends them to DeleteFormsTask to be deleted. 
 * Then recursively empties the ODK form folder of all files.
 * @author soppela.jyri@gmail.com
 */

public class RefreshAllFormsTask extends AsyncTask<Void, Void, Integer> implements DeleteFormsListener {
    private final String TAG = "RefreshAllFormsTask";
    private ContentResolver cr;
    private Context ct;
    private ArrayList<Long> allFormsList = new ArrayList<Long>();
    private Long[] allForms;
    DeleteFormsTask mDeleteFormsTask;

    @Override
    protected Integer doInBackground(Void... params) {
        Cursor c = cr.query(FormsColumns.CONTENT_URI, null, null, null, null);

        allFormsList = new ArrayList<Long>();

        c.moveToFirst();
        while(!c.isAfterLast()) {
            Long mForm = c.getLong(c.getColumnIndex(FormsColumns._ID));
            allFormsList.add(mForm);
            c.moveToNext();
        }

        allForms = (Long[]) allFormsList.toArray(new Long[allFormsList.size()]);

        int versionCode = 0;

        // create deleteFormsTask if current build has reinstall_forms set to Y
        if (BuildConfig.FORMS_CLEANUP) {
            mDeleteFormsTask = new DeleteFormsTask();
            mDeleteFormsTask
                    .setContentResolver(cr);
            mDeleteFormsTask.setDeleteListener(this);

            File formsFolder = new File(Collect.FORMS_PATH);
            try {
                recursiveDeleteDirectory(formsFolder);
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
            }
            formsFolder.mkdirs();
        }

        return allFormsList.size();
    }

    protected void onPostExecute(Integer result) {
        // delete forms from memory if current build has FORMS_CLEANUP build config set to True
        if (BuildConfig.FORMS_CLEANUP) {
            mDeleteFormsTask.execute(allForms);
        }
        new AssetHandler(ct).execute(Collect.ODK_ROOT,"forms");
    }

    public void setContext(Context context) {ct = context; }

    public void setContentResolver(ContentResolver resolver){
        cr = resolver;
    }

    public void recursiveDeleteDirectory(File fileOrDirectory) throws IOException{
        if (fileOrDirectory.isDirectory()) {
            for (File child : fileOrDirectory.listFiles()) {
                try{
                    recursiveDeleteDirectory(child);
                } catch(IOException e){
                    throw new IOException(e);
                }
            }
        }

        if (!fileOrDirectory.delete()){
            throw new IOException("Could not delete file or folder: " + fileOrDirectory.getName());
        }
    }

    @Override
    public void deleteComplete(int deletedForms) {
        // TODO Auto-generated method stub

    }

}