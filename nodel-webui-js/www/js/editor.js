// set global ajax timeout and disable cache
$.ajaxSetup({timeout: 30000, cache: false, contentType: "text/plain; charset=utf-8"});;

// console fallback (for ie)
if (typeof console === "undefined" || typeof console.log === "undefined") {
  console = {};
  console.log = function(msg) {
    alert(msg);
  };
}

CodeMirror.defineMode("multi", function(config) {
  return CodeMirror.multiplexingMode(
    CodeMirror.getMode(config, "text/xml"),
    {open: "<%", close: "%>", mode: CodeMirror.getMode(config, "text/x-python")},
    {open: "<script>", close: "</script>", mode: CodeMirror.getMode(config, "text/javascript")},
    {open: "<html>", close: "</html>", mode: CodeMirror.getMode(config, "text/html")},
    {open: "<style>", close: "</style>", mode: CodeMirror.getMode(config, "text/css")}
  );
});

// function to display a message in a dialog box
var dialog = function(message, type){
  // default to 'info' type
  type = type || "info";
  clearTimeout($('.dialog').stop().data('timer'));
  $('.dialog').html('<div class="'+type+'">'+message+'</div>');
  $('.dialog').slideDown(function() {
    var elem = $(this);
    $.data(this, 'timer', setTimeout(function() { elem.slideUp(); }, 3000));
  });
  return false;
};

var updateeditor = function() {
  $.get('http://' + host + path + '?_source', function(data) {
    $('#script_editor').val(data);
    if(typeof editor === "undefined"){
      editor = CodeMirror.fromTextArea(document.getElementById("script_editor"), {
        mode: "multi",
        selectionPointer: true,
        lineNumbers: true,
        indentUnit: 2,
        tabMode: "shift"
      });
      editor.on("change", function() {
        editor.save();
      });
    }
  });
};

var updatecompiled = function() {
  $.get('http://' + host + path + '?_compiled', function(data) {
    $('#script_compiled').val(data);
    if(typeof compiled === "undefined"){
      compiled = CodeMirror.fromTextArea(document.getElementById("script_compiled"), {
        mode: "python",
        selectionPointer: true,
        lineNumbers: true,
        indentUnit: 2,
        tabMode: "shift"
      });
    } else {
      compiled.setValue(data);
    }
  });
};

var updateoutput = function() {
  $('#script_output').attr('src', 'http://' + host + path);
};

// initialisation
$(function() {
  // get the host and port
  host = document.location.hostname + ':' + window.document.location.port;
  // get the script name
  path = window.location.pathname;
  node = decodeURIComponent(window.location.pathname.split( '/' )[2].replace(/\+/g, '%20'));
  file = decodeURIComponent(window.location.pathname.split( '/' )[3].replace(/\+/g, '%20'));

  document.title = 'Nodel - '+node;
  $('#filename').text(node + ' - ' + file);

  updateeditor();
  updatecompiled();
  updateoutput();

  $('#save').on('click', function(){
    console.log($('#script_editor').val());
    $.post('http://' + host + path + '?_write', $('#script_editor').val(), function(data, status) {
      if(status=="success"){
        dialog('Saved');
        updatecompiled();
        updateoutput();
      } else {
        dialog('Error','error');
      }
    });
    return false;
  });
  $('#script_form').on('keydown', function(event) {
    if (event.ctrlKey || event.metaKey) {
      if (String.fromCharCode(event.which).toLowerCase() == 's') {
        event.preventDefault();
        $(this).find('#save').click();
      }
    }
  });
});
