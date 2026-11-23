(function() {
  'use strict';

  // Legacy wrapper bootstrap.
  // This loader replaces browser-executed XSLT with a client-side reconstruction
  // of the legacy DOM so the existing v1 CSS, JsRender templates, and nodel.js
  // runtime can keep operating against the same markup contract.

  var ALLOWED_SYMBOLS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
  var wrapperTarget = window.NODEL_LEGACY_TARGET || null;
  var idCounter = 0;
  var templateCache = null;
  var XML_MIME = 'application/xml';
  var debugEnabled = getQueryParam('legacyDebug') === '1';
  var unknownTags = {};
  var revealGated = window.NODEL_LEGACY_REVEAL_GATED === true;

  function debugLog() {
    if (!debugEnabled || !window.console || !console.log) {
      return;
    }
    var args = Array.prototype.slice.call(arguments);
    args.unshift('[legacy-loader]');
    console.log.apply(console, args);
  }

  function getQueryParam(name) {
    var url = new URL(window.location.href);
    return url.searchParams.get(name);
  }

  function sanitizeNav(value) {
    value = value || '';
    return value.split('').filter(function(ch) {
      return ALLOWED_SYMBOLS.indexOf(ch) >= 0;
    }).join('');
  }

  function escapeHtml(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function escapeAttribute(value) {
    return escapeHtml(value).replace(/'/g, '&#39;');
  }

  function directText(node) {
    var result = '';
    var childNodes = node.childNodes || [];
    for (var i = 0; i < childNodes.length; i++) {
      var child = childNodes[i];
      if (child.nodeType === 3 || child.nodeType === 4) {
        result += child.nodeValue;
      }
    }
    return result.trim();
  }

  function childElements(node) {
    var result = [];
    var childNodes = node.childNodes || [];
    for (var i = 0; i < childNodes.length; i++) {
      if (childNodes[i].nodeType === 1) {
        result.push(childNodes[i]);
      }
    }
    return result;
  }

  function childElementsByTag(node, tagName) {
    return childElements(node).filter(function(child) {
      return child.tagName === tagName;
    });
  }

  function firstChildByTag(node, tagName) {
    var children = childElementsByTag(node, tagName);
    return children.length ? children[0] : null;
  }

  function hasAttribute(node, name) {
    return node.hasAttribute(name);
  }

  function attr(node, name, fallback) {
    return node.hasAttribute(name) ? node.getAttribute(name) : fallback;
  }

  function appendAttribute(parts, name, value) {
    if (value == null) {
      return;
    }
    parts.push(' ' + name + '="' + escapeAttribute(value) + '"');
  }

  function generateLegacyId(node) {
    var existing = node.getAttribute('data-legacy-id');
    if (existing) {
      return existing;
    }
    idCounter += 1;
    existing = 'legacy_' + idCounter;
    node.setAttribute('data-legacy-id', existing);
    return existing;
  }

  function addShowEventAttributes(parts, node) {
    if (hasAttribute(node, 'showevent')) {
      appendAttribute(parts, 'data-showevent', attr(node, 'showevent', ''));
      if (hasAttribute(node, 'showvalue')) {
        appendAttribute(parts, 'data-showarg', attr(node, 'showvalue', ''));
      }
      if (hasAttribute(node, 'showeventarg')) {
        appendAttribute(parts, 'data-showeventarg', attr(node, 'showeventarg', ''));
      }
    }
  }

  function addEventOrJoin(parts, node, eventAttr, actionAttr) {
    eventAttr = eventAttr || 'data-event';
    actionAttr = actionAttr || 'data-action';
    if (hasAttribute(node, 'join')) {
      appendAttribute(parts, eventAttr, attr(node, 'join', ''));
      appendAttribute(parts, actionAttr, attr(node, 'join', ''));
    } else {
      if (hasAttribute(node, 'event')) {
        appendAttribute(parts, eventAttr, attr(node, 'event', ''));
      }
      if (hasAttribute(node, 'action')) {
        appendAttribute(parts, actionAttr, attr(node, 'action', ''));
      }
    }
  }

  function addEventOrArgAction(parts, node) {
    if (hasAttribute(node, 'join')) {
      appendAttribute(parts, 'data-event', attr(node, 'join', ''));
      appendAttribute(parts, 'data-arg-action', attr(node, 'join', ''));
    } else {
      if (hasAttribute(node, 'event')) {
        appendAttribute(parts, 'data-event', attr(node, 'event', ''));
      }
      if (hasAttribute(node, 'action')) {
        appendAttribute(parts, 'data-arg-action', attr(node, 'action', ''));
      }
    }
  }

  function addConfirmAttributes(parts, node) {
    if (hasAttribute(node, 'confirm') || hasAttribute(node, 'confirmtext')) {
      appendAttribute(parts, 'data-confirm', hasAttribute(node, 'confirm') ? attr(node, 'confirm', '') : 'true');
    }
    if (hasAttribute(node, 'confirmtitle')) {
      appendAttribute(parts, 'data-confirmtitle', attr(node, 'confirmtitle', ''));
    }
    if (hasAttribute(node, 'confirmtext')) {
      appendAttribute(parts, 'data-confirmtext', attr(node, 'confirmtext', ''));
    }
  }

  function renderChildren(node, filter) {
    var output = '';
    var children = node.childNodes || [];
    for (var i = 0; i < children.length; i++) {
      var child = children[i];
      if (child.nodeType === 1) {
        if (!filter || filter(child)) {
          output += renderNode(child);
        }
      } else if (!filter && (child.nodeType === 3 || child.nodeType === 4)) {
        output += escapeHtml(child.nodeValue);
      }
    }
    return output;
  }

  function renderSelectedChildren(node, names) {
    return renderChildren(node, function(child) {
      return names.indexOf(child.tagName) >= 0;
    });
  }

  function renderTitleBody(node) {
    var attrs = [];
    if (hasAttribute(node, 'showevent')) {
      appendAttribute(attrs, 'class', 'sect');
      appendAttribute(attrs, 'data-showevent', attr(node, 'showevent', ''));
      if (hasAttribute(node, 'showvalue')) {
        appendAttribute(attrs, 'data-showarg', attr(node, 'showvalue', ''));
      }
      if (hasAttribute(node, 'showeventarg')) {
        appendAttribute(attrs, 'data-showeventarg', attr(node, 'showeventarg', ''));
      }
    }
    if (hasAttribute(node, 'event')) {
      appendAttribute(attrs, 'data-event', attr(node, 'event', ''));
    }
    return attrs.join('') + '>' + escapeHtml(node.textContent);
  }

  function renderRow(node) {
    var classes = ['row'];
    if (hasAttribute(node, 'class')) {
      classes.push(attr(node, 'class', ''));
    }
    if (hasAttribute(node, 'showevent')) {
      classes.push('sect');
    }
    var parts = ['<div class="' + escapeAttribute(classes.join(' ').trim()) + '"'];
    addShowEventAttributes(parts, node);
    parts.push('>');
    parts.push(renderSelectedChildren(node, ['column']));
    parts.push('</div>');
    return parts.join('');
  }

  function renderColumn(node) {
    var hasResponsive = hasAttribute(node, 'lg') || hasAttribute(node, 'md') || hasAttribute(node, 'sm') || hasAttribute(node, 'xs');
    var classValue = '';
    if (hasResponsive) {
      if (hasAttribute(node, 'xs')) classValue += 'col-xs-' + attr(node, 'xs', '') + ' ';
      if (hasAttribute(node, 'sm')) classValue += 'col-sm-' + attr(node, 'sm', '') + ' ';
      if (hasAttribute(node, 'md')) classValue += 'col-md-' + attr(node, 'md', '') + ' ';
      if (hasAttribute(node, 'lg')) classValue += 'col-lg-' + attr(node, 'lg', '');
    } else {
      classValue = 'col-sm-12';
    }
    if (hasAttribute(node, 'event') || hasAttribute(node, 'showevent')) {
      classValue += ' sect';
    }
    if (hasAttribute(node, 'push')) classValue += ' col-sm-push-' + attr(node, 'push', '');
    if (hasAttribute(node, 'pull')) classValue += ' col-sm-pull-' + attr(node, 'pull', '');
    var parts = ['<div class="' + escapeAttribute(classValue) + '"'];
    if (hasAttribute(node, 'event') || hasAttribute(node, 'showevent')) {
      appendAttribute(parts, 'data-showevent', hasAttribute(node, 'event') ? attr(node, 'event', '') : attr(node, 'showevent', ''));
      if (hasAttribute(node, 'value') || hasAttribute(node, 'showvalue')) {
        appendAttribute(parts, 'data-showarg', hasAttribute(node, 'value') ? attr(node, 'value', '') : attr(node, 'showvalue', ''));
      }
      if (hasAttribute(node, 'showeventarg')) {
        appendAttribute(parts, 'data-showeventarg', attr(node, 'showeventarg', ''));
      }
    }
    parts.push('>');
    parts.push(renderChildren(node));
    parts.push('</div>');
    return parts.join('');
  }

  function renderTitle(node) {
    var tag = hasAttribute(node, 'size') ? 'h' + attr(node, 'size', '') : 'h4';
    return '<' + tag + renderTitleBody(node) + '</' + tag + '>';
  }

  function renderSubtitle(node) {
    var tag = hasAttribute(node, 'size') ? 'h' + attr(node, 'size', '') : 'h5';
    return '<' + tag + renderTitleBody(node) + '</' + tag + '>';
  }

  function renderText(node) {
    var parts = ['<p'];
    var iconHtml = renderSelectedChildren(node, ['icon']);
    var hasRenderedIcon = iconHtml.length > 0;
    if (hasAttribute(node, 'showevent') && !hasRenderedIcon) {
      appendAttribute(parts, 'class', 'sect');
      appendAttribute(parts, 'data-showevent', attr(node, 'showevent', ''));
      if (hasAttribute(node, 'showvalue')) {
        appendAttribute(parts, 'data-showarg', attr(node, 'showvalue', ''));
      }
      if (hasAttribute(node, 'showeventarg')) {
        appendAttribute(parts, 'data-showeventarg', attr(node, 'showeventarg', ''));
      }
    }
    if (hasAttribute(node, 'event') && !hasRenderedIcon) {
      appendAttribute(parts, 'data-event', attr(node, 'event', ''));
    }
    parts.push('>');
    parts.push(iconHtml);
    parts.push(escapeHtml(node.textContent));
    parts.push('</p>');
    return parts.join('');
  }

  function renderButton(node) {
    if (hasAttribute(node, 'type') && attr(node, 'type', '') !== 'momentary') {
      return '';
    }
    var parts = ['<a href="#"'];
    if (!hasAttribute(node, 'type')) {
      appendAttribute(parts, 'type', 'button');
      addConfirmAttributes(parts, node);
      var classes = hasAttribute(node, 'class') ? 'btn ' + attr(node, 'class', '') : 'btn btn-default';
      if (hasAttribute(node, 'showevent')) classes += ' sect';
      if (childElementsByTag(node, 'badge').length || childElementsByTag(node, 'partialbadge').length || childElementsByTag(node, 'signal').length) classes += ' haschild';
      appendAttribute(parts, 'class', classes);
      addEventOrJoin(parts, node);
      if (hasAttribute(node, 'event') || hasAttribute(node, 'action') || hasAttribute(node, 'join')) {
        appendAttribute(parts, 'data-class-on', hasAttribute(node, 'class-on') ? attr(node, 'class-on', '') : 'btn-primary');
      }
      addShowEventAttributes(parts, node);
      if (hasAttribute(node, 'arg')) appendAttribute(parts, 'data-arg', attr(node, 'arg', ''));
      if (hasAttribute(node, 'arg-on')) appendAttribute(parts, 'data-arg-on', attr(node, 'arg-on', ''));
      if (hasAttribute(node, 'arg-off')) appendAttribute(parts, 'data-arg-off', attr(node, 'arg-off', ''));
    } else {
      appendAttribute(parts, 'data-actionon', attr(node, 'action-on', ''));
      appendAttribute(parts, 'data-actionoff', attr(node, 'action-off', ''));
      appendAttribute(parts, 'type', 'button');
      addConfirmAttributes(parts, node);
      appendAttribute(parts, 'class', hasAttribute(node, 'class') ? 'btn ' + attr(node, 'class', '') : 'btn btn-default');
    }
    parts.push('>');
    parts.push('<p>' + escapeHtml(directText(node)) + '</p>');
    parts.push(renderSelectedChildren(node, ['badge', 'partialbadge', 'signal', 'icon', 'text', 'image']));
    parts.push('</a>');
    return parts.join('');
  }

  function renderButtonGroup(node) {
    var classes = '';
    if (!hasAttribute(node, 'type')) {
      classes = hasAttribute(node, 'class') ? 'btn-group ' + attr(node, 'class', '') : 'btn-group';
    } else if (attr(node, 'type', '') === 'vertical') {
      classes = hasAttribute(node, 'class') ? 'btn-group-vertical btn-block ' + attr(node, 'class', '') : 'btn-group-vertical btn-block';
    }
    if (hasAttribute(node, 'showevent')) classes += ' sect';
    var parts = ['<div role="group" class="' + escapeAttribute(classes.trim()) + '"'];
    addShowEventAttributes(parts, node);
    parts.push('>');
    parts.push(renderSelectedChildren(node, ['button', 'switch', 'partialswitch']));
    parts.push('</div>');
    return parts.join('');
  }

  function renderIcon(node) {
    if (hasAttribute(node, 'lib') && attr(node, 'lib', '') === 'fa') {
      var style = hasAttribute(node, 'style') ? attr(node, 'style', '') : 'fas';
      var size = hasAttribute(node, 'size') ? ' fa-' + attr(node, 'size', '') + 'x' : '';
      return '<span class="' + escapeAttribute(style + ' fa-' + attr(node, 'type', '') + size) + '"></span>';
    }
    if (hasAttribute(node, 'lib')) {
      return '';
    }
    return '<span class="glyphicon glyphicon-' + escapeAttribute(attr(node, 'type', '')) + '"></span>';
  }

  function renderImage(node) {
    var parts = ['<img src="' + escapeAttribute(attr(node, 'source', '')) + '"'];
    if (hasAttribute(node, 'showevent')) {
      appendAttribute(parts, 'data-showevent', attr(node, 'showevent', ''));
      if (hasAttribute(node, 'showvalue')) appendAttribute(parts, 'data-showarg', attr(node, 'showvalue', ''));
      if (hasAttribute(node, 'showeventarg')) appendAttribute(parts, 'data-showeventarg', attr(node, 'showeventarg', ''));
    }
    var cls = (hasAttribute(node, 'showevent') ? 'sect ' : '') + 'img-responsive';
    appendAttribute(parts, 'class', cls.trim());
    if (hasAttribute(node, 'event')) appendAttribute(parts, 'data-event', attr(node, 'event', ''));
    if (hasAttribute(node, 'height') || hasAttribute(node, 'width')) {
      var style = '';
      if (hasAttribute(node, 'height')) style += 'max-height:' + attr(node, 'height', '') + 'px;';
      if (hasAttribute(node, 'width')) style += 'max-width:' + attr(node, 'width', '') + 'px;';
      appendAttribute(parts, 'style', style);
    }
    parts.push('></img>');
    return parts.join('');
  }

  function renderGrid(node) {
    var parts = ['<table class="btn-grid">'];
    var rows = childElementsByTag(node, 'row');
    for (var i = 0; i < rows.length; i++) {
      var row = rows[i];
      var rowClass = '';
      if (hasAttribute(row, 'class')) rowClass += ' ' + attr(row, 'class', '');
      if (hasAttribute(row, 'showevent')) rowClass += ' sect';
      parts.push('<tr class="' + escapeAttribute(rowClass) + '"');
      if (hasAttribute(row, 'showevent')) {
        appendAttribute(parts, 'data-showevent', attr(row, 'showevent', ''));
        if (hasAttribute(row, 'showvalue')) appendAttribute(parts, 'data-showarg', attr(row, 'showvalue', ''));
        if (hasAttribute(row, 'showeventarg')) appendAttribute(parts, 'data-showeventarg', attr(row, 'showeventarg', ''));
      }
      parts.push('>');
      var cells = childElementsByTag(row, 'cell');
      for (var j = 0; j < cells.length; j++) {
        parts.push('<td>' + renderChildren(cells[j]) + '</td>');
      }
      parts.push('</tr>');
    }
    parts.push('</table>');
    return parts.join('');
  }

  function renderSwitch(node, partial) {
    var parts = ['<div role="group"'];
    addEventOrArgAction(parts, node);
    addConfirmAttributes(parts, node);
    var classes = hasAttribute(node, 'class') ? (partial ? 'btn-group btn-pswitch ' : 'btn-group btn-switch ') + attr(node, 'class', '') : (partial ? 'btn-group btn-pswitch' : 'btn-group btn-switch');
    if (hasAttribute(node, 'showevent')) classes += ' sect';
    appendAttribute(parts, 'class', classes);
    addShowEventAttributes(parts, node);
    appendAttribute(parts, 'data-class-off', hasAttribute(node, 'class-off') ? attr(node, 'class-off', '') : 'btn-danger');
    appendAttribute(parts, 'data-class-on', hasAttribute(node, 'class-on') ? attr(node, 'class-on', '') : 'btn-success');
    parts.push('>');
    if (partial) {
      parts.push('<a href="#" class="btn btn-default" data-arg="Off">' + escapeHtml(hasAttribute(node, 'off') ? attr(node, 'off', '') : 'Off') + '</a>');
      parts.push('<a href="#" class="btn btn-default" data-arg="On">' + escapeHtml(hasAttribute(node, 'on') ? attr(node, 'on', '') : 'On') + '</a>');
    } else {
      parts.push('<a href="#" class="btn btn-default" data-arg="false">' + escapeHtml(hasAttribute(node, 'off') ? attr(node, 'off', '') : 'Off') + '</a>');
      parts.push('<a href="#" class="btn btn-default" data-arg="true">' + escapeHtml(hasAttribute(node, 'on') ? attr(node, 'on', '') : 'On') + '</a>');
    }
    parts.push('</div>');
    return parts.join('');
  }

  function renderPills(node) {
    var parts = ['<ul'];
    addEventOrArgAction(parts, node);
    addConfirmAttributes(parts, node);
    appendAttribute(parts, 'class', 'nav nav-pills nav-stacked' + (hasAttribute(node, 'showevent') ? ' sect' : ''));
    addShowEventAttributes(parts, node);
    parts.push('>');
    var pills = childElementsByTag(node, 'pill');
    for (var i = 0; i < pills.length; i++) {
      var pill = pills[i];
      var hasChild = childElementsByTag(pill, 'badge').length || childElementsByTag(pill, 'partialbadge').length || childElementsByTag(pill, 'signal').length;
      parts.push('<li');
      if (hasAttribute(pill, 'showevent')) {
        appendAttribute(parts, 'class', 'sect');
      } else if (hasChild) {
        appendAttribute(parts, 'class', 'haschild');
      }
      if (hasAttribute(pill, 'showevent')) {
        appendAttribute(parts, 'data-showevent', attr(pill, 'showevent', ''));
        if (hasAttribute(pill, 'showvalue')) appendAttribute(parts, 'data-showarg', attr(pill, 'showvalue', ''));
        if (hasAttribute(pill, 'showeventarg')) appendAttribute(parts, 'data-showeventarg', attr(pill, 'showeventarg', ''));
      }
      parts.push('><a href="#" data-arg="' + escapeAttribute(attr(pill, 'value', '')) + '">' + escapeHtml(directText(pill)) + renderSelectedChildren(pill, ['badge', 'partialbadge', 'signal']) + '</a></li>');
    }
    parts.push('</ul>');
    return parts.join('');
  }

  function renderSelect(node) {
    var parts = ['<div class="btn-group btn-select' + (hasAttribute(node, 'showevent') ? ' sect' : '') + (hasAttribute(node, 'dropup') ? ' dropup' : '') + '"'];
    addShowEventAttributes(parts, node);
    parts.push('>');
    parts.push('<button type="button" class="btn ' + escapeAttribute(attr(node, 'class', '')) + ' dropdown-toggle" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false"><span>&#160;</span>&#160;<span class="caret"></span></button>');
    parts.push('<ul class="dropdown-menu"');
    addEventOrArgAction(parts, node);
    addConfirmAttributes(parts, node);
    parts.push('>');
    var items = childElementsByTag(node, 'item');
    for (var i = 0; i < items.length; i++) {
      var item = items[i];
      parts.push('<li');
      if (hasAttribute(item, 'showevent')) {
        appendAttribute(parts, 'class', 'sect');
        appendAttribute(parts, 'data-showevent', attr(item, 'showevent', ''));
        if (hasAttribute(item, 'showvalue')) appendAttribute(parts, 'data-showarg', attr(item, 'showvalue', ''));
        if (hasAttribute(item, 'showeventarg')) appendAttribute(parts, 'data-showeventarg', attr(item, 'showeventarg', ''));
      }
      parts.push('><a href="#" data-arg="' + escapeAttribute(attr(item, 'value', '')) + '">' + escapeHtml(directText(item)) + '</a></li>');
    }
    parts.push('</ul></div>');
    return parts.join('');
  }

  function renderDynamicSelect(node) {
    var parts = ['<div class="btn-group btn-select">'];
    parts.push('<button type="button" class="btn ' + escapeAttribute(attr(node, 'class', '')) + ' dropdown-toggle" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false"><span>&#160;</span>&#160;<span class="caret"></span></button>');
    parts.push('<ul class="dropdown-menu dynamic" data-render="' + escapeAttribute(attr(node, 'data', '')) + '" data-render-template="#dynamicSelect"');
    addEventOrArgAction(parts, node);
    addConfirmAttributes(parts, node);
    parts.push('></ul></div>');
    return parts.join('');
  }

  function renderDynamicButtonGroup(node) {
    var parts = ['<div role="group" data-render="' + escapeAttribute(attr(node, 'data', '')) + '" data-render-template="#dynamicButtonGroup"'];
    addEventOrArgAction(parts, node);
    addConfirmAttributes(parts, node);
    appendAttribute(parts, 'class', 'btn-group-vertical dynamic btn-block button-group' + (hasAttribute(node, 'showevent') ? ' sect' : ''));
    if (hasAttribute(node, 'showevent')) appendAttribute(parts, 'data-showevent', attr(node, 'showevent', ''));
    if (hasAttribute(node, 'showeventarg')) appendAttribute(parts, 'data-showeventarg', attr(node, 'showeventarg', ''));
    parts.push('></div>');
    return parts.join('');
  }

  function renderStatusSleep(node) {
    var parts = ['<div class="panel-footer clearfix sect"'];
    addShowEventAttributes(parts, node);
    parts.push('><a href="#" data-action="' + escapeAttribute(attr(node, 'action', '')) + '" class="btn btn-danger" type="button">Sleep</a></div>');
    return parts.join('');
  }

  function renderStatus(node) {
    var parts = ['<div data-status="' + escapeAttribute(attr(node, 'event', '')) + '" class="panel panel-default statusgroup clearfix' + (hasAttribute(node, 'showevent') ? ' sect' : '') + '"'];
    addShowEventAttributes(parts, node);
    if (hasAttribute(node, 'page')) appendAttribute(parts, 'data-nav', sanitizeNav(attr(node, 'page', '')));
    parts.push('><div class="panel-body">');
    parts.push(renderSelectedChildren(node, ['image']));
    parts.push(renderSelectedChildren(node, ['icon']));
    parts.push(renderSelectedChildren(node, ['link']));
    parts.push(renderSelectedChildren(node, ['button', 'switch', 'partialswitch']));
    parts.push(renderSelectedChildren(node, ['badge', 'partialbadge', 'signal']));
    parts.push('<strong>' + escapeHtml(directText(node)) + '</strong>');
    if (hasAttribute(node, 'event')) {
      parts.push('<br/><span class="status">Unknown</span>');
    }
    parts.push('</div>');
    if (hasAttribute(node, 'event')) {
      parts.push(renderSelectedChildren(node, ['statussleep']));
    }
    parts.push('</div>');
    return parts.join('');
  }

  function renderBadge(node) {
    var cls = hasAttribute(node, 'type') ? 'label label-default status ' + attr(node, 'type', '') : 'label label-default status';
    return '<span data-status="' + escapeAttribute(attr(node, 'event', '')) + '" class="' + escapeAttribute(cls) + '">' + escapeHtml(directText(node)) + '</span>';
  }

  function renderPartialBadge(node) {
    var parts = ['<span class="label label-default label-pbadge"'];
    appendAttribute(parts, 'data-event', attr(node, 'event', ''));
    appendAttribute(parts, 'data-class-off', 'label-danger');
    appendAttribute(parts, 'data-class-on', 'label-success');
    appendAttribute(parts, 'data-off', hasAttribute(node, 'off') ? attr(node, 'off', '') : 'Off');
    appendAttribute(parts, 'data-on', hasAttribute(node, 'on') ? attr(node, 'on', '') : 'On');
    parts.push('>' + escapeHtml(directText(node)) + '</span>');
    return parts.join('');
  }

  function renderLink(node) {
    var classes = 'btn btn-outline' + (hasAttribute(node, 'showevent') ? ' sect' : '');
    var parts = ['<a href="#" class="' + escapeAttribute(classes) + '"'];
    if (hasAttribute(node, 'node') && !hasAttribute(node, 'url')) {
      appendAttribute(parts, 'data-link-node', attr(node, 'node', ''));
    } else if (hasAttribute(node, 'url') && !hasAttribute(node, 'node')) {
      appendAttribute(parts, 'data-link-url', attr(node, 'url', ''));
    } else {
      appendAttribute(parts, 'data-link-event', attr(node.parentNode, 'event', ''));
    }
    addShowEventAttributes(parts, node);
    parts.push('><span class="glyphicon glyphicon-new-window"></span><span>' + escapeHtml(directText(node)) + '</span></a>');
    return parts.join('');
  }

  function renderPanel(node) {
    var parts = ['<div'];
    if (hasAttribute(node, 'showevent')) {
      appendAttribute(parts, 'class', 'sect');
      appendAttribute(parts, 'data-showevent', attr(node, 'showevent', ''));
      if (hasAttribute(node, 'value') || hasAttribute(node, 'showvalue')) {
        appendAttribute(parts, 'data-showarg', hasAttribute(node, 'value') ? attr(node, 'value', '') : attr(node, 'showvalue', ''));
      }
      if (hasAttribute(node, 'showeventarg')) appendAttribute(parts, 'data-showeventarg', attr(node, 'showeventarg', ''));
    }
    parts.push('><div class="panel panel-default"><div class="panel-body"><div data-event="' + escapeAttribute(attr(node, 'event', '')) + '" class="panel' + escapeAttribute(attr(node, 'height', '')) + 'px scrollbar-inner"></div></div></div>');
    parts.push('<style>.panel' + escapeHtml(attr(node, 'height', '')) + 'px {height: ' + escapeHtml(attr(node, 'height', '')) + 'px; overflow: hidden;}</style>');
    parts.push('</div>');
    return parts.join('');
  }

  function renderRange(node) {
    var height = hasAttribute(node, 'height') ? attr(node, 'height', '') : '200';
    var outerClass = 'range' + (hasAttribute(node, 'showevent') ? ' sect' : '');
    var parts = ['<div class="' + escapeAttribute(outerClass) + '"'];
    addShowEventAttributes(parts, node);
    parts.push(' data-type="' + escapeAttribute(attr(node, 'type', '')) + '"');
    parts.push('>');
    if (attr(node, 'type', '') === 'vertical') {
      parts[0] = '<div class="' + escapeAttribute('range rangeh' + height + 'px') + '"';
      parts.push('<style>.rangeh' + escapeHtml(height) + 'px {height: ' + escapeHtml(height) + 'px;}</style>');
    }
    parts.push('<div');
    if (attr(node, 'type', '') === 'vertical') {
      appendAttribute(parts, 'class', 'rangew' + height + 'px');
    }
    parts.push('>');
    if (attr(node, 'type', '') === 'vertical') {
      parts.push('<style>.rangew' + escapeHtml(height) + 'px {width: ' + escapeHtml(height) + 'px;}</style>');
    }
    parts.push('<form>');
    if (hasAttribute(node, 'nudge') && (hasAttribute(node, 'action') || hasAttribute(node, 'join'))) {
      parts.push('<a role="button" class="btn btn-default nudge nudge-down"><span class="fa fa-minus"></span></a>');
    }
    parts.push('<input data-arg-source="this" data-arg-type="number" type="range" min="' + escapeAttribute(attr(node, 'min', '')) + '" max="' + escapeAttribute(attr(node, 'max', '')) + '"');
    addEventOrJoin(parts, node);
    appendAttribute(parts, 'step', hasAttribute(node, 'step') ? attr(node, 'step', '') : '1');
    if (hasAttribute(node, 'nudge')) appendAttribute(parts, 'data-nudge', attr(node, 'nudge', ''));
    parts.push('></input>');
    if (hasAttribute(node, 'nudge') && (hasAttribute(node, 'action') || hasAttribute(node, 'join'))) {
      parts.push('<a role="button" class="btn btn-default nudge nudge-up"><span class="fa fa-plus"></span></a>');
    }
    parts.push('<output class="toint' + (hasAttribute(node, 'nudge') ? ' nudge' : '') + '"');
    if (hasAttribute(node, 'join')) {
      appendAttribute(parts, 'data-event', attr(node, 'join', ''));
      appendAttribute(parts, 'data-arg-action', attr(node, 'join', ''));
    } else if (hasAttribute(node, 'event')) {
      appendAttribute(parts, 'data-event', attr(node, 'event', ''));
    }
    parts.push('></output>');
    if (attr(node, 'type', '') === 'mute') {
      parts.push('<a href="#" class="btn btn-default" data-arg-on="true" data-arg-off="false"');
      if (hasAttribute(node, 'join')) {
        appendAttribute(parts, 'data-event', attr(node, 'join', '') + 'Muting');
        appendAttribute(parts, 'data-action', attr(node, 'join', '') + 'Muting');
      } else {
        if (hasAttribute(node, 'event')) appendAttribute(parts, 'data-event', attr(node, 'event', '') + 'Muting');
        if (hasAttribute(node, 'action')) appendAttribute(parts, 'data-action', attr(node, 'action', '') + 'Muting');
      }
      appendAttribute(parts, 'data-class-on', hasAttribute(node, 'class-on') ? 'btn ' + attr(node, 'class-on', '') : 'btn-danger');
      parts.push('>Mute');
      parts.push(renderSelectedChildren(node, ['badge', 'icon']));
      parts.push('</a>');
    }
    parts.push('</form></div></div>');
    return parts.join('');
  }

  function renderField(node) {
    return '<div><form><input class="form-control" data-arg-source="this" data-event="' + escapeAttribute(attr(node, 'event', '')) + '" readonly="true"/></form></div>';
  }

  function renderLighting(node) {
    var parts = ['<div><form><input type="button" class="form-control spectrum-color-picker" data-arg-source="this"'];
    if (hasAttribute(node, 'join')) {
      appendAttribute(parts, 'data-event', attr(node, 'join', ''));
      appendAttribute(parts, 'data-action', attr(node, 'join', ''));
    } else {
      if (hasAttribute(node, 'event')) {
        appendAttribute(parts, 'data-event', attr(node, 'event', ''));
        if (!hasAttribute(node, 'action')) {
          parts.push(' disabled');
        }
      }
      if (hasAttribute(node, 'action')) appendAttribute(parts, 'data-action', attr(node, 'action', ''));
    }
    if (hasAttribute(node, 'options')) appendAttribute(parts, 'data-options', attr(node, 'options', ''));
    parts.push('></input></form></div>');
    return parts.join('');
  }

  function renderQrCode(node) {
    var parts = ['<div class="qrcode-card"><div class="qrcode-wrapper"><div class="qrcode"'];
    if (hasAttribute(node, 'event')) {
      appendAttribute(parts, 'data-event', attr(node, 'event', ''));
      appendAttribute(parts, 'id', 'qrcode-' + attr(node, 'event', ''));
    }
    if (hasAttribute(node, 'text')) appendAttribute(parts, 'data-text', attr(node, 'text', ''));
    if (hasAttribute(node, 'height')) appendAttribute(parts, 'data-height', attr(node, 'height', ''));
    if (hasAttribute(node, 'help')) appendAttribute(parts, 'data-help', attr(node, 'help', ''));
    parts.push('></div></div>');
    if (hasAttribute(node, 'help')) {
      parts.push('<div class="qrcode-help"><p>' + escapeHtml(attr(node, 'help', '')) + '</p></div>');
    }
    parts.push('</div>');
    return parts.join('');
  }

  function renderMeter(node) {
    var parts = ['<div'];
    if (hasAttribute(node, 'showevent')) {
      appendAttribute(parts, 'class', 'meter sect');
      appendAttribute(parts, 'data-showevent', attr(node, 'showevent', ''));
      if (hasAttribute(node, 'showvalue')) appendAttribute(parts, 'data-showarg', attr(node, 'showvalue', ''));
    } else {
      appendAttribute(parts, 'class', 'meter');
    }
    if (hasAttribute(node, 'event')) appendAttribute(parts, 'data-event', attr(node, 'event', ''));
    parts.push(' data-type="' + escapeAttribute(attr(node, 'type', '')) + '"');
    appendAttribute(parts, 'data-range', hasAttribute(node, 'range') ? attr(node, 'range', '') : 'perc');
    parts.push('><div><div data-toggle="tooltip" class="base label-default"></div><div class="bar"><div class="label-danger"></div><div class="label-warning"></div><div class="label-success"></div></div></div><p>0</p></div>');
    return parts.join('');
  }

  function renderSignal(node) {
    return '<span data-event="' + escapeAttribute(attr(node, 'event', '')) + '" class="label signal meter-colour-0" data-range="' + escapeAttribute(hasAttribute(node, 'range') ? attr(node, 'range', '') : 'perc') + '">' + escapeHtml(directText(node)) + '</span>';
  }

  function renderGap(node) {
    var value = hasAttribute(node, 'value') ? attr(node, 'value', '') + 'px' : '20px';
    return '<div style="min-height:' + escapeAttribute(value) + ';"></div>';
  }

  function renderGroup(node) {
    var parts = ['<div class="well' + (hasAttribute(node, 'showevent') ? ' sect' : '') + '"'];
    addShowEventAttributes(parts, node);
    parts.push('>' + renderChildren(node) + '</div>');
    return parts.join('');
  }

  function renderNodel(node) {
    var type = attr(node, 'type', '');
    if (['description', 'actsig', 'log', 'serverlog', 'charts', 'console', 'params', 'remote', 'list', 'locals', 'diagnostics'].indexOf(type) >= 0) {
      return '<div data-nodel="' + escapeAttribute(type) + '" class="nodel-' + escapeAttribute(type) + '"></div>';
    }
    if (type === 'add') {
      var gid = generateLegacyId(node);
      return '<div data-nodel="add" class="nodel-add"><div class="base"><div class="addgrp"><div class="dropdown"><button class="btn btn-default dropdown-toggle" type="button" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false" id="addgrp_' + gid + '">Add node here</button><ul class="dropdown-menu add-node-unified" aria-labelledby="addgrp_' + gid + '"><li><form><fieldset><label for="nodenamval_' + gid + '">Node name</label><input class="form-control nodenamval" type="text" id="nodenamval_' + gid + '"></input><label for="templateval_' + gid + '">Template <small class="text-muted">(optional)</small></label><div style="position:relative"><input class="form-control unified-template-search" type="text" placeholder="Search recipes or nodes..." autocomplete="off" id="templateval_' + gid + '"></input></div></fieldset><div class="btn-toolbar"><button type="submit" class="btn btn-success nodeaddsubmit">Add</button></div></form></li></ul></div></div></div></div>';
    }
    if (type === 'editor') {
      var editorId = generateLegacyId(node);
      return '<div data-nodel="editor" class="nodel-editor"><div class="base"><div class="panel panel-default"><div class="panel-heading accordion-toggle collapsed" data-toggle="collapse" aria-expanded="false" data-target="#editgrp_' + editorId + '"><div class="panel-title"><h5 class="panel-title">Editor</h5></div></div><div class="panel-collapse collapse" aria-expanded="false" id="editgrp_' + editorId + '"><div class="panel-body"><div class="row"><div class="col-sm-12"><div class="flex"><div class="flexgrow"><select class="picker form-control"></select></div><div><button class="btn btn-danger script_delete" disabled="disabled">Delete</button>&#8196;<div class="addgrp"><div class="dropdown"><button class="btn btn-default" type="button" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false" id="addgrp_' + editorId + '">Add</button><ul class="dropdown-menu" aria-labelledby="addgrp_' + editorId + '"><li><form><fieldset><label for="scriptnameval_' + editorId + '">File name</label><input class="form-control scriptnamval" type="text" id="scriptnameval_' + editorId + '"></input></fieldset><button type="submit" class="btn btn-success scriptsubmit">Add</button></form></li></ul></div></div><button class="btn btn-default script_default">Edit script.py</button>&#8196;<button class="btn btn-success script_save" disabled="disabled">Save</button></div></div></div></div><div class="row"><div class="col-sm-12"><div class="editor"><textarea></textarea></div></div></div></div></div></div></div></div>';
    }
    if (type === 'toolkit') {
      return '<div data-nodel="toolkit" class="nodel-toolkit"><div class="base"><div class="row"><div class="col-sm-12"><div class="toolkit"><textarea></textarea></div></div></div></div></div>';
    }
    return '';
  }

  function renderUnknown(node) {
    if (node.tagName && node.tagName.indexOf('special_') === 0) {
      return renderChildren(node);
    }
    if (node.tagName === 'cell' || node.tagName === 'item' || node.tagName === 'pill' || node.tagName === 'header' || node.tagName === 'footer' || node.tagName === 'pagegroup') {
      return renderChildren(node);
    }
    if (node.tagName && debugEnabled && !unknownTags[node.tagName]) {
      unknownTags[node.tagName] = true;
      debugLog('Unsupported XML tag ignored:', node.tagName, 'parent:', node.parentNode && node.parentNode.tagName ? node.parentNode.tagName : '(none)');
    }
    return '';
  }

  function renderNode(node) {
    switch (node.tagName) {
      case 'row': return renderRow(node);
      case 'column': return renderColumn(node);
      case 'title': return renderTitle(node);
      case 'subtitle': return renderSubtitle(node);
      case 'text': return renderText(node);
      case 'button': return renderButton(node);
      case 'buttongroup': return renderButtonGroup(node);
      case 'icon': return renderIcon(node);
      case 'image': return renderImage(node);
      case 'grid': return renderGrid(node);
      case 'switch': return renderSwitch(node, false);
      case 'partialswitch': return renderSwitch(node, true);
      case 'pills': return renderPills(node);
      case 'select': return renderSelect(node);
      case 'dynamicselect': return renderDynamicSelect(node);
      case 'dynamicbuttongroup': return renderDynamicButtonGroup(node);
      case 'status': return renderStatus(node);
      case 'statussleep': return renderStatusSleep(node);
      case 'badge': return renderBadge(node);
      case 'partialbadge': return renderPartialBadge(node);
      case 'link': return renderLink(node);
      case 'panel': return renderPanel(node);
      case 'range': return renderRange(node);
      case 'field': return renderField(node);
      case 'lighting': return renderLighting(node);
      case 'qrcode': return renderQrCode(node);
      case 'meter': return renderMeter(node);
      case 'signal': return renderSignal(node);
      case 'gap': return renderGap(node);
      case 'group': return renderGroup(node);
      case 'nodel': return renderNodel(node);
      default: return renderUnknown(node);
    }
  }

  function renderHeaderControl(node) {
    if (node.tagName === 'input' && attr(node, 'type', '') === 'checkbox') {
      return '<div class="checkbox"><label><input type="checkbox" data-action="' + escapeAttribute(attr(node, 'action', '')) + '" data-event="' + escapeAttribute(attr(node, 'event', '')) + '" value="true"/>' + escapeHtml(directText(node)) + '</label></div>';
    }
    return renderNode(node);
  }

  function renderPageNav(page) {
    return '<li><a role="button" data-nav="' + escapeAttribute(sanitizeNav(attr(page, 'title', ''))) + '"' + (hasAttribute(page, 'action') ? ' data-action="' + escapeAttribute(attr(page, 'action', '')) + '"' : '') + '>' + escapeHtml(attr(page, 'title', '')) + '</a></li>';
  }

  function renderPageGroup(pageGroup) {
    var pages = childElementsByTag(pageGroup, 'page');
    var items = pages.map(function(page) {
      return '<li><a role="button" data-nav="' + escapeAttribute(sanitizeNav(attr(page, 'title', ''))) + '" data-toggle="dropdown" data-target="#nodel-navbar .dropdown.open"' + (hasAttribute(page, 'action') ? ' data-action="' + escapeAttribute(attr(page, 'action', '')) + '"' : '') + '>' + escapeHtml(attr(page, 'title', '')) + '</a></li>';
    }).join('');
    return '<li class="dropdown"><a class="dropdown-toggle" data-toggle="dropdown" role="button" aria-haspopup="true" aria-expanded="false">' + escapeHtml(attr(pageGroup, 'title', '')) + '<span class="caret"></span></a><ul class="dropdown-menu">' + items + '</ul></li>';
  }

  function renderPages(root) {
    var pages = root.getElementsByTagName('page');
    var html = '';
    for (var i = 0; i < pages.length; i++) {
      var page = pages[i];
      html += '<div class="container-fluid page" data-section="' + escapeAttribute(sanitizeNav(attr(page, 'title', ''))) + '">';
      html += renderSelectedChildren(page, ['row']);
      html += renderChildren(page, function(child) {
        return child.tagName.indexOf('special_') === 0;
      });
      html += '</div>';
    }
    return html;
  }

  function renderFooter(root) {
    var footers = root.getElementsByTagName('footer');
    if (!footers.length) {
      return '';
    }
    var theme = hasAttribute(root, 'theme') ? attr(root, 'theme', '') : null;
    var footerClass = theme ? 'navbar navbar-fixed-bottom navbar-' + theme : 'navbar navbar-fixed-bottom navbar-inverse';
    var html = '<footer class="' + escapeAttribute(footerClass) + '"><div class="container-fluid">';
    for (var i = 0; i < footers.length; i++) {
      html += renderSelectedChildren(footers[i], ['row']);
    }
    html += '</div></footer>';
    return html;
  }

  function renderNavbar(root) {
    var header = firstChildByTag(root, 'header');
    var theme = hasAttribute(root, 'theme') ? attr(root, 'theme', '') : null;
    var navClass = theme ? 'navbar navbar-fixed-top navbar-' + theme : 'navbar navbar-fixed-top navbar-inverse';
    var navHtml = '<nav class="' + escapeAttribute(navClass) + '" data-toggle="collapse" data-target=".nav-collapse">';
    navHtml += '<div class="container-fluid">';
    navHtml += '<div class="navbar-header">';
    navHtml += '<button type="button" class="navbar-toggle collapsed" data-toggle="collapse" data-target="#nodel-navbar" aria-expanded="false"><span class="sr-only">Toggle navigation</span><span class="icon-bar"></span><span class="icon-bar"></span><span class="icon-bar"></span></button>';
    navHtml += '<div class="navbar-brand"><a';
    if (header && hasAttribute(header, 'destination')) {
      navHtml += ' href="' + escapeAttribute(attr(header, 'destination', '')) + '"';
    }
    navHtml += '>';
    if (hasAttribute(root, 'logo')) {
      navHtml += '<img src="' + escapeAttribute(attr(root, 'logo', '')) + '"/>';
    } else {
      navHtml += '<img src="v1/img/logo.png"/>';
    }
    navHtml += '</a>';
    if (header) {
      var headerNodel = childElementsByTag(header, 'nodel');
      for (var i = 0; i < headerNodel.length; i++) {
        if (attr(headerNodel[i], 'type', '') === 'hosticon') {
          navHtml += '<span class="nodel-icon"><a><img src="data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7"/></a></span>';
          break;
        }
      }
    }
    navHtml += '<span id="title">' + escapeHtml(attr(root, 'title', '')) + '</span></div></div>';
    navHtml += '<div class="collapse navbar-collapse" id="nodel-navbar" role="navigation"><ul class="nav navbar-nav">';
    var topChildren = childElements(root);
    for (var j = 0; j < topChildren.length; j++) {
      if (topChildren[j].tagName === 'pagegroup') {
        navHtml += renderPageGroup(topChildren[j]);
      } else if (topChildren[j].tagName === 'page') {
        navHtml += renderPageNav(topChildren[j]);
      }
    }
    navHtml += '</ul><div class="navbar-right">';
    if (header) {
      var headerControls = childElements(header).filter(function(child) {
        return ['input', 'button', 'switch'].indexOf(child.tagName) >= 0;
      });
      if (headerControls.length) {
        navHtml += '<div class="navbar-form">';
        for (var k = 0; k < headerControls.length; k++) {
          navHtml += renderHeaderControl(headerControls[k]);
        }
        navHtml += '</div>';
      }
      var headerWidgets = childElementsByTag(header, 'nodel');
      if (headerWidgets.length) {
        for (var m = 0; m < headerWidgets.length; m++) {
          var type = attr(headerWidgets[m], 'type', '');
          if (type === 'edit') {
            navHtml += '<ul class="nav navbar-nav edtgrp"><li class="dropdown"><a class="dropdown-toggle" data-toggle="dropdown" role="button" aria-haspopup="true" aria-expanded="false">Functions <span class="caret"></span></a><ul class="dropdown-menu"><li class="form"><div><input class="form-control renamenode" type="text"/><button class="btn btn-default renamenodesubmit">Rename</button></div></li><li class="form"><div class="checkbox"><label><input type="checkbox" class="advancedmode"/>Override signals</label></div></li><li class="form"><div><div class="btn-group btn-group-justified"><a class="btn btn-danger deletenodesubmit" role="button">Delete node</a><a class="btn btn-warning restartnodesubmit" role="button">Restart node</a></div></div></li></ul></li></ul>';
          }
          if (type === 'nav') {
            navHtml += '<ul class="nav navbar-nav srchgrp"><li class="dropdown"><a href="#" class="dropdown-toggle" data-toggle="dropdown" role="button" aria-haspopup="true" aria-expanded="false">Nav <span class="caret"></span></a><ul class="dropdown-menu"><li class="form"><div><input class="form-control node goto" type="text" placeholder="search nodes"/></div></li><li class="form"><div><div class="btn-group btn-group-justified uipicker"><div class="btn-group" role="group"><button type="button" class="btn btn-default dropdown-toggle" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false" disabled="disabled">Select UI <span class="caret"></span></button><ul class="dropdown-menu"></ul></div></div></div></li><li role="separator" class="divider"></li><li><a href="/toolkit.xml">Toolkit</a></li><li><a href="/diagnostics.xml">Diagnostics</a></li></ul></li></ul>';
          }
        }
      }
    }
    navHtml += '<p class="navbar-text" id="clock"></p></div></div></div></nav>';
    return navHtml;
  }

  function renderSharedChrome() {
    return '<div class="modal" id="offline" tabindex="-1" role="dialog" aria-labelledby="offlinelabel" data-backdrop="static" data-keyboard="false" aria-hidden="true"><div class="modal-dialog"><div class="modal-content"><div class="modal-header"><h4 class="modal-title" id="offlinelabel">Offline</h4></div><div class="modal-body"><p>The system is currently offline. Please wait...</p></div></div></div></div>' +
      '<div class="modal" id="confirm" tabindex="-1" role="dialog" aria-labelledby="confirmlabel" aria-hidden="true"><div class="modal-dialog"><div class="modal-content"><div class="modal-header"><button type="button" class="close" data-dismiss="modal">&#215;</button><h4 class="modal-title" id="confirmlabel"></h4></div><div class="modal-body"><p id="confirmtext"></p><div id="confirmkeypad"><div class="row"><div class="col-xs-4"><a href="#" class="btn btn-block btn-default" data-keypad="1">1</a></div><div class="col-xs-4"><a href="#" class="btn btn-block btn-default" data-keypad="2">2</a></div><div class="col-xs-4"><a href="#" class="btn btn-block btn-default" data-keypad="3">3</a></div></div><div class="row"><div class="col-xs-4"><a href="#" class="btn btn-block btn-default" data-keypad="4">4</a></div><div class="col-xs-4"><a href="#" class="btn btn-block btn-default" data-keypad="5">5</a></div><div class="col-xs-4"><a href="#" class="btn btn-block btn-default" data-keypad="6">6</a></div></div><div class="row"><div class="col-xs-4"><a href="#" class="btn btn-block btn-default" data-keypad="7">7</a></div><div class="col-xs-4"><a href="#" class="btn btn-block btn-default" data-keypad="8">8</a></div><div class="col-xs-4"><a href="#" class="btn btn-block btn-default" data-keypad="9">9</a></div></div><div class="row"><div class="col-xs-4 col-xs-offset-4"><a href="#" class="btn btn-block btn-default" data-keypad="0">0</a></div><div class="col-xs-4"><a href="#" class="btn btn-block btn-default" data-keypad="-1">&#x232b;</a></div></div><div class="row"><div class="col-xs-12"><input id="confirmcodesrc" type="hidden" data-event="ConfirmCode"/><input id="confirmcode" class="form-control" type="password" readonly="true"/></div></div></div></div><div class="modal-footer"><button type="button" class="btn btn-default" data-dismiss="modal">Cancel</button><button id="confirmaction" class="btn btn-danger btn-ok">Ok</button></div></div></div></div>' +
      '<div class="alert collapse alert-floating alert-info"><div class="message"></div></div>';
  }

  function getBodyClass(root) {
    var classes = [];
    if (root.getElementsByTagName('footer').length) {
      classes.push('hasfooter');
    }
    if (hasAttribute(root, 'core')) {
      classes.push('core');
    }
    return classes.join(' ');
  }

  function parseXml(text, source) {
    var parser = new DOMParser();
    var doc = parser.parseFromString(text, XML_MIME);
    var errors = doc.getElementsByTagName('parsererror');
    if (errors.length) {
      throw new Error('Unable to parse XML source: ' + source);
    }
    return doc;
  }

  function fetchText(url) {
    return fetch(url, { credentials: 'same-origin' }).then(function(response) {
      if (!response.ok) {
        throw new Error('Failed to load ' + url + ' (' + response.status + ')');
      }
      return response.text();
    });
  }

  function getTargetPath() {
    if (wrapperTarget) {
      debugLog('Using wrapper XML target:', wrapperTarget);
      return wrapperTarget;
    }
    return window.location.href;
  }

  function xmlFetchUrl(targetPath) {
    var url = new URL(targetPath, window.location.href);
    url.hash = '';
    url.searchParams.set('_raw', '1');
    return url.href;
  }

  function getJsRenderTemplates() {
    if (templateCache) {
      return Promise.resolve(templateCache);
    }
    // Read v1/index.xsl as plain XML data only so the embedded JsRender
    // templates remain the single source of truth. No browser XSLT execution
    // happens here.
    return fetchText('v1/index.xsl').then(function(text) {
      var xslDoc = parseXml(text, 'v1/index.xsl');
      var scripts = xslDoc.getElementsByTagName('script');
      var collected = [];
      for (var i = 0; i < scripts.length; i++) {
        if (scripts[i].getAttribute('type') === 'text/x-jsrender') {
          collected.push('<script id="' + escapeAttribute(scripts[i].getAttribute('id')) + '" type="text/x-jsrender">' + scripts[i].textContent + '</' + 'script>');
        }
      }
      templateCache = collected.join('');
      return templateCache;
    });
  }

  function buildDocument(xmlDoc, templatesHtml, targetPath) {
    var root = xmlDoc.documentElement;
    var themeHref = hasAttribute(root, 'theme') ? 'v1/css/components.' + attr(root, 'theme', '') + '.css' : 'v1/css/components.css';
    var optionalCss = !hasAttribute(root, 'core') && hasAttribute(root, 'css') ? '<link rel="stylesheet" href="' + escapeAttribute(attr(root, 'css', '')) + '">' : '';
    var optionalJs = !hasAttribute(root, 'core') && hasAttribute(root, 'js') ? '<script src="' + escapeAttribute(attr(root, 'js', '')) + '"></' + 'script>' : '';
    var bodyClass = getBodyClass(root);
    var bodyClassAttr = bodyClass ? ' class="' + escapeAttribute(bodyClass) + '"' : '';
    var bodyStyleAttr = revealGated ? ' style="visibility:hidden"' : '';
    var revealScript = revealGated ? '<script>(function(){var shown=false;function show(){if(shown)return;shown=true;document.body.style.visibility="visible";}if(document.readyState==="complete"){show();return;}window.addEventListener("load",show);setTimeout(show,2000);})();</' + 'script>' : '';

    var html = '<!DOCTYPE html SYSTEM "about:legacy-compat"><html lang="en"><head>' +
      '<meta charset="utf-8"/>' +
      '<meta http-equiv="X-UA-Compatible" content="IE=edge"/>' +
      '<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no"/>' +
      '<meta name="apple-mobile-web-app-capable" content="yes"/>' +
      '<meta name="apple-mobile-web-app-status-bar-style" content="black"/>' +
      '<meta name="theme-color" content="#000000"/>' +
      '<title></title>' +
      '<link rel="stylesheet" href="' + escapeAttribute(themeHref) + '">' +
      optionalCss +
      '<link href="v1/img/favicon.ico" rel="shortcut icon"/>' +
      '<link href="v1/img/apple-touch-icon.png" rel="apple-touch-icon"/>' +
      '</head><body' + bodyClassAttr + bodyStyleAttr + '>' +
      renderNavbar(root) +
      renderSharedChrome() +
      renderPages(root) +
      renderFooter(root) +
      '<script src="v1/js/components.min.js"></' + 'script>' +
      '<script src="v1/js/nodel.js"></' + 'script>' +
      optionalJs +
      templatesHtml +
      revealScript +
      '</body></html>';
    return html;
  }

  function renderLegacyPage(targetPath) {
    var startedAt = Date.now();
    return Promise.all([fetchText(xmlFetchUrl(targetPath)), getJsRenderTemplates()]).then(function(results) {
      var xmlDoc = parseXml(results[0], targetPath);
      var html = buildDocument(xmlDoc, results[1], targetPath);
      // Replace the bootstrap wrapper document wholesale so the reconstructed
      // legacy page starts from a clean document and the existing runtime sees
      // the same startup model as the original XSLT-rendered page.
      document.open();
      document.write(html);
      document.close();
      debugLog('Rendered target:', targetPath, 'in', (Date.now() - startedAt) + 'ms', 'unknown tags:', Object.keys(unknownTags));
    });
  }

  function showError(error) {
    document.open();
    document.write('<!DOCTYPE html><html><head><meta charset="utf-8"><title>Legacy UI error</title></head><body><pre>' + escapeHtml(error && error.stack ? error.stack : String(error)) + '</pre></body></html>');
    document.close();
  }

  Promise.resolve().then(getTargetPath)
    .then(function(targetPath) {
      return renderLegacyPage(targetPath);
    })
    .catch(function(error) {
      debugLog('Render failed:', error && error.message ? error.message : String(error));
      showError(error);
    });
})();

