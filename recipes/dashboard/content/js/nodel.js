// set global ajax timeout and disable cache
$.ajaxSetup({timeout: 30000, cache: false});

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
  // convert float to integer 
  floatToInt: function (value) {
    return parseFloat(value);
  }
});

// jsviews custom helper functions
$.views.helpers({
  // is a value defined
  isSet: function (value) {
    if (typeof value !== "undefined") return true;
    else return false;
  },
  getClass: function (alerted, watched, missing) {
    if (alerted && missing) return 'node error missing block';
    else if (missing) return 'node missing block';
    else if (alerted) return 'node error block';
    else if (watched) return 'node block';
    else return 'node hidden';
  },
  getGrpClass: function (nodes) {
    if(!nodes.length) return 'group empty';
    var alerted = missing = false;
    $.each(nodes, function() {
      if(this.alerted) alerted = true;
      if(this.missing) missing = true;
    });
    if (alerted && missing) return 'group error missing';
    else if (missing) return 'group missing';
    else if (alerted) return 'group error';
    else return 'group';
  },
  dummy: function(nodes, trigger) {
    return nodes;
  },
  getTitle: function(title, error) {
    if(error) return title + ' - ' + error;
    else return title;
  },
  // is a value contained within an array
  isIn: function () {
    var args = arguments;
    var valid = true;
    $.each($.map(this.data, function(e,i) {return i}), function(i,v){
      if($.inArray(v, args)<0) return valid = false;
    });
    return valid;
  }
});

