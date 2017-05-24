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
import java.util.Arrays;
import java.util.concurrent.Future;

/**
 * This class extends the CordovaPlugin and provides a wrap for go-ipfs arm binaries
 * which can be used is Cordova projects. It consists of the basic functionalities like
 * downloading, extracting, initing the repo, starting / stopping the daemon
 *
 * @author xSkyripper
 */
public class Ipfs extends CordovaPlugin {
    private URL ipfsArchiveSrc;
    private String appFilesDir;
    private String ipfsBinPath;
    private String ipfsRepo;

    private Process ipfsDaemonProcess = null;
    private Future ipfsDaemonThreadFuture = null;

    private String LOG_TAG = "#######CIP######";


    /**
     * Function for starting a system process that will "shell execute" the given command(s)
     * in the cmdArray, using the environment variables provided in envArray. The process will be
     * waited to finish
     * If ignoreExc is true, the error output of the command will be ignored.
     *
     * @param cmdArray  the array of shell commands
     * @param envArray  the array of environment variables
     * @param ignoreExc if true, the error output is ignores; else, and IOException will be thrown
     * @throws IOException          thrown if something happens with the process or if the error output is
     *                              not ignored
     * @throws InterruptedException thrown if something happens on waiting the process to finish
     */
    private void execShell(final String[] cmdArray, final String[] envArray, Boolean ignoreExc) throws IOException, InterruptedException {
        Log.d(LOG_TAG, Arrays.toString(cmdArray));
        Process proc = Runtime.getRuntime().exec(cmdArray, envArray);
        StringBuilder procOutput = new StringBuilder();
        StringBuilder procError = new StringBuilder();

        String line;

        BufferedReader procOutputReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
        while ((line = procOutputReader.readLine()) != null) {
            procOutput.append(line).append("\n");
            Log.d(LOG_TAG, line);
        }

        BufferedReader procErrorReader = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
        while ((line = procErrorReader.readLine()) != null) {
            procError.append(line).append("\n");
            Log.d(LOG_TAG, line);
        }

        //Log.d(LOG_TAG, procOutput.toString());

        if (!ignoreExc)
            if (!procError.toString().equals("")) {
                Log.d(LOG_TAG, "Tried:" + Arrays.toString(cmdArray));
                throw new IOException(procError.toString());
            }

        proc.waitFor();
    }


