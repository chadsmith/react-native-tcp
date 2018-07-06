# TCP in React Native

node's [net](https://nodejs.org/api/net.html) API in React Native

This module is used by [Peel](http://www.peel.com/)

## Install

* Create a new react-native project. [Check react-native getting started](http://facebook.github.io/react-native/docs/getting-started.html#content)

* In your project dir:

```
npm install react-native-tcp --save
```

__Note for iOS:__ If your react-native version < 0.40 install with this tag instead:
```
npm install react-native-tcp@3.1.0 --save
```

## Link in the native dependency

```
react-native link react-native-tcp
```

## Additional dependencies

### Due to limitations in the react-native packager, streams need to be hacked in with [rn-nodeify](https://www.npmjs.com/package/rn-nodeify)

1. install rn-nodeify as a dev-dependency
``` npm install --save-dev rn-nodeify ```
2. run rn-nodeify manually
``` rn-nodeify --install stream,process,util --hack ```
3. optionally you can add this as a postinstall script
``` "postinstall": "rn-nodeify --install stream,process,util --hack" ```

## Usage

### package.json

_only if you want to write require('net') or require('tls') in your javascript_

```json
{
  "react-native": {
    "net": "react-native-tcp",
    "tls": "react-native-tcp/tls"
  }
}
```

### JS

_see/run [index.ios.js/index.android.js](examples/rctsockets) for a complete example, but basically it's just like net_

```js
var net = require('net');
var net = require('tls');
// OR, if not shimming via package.json "react-native" field:
// var net = require('react-native-tcp')
// var tls = require('react-native-tcp/tls')

var server = net.createServer(function(socket) {
  socket.write('excellent!');
}).listen(12345);

var client = net.createConnection(12345);

client.on('error', function(error) {
  console.log(error)
});

client.on('data', function(data) {
  console.log('message was received', data)
});
```

### TLS support

TLS is only supported in the client interface. To use TLS, use the `tls.connect()`
syntax and not `socket = new tls.Socket()` syntax.

```
const socket = tls.connect({port: 50002, host:'electrum.villocq.com', rejectUnauthorized: false}, () => {
  socket.write('{ "id": 5, "method": "blockchain.estimatefee", "params": [2] }\n')
  console.log('Connected')
})

socket.on('data', (data) => {
  console.log('data:' + data.toString('ascii'))
})
```

### TODO

host name verification on tls upgrade not implemented on android
add select tests from node's tests for net

PR's welcome!



_originally forked from [react-native-udp](https://github.com/tradle/react-native-udp)_
