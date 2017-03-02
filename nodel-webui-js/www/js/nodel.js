// set global ajax timeout and disable cache
$.ajaxSetup({timeout: 30000, cache: false});

// set globals
var adv = false;
var rld = false;
var poll = false;
var tim = 0;
var opts = {};

// override json parse and stringify
var json_parse = JSON.parse;
var json_stringify = JSON.stringify;
var encpattern = /(^[0-9]|[^0-9a-zA-Z])/g;
var decpattern = /__([0-9]+)__/g;

var encodr = function(str){
  return str.replace(encpattern, function(match, char){
    return '__'+char.charCodeAt(0)+'__';
  });
};

var decodr = function(str){
  return str.replace(decpattern, function(match, code){
    return String.fromCharCode(code);
  });
};

JSON.stringify = function(data) {
  return json_stringify(data, function (key, value) {
    if (value && Object.prototype.toString.call(value) === '[object Object]') {
      var replacement = {};
      for (var k in value) {
        if (Object.hasOwnProperty.call(value, k)) {
          replacement[decodr(k)] = value[k];
        }
      }
      return replacement;
    }
    return value;
  });
};

JSON.parse = function(data) {
  return json_parse(data, function (key, value) {
    if (value && Object.prototype.toString.call(value) === '[object Object]') {
      for (var k in value) {
        if (/(^[0-9]|[^0-9a-zA-Z])/g.test(k) && Object.hasOwnProperty.call(value, k)) {
          value[encodr(k)] = value[k];
          delete value[k];
        }
      }
    }
    return value;
  });
};

// jsviews custom converter functions
$.views.converters({
  // convert number to string
  numToStr: function (value) {
    if(typeof value === "undefined") return "";
    else return "" + value;
  },
  // convert string to integer
  strToInt: function (value) {
    return parseInt(value);
  },
  // convert string to float
  strToFloat: function (value) {
    return parseFloat(value);
  },
  // convert db to percentage
  dbToPerc: function (value) {
    return typeof value === "undefined" ? 0 : (Math.pow(10,value/80.04)*0.75)*100;
  }
});

// jsviews custom helper functions
$.views.helpers({
  // is a value defined
  isSet: function (value) {
    return typeof value !== "undefined";
  },
  // is a value contained within an array
  isIn: function () {
    var args = arguments;
    var valid = true;
    var obj = JSON.parse(JSON.stringify(this.data));
    $.each($.map(obj, function(e,i) {return i}), function(i,v){
      if($.inArray(v, args)<0) return valid = false;
    });
    return valid;
  }
});

// console fallback (for ie)
if (typeof console === "undefined" || typeof console.log === "undefined") {
  console = {};
  console.log = function(msg) {
    alert(msg);
  };
}

// removes keys from an object where the value is null
function removeNulls(obj){
  var isArray = obj instanceof Array;
  for (var k in obj){
    if (obj[k]==="" || ((typeof obj[k]=="object") && ($.isEmptyObject(obj[k])))) {
      isArray ? obj.splice(k,1) : delete obj[k];
    } else if (typeof obj[k]=="object") removeNulls(obj[k]);
  }
}

// html special character encoder
function htmlEncode(value) {
  var specialchr = {'"': '&quot;', '&': '&amp;', "'": '&#39;', '/': '&#47;', '<': '&lt;', '>': '&gt;'};
  value = String(value);
  return value.replace(/[\"&'\/<>]/g, function (a) {
    return specialchr[a];
  });
}

// customised jquery ajax function for posting JSON
$.postJSON = function(url, data, callback) {
  return jQuery.ajax({
    'type': 'POST',
    'url': url,
    'contentType': 'application/json',
    'data': data,
    'dataType': 'json',
    'success': callback
  });
};

// initialisation
$(function() {
  // get the host and port
  host = document.location.hostname + ':' + window.document.location.port;
  // get the node name if it is specified as a query string (?node=name)
  node = getParameterByName('node');
  // if a node is found, redirect to the node page
  if(node) window.location.replace('http://'+host+'/nodes/'+node+'/');
  else {
    // if the node is not specified on the query string, look for it in the path
    if (window.location.pathname.split( '/' )[1]=="nodes") node = decodeURIComponent(window.location.pathname.split( '/' )[2].replace(/\+/g, '%20'));
    $(document).on('keyup', function(e) {
      e.preventDefault();
      if (e.keyCode === 27) $('.close').trigger('mousedown');
    });
    if(node){
      // if a node name is found, retrieve node configuration
      $.getJSON('http://'+host+'/REST/nodes/'+encodeURIComponent(node)+'/', "", function(data) {
        // set page details
        document.title = 'Nodel - '+data.name;
        $('#nodename').text(data.name);
        if(data.desc) $('#nodename').after('<p>'+htmlEncode(data.desc)+'</p>');
        $('.logo img').attr('title', 'Nodel '+data.nodelVersion);
        if((typeof preinit !== "undefined") && ($.isFunction(preinit))){ preinit(data); }
        $('body').data('config',data);
        // begin node UI initialisation
        init();
      }).error(function(){
        // if the node configuration cannot be retrieved, display an error
        document.title = 'Nodel - Error';
        $('#nodename').text("node not available");
      });
    } else {
      // if a node name is not found, show the list of nodes instead
      document.title = 'Nodel - Network';
      listNodes();
    }
  }
});

