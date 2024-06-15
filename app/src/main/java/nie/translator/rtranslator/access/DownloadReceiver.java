/*
 * Copyright 2016 Luca Martino.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copyFile of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nie.translator.rtranslator.access;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.io.File;

import nie.translator.rtranslator.Global;
import nie.translator.rtranslator.LoadingActivity;
import nie.translator.rtranslator.R;
import nie.translator.rtranslator.tools.FileTools;

public class DownloadReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if(context != null && intent.getAction() != null && intent.getAction().equals("android.intent.action.DOWNLOAD_COMPLETE")){
            Downloader downloader = new Downloader(context);
            int downloadStatus = downloader.getRunningDownloadStatus();
            if(downloadStatus == DownloadManager.STATUS_SUCCESSFUL) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);

                        Global global = (Global) context.getApplicationContext();
                        if (global != null) {
                            global.isForeground();
                        }

                        if (downloadId != -1) {
                            Downloader downloader = new Downloader(context);
                            int urlIndex = downloader.findDownloadUrlIndex(downloadId);
                            if (urlIndex != -1) {
                                SharedPreferences sharedPreferences = context.getSharedPreferences("default", Context.MODE_PRIVATE);
                                //we save the success of the download
                                SharedPreferences.Editor editor = sharedPreferences.edit();
                                editor.putString("lastDownloadSuccess", DownloadFragment.DOWNLOAD_NAMES[urlIndex]);
                                editor.apply();
                                //we reset the failure info of the transfer
                                editor = sharedPreferences.edit();
                                editor.putString("lastTransferFailure", "");
                                editor.apply();
                                //we move the downloaded content to internal storage and start the next download
                                File from = new File(context.getExternalFilesDir(null) + "/" + DownloadFragment.DOWNLOAD_NAMES[urlIndex]);
                                File to = new File(context.getFilesDir() + "/" + DownloadFragment.DOWNLOAD_NAMES[urlIndex]);
                                int finalUrlIndex = urlIndex;
                                FileTools.moveFile(from, to, new FileTools.MoveFileCallback() {
                                    @Override
                                    public void onSuccess() {
                                        //we save the success of the transfer
                                        SharedPreferences.Editor editor = sharedPreferences.edit();
                                        editor.putString("lastTransferSuccess", DownloadFragment.DOWNLOAD_NAMES[finalUrlIndex]);
                                        editor.apply();

                                        if (finalUrlIndex < (DownloadFragment.DOWNLOAD_URLS.length - 1)) {  //if the download done is not the last one
                                            //we start the next download
                                            long newDownloadId = downloader.downloadModel(DownloadFragment.DOWNLOAD_URLS[finalUrlIndex + 1], DownloadFragment.DOWNLOAD_NAMES[finalUrlIndex + 1]);
                                            editor = sharedPreferences.edit();
                                            editor.putLong("currentDownloadId", newDownloadId);
                                            editor.apply();
                                        } else {
                                            //we notify the completion of the download of all models
                                            Handler mainHandler = new Handler(Looper.getMainLooper());
                                            mainHandler.post(() -> Toast.makeText(context, context.getResources().getString(R.string.toast_download_completed), Toast.LENGTH_LONG).show());
                                            //we save in the preferences that all the download and transfers are completed
                                            editor = sharedPreferences.edit();
                                            editor.putLong("currentDownloadId", -2);
                                            editor.apply();

                                            startRTranslator(context);
                                        }
                                    }

                                    @Override
                                    public void onFailure() {
                                        //we save the failure of the transfer
                                        SharedPreferences.Editor editor = sharedPreferences.edit();
                                        editor.putString("lastTransferFailure", DownloadFragment.DOWNLOAD_NAMES[finalUrlIndex]);
                                        editor.apply();
                                        //we notify the failure to the user
                                        notifyTransferFailed(context);
                                    }
                                });
                            }
                        }
                    }
                }).start();
            } else if (downloadStatus == DownloadManager.STATUS_FAILED /*|| downloadStatus == -1*/) {
                notifyDownloadFailed(context);
            }
        }
    }

    private void notifyDownloadFailed(Context context){
        //if the app is in background we generate a toast that notify the error
        Global global = (Global) context.getApplicationContext();
        if (global != null) {
            if (global.getRunningAccessActivity() == null) {  //if we are in background
                //we notify the failure of the download of the models
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> Toast.makeText(context, context.getResources().getString(R.string.toast_download_error), Toast.LENGTH_LONG).show());
            }
        }
    }

    private void notifyTransferFailed(Context context){
        //if the app is in background we generate a toast that notify the error
        Global global = (Global) context.getApplicationContext();
        if (global != null) {
            if (global.getRunningAccessActivity() == null) {  //if we are in background
                //we notify the failure of the download of the models
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> Toast.makeText(context, context.getResources().getString(R.string.toast_download_error), Toast.LENGTH_LONG).show());
            }
        }
    }

    private void startRTranslator(Context context){
        Global global = (Global) context.getApplicationContext();
        if (global != null) {
            AccessActivity activity = global.getRunningAccessActivity();
            if (activity != null) {
                //modification of the firstStart
                global.setFirstStart(false);
                //start activity
                Intent intent = new Intent(activity, LoadingActivity.class);
                intent.putExtra("activity", "download");
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(intent);
                activity.finish();
            }
        }
    }
}


