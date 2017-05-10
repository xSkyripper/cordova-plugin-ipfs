cordova-plugin-ipfs
===================
A simple Cordova Plugin that wraps go-ipfs v0.4.8. It fetches the go-ipfs ARM binaries archive, extracts it
and offers a simple way of initing the IPFS repo, starting and stopping the IPFS daemon.

### IPFS
IPFS is a new hypermedia distribution protocol, addressed by content and identities. IPFS enables the creation of completely distributed applications. It aims to make the web faster, safer, and more open.

More here:
https://github.com/ipfs/ipfs

https://github.com/ipfs/go-ipfs

**Want to hack on IPFS?**

[![](https://cdn.rawgit.com/jbenet/contribute-ipfs-gif/master/img/contribute.gif)](https://github.com/ipfs/community/blob/master/contributing.md)



### Platforms
- Android

### Installation
1. `$ cordova plugin add http://github.com/xSkyripper/cordova-plugin-ipfs.git`

2. Add `jniLibs.srcDirs = ['libs']` in `/path/to/project/platforms/android/build.gradle` under android > sourceSets > main { ... } (that because the plugin uses a custom jar library for extracting)

### Usage
* Initing with ipfs.init(configObject, winCb, errCb)

configObject needs to have keys "src", "appFilesDir", "resetRepo"

```javascript
    var ipfs = new CordovaIpfs();
    var appFilesPath = cordova.file.dataDirectory.split("file://")[1] + "files/";

    ipfs.init({
        src: "https://dist.ipfs.io/go-ipfs/v0.4.8/go-ipfs_v0.4.8_linux-arm.tar.gz",
        appFilesDir: appFilesPath,
        resetRepo: false
    }, function(res) {
        // success callback
    }, function(err) {
        // error callback
    });
```

* Starting the daemon with ipfs.start(winCb, errCb)

```javascript
    ipfs.start(function(res){
        // success callback
    }, function(err){
        // error callback
    });
```

* Stopping the daemon with ipfs.stop(winCb, errCb)

```javascript
    ipfs.stop(function(res){
        // success callback
    }, function(err){
        // error callback
    });
```

## License

This software is released under the [Apache 2.0 License][apache2_license].
