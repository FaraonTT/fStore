'use strict';
const crypto = require('crypto');

const SECRET = process.env.SERVER_SECRET || '';

/** HKDF-SHA256 with empty salt (matches the Android client implementation). */
function hk(info, ikm, len = 32) {
  return Buffer.from(crypto.hkdfSync('sha256', ikm || Buffer.from(SECRET), Buffer.alloc(0), info, len));
}

function make() {
  if (SECRET.length < 16) throw new Error('SERVER_SECRET must be at least 16 characters');
  const ecdh = crypto.createECDH('prime256v1');
  ecdh.setPrivateKey(hk('fstore-ecdh-seed'));
  return { ecdh, dataKey: hk('fstore-data-key') };
}

module.exports = { hk, make };
