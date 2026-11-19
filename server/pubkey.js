'use strict';
// Prints the server's public ECDH key (base64, uncompressed point). Used at APK build time.
const { make } = require('./keys');
process.stdout.write(make().ecdh.getPublicKey('base64'));