// node display initialisation function
var init = function() {
  // show the init block
  $('.init').show();
  $('body').find('h1, h2, h3, h4, h5, h6, p, legend, label, button').attr('unselectable','on').addClass('disableselect').bind('selectstart', function(){ return false; });
  // get any extra parameters from the query string
  adv = getParameterByName('advanced') == 'true';
  rld = getParameterByName('reload') == 'true';
  poll = getParameterByName('poll') == 'true';
  tim = parseInt(getParameterByName('timeout')) || 0;
  // if a timeout parameter is specified, configure the default ajax handler
  if(tim) $.ajaxSetup({timeout: tim*1000, cache: false});
  // define the colours and icons for the activity display
  opts = {"local": {"event": {"colour": "#ff6a00","icon":"&#x25b2;"},"action":{"colour":"#9bed00","icon":"&#x25ba;"}}, "remote": {"event": {"colour":"#ce0071","icon":"&#x25bc;"},"action":{"colour":"#00a08a","icon":"&#x25c4;"},"eventBinding": {"colour":"#ce0071","icon":"&#x2194;"},"actionBinding":{"colour":"#00a08a","icon":"&#x2194;"}}, "unbound": {"event": {"colour":"#ce0071","icon":"&#x25ac;"},"action":{"colour":"#00a08a","icon":"&#x25ac;"}}};
  // define the variables used for the console function
  var execs = [];
  var execindex = -1;
  // retrieve the node parameters and bindings schema
  $.each(['params','remote'], function(key, form) {
    $.getJSON('http://'+host+'/REST/nodes/'+encodeURIComponent(node)+'/'+form+'/schema',"", function(data) {
      // build the form template from the schema
      var get = buildFormSchema(data);
      if(get){
        // add a save button to the template
        var template = get;
        template += '<button class="save">Save</button>';
        // add the template to jsviews
        eval('$.templates({'+form+'Template: template})');
        // fill the template with data
        buildForm(form, form, [],'save', true);
      } else $('#'+form).replaceWith('<h5 class="pad">None</h5>');
    });
  });
  $('#params').on('ready', function(){
    $('#paraams').find('h1, h2, h3, h4, h5, h6, p, legend, label, button').attr('unselectable','on').addClass('disableselect').bind('selectstart', function(){ return false; });
  });
  $('#remote').on('ready', function(){
    $('#remote').find('h1, h2, h3, h4, h5, h6, p, legend, label, button').attr('unselectable','on').addClass('disableselect').bind('selectstart', function(){ return false; });
  });
  var actions_list = [];
  var events_list = [];
  $('#actions, #events').on('ready', function(evt){
    actions_list = jQuery.grep(actions_list, function(value){return value != evt.target.id;});
    events_list = jQuery.grep(events_list, function(value){return value != evt.target.id;});
    if(actions_list.length == 0 && events_list.length == 0) {
      updateLogs();
      $('#actions, #events').find('h1, h2, h3, h4, h5, h6, p, legend, label, button').attr('unselectable','on').addClass('disableselect').bind('selectstart', function(){ return false; });
    }
  });
  if($('#actions').length) {
    actions_list = ['init'];
    // retrieve the node actions
    $.getJSON('http://' + host + '/REST/nodes/' + encodeURIComponent(node) + '/actions', "", function (data) {
      var actionsData = [];
      $.each(data, function (key, form) {
        actionsData.push(form);
      });
      actions_list = $.map(actionsData, function (val) {
        return 'action_' + val.name;
      });
      // sort
      actionsData.sort(function (a, b) {
        var aName = a.name.toLowerCase();
        var bName = b.name.toLowerCase();
        var aOrder = a.order ? a.order : 0;
        var bOrder = b.order ? b.order : 0;
        return aOrder < bOrder ? -1 : aOrder > bOrder ? 1 : aName < bName ? -1 : aName > bName ? 1 : 0;
      });
      // build
      $.each(actionsData, function (key, form) {
        // build the form tempalte from the schema
        var template = '';
        if (typeof form.schema !== "undefined") {
          var schema = {type: "object", properties: {arg: form.schema}};
          if(typeof schema.properties.arg.title == "undefined") schema.properties.arg.title = '';
          template = buildFormSchema(schema);
        }
        // if the action does not have any fields (it only has a submit button), set it to display inline
        var float = template ? 'unfloat' : 'float';
        // set the class for the form submit handler
        var cls = ['call'];
        // create the form wrapper
        newform = '<div class="' + float + '"><form id="action_' + form.name + '"></form></div>';
        // if the action is part of a group, add the form to the group, otherwise add it ungrouped
        if (form.group) {
          // if the group exists, add the form to the group, otherwise add the new group
          if ($('#actiongroup_' + form.group.replace(/[^0-9a-zA-Z]/g, "")).length) $('#actiongroup_' + form.group.replace(/[^0-9a-zA-Z]/g, "")).append(newform);
          else $('#actions').append('<div class="unfloat block"><h6>' + htmlEncode(form.group) + '</h6><div id="actiongroup_' + form.group.replace(/[^0-9a-zA-Z]/g, "") + '">' + newform + '</div></div>');
        } else $('#actions').append(newform);
        // if a warning is required before submit, add an extra class to the submit handler and add a caution variable to the form data
        if (form.caution) {
          cls.push('caution');
          $('#action_' + form.name).data('caution', form.caution);
        }
        var name = (typeof form.title !== 'undefined') ? htmlEncode(form.title) : form.name;
        // add a submit button to the template
        template = template + '<button title="' + form.name + ': ' + htmlEncode(form.desc) + '" class="' + cls.join(' ') + '"><span>' + opts.local.action.icon + '</span>' + name + '</button>';
        // add the template to jsviews
        eval('$.templates({action_' + form.name + 'Template: template})');
        // fill the template with data
        buildForm(form.name, 'action_' + form.name, ['actions'], 'call', false);
      });
      // if there are no actions, display 'none'
      if ($.isEmptyObject(data)) {
        actions_list = [];
        $('#actions').trigger('ready').append('<h5 class="pad">None</h5>');
      }
    });
  }
  // retrieve the node events
  if($('#events').length) {
    events_list = ['init'];
    $.getJSON('http://' + host + '/REST/nodes/' + encodeURIComponent(node) + '/events', "", function (data) {
      var eventsData = [];
      $.each(data, function (key, form) {
        eventsData.push(form);
      });
      events_list = $.map(eventsData, function (val) {
        return 'event_' + val.name;
      });
      // sort
      eventsData.sort(function (a, b) {
        var aName = a.name.toLowerCase();
        var bName = b.name.toLowerCase();
        var aOrder = a.order ? a.order : 0;
        var bOrder = b.order ? b.order : 0;
        return aOrder < bOrder ? -1 : aOrder > bOrder ? 1 : aName < bName ? -1 : aName > bName ? 1 : 0;
      });
      // build
      $.each(eventsData, function (key, form) {
        // build the form tempalte from the schema
        var template = '';
        if (typeof form.schema !== "undefined") {
          var schema = {type: "object", properties: {arg: form.schema}};
          if(typeof schema.properties.arg.title == "undefined") schema.properties.arg.title = '';
          template = buildFormSchema(schema);
        }
        // if the event does not have any fields (it only has a submit button), set it to display inline
        var float = template ? 'unfloat' : 'float';
        // set the class for the form submit handler
        var cls = ['emit'];
        // create the form wrapper
        newform = '<div class="' + float + '"><form id="event_' + form.name + '"></form></div>';
        // if the event is part of a group, add the form to the group, otherwise add it ungrouped
        if (form.group) {
          // if the group exists, add the form to the group, otherwise add the new group
          if ($('#eventgroup_' + form.group.replace(/[^0-9a-zA-Z]/g, "")).length) $('#eventgroup_' + form.group.replace(/[^0-9a-zA-Z]/g, "")).append(newform);
          else $('#events').append('<div class="unfloat block"><h6>' + htmlEncode(form.group) + '</h6><div id="eventgroup_' + form.group.replace(/[^0-9a-zA-Z]/g, "") + '">' + newform + '</div></div>');
        } else $('#events').append(newform);
        // if a warning is required before submit, add an extra class to the submit handler and add a caution variable to the form data
        if (form.caution) {
          cls.push('caution');
          $('#event_' + form.name).data('caution', form.caution);
        }
        var name = (typeof form.title !== 'undefined') ? htmlEncode(form.title) : form.name;
        // add a submit button to the template
        template = template + '<button title="' + form.name + ': ' + htmlEncode(form.desc) + '" class="' + cls.join(' ') + '"><span>' + opts.local.event.icon + '</span>' + name + '</button>';
        // add the template to jsviews
        eval('$.templates({event_' + form.name + 'Template: template})');
        // fill the template with data and attach UI events
        buildForm(form.name, 'event_' + form.name, ['events'], 'emit', false);
      });
      // if there are no actions, display 'none'
      if ($.isEmptyObject(data)) {
        events_list = [];
        $('#events').trigger('ready').append('<h5 class="pad">None</h5>');
      }
    });
  }
  // define the script form schema
  var scriptSchema = JSON.parse('{"type":"object","required":false,"properties":{ "script": { "type":"string", "title":"Script", "required":false, "format":"long" }}}');
  // build the form template
  var get = buildFormSchema(scriptSchema);
  var template = get;
  // add a save button to the template
  template += '<button class="save">Save</button>';
  // add the template to jsviews
  $.templates({script_editorTemplate: template});
  // fill the template with data
  buildForm('script', 'script_editor', [],'save', true);
  // check if reload has not been disabled (via query string) then begin checking if the page should be refreshed (node has restarted)
  if(!rld) checkReload();
  // set the target for the console form (if it exists)
  if($('#consoleform').get(0)) $('#consoleform').get(0).setAttribute('action', '/REST/nodes/'+encodeURIComponent(node)+'/exec');
  // when the console form submit button is pressed, send the command
  $('#consoleform').on('submit', '', function() {
    $.get($('#consoleform').get(0).getAttribute('action'), { code: $('#exec').val()}, function(data){
      // if it is executed successfully, display a success message
      dialog("exec - Success");
    }, "json").error(function(e, s) {
      // otherwise, display the error
      errtxt = s;
      if(e.responseText) errtxt = s + "\n" + e.responseText;
      dialog("exec - Error:\n"+errtxt, "error");
    });
    // add the command to the list of recent commands, if it is not the same as the last command
    if((typeof execs[execs.length-1] == "undefined") || (execs[execs.length-1] != $('#exec').val())) {
      execs.push($('#exec').val());
      execindex = execs.length;
    }
    // clear the console
    $('#exec').val('');
    return false;
  });
  // watch for text being entered into the console field
  $('#consoleform').keydown(function (e) {
    if(execindex ==-1) execindex = execs.length;
    keyCode = e.keyCode || e.which;
    // if the key is an 'up arrow', display the previous stored command
    if(keyCode == 38) {
      if(execindex>0) execindex--;
      $('#exec').val(execs[execindex]);
      // if the key is a 'down arrow', display the next stored command, or blank if it is the end of the list
    } else if(keyCode == 40) {
      if(execindex<execs.length) {
        execindex++;
        $('#exec').val(execs[execindex]);
      } else $('#exec').val('');
    }
  });
  // watch for the 'advanced mode' checkbox to be changed
  $('#advancedmode').change(function(){
    // if it is 'enabled', show the advanced section
    if($(this).prop('checked')) {
      // begin updating the console
      updateConsoleForm();
      // show the advanced section using animation
      $('.advanced').slideDown();
      // if 'display editor' is checked, show and load the editor as well
      if($('#showeditor').prop('checked')) {
        $('.advancededitor').slideDown();
        loadEditor();
      }
      $('#events input, #events button').prop('disabled', false);
      $('#events .array').removeClass('disabled');
      window.history.replaceState('','','http://'+host+'/nodes/'+node+'/?advanced=true');
      // if it is 'disabled', hide the advanced section
    } else {
      $('#events input, #events button').prop('disabled', true);
      $('#events .array').addClass('disabled');
      window.history.replaceState('','','http://'+host+'/nodes/'+node+'/');
      $('.advanced, .advancededitor').slideUp();
    }
  });
  // if advanced mode is specified on the query string, open it by default
  if(adv) $('#advancedmode').prop('checked', true).trigger('change');
  $('#events').on('ready', function() {
    // if 'advanced mode' and 'display editor' are already enabled, load the editor
    if(!$('#advancedmode').prop('checked')) {
      $('#events input, #events button').prop('disabled',true);
      $('#events .array').addClass('disabled');
    }
  });
  // watch for the editor form to be rendered
  $('#script_editor').on('ready', function() {
    // if 'advanced mode' and 'display editor' are already enabled, load the editor
    if($('#advancedmode').prop('checked') && $('#showeditor').prop('checked')) loadEditor();
  });
  // watch for the 'display editor' checkbox to be changed
  $('#showeditor').change(function(){
    // if 'display editor' is checked, show and load the editor
    if($(this).prop('checked')) {
      $('.advancededitor').slideDown();
      loadEditor();
      // if it is 'disabled', hide the editor
    } else $('.advancededitor').slideUp();
  });
  // watch for clicks on all group or object block titles
  $('body').on('mousedown touchstart', '.block h6:not(#remote div.block div.block h6)', function() {
    // show or hide the contents
    $(this).toggleClass('contract').next('div').finish().slideToggle('slow');
    return false;
  });
  // watch for clicks on all section titles set to expand
  $('body').on('mousedown touchstart', 'h4.expand', function() {
    // show the contents of every group or object
    $(this).parent().find('.block h6').next('div').slideDown('slow');
    // set the section to contract on next click
    $(this).removeClass('expand').addClass('contract');
    return false;
  });
  // watch for clicks on all section titles set to contract
  $('body').on('mousedown touchstart', 'h4.contract', function() {
    // hide the contents of every group or object
    $(this).parent().find('.block h6').next('div').slideUp('slow');
    // set the section to expand on next click
    $(this).removeClass('contract').addClass('expand');
    return false;
  });
  $('body').on('mousedown touchstart', '#nodename', function() {
    $('.noderename').show();
    $('#nodenameval').val($('body').data('config').name).focus();
    /*$('#nodename').html('&nbsp;');*/
    return false;
  });
  $('#nodenameval').keypress(function(e){
    if(e.keyCode == 13) {
      e.preventDefault();
      $('#noderename').trigger('submit');
    }
  });
  $('#noderename').on('submit', function() {
    var name = $('body').data('config').name;
    var newname = $('#nodenameval').val();
    if(name !== newname){
      console.log('renaming');
      $('#nodenameval').prop('disabled',true);
      var nodename = {"value":newname};
      $.getJSON('http://'+host+'/REST/nodes/'+encodeURIComponent(node)+'/rename', nodename, function() {
        checkRedirect('http://' + host + '/nodes/' + encodeURIComponent(newname));
      }).error(function(req){
        if(req.statusText!="abort"){
          var error = 'Node rename failed';
          if(req.responseText) {
            var message = JSON.parse(req.responseText);
            error = error + '<br/>' + message['message'];
          }
          dialog(error,'error');
        }
        $('#nodename').text(name);
        $('.noderename').hide();
        $('#nodenameval').prop('disabled',false);
      });
    } else $('.noderename').hide();
    return false;
  });
  $('#nodedeletesubmit').on('mousedown touchstart', function(e) {
    e.preventDefault();
    if(confirm("Are you sure?")) {
      $.getJSON('http://' + host + '/REST/nodes/' + encodeURIComponent(node) + '/remove?confirm=true', function () {
        window.location.href = 'http://' + host + '/';
      }).error(function (req) {
        if (req.statusText != "abort") {
          var error = 'Node delete failed';
          if (req.responseText) {
            var message = JSON.parse(req.responseText);
            error = error + '<br/>' + message['message'];
          }
          dialog(error, 'error');
        }
        $('.noderename').hide();
        $('#nodenameval').prop('disabled', false);
      });
      return false;
    }
  });
  $('#noderename').on('mousedown touchstart', '.close', function() {
    $('.noderename').hide();
    return false;
  });
  $('.notice .close').on('mousedown touchstart', function() {
    $('.notice').slideUp();
    return false;
  });
};

