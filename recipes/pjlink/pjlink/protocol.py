def read_until(f, term):
    data = []
    c = f.read(1)
    while c != term:
        data.append(c)
        c = f.read(1)
    return ''.join(data)

def to_binary(body, param, sep=' '):
    assert body.isupper()

    assert len(body) == 4
    assert len(param) <= 128

    return '%1' + body + sep + param + '\r'

def parse_response(f, data=''):
    if len(data) < 7:
        data += f.read(2 + 4 + 1 - len(data))

    header = data[0]
    assert header == '%'

    version = data[1]
    # only class 1 is currently defined
    assert version == '1'

    body = data[2:6]
    # commands are case-insensitive, but let's turn them upper case anyway
    # this will avoid the rest of our code from making this mistake
    # FIXME: AFAIR this takes the current locale into consideration, it shouldn't.
    body = body.upper()

    sep = data[6]
    assert sep == '='

    param = read_until(f, '\r')

    return (body, param)

ERRORS = {
    'ERR1': 'undefined command',
    'ERR2': 'out of parameter',
    'ERR3': 'unavailable time',
    'ERR4': 'projector failure',
}

def send_command(f, req_body, req_param):
    data = to_binary(req_body, req_param)
    f.write(data)
    f.flush()

    resp_body, resp_param = parse_response(f)
    assert resp_body == req_body

    if resp_param in ERRORS:
        return False, ERRORS[resp_param]
    return True, resp_param

