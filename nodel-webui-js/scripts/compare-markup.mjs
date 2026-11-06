#!/usr/bin/env node
import fs from 'node:fs/promises';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { JSDOM } from 'jsdom';

const ROOT_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SRC_DIR = path.join(ROOT_DIR, 'src');
const XSL_PATH = path.join(SRC_DIR, 'index.xsl');
const DEFAULT_FIXTURES = [
  path.join(SRC_DIR, 'index.xml'),
  path.join(SRC_DIR, 'nodel.xml'),
  path.join(SRC_DIR, 'nodes.xml'),
  path.join(SRC_DIR, 'locals.xml'),
  path.join(SRC_DIR, 'toolkit.xml'),
  path.join(SRC_DIR, 'diagnostics.xml'),
  path.join(SRC_DIR, 'status.xml'),
  path.join(ROOT_DIR, 'scripts/fixtures/markup-parity.xml'),
  path.join(ROOT_DIR, 'scripts/fixtures/empty-values.xml')
];

const RAW_TEXT_TAGS = new Set(['script', 'style']);
const VOID_TAGS = new Set(['area', 'base', 'br', 'col', 'embed', 'hr', 'img', 'input', 'link', 'meta', 'param', 'source', 'track', 'wbr']);
const GENERATED_ATTRS = new Set(['id', 'for', 'aria-labelledby', 'data-target']);
const BOOLEAN_ATTRS = new Set(['disabled', 'checked', 'readonly', 'selected', 'multiple', 'required', 'autofocus', 'hidden', 'novalidate', 'open']);
const GENERATED_PREFIXES = ['addgrp_', 'editgrp_', 'nodenamval_', 'templateval_', 'scriptnameval_', 'legacy_'];

function usage() {
  console.error('Usage: compare-markup <expected.html> <actual.html>');
  console.error('   or: compare-markup --expected-dir <dir> --actual-dir <dir>');
  console.error('   or: compare-markup [xml files...]');
  process.exit(2);
}

function stripBom(text) {
  return text.replace(/^\uFEFF/, '');
}

function isWhitespaceText(text) {
  return /^\s*$/.test(text);
}

function isInsignificantElement(node) {
  if (!node || node.nodeType !== 1 || node.tagName.toLowerCase() !== 'meta') {
    return false;
  }
  var httpEquiv = node.getAttribute('http-equiv');
  return httpEquiv && httpEquiv.toLowerCase() === 'content-type';
}

function normalizeGeneratedValue(value) {
  var prefix = '';
  var isHash = false;
  if (value.startsWith('#')) {
    isHash = true;
    value = value.slice(1);
  }
  for (var i = 0; i < GENERATED_PREFIXES.length; i++) {
    var candidate = GENERATED_PREFIXES[i];
    if (value.indexOf(candidate) === 0) {
      prefix = candidate;
      return (isHash ? '#' : '') + prefix + 'GEN';
    }
  }
  return (isHash ? '#' : '') + value;
}

function parseMarkup(input) {
  return new JSDOM(stripBom(input), { contentType: 'text/html' }).window.document;
}

function canonicalize(node) {
  if (!node) {
    return null;
  }
  if (node.nodeType === 9) {
    return {
      type: 'root',
      children: Array.from(node.childNodes).map(canonicalize).filter(Boolean)
    };
  }
  if (node.nodeType === 3 || node.nodeType === 4) {
    var text = String(node.nodeValue || '').replace(/^\s+|\s+$/g, '');
    if (!text) {
      return null;
    }
    return { type: 'text', text: text };
  }
  if (node.nodeType === 1) {
    var name = node.tagName.toLowerCase();
    if (isInsignificantElement(node)) {
      return null;
    }
    var attrs = Array.from(node.attributes).map(function(attr) {
      var attrName = attr.name.toLowerCase();
      var value = attr.value;
      if (/^\s+$/.test(value)) {
        value = '';
      }
      if (BOOLEAN_ATTRS.has(attrName)) {
        value = '';
      }
      if (GENERATED_ATTRS.has(attrName)) {
        value = normalizeGeneratedValue(value);
      }
      return [attrName, value];
    }).sort(function(a, b) {
      return a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : (a[1] < b[1] ? -1 : a[1] > b[1] ? 1 : 0);
    });
    return {
      type: 'element',
      name: name,
      attrs: attrs,
      children: Array.from(node.childNodes).map(canonicalize).filter(Boolean)
    };
  }
  return null;
}

function collectIds(node, ids) {
  if (node.type === 'element') {
    for (var i = 0; i < node.attrs.length; i++) {
      if (node.attrs[i][0] === 'id') {
        ids.add(node.attrs[i][1]);
      }
    }
  }
  var children = node.children || [];
  for (var j = 0; j < children.length; j++) {
    collectIds(children[j], ids);
  }
}