// function to load the code editor
var loadEditor = function() {
  // ensure the editor has not been loaded already and the form exists
  if((typeof editor === "undefined") && ($('#field_script').length)){
    // load the codemirror library, setting to 'python' syntax highlighting mode
    editor = CodeMirror.fromTextArea(document.getElementById("field_script"), {
      mode: {
        name: "python",
        version: 2,
        singleLineStringErrors: false
      },
      lineNumbers: true,
      indentUnit: 2,
      tabMode: "shift",
      matchBrackets: true,
      foldGutter: true,
      gutters: ["CodeMirror-linenumbers", "CodeMirror-foldgutter"]
    });
    // ensure the editor form is updated as changes are made
    editor.on("change", function() {
      editor.save();
      $('#field_script').trigger('change');
    });
    // attach the 'resize' function to the code editing window
    $('.CodeMirror-scrollbar-filler').on('mousedown', function(ele) {
      var $parent = $(this).parent();
      var pheight = $parent.height();
      var y = ele.pageY;
      $(document).on('mousemove.dragging', function(ele) {
        $('body').addClass('disableselect');
        var my = ele.pageY;
        var ry = my - y;
        if(pheight+ry>30) editor.setSize(null, (pheight+ry));
      }).on('mouseup.dragging mouseleave.dragging', function(ele) {
        $(document).off('.dragging');
        $('body').removeClass('disableselect');
      });
    });
    $('#script_editor').on('keydown', function(event) {
      if (event.ctrlKey || event.metaKey) {
        if (String.fromCharCode(event.which).toLowerCase() == 's') {
          event.preventDefault();
          $(this).find('button').click();
        }
      }
    });
  }
};

// function to reload the UI
var reload = function() {
  window.location.reload();
};

// function to display all nodes in the nodel network
var listNodes = function(){
  // show the list
  $('#nodelist').show();
  $('.logo a').attr('href','/diagnostics.htm');
  // set the initial display limit to 50
  $('#nodefilter').data('num', 50);
  // get the list of nodes from the host
  req = $.getJSON('http://'+host+'/REST/nodeURLs', function(data) {
    // ensure the list is empty
    $('#nodelist ul').empty();
    // for each node, create an entry in the list until the display limit is reached
    $.each(data, function(key, value) {
      $('#nodelist ul').append('<li><a href="'+value.address+'">'+value.node+'</a></li>');
      return key < $('#nodefilter').data('num')-1;
    });
    // if the list goes over the display limit, add a 'more' link
    if(data.length > $('#nodefilter').data('num')) $('#nodelist ul').append('<li><strong><a id="listmore" href="#">'+(data.length-$('#nodefilter').data('num'))+' more</a>...</strong></li>');
    // if there are no nodes, display 'no results'
    if(data.length == 0) $('#nodelist ul').append('<li>searching...</li>');
    // if there is an error retrieving the list of nodes, display an error message
  }).error(function(){
    $('#nodelist ul').empty();
    $('#nodelist ul').append('<li>error retrieving results</li>');
  });
  req = 0;
  // watch for text being entered into the node filter field
  $('#nodefilter').keypress(function(e) {
    var charCode = e.charCode || e.keyCode;
    // trap the 'return' key to prevent it from submitting the form
    if (charCode  == 13) {
      $('#nodefilter').keyup();
      return false;
    }
  });
  // watch for 'more' to be clicked, add 25 to the limit and refresh the list
  $('#nodelist').on('mousedown touchstart', '#listmore', function() {
    $('#nodefilter').data('num', $('#nodefilter').data('num')+25);
    $('#nodefilter').keyup();
    return false;
  });
  // watch for text being entered into the node filter field
  $('#nodefilter').keyup(function(e) {
    // if the field has a value, set the filter to this value
    if($(this).val().length >0) filter = {filter:$(this).val()};
    else filter = '';
    var ele = this;
    // abort any requests in progress
    if(req) req.abort();
    // get the list of nodes from the host
    req = $.getJSON('http://'+host+'/REST/nodeURLs',filter, function(data) {
      // ensure the list is empty
      $('#nodelist ul').empty();
      // for each node, create an entry in the list until the display limit is reached
      $.each(data, function(key, value) {
        var re = new RegExp("(.*)("+$(ele).val()+")(.*)","ig");
        var val = value.node.replace(re, '$1<strong>$2</strong>$3')
        $('#nodelist ul').append('<li><a href="'+value.address+'">'+val+'</a></li>');
        return key < $('#nodefilter').data('num')-1;
      });
      // if the list goes over the display limit, add a 'more' link
      if(data.length > $('#nodefilter').data('num')) $('#nodelist ul').append('<li><strong><a id="listmore" href="#">'+(data.length-$('#nodefilter').data('num'))+' more</a>...</strong></li>');
      // if there are no nodes, display 'no results'
      if(data.length == 0) $('#nodelist ul').append('<li>searching...</li>');
      // if there is an error retrieving the list of nodes (and it was not because it was aborted), display an error message
    }).error(function(req){
      if(req.statusText!="abort"){
        $('#nodelist ul').empty();
        $('#nodelist ul').append('<li>error retrieving results</li>');
      }
    });
  });
  $('#nodelist').on('mousedown touchstart', '#nodeaddnew', function() {
    $('.nodeadd').show();
    $('#newnodename').focus();
    return false;
  });
  $('#nodelist').on('mousedown touchstart', '.close', function() {
    $('.nodeadd').hide();
    return false;
  });
  $('#nodeadd').on('submit', function(e) {
    var nodenameraw = $('#nodelist #newnodename').val();
    var nodename = {"value":nodenameraw};
    var req = $.getJSON('http://' + host + '/REST/newNode', nodename, function() {
      $('.nodeadd').hide();
      $('#nodeaddnew').prop('disabled', true);
      checkRedirect('http://' + host + '/nodes/' + encodeURIComponent(nodenameraw));
    }).error(function(req){
      if(req.statusText!="abort"){
        var error = 'Node add failed';
        if(req.responseText) {
          var message = JSON.parse(req.responseText);
          error = error + '<br/>' + message['message'];
        }
        dialog(error,'error');
      }
    });
    return false;
  });
  $('#newnodename').keypress(function(e) {
    if (e.keyCode == 13) {
      e.preventDefault();
      $('#nodeadd').trigger('submit');
    }
  });
  setInterval(function(){ $('#nodefilter').keyup(); }, 3000);
};

