var offline = false;

$(document).ready(function() {
    if (offline) {
		$('#graphs').append('<h4 style="display: block;">Realtime counters</h4>(Unavailable - ' + 
	        'rendering tools require this browser session has Internet access)');
	}
    updateDiagnostics();
    updateBuildInfo();
    updateCounters();
    updateConsoleForm();
});

try {
  google.load("visualization", "1", {
    packages : [ "corechart" ]
  });

  google.setOnLoadCallback(function() {
      setInterval(function() {
        if (!paused) updateCounters();
      }, 10000);
  });
} catch (err) {
  offline = true;
}

var paused = false;
var tim = 0;

$(window).resize(function() {
  if(this.resizeTO) {
    paused = true;
    $('#graphs').empty();
    clearTimeout(this.resizeTO);
  }
  this.resizeTO = setTimeout(function() {
    $(this).trigger('resizeEnd');
  }, 500);
});

$(window).on('resizeEnd', function() {
  paused = false;
  updateCounters();
});

preparedCharts = {};

var now = moment()

function updateDiagnostics() {
    $.getJSON('/REST/diagnostics', function(diagnostics) {
        $('#agent').text(diagnostics.agent);
        $('#uptime').text(moment(diagnostics.startTime).from(now, true));
        $('#started').text(moment(diagnostics.startTime).format('D-MMM-YYYY HH:mm:ss'));
        $('#hostname').text(diagnostics.hostname);
        $('#httpAddresses').text(diagnostics.httpAddresses);
        $('#hostPath').text(diagnostics.hostPath);
        $('#nodesRoot').text(diagnostics.nodesRoot);
        $('#hostingRule').text(diagnostics.hostingRule);
    });
};

function updateBuildInfo() {
    $.getJSON('/build.json', function(buildInfo) {
        $('#build').text(buildInfo.version);
        $('#builtBy').text(buildInfo.host);
        $('#builtOn').text(moment(buildInfo.date).format('D-MMM-YYYY HH:mm:ss Z'));
		$("#commit").attr("href", "https://github.com/museumvictoria/nodel/commit/" + buildInfo.id);
    });
};

function updateCounters() {
    $.getJSON('/REST/diagnostics/measurements', function(rawMeasurements) {
        rawMeasurements.sort(function(x, y) {
            return x.name.localeCompare(y.name);
        });
        rawMeasurements.forEach(function(measurement, i, a) {
            try {
                drawChart(measurement.values, measurement.isRate, measurement.name);
            } catch (err) {
                throw ('draw chart failed related to ' + measurement.name + ': ' + err);
            }
        });
    });
}

// function to update the console
var updateConsoleForm = function(){
    var url;
    // if the last sequence number is not set, set the filter to retrieve the last 100 entries
    if(typeof $('#console').data('seq') === "undefined") url = '/REST/logs?from=-1&max=1000';
    // otherwise, set the filter to retrieve the next 100 changes
    else url = '/REST/logs?from='+$('#console').data('seq')+'&max=100';
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
            var message = (value.message ? value.message.replace(/(\r\n|\r|\n)/g, '\r\n    ') : '');
            $('#console').append($('<div class="'+value.level+'"></div>')
              .text(moment(value.timestamp).format('YY-MM-DD HH:mm:ss')+
                ' ['+value.thread+']'+' ['+value.tag+']'+' '+message));
            if(value.error) $('#console').append($('<div class="'+value.level+'"></div>')
              .text('    '+value.error.replace(/(\r\n|\r|\n)/g, '\r\n    ')));
            // set the current sequence number
            $('#console').data('seq', value.seq+1);
            // trim the list if it goes over 400 items
            if($("#console").children("div").length > 400) $("#console").children('div:lt(1)').remove();
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
        $.data(this, 'timer', setTimeout(function() { updateConsoleForm(); }, 4000));
    });
};

var chart;
var httpdOpRate;

function drawChart(values, isRate, name) {
    // console.log('Drawing ' + name);

    if (isRate)
        scale = 10;
    else
        scale = 1;

    chartData = new google.visualization.DataTable();

    chartData.addColumn('string', name);
    chartData.addColumn('number', name);

    values.forEach(function(element, index, array) {
        chartData.addRow([ '', element / scale ]);
    });

    var parts = name.split('.');
    var category, subcategory;
    if (parts.length == 2) {
        category = parts[0];
        subcategory = parts[1];
    } else {
        category = 'general';
        subcategory = name;
    }
    re = /[^a-zA-Z0-9]/g;
    categoryForDiv = category.replace(re, '_');
    subcategoryForDiv = subcategory.replace(re, '_')
    nameForDiv = name.replace(re, '_')

    var categoryDiv = $('#' + categoryForDiv);
    if (categoryDiv.length == 0) {
        $('#graphs').append('<div><h4 style="display: block;">' + category + '</h4><hr/><div id="' + categoryForDiv + 
		'" class="container"></div></div>');
        categoryDiv = $('#' + categoryForDiv);
    }

    var chartDiv = $('#' + nameForDiv);
    var chart;
    if (chartDiv.length == 0) {
        // console.log('Preparing new ' + nameForDiv);
        chartDiv = categoryDiv.append('<div id="' + nameForDiv + '" class="one-third column"></div>');

        chart = {
            chart : new google.visualization.LineChart(document.getElementById(nameForDiv)),
            options : {
                title : subcategory,
                vAxis : {
                    minValue : 0
                },
                legend : {
                    position : 'none'
                }
            }
        };
        preparedCharts[name] = chart;
    } else {
        chart = preparedCharts[name];
    }

    chart.chart.draw(chartData, chart.options);
};