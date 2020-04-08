local_event_DynamicList = LocalEvent({'group': 'Custom', 'schema': {'type': 'array', 'items': {
        'type': 'object', 'title': 'List', 'properties': {
          'key': {'type': 'string', 'title': 'Key'}, 'value': {'type': 'string', 'title': 'Label'}                                                  
        }}}})

local_event_ConfirmCode = LocalEvent({'group': 'Custom', 'schema': {'type': 'number'}})