    /**
     * Deletes a file or a directory recursively; if the delete returns 'false' (some file could
     * not be deleted), it simply logs a messages
     *
     * @param fileOrDirectory the file / dir to be deleted
     */
    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory())
            for (File child : fileOrDirectory.listFiles())
                deleteRecursive(child);
        if (!(fileOrDirectory.delete()))
            Log.d(LOG_TAG, "File " + fileOrDirectory.getName() + " couldn't be deleted !");
    }

    /**
     * Checks if a process is running by trying to access the exitValue; if an exception is thrown
     * then the process is still running; otherwise, it finished running and it has an exit value
     *
     * @param process the process to be checked
     * @return true if the process is running; false otherwise
     */
    private Boolean isRunning(Process process) {
        try {
            process.exitValue();
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Download the go-ipfs archive from the ipfsArchiveSrc provided at "init"
     * The method open the connection to the URL provided, checks if the connection
     * returns HTTP "200", else it throws an exception; It also checks if the archive already exists
     * and it has the same size as the archive existing at the URL provided;
     *
     * @throws Exception if something happens (connection couldn't be initiated, the source server
     *                   returned something else than "200", stream exceptions)
     */
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

        int lastDownTotal = -1;
        while ((blockSize = input.read(block)) != -1) {
            totalDownloaded += blockSize;

            if (archiveFileLength > 0) {
                if (((int) (totalDownloaded * 100 / archiveFileLength)) % 10 == 0)
                    if (((int) (totalDownloaded * 100 / archiveFileLength)) != lastDownTotal) {
                        lastDownTotal = (int) (totalDownloaded * 100 / archiveFileLength);
                        Log.d(LOG_TAG, "Downloaded " + lastDownTotal + "%");
                    }
            }

            output.write(block, 0, blockSize);
        }

        Log.d(LOG_TAG, "FINISHED DOWNLOADING");

        input.close();
        output.close();
        conn.disconnect();
    }

    /**
     * Extracts the archive downloaded by downloadIpfs using the custom jar included in the project
     * to the same directory, tries to make the binary executable and
     * finally deletes the archive for "space reasons"
     *
     * @throws Exception an exception is thrown if the IPFS binary couldn't be made executable
     */
    private void extractIpfs() throws Exception {
        Log.d(LOG_TAG, "STARTING EXTRACT");

        Archiver archiver = ArchiverFactory.createArchiver(ArchiveFormat.TAR, CompressionType.GZIP);
        archiver.extract(new File(appFilesDir + "go-ipfs.tar.gz"), new File(appFilesDir));

        if (!(new File(ipfsBinPath).setExecutable(true, true))) {
            throw new Exception("IPFS Bin " + ipfsBinPath + " cannot be set executable !");
        }

        this.deleteRecursive(new File(appFilesDir + "go-ipfs.tar.gz"));
        Log.d(LOG_TAG, "FINISHED EXTRACT");
    }


    /**
     * Calls the 2 methods that "prepare" go-ipfs ARM: downloadIpfs and extractIpfs
     *
     * @throws Exception exceptions are re-thrown from these 2 functions
     */
    private void prepareIpfs() throws Exception {
        this.downloadIpfs();
        this.extractIpfs();
    }


    /**
     * Deletes the current IPFS Repo folder and tries to recreate it using "ipfs init" exec shell
     * taking into account possible errors on initing
     *
     * @throws Exception thrown by exec shell ('ipfs init') if something happens during initialization
     */
    private void initRepo() throws Exception {
        Log.d(LOG_TAG, "INITING IF REPO RESET OR REPO NOT EXISTS");

        this.deleteRecursive(new File(ipfsRepo));
        this.execShell(
                new String[]{ipfsBinPath, "init"},
                new String[]{"IPFS_PATH=" + this.ipfsRepo}, false);

        Log.d(LOG_TAG, "INITING FINISHED");
    }


    /**
     * 'init' plugin function exposed to JS interface, ran asynchronously
     * Parses the arguments provided, saves them. builds the path of the repo and the binary
     * and tries to prepare the IPF if the binary doesn't exist
     * and to init the repo if the IPFS repo dir doesn't exists or if resetRepo option is 'true'
     *
     * @param args  JSONArray arguments provided from the call; expected to find
     *              'appFilesDir' the path to the app's files/files dir,
     *              'src' the URL of the go-ipfs ARM tar.gz archive
     *              'resetRepo' boolean used for reseting the repo
     * @param cbCtx callback context used to call succes or error callbacks
     */
    private void init(final JSONArray args, final CallbackContext cbCtx) {

        final Runnable initAsync = new Runnable() {
            @Override
            public void run() {
                Boolean resetRepo;
                try {
                    JSONObject config = args.getJSONObject(0);

                    appFilesDir = config.getString("appFilesDir");
                    ipfsArchiveSrc = new URL(config.getString("src"));
                    ipfsBinPath = appFilesDir.concat("go-ipfs/ipfs");
                    ipfsRepo = appFilesDir.concat(".ipfs");
                    resetRepo = config.getBoolean("resetRepo");

                    Log.d(LOG_TAG, "ipfsBinPath: " + ipfsBinPath);
                    Log.d(LOG_TAG, "ipfsArchiveSrc: " + ipfsArchiveSrc);
                    Log.d(LOG_TAG, "appFilesDir: " + appFilesDir);
                    Log.d(LOG_TAG, "ipfsRepo: " + ipfsRepo);
                    Log.d(LOG_TAG, "resetRepo: " + resetRepo);
                } catch (JSONException e) {
                    e.printStackTrace();
                    cbCtx.error("Cordova IPFS Plugin (init): \n" + e.toString());
                    return;
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                    cbCtx.error("Cordova IPFS Plugin (init): \n" + e.toString());
                    return;
                }

                if (!(new File(ipfsBinPath).exists()))
                    try {
                        prepareIpfs();
                    } catch (Exception e) {
                        e.printStackTrace();
                        cbCtx.error("Cordova IPFS Plugin (init): \n" + e.toString());
                        return;
                    }

                if (resetRepo || !(new File(ipfsRepo).exists()))
                    try {
                        initRepo();
                    } catch (IOException e) {
                        e.printStackTrace();
                        cbCtx.error("Cordova IPFS Plugin (init): \n" + e.toString());
                        return;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        cbCtx.error("Cordova IPFS Plugin (init): \n" + e.toString());
                        return;
                    } catch (Exception e) {
                        e.printStackTrace();
                        cbCtx.error("Cordova IPFS Plugin (init): \n" + e.toString());
                        return;
                    }

                cbCtx.success("Cordova IPFS Plugin (init): IPFS was prepared & inited !");
            }
        };

        cordova.getThreadPool().execute(initAsync);
    }

    /**
     * 'start' plugin function exposed to JS interface, ran asynchronously
     * Checks if the IPFS binary and the IPFS repo dir exist, throwing an error of they don't
     * Tries to set through exec shell the IPFS config access control
     * Starts the IPFS daemon with 'pubsub' in a new process
     * contained by a thread if it's not already running and sets the local Future of the thread
     *
     * @param cbCtx callback context used to call success or error callbacks
     */
    private void startDaemon(final CallbackContext cbCtx) {
        //> ipfs config --json API.HTTPHeaders.Access-Control-Allow-Credentials "[\"true\"]"
        //> ipfs config --json API.HTTPHeaders.Access-Control-Allow-Origin "[\"*\"]"

        final Runnable ipfsDaemonThread = new Runnable() {
            @Override
            public void run() {
                if (!(new File(ipfsBinPath).exists())) {
                    cbCtx.error("Cordova IPFS Plugin (start): \n"
                            + "The IPFS was not prepared (binary not found)"
                            + " Run init first or wait for init to finish !");
                    return;
                }

                if (!(new File(ipfsRepo).exists())) {
                    cbCtx.error("Cordova IPFS Plugin (start): \n"
                            + "The IPFS repo was not initialized (" + ipfsRepo + " not found)"
                            + " Run init first or wait for init to finish !");
                    return;
                }

                try {
                    StringBuilder ipfsDaemonProcOutput = new StringBuilder();
                    StringBuilder ipfsDaemonProcError = new StringBuilder();

                    String line;

                    ipfsDaemonProcess = Runtime.getRuntime().exec(
                            new String[]{ipfsBinPath, "daemon", "--enable-pubsub-experiment"},
                            new String[]{"IPFS_PATH=" + ipfsRepo}
                    );


                    BufferedReader ipfsDaemonProcOutputReader = new BufferedReader(new InputStreamReader(ipfsDaemonProcess.getInputStream()));
                    while ((line = ipfsDaemonProcOutputReader.readLine()) != null) {
                        ipfsDaemonProcOutput.append(line).append("\n");
                        if (line.contains("Daemon is ready")) {

                            try {
                                execShell(
                                        new String[]{
                                                ipfsBinPath, "config", "--json",
                                                "API.HTTPHeaders.Access-Control-Allow-Credentials",
                                                "\"[\\\"*\\\"]\""},
                                        new String[]{"IPFS_PATH=" + ipfsRepo}, true);

                                execShell(
                                        new String[]{
                                                ipfsBinPath, "config", "--json",
                                                "API.HTTPHeaders.Access-Control-Allow-Origin",
                                                "\"[\\\"true\\\"]\""},
                                        new String[]{"IPFS_PATH=" + ipfsRepo}, true);

                            } catch (IOException e) {
                                e.printStackTrace();
                                cbCtx.error(e.toString());
                                return;
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                                cbCtx.error(e.toString());
                                return;
                            }

                            cbCtx.success("Cordova IPFS Plugin (start): \n Started");
                        }
                        Log.d(LOG_TAG, line);
                    }

                    BufferedReader ipfsDaemonProcErrorReader = new BufferedReader(new InputStreamReader(ipfsDaemonProcess.getErrorStream()));
                    while ((line = ipfsDaemonProcErrorReader.readLine()) != null) {
                        ipfsDaemonProcError.append(line).append("\n");
                        Log.d(LOG_TAG, line);
                    }

                    Log.d(LOG_TAG, "IPFS daemon exit val: " + ipfsDaemonProcess.waitFor());
                    Log.d(LOG_TAG, "IPFS daemon out     : " + ipfsDaemonProcOutput.toString());
                    Log.d(LOG_TAG, "IPFS daemon err     : " + ipfsDaemonProcError.toString());
                } catch (IOException e) {
                    e.printStackTrace();
                    cbCtx.error("Cordova IPFS Plugin (start): \n" + e.toString());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
        };

        if (ipfsDaemonThreadFuture == null)
            ipfsDaemonThreadFuture = cordova.getThreadPool().submit(ipfsDaemonThread);
        else if (ipfsDaemonThreadFuture.isCancelled() || ipfsDaemonThreadFuture.isDone()) {
            ipfsDaemonThreadFuture = cordova.getThreadPool().submit(ipfsDaemonThread);
        } else {
            cbCtx.success("Cordova IPFS Plugin (start): Daemon is already running");
        }
    }

    /**
     * 'stop' plugin function exposed to JS interface
     * Stops the IPFS daemon if it's running and waits for the process to exit
     *
     * @param cbCtx callback context used to call succes or error callbacks
     */
    private void stopDaemon(final CallbackContext cbCtx) {
        if (this.ipfsDaemonThreadFuture != null)
            if (this.ipfsDaemonThreadFuture.isDone()) {
                Log.d(LOG_TAG, "IPFS daemon thread is STOPPED");
            } else {
                Log.d(LOG_TAG, "IPFS daemon thread is still DAEMONING");
            }

        if (this.ipfsDaemonProcess != null)
            if (this.isRunning(this.ipfsDaemonProcess)) {
                this.ipfsDaemonProcess.destroy();

                try {
                    cbCtx.success("Cordova IPFS Plugin (stop): Success, exit code: " + this.ipfsDaemonProcess.waitFor());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    cbCtx.error("Cordova IPFS Plugin (stop): \n" + e.toString());
                }
                return;
            }

        cbCtx.success("Cordova IPFS Plugin (stop): Success");
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