// function to retrieve the value of a query string parameter by name
var getParameterByName = function(name) {
  name = name.replace(/[\[]/, "\\\[").replace(/[\]]/, "\\\]");
  var regexS = "[\\?&]" + name + "=([^&#]*)";
  var regex = new RegExp(regexS);
  var results = regex.exec(window.location.search);
  if(results == null) return "";
  else return decodeURIComponent(results[1].replace(/\+/g, " "));
};

// function to display a message in a dialog box
var dialog = function(message, type, duration){
  // default to 'info' type
  type = type || "info";
  duration = duration || 3000;
  // clear any timer that was set to close the dialog
  clearTimeout($('.dialog').stop().data('timer'));
  // set the message
  $('.dialog').html('<div class="'+type+'">'+message+'</div>');
  // show the dialog box
  $('.dialog').slideDown(function() {
    var elem = $(this);
    // set a timer to close the dialog
    if(duration>0) $.data(this, 'timer', setTimeout(function() { elem.slideUp(); }, duration));
  });
  return false;
};

var notice = function(message){
  $('.notice .msg').html(message);
  $('.notice').slideDown();
  return false;
};

var checkRedirect = function(url) {
  console.log(url);
  $.get(url, function() {
    window.location.href = url;
  }).error(function(e) {
    // check again in one second
    if($('body').data('redirecttimeout')) $('body').data('redirecttimeout', $('body').data('redirecttimeout')+1)
    else $('body').data('redirecttimeout',1)
    $('body').data('redirect', setTimeout(function() { checkRedirect(url); }, 1000));
    dialog('Redirecting. Waiting for node to become available ('+$('body').data('redirecttimeout')+')','info',0);
  });
};

// function to start polling the nodehost to see if the UI should reload (the node has restarted)
var checkReload = function(){
  // set the filter to get the current timestamp if it is not known
  var params = {};
  if(typeof $('body').data('timestamp') === "undefined") params = {timeout:tim};
  // otherwise, set the filter to the current timestamp
  else var params = {timeout:tim, timestamp:$('body').data('timestamp')};
  // call the function
  $.getJSON('http://'+host+'/REST/nodes/'+encodeURIComponent(node)+'/hasRestarted', params, function(data) {
    // set the current timestamp if it is not known
    if(typeof $('body').data('timestamp') === "undefined"){
      $('body').data('timestamp', data.timestamp);
      // otherwise, reload the UI if the timestamps do not match
    } else if ($('body').data('timestamp')!=data.timestamp) {
      $('body').data('timestamp', data.timestamp);
      reload();
    }
  }).always(function() {
    // check again in one second
    $('body').data('timer', setTimeout(function() { checkReload(); }, 1000));
  });
};

// function to update the activity display using polling
var updateLogs = function(){
  if(poll || !("WebSocket" in window) || (typeof $('body').data('config')['webSocketPort'] === "undefined")) {
    var url;
    // if the last sequence number is not set, set the filter to retrieve the last 100 entries
    if (typeof $('body').data('seq') === "undefined") url = 'http://' + host + '/REST/nodes/' + encodeURIComponent(node) + '/activity?from=-1';
    // otherwise, set the filter to retrieve the next 100 changes
    else url = 'http://' + host + '/REST/nodes/' + encodeURIComponent(node) + '/activity?from=' + $('body').data('seq');
    // call the function
    $.getJSON(url, {timeout: tim}, function (data) {
      // if the last sequence number is not set, set it and flag that the display should not be animated
      if (typeof $('body').data('seq') === "undefined") {
        var noanimate = true;
        $('body').data('seq', -1);
      }
      data.sort(function (a, b) {
        return a.seq < b.seq ? -1 : a.seq > b.seq ? 1 : 0;
      });
      // display the items in the activity
      $.each(data, function (key, value) {
        if (value.seq != 0) {
          // add one to the current sequence
          $('body').data('seq', value.seq + 1);
          parseLog(value, noanimate);
        }
      });
    }).always(function () {
      // check again in one second
      $('body').data('logs', setTimeout(function () {
        updateLogs();
      }, 1000));
    });
  } else {
    var wshost = "ws://"+document.location.hostname+":"+$('body').data('config')['webSocketPort']+"/nodes/"+node;
    try{
      var socket = new WebSocket(wshost);
      socket.onopen = function(){
        console.log('Socket Status: '+socket.readyState+' (open)');
        online(socket);
      }
      socket.onmessage = function(msg){
        var data = JSON.parse(msg.data);
        //console.log('Received:');
        //console.log(data);
        if('activityHistory' in data){
          $.each(data['activityHistory'], function() {
            if(this.seq != 0) parseLog(this, true);
          });
        } else parseLog(data['activity']);
      }
      socket.onclose = function(){
        console.log('Socket Status: '+socket.readyState+' (Closed)');
        offline();
      }
      socket.onerror = function(){
        poll = true;
      }
    } catch(exception) {
      console.log('Error: '+exception);
      offline();
    }
  }
};

var parseLog = function(value, noanimate) {
  if (typeof(noanimate) === 'undefined') noanimate = false;
  if ((typeof value.arg !== 'undefined') && (value.source == 'local')) $('#' + value.type + '_' + encodr(value.alias)).trigger('updatedata', {"arg": value.arg});
  // if the activity is a local event or action, and the display is not set to animate, highlight the action button
  if ((value.source == 'local') && (!noanimate)) $('#' + value.type + '_' + encodr(value.alias) + ' button span').stop(true, true).css({'color': opts.local[value.type].colour}).animate({'color': '#bbb'}, 10000);
  // construct the activity log entry
  var tme = moment(value.timestamp);
  var str = '<li title="' + tme.format('Do MMM, HH:mm.ss') + '"><div>' + opts[value.source][value.type].icon + '</div>' + value.alias;
  str += '&nbsp;&nbsp;<span class="timestamp">- ' + tme.format('Do MMM, h:mm a') + '</span>';
  // add the return value to the entry, if it exists
  if ((typeof value.arg !== 'undefined') && (value.arg !== null)) {
    str += '<span><br/>' + JSON.stringify(value.arg, null, 2) + '</span>';
  }
  str += '</li>';
  // create the activity element
  var activity = $(str).attr('id', 'activity_' + value.source + '_' + value.type + '_' + encodr(value.alias)).data('time',value.timestamp);
  // if animation is disabled, set to display without animation
  if (noanimate) $(activity).children('div').css('color', opts[value.source][value.type].colour).css('opacity', 0.25);
  // otherwise, set to display with animation
  else $(activity).children('div').css('color', opts[value.source][value.type].colour).animate({'opacity': 0.25}, 10000);
  // check for current entry
  var ele = $('#activity_' + value.source + '_' + value.type + '_' + encodr(value.alias));
  if (ele.length) {
    ele.remove();
    $('#activity ul').prepend(activity);
  } else {
    $('#activity ul').prepend(activity);
  }
};

var online = function(socket){
  $('body').data('timeout', setInterval(function() { socket.send('{}'); }, 1000));
  console.log('online');
};

var offline = function(){
  clearInterval($('body').data('timeout'));
  $('body').data('update', setTimeout(function() { updateLogs(); }, 1000));
  console.log('offline');
};

