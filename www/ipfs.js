exports.ipfs = function Ipfs() {
    this.exec = function(cmd, callback) {
        return cordova.exec(callback, function (err){
            callback();
        }, "Ipfs", "init", [cmd]);
    };
};

