package org.apache.cordova.ipfs;

import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Ipfs extends CordovaPlugin {

    private String appFilesDir;

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);

        Log.d('Initializing Ipfs Cordova Plugin');
    }

    @Override
    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) {
        if (action.equals("init")) {
            callbackContext.success("Ipfs Cordova Plugin 'init' function");
            return true;
        }
        return false;
    }
}
