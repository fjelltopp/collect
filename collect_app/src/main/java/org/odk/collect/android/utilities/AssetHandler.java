/*
 * Copyright (C) 2015 WHO
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
 * 
 * Original License from BasicCredentialsProvider:
 * ====================================================================
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.odk.collect.android.utilities;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.AsyncTask;
import android.util.Log;

import org.odk.collect.android.application.Collect;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

//import android.content.res.Resources;

public class AssetHandler extends AsyncTask<String, Integer, Integer>{

    private final String TAG = "AssetHandler";
    private static Context context=Collect.getInstance().getBaseContext();
    private String rootpath;
    private AssetManager manager;
    private Integer filesHandled = 0;

    @Override
    protected Integer doInBackground(String... params) {
        this.rootpath=params[0];
        this.manager=context.getAssets();
        copyAsset(params[1]);
        return 0;
    }

    // recursive method to go through files
    private void copyAsset(String path) {
        String assets[] = null;
        try {
            assets = manager.list(path);
            if (assets.length == 0) {
                copyFile(path);
            } else {
                String fullPath = rootpath + File.separator + path;
                File dir = new File(fullPath);
                if (!dir.exists())
                    dir.mkdir();
                for (int i = 0; i < assets.length; ++i) {
                    copyAsset(path + File.separator + assets[i]);
                }
            }
        } catch (IOException ex) {
            Log.e(TAG, "I/O Exception", ex);
        }
    }

    private void copyFile(String filename) {

        InputStream in = null;
        OutputStream out = null;

        try {
            in = manager.open(filename);
            String newFileName = rootpath + File.separator + filename;
            File newFile = new File(newFileName);
            File importedFile = new File(newFileName + ".imported");

            if (!newFile.exists() && !importedFile.exists()) {
                out = new FileOutputStream(newFileName);

                byte[] buffer = new byte[1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                in.close();
                in = null;
                out.flush();
                out.close();
                out = null;
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
    }
}