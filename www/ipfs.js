var exec = require('cordova/exec');

var PLUGIN_NAME = 'Ipfs';

function Ipfs() {
    init: function(args, callback, errorCallback) {
        exec(args, callback, errorCallback, PLUGIN_NAME, "init");
    }
};

module.exports = Ipfs;
