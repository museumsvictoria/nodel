$(document).ready(function() {
    $.getJSON('REST?schema', function(obj) {
        obj['title'] = 'Root';
        obj['description'] = 'The schema root.';
        updateObjectSchema('root', obj, $('#root'), 'root');
    });
    $('body').on('click touchstart', '.block h6', function() {
        // show or hide the contents
        $(this).next('div').slideToggle('slow');
        return false;
    });
});

function updateObjectSchema(name, obj, div, id, isService, isArray) {
    console.info('updating object schema ' + id);

    var txt = '<div class="block">';
    if (name)
        txt += '<h6>' + name + '</h6>';
    txt += '<div>';

    if (!isArray) {
        if (obj.description)
            txt += '<span class="block"><em>Desc:</em>' + obj.description + '</span>';
        if (obj.title && obj.title != "undefined")
            txt += '<span style="color:red;" class="block"><em>Title:</em>' + obj.title + '</span>';
    }

    if (isService)
        txt += '<span class="block"><em>Returns:</em>' + obj.type + '</span>';
    else
        txt += '<span class="block"><em>Type:</em>' + obj.type + '</span>';

    // txt += '<br/><i>Required:</i> ' + obj.required;

    if (obj.format)
        txt += '<span class="block"><em>Format:</em>' + obj.format + '</span>';

    if (obj.enum) {
        var choices;
        for ( var choice in obj.enum) {
            if (!choices)
                choices = obj.enum[choice];
            else
                choices += ', ' + obj.enum[choice];
        }

        txt += '<span class="block"><em>Choices:</em>' + choices + '</span>';
    }

    var gotItems = false;
    var itemsId;
    if (obj.items) {
        gotItems = true;
        itemsId = id + '_' + name + '_items';
        txt += '<span id="' + itemsId + '"></span>';
    }

    var sectionsId = id + '_' + name + '_sections';
    txt += '<span id="' + sectionsId + '"></span>'

    txt += '</div>';
    div.append(txt);

    if (gotItems) {
        updateObjectSchema('items', obj.items, $('#' + itemsId), itemsId, false, true);
    }

    var sectionsDiv = $('#' + sectionsId);

    if (obj.takes && !$.isEmptyObject(obj.takes)) {
        var takesId = id + '_' + name + '_takes';
        sectionsDiv.append('<span class="block">Takes:</span><span id="' + takesId + '"></span>');
        var takesDiv = $('#' + takesId);
        updateProperties(obj.takes, takesDiv, takesId);
    }

    if (obj.properties && !$.isEmptyObject(obj.properties)) {
        var propsId = id + '_' + name + '_props';
        sectionsDiv.append('<span class="block">Readable attributes:</span><span id="' + propsId + '"></span>');
        var propsDiv = $('#' + propsId);
        updateProperties(obj.properties, propsDiv, propsId);
    }

    if (obj.services && !$.isEmptyObject(obj.services)) {
        var servicesId = id + '_' + name + '_services';
        sectionsDiv.append('<span class="block">Services:</span><span id="' + servicesId + '"></span>');
        var servicesDiv = $('#' + servicesId);
        updateServices(obj.services, servicesDiv, servicesId);
    }

};

function updateProperties(properties, propsDiv, id) {
    console.info('updating properties ' + id);

    for ( var name in properties) {
        updateObjectSchema(name, properties[name], propsDiv, id);
    }
};

function updateServices(services, servicesDiv, id) {
    console.info('updating services ' + id);

    for ( var name in services) {
        updateObjectSchema(name, services[name], servicesDiv, id, true);
    }
};

function updateTakes(takes, takesDiv, id) {
    console.info('updating takes ' + id);

    for ( var name in takes) {
        updateObjectSchema(name, takes[name], takesDiv, id);
    }
};