// jsviews custom for renderer
$.views.tags({
  forsorttime: function(array) {
    var ret = "";
    array.sort(function(a, b) {
      var ha = hb = moment(0);
      $.each(a.alert, function(i, x) {
        var xt = moment(x.timestamp);
        if(xt > ha) {
          ha = xt;
        }
      });
      $.each(b.alert, function(i, x) {
        var xt = moment(x.timestamp);
        if(xt > hb) {
          hb = xt;
        }
      });
      if(a.alerted && !b.alerted) return -1;
      else if(!a.alerted && b.alerted) return 1;
      else if(a.missing && !b.missing) return -1;
      else if(!a.missing && b.missing) return 1;
      else return (ha < hb) ? -1 : (ha > hb) ? 1 : 0;
    });
    for (var index = 0; index < array.length; ++index) {
      // Render tag content, for this data item
      ret += this.tagCtx.render(array[index]);
    }
    return ret;
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
    if(node){
      // if a node name is found, retrieve node configuration
      $.getJSON('http://'+host+'/REST/nodes/'+node+'/', "",
        function(data) {
          // set page details
          document.title = 'Nodel - '+node;
          $('#nodename').text(node);
          if(data.desc) $('#nodename').after('<p>'+data.desc+'</p>');
          $('.logo img').attr('title', 'Nodel '+data.nodelVersion);
          if((typeof preinit !== "undefined") && ($.isFunction(preinit))){ preinit(data); }
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
  // get any extra parameters from the query string
  adv = getParameterByName('advanced');
  rld = getParameterByName('reload');
  tim = getParameterByName('timeout');
  // if a timeout parameter is specified, configure the default ajax handler
  if(tim) $.ajaxSetup({timeout: parseInt(tim)+1000, cache: false});
  else tim = 0;
  // define the colours and icons for the activity display
  opts = {"local": {"event": {"colour": "#ff6a00","icon":"&#x25b2;"},"action":{"colour":"#9bed00","icon":"&#x25ba;"}}, "remote": {"event": {"colour":"#ce0071","icon":"&#x25bc;"},"action":{"colour":"#00a08a","icon":"&#x25c4;"},"eventBinding": {"colour":"#ce0071","icon":"&#x2194;"},"actionBinding":{"colour":"#00a08a","icon":"&#x2194;"}}, "unbound": {"event": {"colour":"#ce0071","icon":"&#x25ac;"},"action":{"colour":"#00a08a","icon":"&#x25ac;"}}};
  // define the variables used for the console function
  execs = [];
  execindex = -1;
  // retrieve the node parameters schema
  $.getJSON('http://'+host+'/REST/nodes/'+node+'/params/schema',"",
    function(data) {
      // build the form template from the schema
      var get = buildFormSchema(data);
      if(get){
        // add a save button to the template
        var template = get;
        template += '<button class="save">Save</button>';
        // add the template to jsviews
        eval('$.templates({paramsTemplate: template})');
        // fill the template with data
        buildForm('params', 'params', [],'save', true);
      } else $('#params').replaceWith('<h5 class="pad">None</h5>');
    });
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
  // build the node activity tree
  createView();
  // update the activity list
  updateLogs();
  // check if reload has not been disabled (via query string) then begin checking if the page should be refreshed (node has restarted)
  if(!rld || rld!="false") checkReload();
  // set the target for the console form (if it exists)
  if($('#consoleform').get(0)) $('#consoleform').get(0).setAttribute('action', '/REST/nodes/'+node+'/exec');
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
      window.history.replaceState('','','http://'+host+'/nodes/'+node+'/?advanced=true');
      // if it is 'disabled', hide the advanced section
    } else {
      window.history.replaceState('','','http://'+host+'/nodes/'+node+'/');
      $('.advanced, .advancededitor').slideUp();
    }
  });
  // if advanced mode is specified on the query string, open it by default
  if(adv) $('#advancedmode').prop('checked', true).trigger('change');
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
  $('body').on('click touchstart', '.block h6', function() {
    // show or hide the contents
    $(this).siblings('div').slideToggle('slow');
    return false;
  });
  // watch for clicks on all group or object block titles
  $('body').on('click touchstart', '.group h6', function() {
    // show or hide the contents
    $(this).siblings('div.grpdata').slideToggle('slow');
    return false;
  });
  // watch for clicks on all section titles set to expand
  $('body').on('click touchstart', 'h4.expand', function() {
    // show the contents of every group or object
    $(this).parent().find('.block h6').next('div').slideDown('slow');
    // set the section to contract on next click
    $(this).removeClass('expand').addClass('contract');
    return false;
  });
  // watch for clicks on all section titles set to contract
  $('body').on('click touchstart', 'h4.contract', function() {
    // hide the contents of every group or object
    $(this).parent().find('.block h6').next('div').slideUp('slow');
    // set the section to expand on next click
    $(this).removeClass('contract').addClass('expand');
    return false;
  });
};

// function to load the code editor
var loadEditor = function() {
  // ensure the editor has not been loaded already and the form exists
  if((typeof editor === "undefined") && ($('#field_script').length)){
    // load the codemirror library, setting to 'python' syntax highlighting mode
    editor = CodeMirror.fromTextArea(document.getElementById("field_script"), {
      mode: {name: "python",
        version: 2,
        singleLineStringErrors: false},
      lineNumbers: true,
      indentUnit: 2,
      tabMode: "shift",
      matchBrackets: true
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
    if(data.length == 0) $('#nodelist ul').append('<li>no results</li>');
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
  $('#nodelist').on('click touchstart', '#listmore', function() {
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
      if(data.length == 0) $('#nodelist ul').append('<li>no results</li>');
      // if there is an error retrieving the list of nodes (and it was not because it was aborted), display an error message
    }).error(function(req){
      if(req.statusText!="abort"){
        $('#nodelist ul').empty();
        $('#nodelist ul').append('<li>error retrieving results</li>');
      }
    });
  });
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
var dialog = function(message, type){
  // default to 'info' type
  type = type || "info";
  // clear any timer that was set to close the dialog
  clearTimeout($('.dialog').stop().data('timer'));
  // set the message
  $('.dialog').html('<div class="'+type+'">'+message+'</div>');
  // show the dialog box
  $('.dialog').slideDown(function() {
    var elem = $(this);
    // set a timer to close the dialog in 3 seconds
    $.data(this, 'timer', setTimeout(function() { elem.slideUp(); }, 3000));
  });
  return false;
};

// function to start polling the nodehost to see if the UI should reload (the node has restarted)
var checkReload = function(){
  // set the filter to get the current timestamp if it is not known
  if(typeof $('body').data('timestamp') === "undefined") params = {timeout:tim};
  // otherwise, set the filter to the current timestamp
  else params = {timeout:tim, timestamp:$('body').data('timestamp')};
  // call the function
  $.getJSON('http://'+host+'/REST/nodes/'+node+'/hasRestarted', params,
    function(data) {
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

var fetchHost = function(value){
  var srchost = false;
  $.each(viewData.groups, function() {
    $.each(this.nodes, function() {
      var node = this.node;
      if(value == node) {
        var a = document.createElement('a');
        a.href = this.address;
        return srchost = a.host;
      }
    });
  });
  return srchost;
}

var viewData = {"groups":[{"title":"Initialising","nodes":[{"title":"Initialising","alert":[],"watch":[]}]}]};

function changeHandler(ev, eventArgs) {
  if($.inArray(eventArgs.path, ['alerted','missing']) > -1) {
    var path = ev.data.observeAll.path();
    var reg1 = path.match(/groups\[(\d+)\]/m);
    var reg2 = path.match(/nodes\[(\d+)\]/m);
    var index1 = parseInt(reg1[1]);
    var index2 = parseInt(reg2[1]);
    var x = typeof viewData.groups[index1].trigger !== 'undefined' ? viewData.groups[index1].trigger + 1 : 0;
    $.observable(viewData.groups[index1]).setProperty('trigger',x);
    var y = typeof viewData.groups[index1].nodes[index2].trigger !== 'undefined' ? viewData.groups[index1].nodes[index2].trigger + 1 : 0;
    $.observable(viewData.groups[index1].nodes[index2]).setProperty('trigger',y);
  }
};
$.observable(viewData).observeAll(changeHandler);

var updateNodes = function(){
  $.getJSON('http://'+host+'/REST/nodeURLs').done(
    function(data) {
      var groups = $('#activity').data('filter');
      var list = [];
      $.each(groups, function() {
        var re = new RegExp("^"+this.filter,"g");
        var nodes = []; 
        var title = this.title;
        $.each(data, function() {
          if(this.node.match(re)){
            this.title = this.node;
            this.node = this.node.replace(/[^a-zA-Z0-9]+/g,'');
            this.alert = $.jStorage.get(this.node+'alert', []);
            this.watch = $.jStorage.get(this.node+'watch', []);
            this.alerted = this.alert.length ? true : false;
            this.watched = this.watch.length ? true : false;
            this.missing = false;
            nodes.push(this);
          }
        });
        if($.isEmptyObject($.grep(viewData.groups, function(n) { return n.title == title; }))) {
          list.push({'title':this.title,'nodes':nodes});
        } else {
          $.each(viewData.groups, function(i,x) {
            if(x.title == title) {
              var missing = false;
              $.each(viewData.groups[i].nodes, function(y,z) {
                if($.isEmptyObject($.grep(nodes, function(n) { return n.title == z.title; }))) {
                  console.log('node dissapeared');
                  $.observable(viewData.groups[i].nodes[y]).setProperty('missing',true);
                  nodes.push(viewData.groups[i].nodes[y]);
                }
                if(z.missing) {
                  $.each(nodes, function(s,t) {
                    if(z.title == t.title) {
                      nodes[s].missing = true;
                      nodes[s].error = z.error;
                    }
                  });
                }
              });
              $.observable(viewData.groups[i]).setProperty('nodes',nodes);
            }
          });
        }
      });
      if(!$.isEmptyObject(list)) {
        $.observable(viewData).setProperty("groups",list);
      }
    }).always(function() {
      $('#activity').data('nodelistrefresh', setTimeout(function() { updateNodes(); }, 60000*5));
    });
}

var createView = function(){
  viewTemplate = '{^{for groups}}<div class="group" data-link="class{:~getGrpClass(nodes, trigger)}"><span><svg width="18" height="18"><circle cx="9" cy="9" r="9"></circle></svg></span><h6 data-link="text{:title} id{:\'grp_\'+title}"></h6><div class="grpdata">{^{forsorttime ~dummy(nodes, trigger)}}<div class="block" data-link="class{:~getClass(alerted, watched, missing)}"><span><svg width="18" height="18"><circle cx="9" cy="9" r="9"></circle></svg></span><a class="nodelink" target="_blank" href="#" data-link="href{:nodelink}">&#x2197;</a><h6 data-link="text{:~getTitle(title, error)} id{:node}"></h6> {^{for alert}} {^{if #getIndex()==0}}<div class="info"><button class="clearallinfo">Clear All</button></div>{{/if}} <div class="info"> <div class="alertblock"> <button class="clearinfo">Clear</button> <p><strong>Timestamp</strong>: <span data-link="timestamp"></span></p> <p><strong>Source</strong>: <span data-link="source"></span></p> <p><strong>Type</strong>: <span data-link="type"></span></p> <p><strong>Alias</strong>: <span data-link="alias"></span></p> <p><strong>Message</strong>: <span data-link="arg"></span></p> </div> </div> {{/for}} {^{for watch}} <div class="info"> <div class="watchblock"> <p><strong data-link="alias"></strong>: <span data-link="arg"></span></p> </div> </div> {{/for}} </div> {{/forsorttime}}</div></div>{{/for}}';
  $.templates({viewTemplate: viewTemplate})
  $.link.viewTemplate("#activity", viewData);
  $.getJSON('http://'+host+'/REST/nodes/'+node+'/params',"",
    function(data) {
      $('#activity').data('watchlist', typeof data.watch!=='undefined' ? $.map(data.watch, function(data) { return data.alias; }) : []);
      $('#activity').data('alertlist', typeof data.alert!=='undefined' ? $.map(data.alert, function(data) { return data.alias; }) : []);
      $('#activity').data('filter', typeof data.filter!=='undefined' ? data.filter : []);
      updateNodes();
    });
  $('body').on('click touchstart', 'button.clearinfo', function() {
    var node = $(this).closest('.block').children('h6').attr('id');
    var view = $.view(this);
    while(view.data==view.parent.data) view=view.parent;
    $.observable(view.parent.data).remove(view.index,1);
    $.jStorage.set(node+'alert', $.observable(view.parent.data).data());
    if($.observable(view.parent.data).data().length<1) {
      $.observable(view.parent.parent.data).setProperty('alerted',false);
    }
    return false;
  });
  $('body').on('click touchstart', 'button.clearallinfo', function() {
    var node = $(this).closest('.block').children('h6').attr('id');
    var view = $.view(this);
    while(view.data==view.parent.data) view=view.parent;
    $.observable(view.parent.data).remove(0,view.parent.data.length);
    $.jStorage.set(node+'alert', []);
    $.observable(view.parent.parent.data).setProperty('alerted',false);
    return false;
  });
};

var updateLogs = function(){
  var url;
  var node;
  var nodeList = $("#activity .node > h6[id]").map(function() { return this.id; }).get();
  var retry = ((5000/nodeList.length) > 100) ? 5000/nodeList.length : 100;
  if(typeof $('#activity').data('nodeindex') === "undefined") {
    $('#activity').data('nodeindex', 0);
    node = nodeList[0];
  } else {
    var index = $('#activity').data('nodeindex') + 1;
    $('#activity').data('nodeindex', (index < nodeList.length) ? index : 0);
    node = nodeList[$('#activity').data('nodeindex')];
  }
  if(typeof node==="undefined") {
    $('#activity').data('timer', setTimeout(function() { updateLogs(); }, 1000));
    return true
  }
  console.log(node);
  var nodehost = fetchHost(node);
  if(nodehost){
    var ele = $('#'+node);
    if(typeof $(ele).data('seq') === "undefined") {
      if($.jStorage.get(node)) {
        url = 'http://'+nodehost+'/REST/nodes/'+node+'/logs?from='+$.jStorage.get(node)+'&max=999';
        $(ele).data('seq', $.jStorage.get(node));
      } else url = 'http://'+nodehost+'/REST/nodes/'+node+'/logs?from=-1&max=100';
    } else url = 'http://'+nodehost+'/REST/nodes/'+node+'/logs?from='+$(ele).data('seq')+'&max=999';
    var view = $.view(ele);
    $.observable(view.data).setProperty('nodelink','http://'+nodehost+'/nodes/'+node+'/');
    $.getJSON(url, {timeout:tim}, function(data) {
      $.each(data.reverse(), function(key, value) {
        if($.inArray(value.alias,$('#activity').data('alertlist'))>=0) {
          var obj = {'timestamp':value.timestamp,'source':value.source,'type':value.type,'alias':value.alias,'arg':value.arg};
          $.observable(view.data.alert).insert(0, obj);
          $.observable(view.data).setProperty('alerted',true);
          ele.siblings('div').show();
          ele.parents('div.block:hidden').each(function(){
            $(this).slideDown('slow');
            $(this).siblings('div').slideDown('slow');
          });
        }
        if($.inArray(value.alias,$('#activity').data('watchlist'))>=0) {
          var obj = {'timestamp':value.timestamp,'source':value.source,'type':value.type,'alias':value.alias,'arg':value.arg};
          $.each($.observable(view.data.watch).data(), function(i,x){
            if(x.alias == value.alias) {
              $.observable(view.data.watch).remove(i,1);
              return false;
            }
          });
          $.observable(view.data.watch).insert(0, obj);
          if(ele.parent('div').children('div').filter(':visible').length) ele.siblings().show();
          $.observable(view.data).setProperty('watched',true);
        }
        $(ele).data('seq', value.seq+1);
      });
      $.observable(view.data).setProperty('missing',false);
      $.observable(view.data).setProperty('error','');
      $.jStorage.set(node, $(ele).data('seq'));
      $.jStorage.set(node+'alert', $.observable(view.data.alert).data());
      $.jStorage.set(node+'watch', $.observable(view.data.watch).data());
    }).fail(function(j,t,e) {
       $.observable(view.data).setProperty('missing',true);
       if(e) var status = j.status + ':' + e;
       else var status = j.status + ':' + t;
       $.observable(view.data).setProperty('error', status);
    }).always(function() {
      $('#activity').data('timer', setTimeout(function() { updateLogs(); }, retry));
    });
  } else $('#activity').data('timer', setTimeout(function() { updateLogs(); }, retry));
};

// function to update the console
var updateConsoleForm = function(){
  var url;
  // if the last sequence number is not set, set the filter to retrieve the last 100 entries
  if(typeof $('#console').data('seq') === "undefined") url = 'http://'+host+'/REST/nodes/'+node+'/console?from=-1&max=100';
  // otherwise, set the filter to retrieve only the next 10 changes
  else url = 'http://'+host+'/REST/nodes/'+node+'/console?from='+$('#console').data('seq')+'&max=10';
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
      $('#console').append('<div class="'+value.console+'">'+timestamp.format('MM-DD HH:mm:ss')+'&nbsp-&nbsp'+value.comment+'</div>');
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
    $.getJSON('http://'+host+'/REST/nodes/'+node+'/'+(path.length!==0?(path.join('/')+'/'+name):name),"",
      function(data) {
        // if there is no data for this form, set the data to an empty object
        if($.isEmptyObject(data)) data = {};
        // set the form action (if it exsits)
        if($('#'+formname).get(0)) $('#'+formname).get(0).setAttribute('action', '/REST/nodes/'+node+'/'+(path.length!==0?(path.join('/')+'/'+name):name)+'/'+action);
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
    if($('#'+formname).get(0)) $('#'+formname).get(0).setAttribute('action', '/REST/nodes/'+node+'/'+(path.length!==0?(path.join('/')+'/'+name):name)+'/'+action);
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
  // handle submit validation
  $('#'+name).on('click touchstart', '.'+action, function() {
    var proceed = true;
    // remove any previous validation error highlighting
    $('.highlight').each(function(){
      $(this).removeClass('highlight');
    });
    // check if an input is required
    $('#'+name+' input.required, #'+name+' select.required').each(function(){
      // check if it is a child of a required object
      if(($(this).closest('div.object').length > 0) && (!$(this).closest('div.object').hasClass('required'))) {
        // if it has a value
        if($(this).val()) {
          // if another field in the required object does not also have a value, highlight it and prevent the form from being submitted
          $(this).closest('div.object').children('div.field').children('input.required, select.required').each(function(){
            if(!$(this).val()) {
              $(this).addClass('highlight').focus();
              $(this).parents('div:hidden').each(function(){
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
        if(!$(this).val()) {
          $(this).addClass('highlight').focus();
          $(this).parents('div:hidden').each(function(){
            $(this).slideDown('slow');
          });
          dialog('Required value missing', 'error');
          proceed = false;
          return false;
        }
      };
    });
    // if a warning is required before submit, display the warning and submit only if 'ok' is clicked
    if($('#'+name).data('caution') && proceed){
      if(!confirm('Caution: ' + $('#'+name).data('caution'))) proceed = false;
      dialog("Cancelled", "error");
    }
    // if validation and warning are ok, submit the form
    if(proceed){
      // make a copy of the data to be sent. Stringify removes extra jsviews data
      var tosend = JSON.parse(JSON.stringify(data));
      // remove empty values
      removeNulls(tosend);
      // post the data as JSON
      $.postJSON($('#'+name).get(0).getAttribute('action'), JSON.stringify(tosend), function(data){
        // if there is no error, print a success message
        dialog(action + " - Success");
      }).error(function(e, s) {
        // otherwise, print the error details
        errtxt = s;
        if(e.responseText) errtxt = s + "\n" + e.responseText;
        dialog("exec - Error:\n"+errtxt, "error");
      });
    }
    return false;
  });
  // handle array 'add' events
  $('#'+name).on('click touchstart', '.add', function() {
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
  $('#'+name).on('click touchstart', '.delete', function() {
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
  $('#'+name).on('click touchstart', '.up', function() {
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
  $('#'+name).on('click touchstart', '.down', function() {
    // get the context of the element
    var view = $.view(this);
    // ensure we are at the root element
    while(view.data==view.parent.data) view=view.parent;
    // move the item
    $.observable(view.parent.data).move(view.index,view.index+1,1);
    $('#'+name).trigger('updated');
    return false;
  });
  // handle when cron fields are created
  $('#'+name).on('ready updated', function() {
    $(this).find('.addobj').each(function(){
      var v = $.view(this);
      $.observable(v.data).setProperty(this.id, {});
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
  $('#'+name).on('keyup', 'input.node', function(e) {
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
  });
  // watch for text being entered into action and event type fields
  $('#'+name).on('keyup', 'input.event, input.action', function(e) {
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
            reqs.push($.getJSON('http://'+host+'/REST/nodes/'+lnode+'/'+type,"", function(data) {
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
  });
  // handle file browse button click events
  $('#'+name).on('click touchstart','.browse', function(e) {
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
  // handle when an item is selected from the autocomplete popup
  $('#'+name).on('click touchstart', 'div.autocomplete ul li', function() {
    // set the field value
    $(this).parents('div.autocomplete').siblings('input#'+$(this).parents('div.autocomplete').data('target')).val($(this).text()).trigger('change');
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
  if(parent) group = parent;
  // if there is a parent, set the new parent to be the current parent plus the current field key
  if(parent) parent = parent + '.' + key;
  // otherwise, set the parent to the field key
  else parent = key;
  // collect and format extra classes required for the element
  var xtr = [];
  if(data.required) xtr.push('required');
  if(data.format) xtr.push(data.format);
  if(xtr.length!==0) cls = ' class="'+xtr.join(' ')+'"';
  // format according to the field type
  switch(data.type) {
    // format an object
    case 'object':
      // render each object item
      $.each(data.properties, function(lkey, lvalue) {
        get=buildFormSchema(lvalue, lkey, parent);
        // if the item rendered is an object, append a conditionally displayed 'add' div for jsviews to initialise
        if(lvalue.type=="object") {
          get='{^{if ~isSet('+lkey+')}}'+get+'{{else}}<div class="addobj" id="'+lkey+'"></div>{{/if}}';
        }
        // add the item to the current template string
        set+=get;
      });
      // if this isn't a root object, add a block element wrapper
      if(key){
        xtr.push('object');
        set = '<div class="block"><h6>'+data.title+'</h6><div class="'+xtr.join(' ')+'">'+set+'</div></div>';
      }
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
        if(data.minItems) set= '{^{for '+parent+'}}<span>'+set+'{^{if #parent.data.length > '+data.minItems+'}}<input type="button" class="delete" id="'+parent.replace(/\./g, '_')+'{{:#index}}" value="Delete" />{{/if}}{^{if #index > 0}}<input type="button" class="up" id="up_'+parent.replace(/\./g, '_')+'{{:#index}}" value="&#x25b2;" />{{/if}}{^{if #index < #parent.data.length-1}}<input type="button" class="down" id="down_'+parent.replace(/\./g, '_')+'{{:#index}}" value="&#x25bc;" /><hr/>{{/if}}</span>{{/for}}';
        else set= '{^{for '+parent+'}}<span>'+set+'<input type="button" class="delete" id="'+parent.replace(/\./g, '_')+'{{:#index}}" value="Delete" />{^{if #index > 0}}<input type="button" class="up" id="up_'+parent.replace(/\./g, '_')+'{{:#index}}" value="&#x25b2;" />{{/if}}{^{if #index < #parent.data.length-1}}<input type="button" class="down" id="down_'+parent.replace(/\./g, '_')+'{{:#index}}" value="&#x25bc;" /><hr/>{{/if}}</span>{{/for}}';
        // create an 'add' button for each of the object types
        var buttons = '';
        $.each(opt, function(e, i) {
          buttons+= '<input type="button" class="add" id="'+parent.replace(/\./g, '_')+'" data-seed="'+i.join(',')+'" value="Add '+e+'" />';
        });
        // add conditionally displayed add buttons (accounting for a maximm number of items)
        if(data.maxItems) set+= '{^{if '+parent+'}}{^{if '+parent+'.length < '+data.maxItems+'}}{^{if '+parent+'.length != 0}}<hr/>{{/if}}'+buttons+'{{/if}}{{/if}}';
        else set+= '{^{if '+parent+'}}{^{if '+parent+'.length > 0}}<hr/>{{/if}}'+buttons+'{{/if}}';
        console.log(set);
        // if this isn't a root object, add a block element wrapper
        if(key) set = '<div class="block"><h6>'+data.title+'</h6><div>'+set+'</div></div>';
        // if the array can contain only one object
      } else if(data.items.type == 'object') {
        // render the object
        get=buildFormSchema(data.items, null, null);
        // add conditionally displayed delete, up and down buttons (accounting for a minimum number of items)
        if(data.minItems) set= '{^{for '+parent+'}}<span>'+get+'{^{if #parent.data.length > '+data.minItems+'}}<input type="button" class="delete" id="'+parent.replace(/\./g, '_')+'{{:#index}}" value="Delete" />{{/if}}{^{if #index > 0}}<input type="button" class="up" id="up_'+parent.replace(/\./g, '_')+'{{:#index}}" value="&#x25b2;" />{{/if}}{^{if #index < #parent.data.length-1}}<input type="button" class="down" id="down_'+parent.replace(/\./g, '_')+'{{:#index}}" value="&#x25bc;" /><hr/>{{/if}}</span>{{/for}}';
        else set= '{^{for '+parent+'}}<span>'+get+'<input type="button" class="delete" id="'+parent.replace(/\./g, '_')+'{{:#index}}" value="Delete" />{^{if #index > 0}}<input type="button" class="up" id="up_'+parent.replace(/\./g, '_')+'{{:#index}}" value="&#x25b2;" />{{/if}}{^{if #index < #parent.data.length-1}}<input type="button" class="down" id="down_'+parent.replace(/\./g, '_')+'{{:#index}}" value="&#x25bc;" /><hr/>{{/if}}</span>{{/for}}';
        // add conditionally displayed add button (accounting for a maximm number of items)
        if(data.maxItems) set+= '{^{if '+parent+'}}{^{if '+parent+'.length < '+data.maxItems+'}}{^{if '+parent+'.length != 0}}<hr/>{{/if}}<input type="button" class="add" id="'+parent.replace(/\./g, '_')+'" value="Add" />{{/if}}{{/if}}';
        else set+= '{^{if '+parent+'}}{^{if '+parent+'.length > 0}}<hr/>{{/if}}{{/if}}<input type="button" class="add" id="'+parent.replace(/\./g, '_')+'" value="Add" />';
        // if this isn't a root object, add a block element wrapper
        if(key) set = '<div class="block"><h6>'+data.title+'</h6><div>'+set+'</div></div>';
      }
      break;
    // format a string
    case 'string':
      // if the string has a fixed set of options, render as a select list
      if(data['enum']){
        set = '<div class="field"><label for="field_'+parent+'{{:#index}}"'+cls+'>'+data.title+'</label><select id="field_'+parent.replace(/\./g, '_')+'{{:#index}}" title="'+data.description+'" data-link="'+parent+'"'+cls+'>';
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
            set = '<div class="field"><label for="field_'+parent.replace(/\./g, '_')+'{{:#index}}"'+cls+'>'+data.title+'</label><textarea id="field_'+parent.replace(/\./g, '_')+'{{:#index}}" title="'+data.description+'" data-link="'+parent+'"'+cls+'></textarea></div>';
            break;
          // node, action and event fields render with an additional group attribute
          case 'node':
          case 'action':
          case 'event':
            set = '<div class="field"><label for="field_'+parent.replace(/\./g, '_')+'{{:#index}}"'+cls+'>'+data.title+'</label><input id="field_'+parent.replace(/\./g, '_')+'{{:#index}}" title="'+data.description+'" type="text" data-group="'+group+'" data-link="'+parent+'"'+cls+' /></div>';
            break;
          // file fields have a hidden upload element, progress indicator and 'browse' button
          case 'file':
            set = '<div class="field"><label for="field_'+parent.replace(/\./g, '_')+'{{:#index}}"'+cls+'>'+data.title+'</label><input id="field_'+parent.replace(/\./g, '_')+'{{:#index}}" title="'+data.description+'" type="text" data-link="'+parent+'"'+cls+' disabled /><input title="browse" class="browse" type="button" value="Browse"/><input class="upload" type="file" /><progress value="0" max="100"></progress></div>';
            break;
          // basic renderer for any other elements
          default:
            set = '<div class="field"><label for="field_'+parent.replace(/\./g, '_')+'{{:#index}}"'+cls+'>'+data.title+'</label><input id="field_'+parent.replace(/\./g, '_')+'{{:#index}}" title="'+data.description+'" type="text" data-link="'+parent+'"'+cls+' /></div>';
            break;
        }
      }
      break;
    // format an integer
    case 'integer':
      // integers are forced to whole numbers
      set = '<div class="field"><label for="field_'+parent.replace(/\./g, '_')+'{{:#index}}"'+cls+'>'+data.title+'</label><input id="field_'+parent.replace(/\./g, '_')+'{{:#index}}" title="'+data.description+'" type="number" step="1" data-link="{numToStr:'+parent+':strToInt}"'+cls+' /></div>';
      break;
    // format a number
    case 'number':
      // numbers can be floating point
      set = '<div class="field"><label for="field_'+parent.replace(/\./g, '_')+'{{:#index}}"'+cls+'>'+data.title+'</label><input id="field_'+parent.replace(/\./g, '_')+'{{:#index}}" title="'+data.description+'" type="number" step="any" data-link="{numToStr:'+parent+':strToFloat}"'+cls+' /></div>';
      break;
    // format a boolean
    case 'boolean':
      // booleans are rendered as checkboxes
      set = '<div class="field"><fieldset><legend>'+data.title+'</legend><label for="field_'+parent.replace(/\./g, '_')+'{{:#index}}"'+cls+'><input id="field_'+parent.replace(/\./g, '_')+'{{:#index}}" title="'+data.description+'" type="checkbox" data-link="'+parent+'"'+cls+' /><span>Yes</span></label></fieldset></div>';
      break;
    // format a time
    case 'time':
      // time is rendered as html5 time type
      set = '<div class="field"><label for="field_'+parent.replace(/\./g, '_')+'{{:#index}}"'+cls+'>'+data.title+'</label><input id="field_'+parent.replace(/\./g, '_')+'{{:#index}}" title="'+data.description+'" type="time" data-link="'+parent+'"'+cls+' /></div>';
      break;
    // format a date
    case 'date':
      // date is rendered as html5 date type
      set = '<div class="field"><label for="field_'+parent.replace(/\./g, '_')+'{{:#index}}"'+cls+'>'+data.title+'</label><input id="field_'+parent.replace(/\./g, '_')+'{{:#index}}" title="'+data.description+'" type="date" data-link="'+parent+'"'+cls+' /></div>';
      break;
    // format a date-time
    case 'date-time':
      // date-time is rendered as html5 datetime type
      set = '<div class="field"><label for="field_'+parent.replace(/\./g, '_')+'{{:#index}}"'+cls+'>'+data.title+'</label><input id="field_'+parent.replace(/\./g, '_')+'{{:#index}}" title="'+data.description+'" type="datetime" data-link="'+parent+'"'+cls+' /></div>';
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