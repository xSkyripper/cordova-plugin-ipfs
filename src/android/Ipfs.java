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
import java.util.concurrent.Future;

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
    private Process ipfsDaemonProcess = null;
    private Future ipfsDaemonThreadFuture = null;

    private String LOG_TAG = "#######CIP######";

    private void execShell(final String[] cmdArray, final String[] envArray) {
        Process extrProc;
        StringBuilder extrOutput = new StringBuilder();

        try {
            extrProc = Runtime.getRuntime().exec(cmdArray, envArray);
            BufferedReader extrOutputReader = new BufferedReader(new InputStreamReader(extrProc.getInputStream()));
            String line;

            while ((line = extrOutputReader.readLine()) != null) {
                extrOutput.append(line).append("\n");
                Log.d(LOG_TAG, line);
            }

            Log.d(LOG_TAG, extrOutput.toString());
            extrProc.waitFor();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    private void execShellAsync(final String[] cmdArray, final String[] envArray) {
        cordova.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                Process extrProc;
                StringBuilder extrOutput = new StringBuilder();

                try {
                    extrProc = Runtime.getRuntime().exec(cmdArray, envArray);
                    BufferedReader extrOutputReader = new BufferedReader(new InputStreamReader(extrProc.getInputStream()));
                    String line;

                    while ((line = extrOutputReader.readLine()) != null) {
                        extrOutput.append(line).append("\n");
                        Log.d(LOG_TAG, line);
                    }

                    Log.d(LOG_TAG, extrOutput.toString());
                    extrProc.waitFor();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
        });
    }

    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory())
            for (File child : fileOrDirectory.listFiles())
                deleteRecursive(child);

        fileOrDirectory.delete();
    }

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

    private void extractIpfs() {
        Log.d(LOG_TAG, "STARTING EXTRACT");
        this.execShell(
                new String[]{"busybox", "tar", "fx", appFilesDir + "go-ipfs.tar.gz", "-C", appFilesDir},
                new String[]{}
        );
        this.deleteRecursive(new File(appFilesDir + "go-ipfs.tar.gz"));
        Log.d(LOG_TAG, "FINISHED EXTRACT");
    }

    private void initRepo() {
        Log.d(LOG_TAG, "INITING IF REPO RESET OR REPO NOT EXISTS");
        if (this.resetRepo || !(new File(ipfsRepo).exists())) {
            this.deleteRecursive(new File(ipfsRepo));
            this.execShell(
                    new String[]{ipfsBinPath, "init"},
                    new String[]{"IPFS_PATH=" + this.ipfsRepo}
            );
        }
        Log.d(LOG_TAG, "INITING FINISHED");
    }

    private String prepareIpfs() {
        String downResult = this.downloadIpfs();
        if (downResult != null)
            return downResult;

        this.extractIpfs();

        return null;
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
            this.initRepo();
        } else {
            String prepResult = this.prepareIpfs();
            if (prepResult != null) {
                cbCtx.error(prepResult);
            }
            this.initRepo();
            cbCtx.success("IPFS doesn't exist but was prepared & inited !");
        }

    }

    private void startDaemon(JSONArray args, final CallbackContext cbCtx) {
        //> ipfs config --json API.HTTPHeaders.Access-Control-Allow-Origin "[\"*\"]"
        //> ipfs config --json API.HTTPHeaders.Access-Control-Allow-Credentials "[\"true\"]"

        this.execShell(
                new String[]{
                        ipfsBinPath, "config", "--json",
                        "API.HTTPHeaders.Access-Control-Allow-Origin", "\"[\\\"*\\\"]\""},
                new String[]{"IPFS_PATH=" + ipfsRepo}
        );
        this.execShell(
                new String[]{
                        ipfsBinPath, "config", "--json",
                        "API.HTTPHeaders.Access-Control-Allow-Credentials", "\"[\\\"truee\\\"]\""},
                new String[]{"IPFS_PATH=" + ipfsRepo}
        );

        final Runnable ipfsDaemonThread = new Runnable() {
            @Override
            public void run() {
//                Process extrProc;
                StringBuilder extrOutput = new StringBuilder();

                try {
                    ipfsDaemonProcess = Runtime.getRuntime().exec(
                            new String[]{ipfsBinPath, "daemon", "--enable-pubsub-experiment"},
                            new String[]{"IPFS_PATH=" + ipfsRepo});

                    BufferedReader extrOutputReader = new BufferedReader(new InputStreamReader(ipfsDaemonProcess.getInputStream()));
                    String line;

                    while ((line = extrOutputReader.readLine()) != null) {
                        extrOutput.append(line).append("\n");
                        Log.d(LOG_TAG, line);
                    }

                    Log.d(LOG_TAG, "IPFS DAEMON EXITVAL: " + ipfsDaemonProcess.waitFor());
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
        };

        if(ipfsDaemonThreadFuture == null) {
            ipfsDaemonThreadFuture = cordova.getThreadPool().submit(ipfsDaemonThread);
        } else {
            if(ipfsDaemonThreadFuture.isCancelled() || ipfsDaemonThreadFuture.isDone()) {
                ipfsDaemonThreadFuture = cordova.getThreadPool().submit(ipfsDaemonThread);
            }
        }

        cbCtx.success("Cordova Ipfs Plugin: start success");
    }

    private void stopDaemon(JSONArray args, final CallbackContext cbCtx) {
        if(this.ipfsDaemonThreadFuture != null) {
            if(this.ipfsDaemonThreadFuture.isDone()) {
                Log.d(LOG_TAG, "DAEMON IS STOPPED");
            } else {
                Log.d(LOG_TAG, "DAEMON IS STILL DAEMONING");
            }
        }

        if (this.ipfsDaemonProcess != null) {
            this.ipfsDaemonProcess.destroy();
        }

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