// function to update the console
var updateConsoleForm = function(){
  var url;
  // if the last sequence number is not set, set the filter to retrieve the last 100 entries
  if(typeof $('#console').data('seq') === "undefined") url = 'http://'+host+'/REST/nodes/'+encodeURIComponent(node)+'/console?from=-1&max=100';
  // otherwise, set the filter to retrieve the next 100 changes
  else url = 'http://'+host+'/REST/nodes/'+encodeURIComponent(node)+'/console?from='+$('#console').data('seq')+'&max=100';
  // call the function
  $.getJSON(url, {timeout:tim}, function(data) {
    // disable animation unless there is new data
    var animate = false;
    // reverse the order so that the newest entry is last
    data.reverse();
    // if the last sequence number is not set, set the current sequence number
    if (typeof $('#console').data('seq') === "undefined") {
      // set to zero if it doesn't exist
      if(data[0] === undefined) $('#console').data('seq', 0);
      // otherwise set to the first number
      else $('#console').data('seq', data[0].seq);
    }
    // display each entry
    $.each(data, function(key, value) {
      // parse the timestamp for formatting
      var timestamp = moment(value.timestamp);
      // add the entry to the list
      var div = $('<div class="'+value.console+'"></div>').text(timestamp.format('MM-DD HH:mm:ss.SS')+' '+value.comment);
      $('#console').append(div);
      // set the current sequence number
      $('#console').data('seq', value.seq+1);
      // trim the list if it goes over 100 items
      if($("#console").children("div").length > 100) $("#console").children('div:lt(1)').remove();
      // flag that the list should scroll to the latest item
      animate = true;
    });
    // if there is new data and console scroll is enabled, scroll to the bottom of the list
    if(animate && !$('#consolescroll').is(':checked')){
      $('#console').animate({
        scrollTop: $("#console")[0].scrollHeight
      },1000);
    }
  }).always(function() {
    // check again in one second
    $.data(this, 'timer', setTimeout(function() { updateConsoleForm(); }, 1000));
  });
};

// function to build a form using the template
var buildForm = function(name, formname, path, action, link){
  // if this form should be linked to data
  if(link) {
    // get the data
    $.getJSON('http://'+host+'/REST/nodes/'+encodeURIComponent(node)+'/'+(path.length!==0?(path.join('/')+'/'+name):name),"", function(data) {
      // if there is no data for this form, set the data to an empty object
      if($.isEmptyObject(data)) data = {};
      // set the form action (if it exsits)
      if($('#'+formname).get(0)) $('#'+formname).get(0).setAttribute('action', '/REST/nodes/'+encodeURIComponent(node)+'/'+(path.length!==0?(path.join('/')+'/'+name):name)+'/'+action);
      // link the form to the data (this renders the form)
      eval('$.link.'+formname+'Template("#"+formname, data)');
      // attach UI events
      buildFormEvents(formname, action, data);
      // indicate that the form is ready
      $('#'+formname).trigger('ready');
    });
    // if the form does not need to link to exist data
  } else {
    // set the data to an empty object
    data = {};
    // set the form action (if it exsits)
    if($('#'+formname).get(0)) $('#'+formname).get(0).setAttribute('action', '/REST/nodes/'+encodeURIComponent(node)+'/'+(path.length!==0?(path.join('/')+'/'+name):name)+'/'+action);
    // link the form to the data (this renders the form)
    eval('$.link.'+formname+'Template("#"+formname, data)');
    // attach UI events
    buildFormEvents(formname, action, data);
    // indicate that the form is ready
    $('#'+formname).trigger('ready');
  }
};

