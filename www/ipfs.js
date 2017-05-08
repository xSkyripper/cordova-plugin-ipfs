var exec = require('cordova/exec');

var PLUGIN_NAME = 'Ipfs';

var Ipfs = function () {
    this.init = function(args, cb, cbErr) {
        exec(cb, cbErr, PLUGIN_NAME, "init", [args]);
    };

    this.start = function(cb, cbErr) {
        exec(cb, cbErr, PLUGIN_NAME, "start", []);
    };

    this.stop = function(cb, cbErr) {
        exec(cb, cbErr, PLUGIN_NAME, "stop", []);
    };
};

module.exports = Ipfs;
