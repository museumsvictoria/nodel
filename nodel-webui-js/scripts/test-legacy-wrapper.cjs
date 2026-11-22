'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const { JSDOM, VirtualConsole } = require('jsdom');

const sourceDir = path.join(__dirname, '..', 'src');
const loaderSource = fs.readFileSync(path.join(sourceDir, 'legacy-loader.js'), 'utf8');

async function render(xml, options = {}) {
  const dom = new JSDOM('<!doctype html><html><head></head><body></body></html>', {
    url: options.url || 'http://nodel.test/nodes/example/index.htm',
    runScripts: 'outside-only',
    virtualConsole: new VirtualConsole()
  });
  const { window } = dom;
  const { document } = window;
  const close = document.close.bind(document);
  let finish;
  const completed = new Promise(resolve => { finish = resolve; });

  document.close = function() {
    const result = close();
    queueMicrotask(finish);
    return result;
  };
  window.NODEL_LEGACY_TARGET = options.target || null;
  window.NODEL_LEGACY_REVEAL_GATED = false;
  const requests = [];
  window.fetch = async function(url) {
    requests.push(url);
    if (options.missingIndex && new URL(url, window.location.href).pathname.endsWith('/index.xml')) {
      return { ok: false, status: 404 };
    }
    const text = url !== 'v1/index.xsl' ? xml :
      '<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"/>';
    return { ok: true, status: 200, text: async () => text };
  };
  window.eval(loaderSource);
  await completed;
  if (!options.expectError) assert.notEqual(document.title, 'Legacy UI error', document.body.textContent);
  return { dom, window, document, requests };
}

function page(content) {
  return '<pages><page title="Main"><row><column>' + content +
    '</column></row></page></pages>';
}

test('preserves explicit empty action arguments and empty show values', async t => {
  const { dom, document } = await render(page(
    '<button action="Set" arg="">Clear</button>' +
    '<button action="Toggle" arg-on="" arg-off="stop">Toggle</button>' +
    '<text showevent="State" showvalue="">No state</text>'
  ));
  t.after(() => dom.window.close());
  const clear = document.querySelector('[data-action="Set"]');
  assert.equal(clear.getAttribute('data-arg'), '');
  const toggle = document.querySelector('[data-action="Toggle"]');
  assert.equal(toggle.getAttribute('data-arg-on'), '');
  assert.equal(toggle.getAttribute('data-arg-off'), 'stop');
  const state = document.querySelector('[data-showevent="State"]');
  assert.equal(state.getAttribute('data-showarg'), '');
});

test('preserves descendant text and whitespace in title, subtitle, and text', async t => {
  const { dom, document } = await render(page(
    '<title> One <b>bold</b> word </title>' +
    '<subtitle> One <b>bold</b> word </subtitle>' +
    '<text> One <b>bold</b> word </text>'
  ));
  t.after(() => dom.window.close());
  for (const selector of ['.page h4', '.page h5', '.page p']) {
    assert.equal(document.querySelector(selector).textContent, ' One bold word ');
  }
});

test('direct XML rendering preserves address/query/fragment', async t => {
  const url = 'http://nodel.test/nodes/example/custom.XML?xml=other.xml&a=one&a=two&_raw=0&_raw=2#Main';
  const { dom, window, requests } = await render(page('<text>Custom</text>'), { url });
  t.after(() => dom.window.close());
  assert.equal(window.location.href, url);
  const fetchUrl = new URL(requests.find(request => request !== 'v1/index.xsl'));
  assert.equal(fetchUrl.pathname, '/nodes/example/custom.XML');
  assert.deepEqual(fetchUrl.searchParams.getAll('_raw'), ['1']);
  assert.deepEqual(fetchUrl.searchParams.getAll('a'), ['one', 'two']);
  assert.equal(fetchUrl.searchParams.get('xml'), 'other.xml');
  assert.equal(fetchUrl.hash, '');
  assert.ok(requests.includes('v1/index.xsl'), 'template fetch remains unchanged');
});

test('loader renders only its current XML URL and preserves XML navigation links', async t => {
  const { dom, document, requests } = await render('<pages><header><nodel type="nav"/></header></pages>', {
    url: 'http://nodel.test/nodes/example/index.xml?keep=yes#Power'
  });
  t.after(() => dom.window.close());
  const fetchUrl = new URL(requests.find(request => request !== 'v1/index.xsl'));
  assert.equal(fetchUrl.pathname, '/nodes/example/index.xml');
  assert.equal(fetchUrl.searchParams.get('keep'), 'yes');
  assert.equal(fetchUrl.hash, '');
  assert.ok(document.querySelector('a[href="/toolkit.xml"]'));
});
