package org.apache.cordova.ipfs;

import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

public class Ipfs extends CordovaPlugin {
    private String appFilesDir;

    private void init(JSONArray args, final CallbackContext cbCtx) {
        cbCtx.success("Cordova Ipfs Plugin: init success");
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
