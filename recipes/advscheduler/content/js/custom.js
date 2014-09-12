// find exceptions

$(function() {
  $('#paramfilter').on('change', '#schedulefilter', function() {
    var date = $(this).val();
    if(date) {
      var filterdate = moment();
      filterdate.month(date.split('-')[1]);
      filterdate.date(date.split('-')[2]);
      var dow = filterdate.isoWeekday()-1;
      $('#params > .block > div > span').each(function( index ) {
        var _this = this;
        $(_this).attr("class","include");
        $(this).find('[id^=field_date]').each(function( index ) {
          if($(this).val()==date) {
            $(_this).attr("class","exclude");
            return false;
          }
        });
        var values = $(this).find('[id^=field_cron]').data('jqCron').getValues();
        var fdow = (values[0].length === 0) ? true : ($.inArray(dow, values[0]) != -1);
        var fdate = (values[1].length === 0) ? true : ($.inArray(parseInt(date.split('-')[2]), values[1]) != -1);
        var fmonth = (values[2].length === 0) ? true : ($.inArray(parseInt(date.split('-')[1]), values[2]) != -1);
        if(!(fdow && fdate && fmonth)) $(_this).attr("class","hide");
      });
    } else {
      $('#params > .block > div > span').each(function( index ) {
        $(this).removeClass();
      });
    }
  });
  $('#params').on('mouseenter', 'input.cron, span.jqCron', function() {
    clearTimeout($(this).closest('div.field').data('timeout'));
  });
  // handle when the mouse leaves cron fields
  $('#params').on('mouseleave', 'input.cron, span.jqCron', function() {
    // set a timeout to auto-update the date filter
    $(this).closest('div.field').data('timeout', setTimeout(function() {
      $('#schedulefilter').trigger('change');
    }, 3000));
   });
  $('#params').on('jqcronenabled', function() {
    $('#schedulefilter').trigger('change');
  });
});