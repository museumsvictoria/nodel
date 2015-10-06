// set global ajax timeout and disable cache
$.ajaxSetup({timeout: 30000, cache: false, contentType: "application/json; charset=utf-8"});;

// console fallback (for ie)
if (typeof console === "undefined" || typeof console.log === "undefined") {
  console = {};
  console.log = function(msg) {
    alert(msg);
  };
}

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
  $.get('/REST/toolkit', function(data) {
    $('#script_editor').val(data.script);
    if(typeof editor === "undefined"){
        editor = CodeMirror.fromTextArea(document.getElementById("script_editor"), {
          mode: {
            name: "python",
            version: 2,
            singleLineStringErrors: false
          },
          lineNumbers: true,
          indentUnit: 2,
          tabMode: "shift",
          matchBrackets: true
        });
    }
  });
};

// initialisation
$(function() {
  // get the host and port
  host = document.location.hostname + ':' + window.document.location.port;
  // get the script name
  path = window.location.pathname;

  document.title = 'Nodel toolkit';

  updateeditor();
});