// function to attach required UI events to a form
var buildFormEvents = function(name, action, data){
  var req = 0;
  var reqs = [];
  $('#'+name).on('updatedata', function(evt, newdata) {
    if(!$(this).hasClass('active')) {
      $.observable(data).setProperty(newdata);
      $('#'+name).trigger('updated');
    }
  });
  // handle submit validation
  $('#'+name).on('click', '.'+action, function() {
    var ele = this;
    if(!$(ele).hasClass('processing')) {
      var proceed = true;
      $(ele).addClass('processing');
      // remove any previous validation error highlighting
      $('.highlight').each(function () {
        $(this).removeClass('highlight');
      });
      // check if an input is required
      $('#' + name + ' input.required, #' + name + ' select.required').each(function () {
        // check if it is a child of a required object
        if (($(this).closest('div.object').length > 0) && (!$(this).closest('div.object').hasClass('required'))) {
          // if it has a value
          if ($(this).val()) {
            // if another field in the required object does not also have a value, highlight it and prevent the form from being submitted
            $(this).closest('div.object').children('div.field').children('input.required, select.required').each(function () {
              if (!$(this).val()) {
                $(this).addClass('highlight').focus();
                $(this).parents('div:hidden').each(function () {
                  $(this).slideDown('slow');
                });
                dialog('Required value missing', 'error');
                proceed = false;
                return false;
              }
            });
          }
          // if this is not a child of a required object
        } else {
          // if it doesn't have a value, highlight it and prevent the form from being submitted
          if (!$(this).val()) {
            $(this).addClass('highlight').focus();
            $(this).parents('div:hidden').each(function () {
              $(this).slideDown('slow');
            });
            dialog('Required value missing', 'error');
            proceed = false;
            return false;
          }
        }
      });
      // if a warning is required before submit, display the warning and submit only if 'ok' is clicked
      if ($('#' + name).data('caution') && proceed) {
        if (!confirm('Caution: ' + $('#' + name).data('caution'))) proceed = false;
        dialog("Cancelled", "error");
      }
      // if validation and warning are ok, submit the form
      if (proceed) {
        // make a copy of the data to be sent. Stringify removes extra jsviews data
        var tosend = JSON.parse(JSON.stringify(data));
        // remove empty values
        removeNulls(tosend);
        // post the data as JSON
        $.postJSON($('#' + name).get(0).getAttribute('action'), JSON.stringify(tosend), function () {
          // if there is no error, print a success message
          dialog(action + " - Success");
        }).error(function (e, s) {
          // otherwise, print the error details
          errtxt = s;
          if (e.responseText) errtxt = s + "\n" + e.responseText;
          dialog("exec - Error:\n" + errtxt, "error");
        }).always(function () {
          $(ele).removeClass('processing');
        });
      } else $(ele).removeClass('processing');
    }
    if(typeof $(ele).data('retry') !== "undefined") clearTimeout($(ele).data('retry'));
    else $(ele).data('retry', setTimeout(function(){$(ele).find('.'+action).trigger('click')},100));
    return false;
  });
  // handle array 'add' events
  $('#'+name).on('click', '.add', function() {
    var value = '';
    // get the context of the element
    var view = $.view(this);
    // obtain the current value
    value = eval('view.data.'+this.id);
    // if the value does not exists, or is not an array, set it to a blank array
    if(!value || !$.isArray(value)) {
      $.observable(view.data).setProperty(this.id,[]);
      value = eval('view.data.'+this.id);
    }
    // if object keys are supplied, create the object
    if($(this).data('seed')) {
      var obj = {};
      $.each($(this).data('seed').split(','), function(e,i){
        obj[i] = null;
      });
      // add the object to the array
      $.observable(value).insert(value.length, obj);
    }
    // otherwise, add an empty object into the array
    else $.observable(value).insert(value.length,{});
    $('#'+name).trigger('updated');
    return false;
  });
  // handle array 'delete' events
  $('#'+name).on('click', '.delete', function() {
    // get the context of the element
    var view = $.view(this);
    // ensure we are at the root element
    while(view.data==view.parent.data) view=view.parent;
    // remove the item from the array
    $.observable(view.parent.data).remove(view.index,1);
    $('#'+name).trigger('updated');
    return false;
  });
  // handle array 'up' events
  $('#'+name).on('click', '.up', function() {
    // get the context of the element
    var view = $.view(this);
    // ensure we are at the root element
    while(view.data==view.parent.data) view=view.parent;
    // move the item
    $.observable(view.parent.data).move(view.index,view.index-1,1);
    $('#'+name).trigger('updated');
    return false;
  });
  // handle array 'down' events
  $('#'+name).on('click', '.down', function() {
    // get the context of the element
    var view = $.view(this);
    // ensure we are at the root element
    while(view.data==view.parent.data) view=view.parent;
    // move the item
    $.observable(view.parent.data).move(view.index,view.index+1,1);
    $('#'+name).trigger('updated');
    return false;
  });
  // handle updates to forms
  $('#'+name).on('ready updated', function() {
    var root = this.id;
    // initialise unset objects
    $(this).find('.addobj').each(function(){
      var v = $.view(this);
      $.observable(v.data).setProperty(this.id, {});
      $('#'+root).trigger('updated');
    });
    // initialise jqCron and set the current value
    $.when($(this).find('input.cron').each(function() {
      $(this).jqCron({
        enabled_minute: true,
        multiple_dom: true,
        multiple_month: true,
        multiple_mins: true,
        multiple_dow: true,
        multiple_time_hours: true,
        multiple_time_minutes: true,
        default_period: 'minute',
        default_value: $(this).val(),
        lang: 'en'
      });
    })).then($(this).trigger('jqcronenabled'));
  });
  // watch for text being entered into node type fields
  $('#'+name).on('keydown', 'input.node', function(e) {
    var charCode = e.charCode || e.keyCode;
    if((charCode == 40) || (charCode == 38) || (charCode == 13)) {
      e.preventDefault();
      // is an arrow down or up, so pick an item from the autocomplete box
      var ele = $(this).siblings('div.autocomplete[data-target="'+$(this).attr('id')+'"]');
      if(ele.length !== 0){
        if((charCode == 40) || (charCode == 38)) {
          if($(ele).find('li.active').length != 0) {
            var sub = $(ele).find('li.active');
            if (charCode == 40) {
              if($(sub).next().length !== 0){
                $(sub).removeClass('active');
                $(sub).next().addClass('active');
              }
            } else {
              if($(sub).prev().length !== 0){
                $(sub).removeClass('active');
                $(sub).prev().addClass('active');
              }
            }
          } else {
            $(ele).find("li").first().addClass('active');
          }
        } else {
          $(ele).find('li.active').click();
        }
      }
    } else if (charCode != 9) {
      if(e.ctrlKey || e.altKey) return true;
      // if the field has a value, set the filter to this value
      if($(this).val().length >0) filter = {filter:$(this).val()};
      else filter = '';
      // abort any requests in progress
      if(req) req.abort();
      var ele = this;
      // if there is a value to lookup
      if($(this).val().length >0) {
        // get the list of nodes from the host
        req = $.getJSON('http://'+host+'/REST/nodeURLs',filter, function(data) {
          // dynamically add the popup code to the page if it doesn't exist
          if(!$(ele).siblings('div.autocomplete[data-target="'+$(ele).attr('id')+'"]').length) $(ele).after('<div class="autocomplete" data-target="'+$(ele).attr('id')+'"><ul></ul></div>');
          // get the list element
          var list = $(ele).siblings('div.autocomplete[data-target="'+$(ele).attr('id')+'"]').children('ul');
          // ensure the list is empty
          $(list).empty();
          // for each node, create an entry in the list to a maximum of six
          $.each(data, function(key, value) {
            var re = new RegExp("(.*)("+$(ele).val()+")(.*)","ig");
            var val = value.node.replace(re, '$1<strong>$2</strong>$3')
            $(list).append('<li>'+val+'</li>');
            return key < 6;
          });
          // if there are no results, remove the popup
          if(data.length == 0) $(ele).siblings('div.autocomplete[data-target="'+$(ele).attr('id')+'"]').remove();
          // if there is an error, remove the popup
        }).error(function(req){
          if(req.statusText!="abort") $(this).siblings('div.autocomplete[data-target="'+$(ele).attr('id')+'"]').remove();
        });
        // if there is nothing to look up, remove the popup
      } else {
        $(this).siblings('div.autocomplete[data-target="'+$(ele).attr('id')+'"]').remove();
      }
    }
  });
  // watch for text being entered into action and event type fields
  $('#'+name).on('keydown', 'input.event, input.action', function(e) {
    var charCode = e.charCode || e.keyCode;
    if((charCode == 40) || (charCode == 38) || (charCode == 13)) {
      e.preventDefault();
      // is an arrow down or up, so pick an item from the autocomplete box
      var ele = $(this).siblings('div.autocomplete[data-target="'+$(this).attr('id')+'"]');
      if(ele.length !== 0){
        if((charCode == 40) || (charCode == 38)) {
          if($(ele).find('li.active').length != 0) {
            var sub = $(ele).find('li.active');
            if (charCode == 40) {
              if($(sub).next().length !== 0){
                $(sub).removeClass('active');
                $(sub).next().addClass('active');
              }
            } else {
              if($(sub).prev().length !== 0){
                $(sub).removeClass('active');
                $(sub).prev().addClass('active');
              }
            }
          } else {
            $(ele).find("li").first().addClass('active');
          }
        } else {
          $(ele).find('li.active').click();
        }
      }
    } else if (charCode != 9) {
      if(e.ctrlKey || e.altKey) return true;
      // abort any requests in progress
      if(req) req.abort();
      var ele = this;
      var lnode = '';
      // find the nearest node field and retrieve its value 
      if($(this).closest('div.object').children('div.field').children('input.node[data-group="'+$(this).data('group')+'"]').length > 0) {
        lnode = $(this).closest('div.object').children('div.field').children('input.node[data-group="'+$(this).data('group')+'"]').val();
        // if no node field is found, use the local node
      } else lnode = node;
      // check if this this is an action or event
      var type = $(this).hasClass("event") ? 'events' : 'actions';
      // set the filter to the node name
      var filter = {filter:lnode};
      // if the field has a value
      if(($(this).val().length >0)) {
        // get the node list
        req = $.getJSON('http://'+host+'/REST/nodeURLs',filter, function(data) {
          // if one or more nodes is found
          if(data.length > 0){
            var len = 0;
            // stop any requests in progress
            $.each(reqs, function(key,value){ value.abort() });
            reqs=[];
            // create an empty list
            var items = $('<ul>');
            // for every item returned
            $.each(data, function(key, value) {
              // use an anchor element to parse url for each node
              var parser = document.createElement('a');
              parser.href = value.address;
              var host = parser.host;
              $(parser).remove();
              var lnode = value.node;
              // get the list of actions/events from the host
              reqs.push($.getJSON('http://'+host+'/REST/nodes/'+encodeURIComponent(lnode)+'/'+type,"", function(data) {
                // for every action
                $.each(data, function(key, value) {
                  // if there is a value
                  if(value.name.toLowerCase().indexOf($(ele).val().toLowerCase()) >= 0) {
                    // check if the name matches the current value, highlight and add to the list
                    var re = new RegExp("(.*)("+$(ele).val()+")(.*)","ig");
                    var val = value.name.replace(re, '$1<strong>$2</strong>$3')
                    $(items).append('<li>'+val+'</li>');
                    len++;
                  }
                  // limit to 20 results
                  return len < 20;
                });
              }));
            });
            // when all requests have completed
            $.when.apply($, reqs).then(function (){
              // if the requests were not aborted and there are results
              if(($(items).children('li').length != 0) && (req.statusText!="abort")){
                // dynamically add the popup code to the page if it doesn't exist
                if(!$(ele).siblings('div.autocomplete[data-target="'+$(ele).attr('id')+'"]').length) $(ele).after('<div class="autocomplete" data-target="'+$(ele).attr('id')+'"><ul></ul></div>');
                // replace the current list with the new one
                $(ele).siblings('div.autocomplete[data-target="'+$(ele).attr('id')+'"]').children('ul').replaceWith(items);
                // if there are no results, remove the popup
              } else $(ele).siblings('div.autocomplete[data-target="'+$(ele).attr('id')+'"]').remove();
            });
          }
        })
        // if the field has no value, remove the popup
      } else {
        $(this).siblings('div.autocomplete[data-target="'+$(ele).attr('id')+'"]').remove();
      }
    }
  });
  // handle file browse button click events
  $('#'+name).on('click','.browse', function(e) {
    // trigger the native control
    $(this).next('input.upload').trigger('click');
    return false;
  });
  // handle when file has been selected
  $('#'+name).on('change', 'input.upload', function(e) {
    // create a new dynamic data form
    var data = new FormData();
    // set the file name
    data.append('file', this.files[0]);
    var _this = this;
    // using ajax, submit the file (upload)
    $.ajax({
      context: $(this),
      url: 'http://'+host+'/upload',
      data: data,
      type: 'POST',
      timeout: 0,
      processData: false,
      contentType: false,
      xhr: function() {
        myXhr = $.ajaxSettings.xhr();
        if(myXhr.upload){
          // update the progress indicator
          myXhr.upload.addEventListener('progress',function(data){
            $(_this).siblings('progress').val(Math.floor((data.loaded/data.total)*100));
          }, false);
        }
        return myXhr;
      },
      beforeSend: function() {
        // show the progress indicator when we begin uploading
        $(this).siblings('progress').show();
      },
      error: function(data, error) {
        // show an error on failure
        if(data.responseText) error = error + "\n" + data.responseText;
        dialog("exec - Error:\n"+error, "error");
      },
      success: function(data) {
        // if upload is successful, set the file name as the value of the upload field
        var value = $(this).siblings('input.file');
        value.val(data);
        // trigger a change to invoke jsviews
        value.trigger('change');
        // display a success message
        dialog("File uploaded: "+data);
      },
      complete: function(data) {
        // hide the progress indicator when finished
        $(this).siblings('progress').val(0).hide();
      }
    });
  });
  $('#'+name).on('mouseenter', 'div.autocomplete ul li', function() {
    $(this).parent().find('.active:not(:hover)').removeClass('active');
    $(this).addClass('active');
  });
  // handle when an item is selected from the autocomplete popup
  $('#'+name).on('click', 'div.autocomplete ul li', function() {
    // set the field value
    $(this).parents('div.autocomplete').siblings('input#'+$(this).parents('div.autocomplete').data('target').replace(/\./g,'\\.')).val($(this).text()).trigger('change');
    // hide the autocomplete popup
    $(this).parents('div.autocomplete').remove();
  });
  // handle when a node/event/action field loses focus
  $('#'+name).on('focusout', 'input.node, input.event, input.action', function() {
    var ele = this;
    // set a timeout to remove any autocomplete popup that may be displayed
    setTimeout(function() {
      if(!$(ele).is(":focus")) $(ele).siblings('div.autocomplete[data-target="'+$(ele).attr('id')+'"]').remove();
    }, 1000);
  });
  $('#'+name).on('focusout', 'input.date', function() {
    var ele = this;
    if(!moment($(ele).val(),'YYYY-MM-DD').isValid()) {
      $(ele).addClass('highlight').focus();
      dialog('Date is invalid', 'error');
    } else $(ele).removeClass('highlight');
  });
  $('#'+name).on('mousedown touchstart', 'input.range', function() {
    $(this).closest('form').addClass('active');
  });
  $('#'+name).on('mouseup mouseleave touchend', 'input.range', function() {
    $(this).closest('form').removeClass('active');
  });
  $('#'+name).on('input change', 'input.range', function() {
    var ele = this;
    // ensure correct event order and rate limit
    clearTimeout($(ele).data('bounce'));
    $(ele).data('bounce', setTimeout(function(){$(ele).parents('form').children('button').click()}, 100));
  });
  $('#'+name).on('contextmenu', 'button', function(e) {
    e.preventDefault();
    if(typeof $(this).attr('title')!= "undefined") {
      var text = $(this).attr('title');
      if(text.indexOf(': ') > -1)
        notice('<strong>' + text.split(': ')[0] + '</strong></br>' + text.split(': ')[1]);
      else
        notice(text);
    }
  });
  $('#'+name+' button').addTouch();
  $('#'+name).find('label, button').attr('unselectable','on').addClass('disableselect').bind('selectstart', function(){ return false; });
};

