package org.apache.cordova.ipfs;

import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.rauschig.jarchivelib.ArchiveFormat;
import org.rauschig.jarchivelib.Archiver;
import org.rauschig.jarchivelib.ArchiverFactory;
import org.rauschig.jarchivelib.CompressionType;

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
        Process proc;
        StringBuilder procOutput = new StringBuilder();

        try {
            proc = Runtime.getRuntime().exec(cmdArray, envArray);
            BufferedReader procOutputReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            String line;

            while ((line = procOutputReader.readLine()) != null) {
                procOutput.append(line).append("\n");
                Log.d(LOG_TAG, line);
            }

            Log.d(LOG_TAG, procOutput.toString());
            proc.waitFor();
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
                Process proc;
                StringBuilder procOutput = new StringBuilder();

                try {
                    proc = Runtime.getRuntime().exec(cmdArray, envArray);
                    BufferedReader extrOutputReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                    String line;

                    while ((line = extrOutputReader.readLine()) != null) {
                        procOutput.append(line).append("\n");
                        Log.d(LOG_TAG, line);
                    }

                    Log.d(LOG_TAG, procOutput.toString());
                    proc.waitFor();
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

    private boolean isRunning(Process process) {
        try {
            process.exitValue();
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    private void downloadIpfs() throws Exception {
        InputStream input;
        OutputStream output;
        HttpURLConnection conn;
        int archiveFileLength;
        long totalDownloaded = 0;
        byte block[] = new byte[4096];
        int blockSize;


        Log.d(LOG_TAG, "STARTING DOWNLOAD");

        conn = (HttpURLConnection) ipfsArchiveSrc.openConnection();
        Log.d(LOG_TAG, "OPENED CONN");

        conn.connect();
        Log.d(LOG_TAG, "CONNECTED TO SRC");

        if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new Exception("Server returned HTTP " + conn.getResponseCode()
                    + " " + conn.getResponseMessage());
        }
        Log.d(LOG_TAG, "GOT 200 FROM SRC");

        archiveFileLength = conn.getContentLength();
        File oldArchive = new File(appFilesDir + "go-ipfs.tar.gz");
        // archive exists and has the same size (TODO: HASH CHECKING)
        if (oldArchive.exists() && oldArchive.length() == archiveFileLength) {
            Log.d(LOG_TAG, "archive exists and has the same length");
            return;
        }

        input = conn.getInputStream();
        output = new FileOutputStream(appFilesDir + "go-ipfs.tar.gz");

        while ((blockSize = input.read(block)) != -1) {
            totalDownloaded += blockSize;
//                if (archiveFileLength > 0)
//                    Log.d(LOG_TAG, "Downloaded " + (int) (totalDownloaded * 100 / archiveFileLength) + "%");
            output.write(block, 0, blockSize);
        }

        Log.d(LOG_TAG, "FINISHED DOWNLOADING");

        input.close();
        output.close();
        conn.disconnect();
    }

    private void extractIpfs() throws Exception {
        Log.d(LOG_TAG, "STARTING EXTRACT");

        Archiver archiver = ArchiverFactory.createArchiver(ArchiveFormat.TAR, CompressionType.GZIP);
        archiver.extract(
                new File(appFilesDir + "go-ipfs.tar.gz"),
                new File(appFilesDir)
        );

        if (!(new File(ipfsBinPath).setExecutable(true, true))) {
            throw new Exception("IPFS Bin cannot be set executable");
        }

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

    private void prepareIpfs() throws Exception {
        this.downloadIpfs();

        this.extractIpfs();
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
            cbCtx.success("Cordova IPFS Plugin (init): IPFS exists !");
            this.initRepo();
        } else {
            try {
                this.prepareIpfs();
            } catch (Exception e) {
                e.printStackTrace();
                cbCtx.error(e.toString());
                return;
            }

            this.initRepo();
            cbCtx.success("Cordova IPFS Plugin (init): IPFS doesn't exist but it was prepared & inited !");
        }

    }

    private void startDaemon(final CallbackContext cbCtx) {
        //> ipfs config --json API.HTTPHeaders.Access-Control-Allow-Credentials "[\"true\"]"
        //> ipfs config --json API.HTTPHeaders.Access-Control-Allow-Origin "[\"*\"]"

        this.execShell(
                new String[]{
                        ipfsBinPath, "config", "--json",
                        "API.HTTPHeaders.Access-Control-Allow-Credentials", "'[\"true\"]'"},
                new String[]{"IPFS_PATH=" + ipfsRepo}
        );

        this.execShell(
                new String[]{
                        ipfsBinPath, "config", "--json",
                        "API.HTTPHeaders.Access-Control-Allow-Origin", "'[\"*\"]'"},
                new String[]{"IPFS_PATH=" + ipfsRepo}
        );


        final Runnable ipfsDaemonThread = new Runnable() {
            @Override
            public void run() {
                StringBuilder extrOutput;
                extrOutput = new StringBuilder();

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
                    //TODO: cb ctx that daemon stopped
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
        };

        if (ipfsDaemonThreadFuture == null) {
            ipfsDaemonThreadFuture = cordova.getThreadPool().submit(ipfsDaemonThread);
        } else {
            if (ipfsDaemonThreadFuture.isCancelled() || ipfsDaemonThreadFuture.isDone()) {
                ipfsDaemonThreadFuture = cordova.getThreadPool().submit(ipfsDaemonThread);
            }
        }

        cbCtx.success("Cordova Ipfs Plugin: start success");
    }

    private void stopDaemon(final CallbackContext cbCtx) {
        if (this.ipfsDaemonThreadFuture != null) {
            if (this.ipfsDaemonThreadFuture.isDone()) {
                Log.d(LOG_TAG, "DAEMON IS STOPPED");
            } else {
                Log.d(LOG_TAG, "DAEMON IS STILL DAEMONING");
            }
        }

        if (this.ipfsDaemonProcess != null && this.isRunning(this.ipfsDaemonProcess)) {
            this.ipfsDaemonProcess.destroy();

            try {
                int destroyResult = this.ipfsDaemonProcess.waitFor();
                cbCtx.success("Cordova Ipfs Plugin: stop success; exitVal = " + destroyResult);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
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
            this.startDaemon(callbackContext);
            return true;
        } else if (action.equals("stop")) {
            this.stopDaemon(callbackContext);
            return true;
        }
        return false;
    }

}
