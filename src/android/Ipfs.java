package org.apache.cordova.ipfs;

import android.app.DownloadManager;
import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.LOG;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.icu.util.Output;
import android.util.Log;

import static android.content.Context.DOWNLOAD_SERVICE;

public class Ipfs extends CordovaPlugin {
    private String appFilesDir;
    private URL ipfsArchiveSrc;
    private String ipfsBinPath;
    private String ipfsRepo;
    private Boolean resetRepo;

    private String LOG_TAG = "#######CIP######";

    private String downloadIpfs() {
        InputStream input = null;
        OutputStream output = null;
        HttpURLConnection conn = null;
        int archiveFileLength;
        long totalDownloaded = 0;
        byte block[] = new byte[4096];
        int blockSize;

        try {
            Log.d(LOG_TAG, "STARTING DOWNLOAD");
            conn = (HttpURLConnection) ipfsArchiveSrc.openConnection();
            Log.d(LOG_TAG, "OPENED CONN");

            // TODO: fix - connect freezes on wifi
            conn.connect();
            Log.d(LOG_TAG, "CONNECTED TO SRC");
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return "Server returned HTTP " + conn.getResponseCode()
                        + " " + conn.getResponseMessage();
            }
            Log.d(LOG_TAG, "GOT 200 FROM SRC");
            archiveFileLength = conn.getContentLength();

            File oldArchive = new File(appFilesDir + "go-ipfs.tar.gz");
            // archive exists and has the same size (TODO: HASH ?)
            if (oldArchive.exists() && oldArchive.length() == archiveFileLength) {
                Log.d(LOG_TAG, "archive exists and has the same length");
                return null;
            }

            input = conn.getInputStream();
            output = new FileOutputStream(appFilesDir + "go-ipfs.tar.gz");

            while ((blockSize = input.read(block)) != -1) {
                totalDownloaded += blockSize;
                if (archiveFileLength > 0)
                    Log.d(LOG_TAG, "Download Prograssing" + (int) (totalDownloaded * 100 / archiveFileLength));
                output.write(block, 0, blockSize);
            }

            Log.d(LOG_TAG, "FINISHED DOWNLOADING");
        } catch (Exception e) {
            return e.toString();
        } finally {
            try {
                if (output != null)
                    output.close();
                if (input != null)
                    input.close();
            } catch (IOException ignored) {
            }
            if (conn != null)
                conn.disconnect();
        }

        return null;
    }

    private String extractIpfs() {
        cordova.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                Process extrProc;
                StringBuffer extrOutput = new StringBuffer();

            }
        });

        return null;
    }

    private void prepareIpfs() {
        String downResult = this.downloadIpfs();
        String extrResult = this.extractIpfs();
    }

    private void init(JSONArray args, final CallbackContext cbCtx) {
        // get the config from the provided config JSON
        try {
            JSONObject config = args.getJSONObject(0);
            this.appFilesDir = config.getString("appFilesDir");
            this.ipfsArchiveSrc = new URL(config.getString("src"));
            this.resetRepo = config.getBoolean("resetRepo");
            this.ipfsBinPath = appFilesDir.concat("go-ipfs/ipfs");
            this.ipfsRepo = appFilesDir.concat(".ipfs");
            Log.d(LOG_TAG, "ipfsBinPath: " + ipfsBinPath);
            Log.d(LOG_TAG, "ipfsArchiveSrc: " + ipfsArchiveSrc);
            Log.d(LOG_TAG, "appFilesDir: " + appFilesDir);
        } catch (JSONException e) {
            cbCtx.error("Cordova IPFS Plugin (init): Invalid Config JSON Object ! \n" + e.toString());
        } catch (MalformedURLException e) {
            cbCtx.error("Cordova IPFS Plugin (init): Malformed URL ! \n" + e.toString());
        }
        // check if archive exists
        File ipfsBin = new File(ipfsBinPath);
        if (ipfsBin.exists()) {
            cbCtx.success("IPFS exists !");
        } else {
            this.prepareIpfs();
            cbCtx.success("IPFS doesn't exist but was prepared");
        }

    }

    private void startDaemon(JSONArray args, final CallbackContext cbCtx) {
        cbCtx.success("Cordova Ipfs Plugin: start success");
    }

    private void stopDaemon(JSONArray args, final CallbackContext cbCtx) {
        cbCtx.success("Cordova Ipfs Plugin: stop success");
    }

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
    }

    @Override
    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) {
        if (action.equals("init")) {
            this.init(args, callbackContext);
            return true;
        } else if (action.equals("start")) {
            this.startDaemon(args, callbackContext);
            return true;
        } else if (action.equals("stop")) {
            this.stopDaemon(args, callbackContext);
            return true;
        }
        return false;
    }

}