function validateGeneratedReferences(node, label) {
  var ids = new Set();
  collectIds(node, ids);

  function walk(current) {
    if (current.type === 'element') {
      for (var i = 0; i < current.attrs.length; i++) {
        var name = current.attrs[i][0];
        var value = current.attrs[i][1];
        if (!GENERATED_ATTRS.has(name)) continue;
        var tokens = String(value).split(/\s+/).filter(Boolean);
        for (var t = 0; t < tokens.length; t++) {
          var token = tokens[t];
          var stripped = token[0] === '#' ? token.slice(1) : token;
          for (var p = 0; p < GENERATED_PREFIXES.length; p++) {
            if (stripped.indexOf(GENERATED_PREFIXES[p]) === 0 && !ids.has(stripped)) {
              throw new Error(label + ': unresolved generated reference ' + token + ' on <' + current.name + '>');
            }
          }
        }
      }
    }
    var children = current.children || [];
    for (var j = 0; j < children.length; j++) walk(children[j]);
  }

  walk(node);
}

function compareNodes(a, b, pathLabel) {
  if (a.type !== b.type) return pathLabel + ': node type mismatch';
  if (a.type === 'text') {
    if (a.text !== b.text) return pathLabel + ': text mismatch';
    return null;
  }
  if (a.type === 'element') {
    if (a.name !== b.name) return pathLabel + ': tag mismatch ' + a.name + ' != ' + b.name;
    if (a.attrs.length !== b.attrs.length) return pathLabel + ': attribute count mismatch on <' + a.name + '> (expected ' + a.attrs.length + ', actual ' + b.attrs.length + ') expected=' + JSON.stringify(a.attrs) + ' actual=' + JSON.stringify(b.attrs);
    for (var i = 0; i < a.attrs.length; i++) {
      if (a.attrs[i][0] !== b.attrs[i][0] || a.attrs[i][1] !== b.attrs[i][1]) {
        return pathLabel + ': attribute mismatch on <' + a.name + '> expected=' + JSON.stringify(a.attrs) + ' actual=' + JSON.stringify(b.attrs);
      }
    }
    if (a.children.length !== b.children.length) return pathLabel + ': child count mismatch on <' + a.name + '> (expected ' + a.children.length + ', actual ' + b.children.length + ')';
    for (var j = 0; j < a.children.length; j++) {
      var childPath = pathLabel + '/' + a.name + '[' + j + ']';
      var diff = compareNodes(a.children[j], b.children[j], childPath);
      if (diff) return diff;
    }
    return null;
  }
  if (a.children.length !== b.children.length) return pathLabel + ': child count mismatch (expected ' + a.children.length + ', actual ' + b.children.length + ')';
  for (var k = 0; k < a.children.length; k++) {
    var rootDiff = compareNodes(a.children[k], b.children[k], pathLabel + '/root[' + k + ']');
    if (rootDiff) return rootDiff;
  }
  return null;
}

async function readFile(filePath) {
  return stripBom(await fs.readFile(filePath, 'utf8'));
}

function compareStrings(label, expected, actual) {
  var expectedTree = canonicalize(parseMarkup(expected));
  var actualTree = canonicalize(parseMarkup(actual));
  validateGeneratedReferences(expectedTree, label + ' expected');
  validateGeneratedReferences(actualTree, label + ' actual');
  var diff = compareNodes(expectedTree, actualTree, label);
  if (diff) {
    throw new Error(diff + ' (expected root children=' + expectedTree.children.length + ', actual root children=' + actualTree.children.length + ')');
  }
}

function runJshellTransform(xsltPath, xmlPath) {
  var code = [
    'import javax.xml.transform.*;',
    'import javax.xml.transform.stream.*;',
    'import java.io.*;',
    'var tf = TransformerFactory.newInstance();',
    'var xslt = new StreamSource(new File(' + JSON.stringify(xsltPath) + '));',
    'var xml = new StreamSource(new File(' + JSON.stringify(xmlPath) + '));',
    'var sw = new StringWriter();',
    'tf.newTransformer(xslt).transform(xml, new StreamResult(sw));',
    'System.out.print(sw.toString());',
    '/exit'
  ].join('\n');

  var result = spawnSync('jshell', ['-q', '--execution', 'local'], {
    input: code,
    encoding: 'utf8',
    maxBuffer: 10 * 1024 * 1024
  });
  if (result.status !== 0) {
    throw new Error('JShell transform failed for ' + xmlPath + '\n' + (result.stderr || result.stdout || 'unknown error'));
  }
  var output = result.stdout;
  var start = output.indexOf('<!DOCTYPE');
  if (start >= 0) {
    output = output.slice(start);
  }
  var end = output.lastIndexOf('</html>');
  if (end >= 0) {
    output = output.slice(0, end + '</html>'.length);
  }
  return output;
}