// function to build a form template using a provided JSON schema (recursive)
var buildFormSchema = function(data, key, parent) {
  // return null if there is nothing to build
  if($.isEmptyObject(data)) return null;
  // set empty variables for the element and its class
  var set = '';
  var cls = '';
  var group = '';
  // field group is always the parent
  if(parent) { 
    group = parent;
    parent = parent + '.' + key;
  }
  // otherwise, set the parent to the field key (or none)
  else parent = key ? key : '';
  if(typeof data.title == "undefined") data.title = key;
  var link = parent.replace('.','^');
  // create string for display
  var xtr = [];
  if(data.required) xtr.push('required');
  if(data.format) xtr.push(data.format);
  if(xtr.length!==0) cls = ' class="'+xtr.join(' ')+'"';
  // determine placeholder value
  var placeholder = typeof data.hint != "undefined" ? htmlEncode(data.hint) : '';
  // format according to the field type
  switch(data.type) {
    // format an object
    case 'object':
      // render each object item
      if('properties' in data) {
        var odr = [];
        $.each(data.properties, function (lkey, lvalue) {
          odr.push({key:lkey,val:lvalue});
        });
        odr.sort(function(a, b){
          var aName = a.key.toLowerCase();
          var bName = b.key.toLowerCase();
          var aOrder = a.val.order ? a.val.order : 0;
          var bOrder = b.val.order ? b.val.order : 0;
          return aOrder < bOrder ? -1 : aOrder > bOrder ? 1 : aName < bName ? -1 : aName > bName ? 1 : 0;
        });
        $.each(odr, function (skey, svalue) {
          get = buildFormSchema(svalue.val, svalue.key, parent);
          // if the item rendered is an object, append a conditionally displayed 'add' div for jsviews to initialise
          if (svalue.val.type == "object") {
            var fkey = parent ? parent + '.' + svalue.key : svalue.key;
            var flink = fkey.replace('.','^');
            get = '{^{if ~isSet(' + flink + ')}}' + get + '{{else}}<div class="addobj" id="' + fkey + '"></div>{{/if}}';
          }
          // add the item to the current template string
          set += get;
        });
        // if this isn't a root object, add a block element wrapper
        if (key) {
          xtr.push('object');
          set = '<div class="block"><h6>' + data.title + '</h6><div class="' + xtr.join(' ') + '">' + set + '</div></div>';
        }
      } else console.log('object '+parent+' has no proprties');
      break;
    // format an array
    case 'array':
      // if the array may contain multiple object types
      if($.isArray(data.items.type)){
        var opt = {};
        // render each type, adding a jsviews conditional display handler
        $.each(data.items.type, function(lkey, lvalue) {
          if(!lvalue.title) lvalue.title = lkey;
          get=buildFormSchema(lvalue, null, null);
          keys=$.map(lvalue.properties, function(e,i) {return i});
          set += '{{if ~isIn("'+keys.join('","')+'")}}'+get+'{{/if}}';
          opt[lvalue.title]=keys;
        });
        // add conditionally displayed delete, up and down buttons (accounting for a minimum number of items)
        if(data.minItems) set= '{^{for '+parent+'}}<span>'+set+'{^{if #parent.data.length > '+data.minItems+'}}<input type="button" class="delete" id="'+parent+'{{:#getIndex()}}" value="Delete" />{{/if}}{^{if #getIndex() > 0}}<input type="button" class="up" id="up_'+parent+'{{:#getIndex()}}" value="&#x25b2;" />{{/if}}{^{if #getIndex() < #parent.data.length-1}}<input type="button" class="down" id="down_'+parent+'{{:#getIndex()}}" value="&#x25bc;" /><hr/>{{/if}}</span>{{/for}}';
        else set= '{^{for '+parent+'}}<span>'+set+'<input type="button" class="delete" id="'+parent+'{{:#getIndex()}}" value="Delete" />{^{if #getIndex() > 0}}<input type="button" class="up" id="up_'+parent+'{{:#getIndex()}}" value="&#x25b2;" />{{/if}}{^{if #getIndex() < #parent.data.length-1}}<input type="button" class="down" id="down_'+parent+'{{:#getIndex()}}" value="&#x25bc;" /><hr/>{{/if}}</span>{{/for}}';
        // create an 'add' button for each of the object types
        var buttons = '';
        $.each(opt, function(e, i) {
          buttons+= '<input type="button" class="add" id="'+parent+'" data-seed="'+i.join(',')+'" value="Add '+e+'" />';
        });
        // add conditionally displayed add buttons (accounting for a maximm number of items)
        if(data.maxItems) set+= '{^{if '+parent+'}}{^{if '+parent+'.length < '+data.maxItems+'}}{^{if '+parent+'.length != 0}}<hr/>{{/if}}'+buttons+'{{/if}}{{/if}}';
        else set+= '{^{if '+parent+'}}{^{if '+parent+'.length > 0}}<hr/>{{/if}}'+buttons+'{{/if}}';
        // if this isn't a root object, add a block element wrapper
        if(key) set = '<div class="block array"><h6>'+htmlEncode(data.title)+'</h6><div>'+set+'</div></div>';
        // if the array can contain only one object
      } else if(data.items.type == 'object') {
        // render the object
        get=buildFormSchema(data.items, null, null);
        // add conditionally displayed delete, up and down buttons (accounting for a minimum number of items)
        if(data.minItems) set= '{^{for '+parent+'}}<span>'+get+'{^{if #parent.data.length > '+data.minItems+'}}<input type="button" class="delete" id="'+parent+'{{:#getIndex()}}" value="Delete" />{{/if}}{^{if #getIndex() > 0}}<input type="button" class="up" id="up_'+parent+'{{:#getIndex()}}" value="&#x25b2;" />{{/if}}{^{if #getIndex() < #parent.data.length-1}}<input type="button" class="down" id="down_'+parent+'{{:#getIndex()}}" value="&#x25bc;" /><hr/>{{/if}}</span>{{/for}}';
        else set= '{^{for '+parent+'}}<span>'+get+'<input type="button" class="delete" id="'+parent+'{{:#getIndex()}}" value="Delete" />{^{if #getIndex() > 0}}<input type="button" class="up" id="up_'+parent+'{{:#getIndex()}}" value="&#x25b2;" />{{/if}}{^{if #getIndex() < #parent.data.length-1}}<input type="button" class="down" id="down_'+parent+'{{:#getIndex()}}" value="&#x25bc;" /><hr/>{{/if}}</span>{{/for}}';
        // add conditionally displayed add button (accounting for a maximum number of items)
        if(data.maxItems) set+= '{^{if '+parent+'}}{^{if '+parent+'.length < '+data.maxItems+'}}{^{if '+parent+'.length != 0}}<hr/>{{/if}}<input type="button" class="add" id="'+parent+'" value="Add" />{{/if}}{{/if}}';
        else set+= '{^{if '+parent+'}}{^{if '+parent+'.length > 0}}<hr/>{{/if}}{{/if}}<input type="button" class="add" id="'+parent+'" value="Add" />';
        // if this isn't a root object, add a block element wrapper
        if(key) set = '<div class="block array"><h6>'+htmlEncode(data.title)+'</h6><div>'+set+'</div></div>';
      }
      break;
    // format a string
    case 'string':
      // if the string has a fixed set of options, render as a select list
      if(data['enum']){
        set = '<div class="field"><label for="field_'+parent+'{{:#getIndex()}}"'+cls+'>'+htmlEncode(data.title)+'</label><select id="field_'+parent+'{{:#getIndex()}}" title="'+htmlEncode(data.desc)+'" data-link="'+link+'"'+cls+'>';
        set+= '<option value=""></option>';
        for (var i=0;i<data['enum'].length;i++) {
          set+= '<option value="'+data['enum'][i]+'">'+data['enum'][i]+'</option>';
        }
        set += '</select></div>';
      } else {
        // render according to field format
        switch(data.format){
          // long fields are rendered as a textarea element
          case 'long':
            set = '<div class="field"><label for="field_'+parent+'{{:#getIndex()}}"'+cls+'>'+htmlEncode(data.title)+'</label><textarea placeholder="'+placeholder+'" id="field_'+parent+'{{:#getIndex()}}" title="'+htmlEncode(data.desc)+'" data-link="'+link+'"'+cls+'></textarea></div>';
            break;
          // node, action and event fields render with an additional group attribute
          case 'node':
          case 'action':
          case 'event':
            set = '<div class="field"><label for="field_'+parent+'{{:#getIndex()}}"'+cls+'>'+htmlEncode(data.title)+'</label><input placeholder="'+placeholder+'" id="field_'+parent+'{{:#getIndex()}}" title="'+htmlEncode(data.desc)+'" type="text" data-group="'+group+'" data-link="'+link+'"'+cls+' /></div>';
            break;
          // file fields have a hidden upload element, progress indicator and 'browse' button
          case 'file':
            set = '<div class="field"><label for="field_'+parent+'{{:#getIndex()}}"'+cls+'>'+htmlEncode(data.title)+'</label><input placeholder="'+placeholder+'" id="field_'+parent+'{{:#getIndex()}}" title="'+htmlEncode(data.desc)+'" type="text" data-link="'+link+'"'+cls+' disabled /><input title="browse" class="browse" type="button" value="Browse"/><input class="upload" type="file" /><progress value="0" max="100"></progress></div>';
            break;
          // format a time
          case 'time':
            // time is rendered as html5 time type
            set = '<div class="field"><label for="field_'+parent+'{{:#getIndex()}}"'+cls+'>'+htmlEncode(data.title)+'</label><input placeholder="'+placeholder+'" id="field_'+parent+'{{:#getIndex()}}" title="'+htmlEncode(data.desc)+'" type="time" data-link="'+link+'"'+cls+' /></div>';
            break;
          // format a date
          case 'date':
            // date is rendered as html5 date type
            set = '<div class="field"><label for="field_'+parent+'{{:#getIndex()}}"'+cls+'>'+htmlEncode(data.title)+'</label><input placeholder="'+placeholder+'" id="field_'+parent+'{{:#getIndex()}}" title="'+htmlEncode(data.desc)+'" type="date" data-link="'+link+'"'+cls+' /></div>';
            break;
          // format a date-time
          case 'date-time':
            // date-time is rendered as html5 datetime type
            set = '<div class="field"><label for="field_'+parent+'{{:#getIndex()}}"'+cls+'>'+htmlEncode(data.title)+'</label><input placeholder="'+placeholder+'" id="field_'+parent+'{{:#getIndex()}}" title="'+htmlEncode(data.desc)+'" type="datetime" data-link="'+link+'"'+cls+' /></div>';
            break;
          // format a password
          case 'password':
            set = '<div class="field"><label for="field_'+parent+'{{:#getIndex()}}"'+cls+'>'+htmlEncode(data.title)+'</label><input placeholder="'+placeholder+'" id="field_'+parent+'{{:#getIndex()}}" title="'+htmlEncode(data.desc)+'" type="password" data-link="'+link+'"'+cls+' /></div>';
            break;
          // format a color
          case 'color':
            set = '<div class="field"><label for="field_'+parent+'{{:#getIndex()}}"'+cls+'>'+htmlEncode(data.title)+'</label><input placeholder="'+placeholder+'" id="field_'+parent+'{{:#getIndex()}}" title="'+htmlEncode(data.desc)+'" type="color" data-link="'+link+'"'+cls+' /></div>';
            break;
          // basic renderer for any other elements
          default:
            set = '<div class="field"><label for="field_'+parent+'{{:#getIndex()}}"'+cls+'>'+htmlEncode(data.title)+'</label><input placeholder="'+placeholder+'" id="field_'+parent+'{{:#getIndex()}}" title="'+htmlEncode(data.desc)+'" type="text" data-link="'+link+'"'+cls+' /></div>';
            break;
        }
      }
      break;
    // format an integer
    case 'integer':
      // integers are forced to whole numbers
      if(data.format == "range" && (typeof data.min !== "undefined") && (typeof data.max !== "undefined")) {
        set = '<div class="field"><label for="field_'+parent+'{{:#getIndex()}}"'+cls+'>'+htmlEncode(data.title)+'<output class="labelvalue" data-link="{numToStr:'+link+' trigger=\'input\':strToInt}"></output></label><input id="field_'+parent+'{{:#getIndex()}}" title="'+htmlEncode(data.desc)+'" type="range" step="1" min="'+data.min+'" max="'+data.max+'" data-link="{numToStr:'+link+' trigger=\'input\':strToInt}"'+cls+' /></div>';
      } else {
        set = '<div class="field"><label for="field_'+parent+'{{:#getIndex()}}"'+cls+'>'+htmlEncode(data.title)+'</label><input placeholder="'+placeholder+'" id="field_'+parent+'{{:#getIndex()}}" title="'+htmlEncode(data.desc)+'" type="number" step="1" data-link="{numToStr:'+link+':strToInt}"'+cls+' /></div>';
      }
      break;
    // format a number
    case 'number':
      // numbers can be floating point
      if(data.format == "dbmeter"){
        set = '<div class="field"><label for="field_' + parent + '{{:#getIndex()}}"' + cls + '>' + htmlEncode(data.title) + '</label><svg viewBox="0 0 100 10" style="width:100%; height:30px;" preserveAspectRatio="none"><defs><clipPath id="clip"><rect x="0" y="0" width="0" height="10" data-link="width{dbToPerc:'+link+'}"/></clipPath><linearGradient id="grad1"><stop offset="0%" stop-color="rgb(0,200,0)"/><stop offset="65%" stop-color="rgb(0,200,0)"/><stop offset="80%" stop-color="rgb(255,255,0)"/><stop offset="100%" stop-color="rgb(255,0,0)"/></linearGradient></defs><rect x="0" y="0" width="100" height="10" fill="url(#grad1)" clip-path="url(#clip)"/><line x1="75" x2="75" y1="0" y2="10" stroke="rgb(0,0,0)" stroke-width="0.25"/><text x="72.5" y="3" font-size="3px">0</text><line x1="75" x2="75" y1="0" y2="10" stroke="rgb(0,0,0)" stroke-width="0.25"/><text x="72.5" y="3" font-size="3px">0</text><line x1="99.75" x2="99.75" y1="0" y2="10" stroke="rgb(0,0,0)" stroke-width="0.25"/><text x="94" y="3" font-size="3px">+10</text></svg></div>'
      } else {
        set = '<div class="field"><label for="field_' + parent + '{{:#getIndex()}}"' + cls + '>' + htmlEncode(data.title) + '</label><input placeholder="' + placeholder + '" id="field_' + parent + '{{:#getIndex()}}" title="' + htmlEncode(data.desc) + '" type="number" step="any" data-link="{numToStr:' + link + ':strToFloat}"' + cls + ' /></div>';
      }
      break;
    // format a boolean
    case 'boolean':
      // booleans are rendered as checkboxes
      set = '<div class="field"><fieldset><legend>'+htmlEncode(data.title)+'</legend><label for="field_'+parent+'{{:#getIndex()}}"'+cls+'><input id="field_'+parent+'{{:#getIndex()}}" title="'+htmlEncode(data.desc)+'" type="checkbox" data-link="'+link+'"'+cls+' /><span>Yes</span></label></fieldset></div>';
      break;
    // don't render a null type
    case 'null':
      break;
    // output a silent debug message if a type has no handler
    default:
      console.log('unhandled type: ' + data.type);
  }
  return set;
};