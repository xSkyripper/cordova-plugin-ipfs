var exec = require('cordova/exec');

var PLUGIN_NAME = 'Ipfs';

var Ipfs = function () {
    this.init = function(args, cb, cbErr) {
        exec(cb, cbErr, PLUGIN_NAME, "init", [args]);
    };

    this.start = function(args, cb, cbErr) {
        exec(cb, cbErr, PLUGIN_NAME, "start", [args]);
    };

    this.stop = function(args, cb, cbErr) {
        exec(cb, cbErr, PLUGIN_NAME, "stop", [args]);
    };
};

module.exports = Ipfs;