async function runLoaderTransform(xmlPath) {
  var loaderSource = await readFile(path.join(SRC_DIR, 'legacy-loader.js'));
  var baseUrl = 'file://' + SRC_DIR.replace(/\\/g, '/') + '/index.htm';
  var html = '<!DOCTYPE html><html><head><meta charset="utf-8"><title>Loading legacy UI</title></head><body></body></html>';
  var dom = new JSDOM(html, { url: baseUrl, runScripts: 'outside-only', pretendToBeVisual: true });
  var writes = [];
  var doneResolve;
  var doneReject;
  var done = new Promise(function(resolve, reject) {
    doneResolve = resolve;
    doneReject = reject;
  });
  var timeout = setTimeout(function() {
    doneReject(new Error('Timed out waiting for legacy loader output for ' + xmlPath));
  }, 10000);

  function localFetch(url) {
    try {
      var resolved = new URL(url, baseUrl);
      if (resolved.protocol !== 'file:') {
        return Promise.resolve({ ok: false, status: 400, text: async function() { return ''; } });
      }
      var filePath = fileURLToPath(resolved);
      return fs.readFile(filePath, 'utf8').then(function(text) {
        return {
          ok: true,
          status: 200,
          text: async function() { return text; }
        };
      }).catch(function() {
        var fallbackPath = filePath.replace(path.join(SRC_DIR, 'v1') + path.sep, SRC_DIR + path.sep);
        if (fallbackPath !== filePath) {
          return fs.readFile(fallbackPath, 'utf8').then(function(text) {
            return {
              ok: true,
              status: 200,
              text: async function() { return text; }
            };
          }).catch(function() {
            return {
              ok: false,
              status: 404,
              text: async function() { return ''; }
            };
          });
        }
        return {
          ok: false,
          status: 404,
          text: async function() { return ''; }
        };
      });
    } catch (error) {
      return Promise.resolve({ ok: false, status: 400, text: async function() { return ''; } });
    }
  }

  dom.window.fetch = localFetch;
  dom.window.console = { log: function() {}, error: function() {}, warn: function() {}, info: function() {} };
  dom.window.NODEL_LEGACY_REVEAL_GATED = false;
  dom.window.NODEL_LEGACY_TARGET = path.relative(SRC_DIR, xmlPath).replace(/\\/g, '/');

  var originalWrite = dom.window.document.write.bind(dom.window.document);
  dom.window.document.write = function(content) {
    var text = String(content);
    writes.push(text);
    originalWrite(content);
    if (/Legacy UI error/i.test(text)) {
      clearTimeout(timeout);
      doneReject(new Error('Legacy loader emitted an error page for ' + xmlPath));
      return;
    }
    clearTimeout(timeout);
    doneResolve(text);
  };

  try {
    dom.window.eval(loaderSource);
  } catch (error) {
    clearTimeout(timeout);
    throw error;
  }

  return done;
}

async function compareFixture(xmlPath) {
  var expected = runJshellTransform(XSL_PATH, xmlPath);
  var actual = await runLoaderTransform(xmlPath);
  compareStrings(path.basename(xmlPath), expected, actual);
}

async function comparePair(expectedPath, actualPath) {
  compareStrings(path.basename(expectedPath), await readFile(expectedPath), await readFile(actualPath));
}

async function compareDirs(expectedDir, actualDir) {
  var entries = (await fs.readdir(expectedDir)).filter(function(name) {
    return /\.(html?|xml)$/i.test(name);
  }).sort();
  for (var i = 0; i < entries.length; i++) {
    var expectedPath = path.join(expectedDir, entries[i]);
    var actualPath = path.join(actualDir, entries[i]);
    await comparePair(expectedPath, actualPath);
  }
}

async function compareDefaultFixtures(fixtures) {
  for (var i = 0; i < fixtures.length; i++) {
    await compareFixture(fixtures[i]);
  }
}

async function main(argv) {
  if (argv.includes('--expected-dir') || argv.includes('--actual-dir')) {
    var expectedIndex = argv.indexOf('--expected-dir');
    var actualIndex = argv.indexOf('--actual-dir');
    if (expectedIndex < 0 || actualIndex < 0 || !argv[expectedIndex + 1] || !argv[actualIndex + 1]) usage();
    await compareDirs(argv[expectedIndex + 1], argv[actualIndex + 1]);
    return;
  }

  if (argv.length === 0) {
    await compareDefaultFixtures(DEFAULT_FIXTURES);
    return;
  }

  if (argv.length === 2) {
    await comparePair(argv[0], argv[1]);
    return;
  }

  if (argv.length > 0) {
    await compareDefaultFixtures(argv.map(function(item) {
      return path.isAbsolute(item) ? item : path.resolve(item);
    }));
    return;
  }

  usage();
}

main(process.argv.slice(2)).catch(function(error) {
  console.error(error && error.stack ? error.stack : String(error));
  process.exit(1);
});
