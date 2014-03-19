import hashlib

from pjlink import protocol

class ProjectorError(Exception):
    pass

reverse_dict = lambda d: dict(zip(d.values(), d.keys()))

POWER_STATES = {
    'off': '0',
    'on': '1',
    'cooling': '2',
    'warm-up': '3',
}
POWER_STATES_REV = reverse_dict(POWER_STATES)

SOURCE_TYPES = {
    'RGB': '1',
    'VIDEO': '2',
    'DIGITAL': '3',
    'STORAGE': '4',
    'NETWORK': '5',
}
SOURCE_TYPES_REV = reverse_dict(SOURCE_TYPES)

MUTE_VIDEO = 1
MUTE_AUDIO = 2
MUTE_STATES_REV = {
    '11': (True, False),
    '21': (False, True),
    '31': (True, True),
    '30': (False, False),
}

ERROR_STATES_REV = {
    '0': 'ok',
    '1': 'warning',
    '2': 'error',
}

class Projector(object):
    def __init__(self, f):
        self.f = f

    def authenticate(self, get_password):
        # I'm just implementing the authentication scheme designed in the
        # protocol. Don't take this as any kind of assurance that it's secure.

        data = self.f.read(9)
        assert data[:7] == 'PJLINK '
        security = data[7]
        if security == '0':
            return None
        data += self.f.read(9)
        assert security == '1'
        assert data[8] == ' '
        salt = data[9:17]
        assert data[17] == '\r'

        # we *must* send a command to complete the procedure,
        # so we just get the power state.

        password = get_password()
        pass_data = hashlib.md5(salt + password).hexdigest()
        cmd_data = protocol.to_binary('POWR', '?')
        self.f.write(pass_data + cmd_data)
        self.f.flush()

        # read the response, see if it's a failed auth
        data = self.f.read(7)
        if data == 'PJLINK ':
            # should be a failed auth if we get that
            data += self.f.read(5)
            assert data == 'PJLINK ERRA\r'
            # it definitely is
            return False

        # good auth, so we should get a reply to the command we sent
        body, param = protocol.parse_response(self.f, data)

        # make sure we got a sensible response back
        assert body == 'POWR'
        if param in protocol.ERRORS:
            raise ProjectorError(protocol.ERRORS[param])

        # but we don't care about the value if we did
        return True

    def get(self, body):
        success, response = protocol.send_command(self.f, body, '?')
        if not success:
            raise ProjectorError(response)
        return response

    def set(self, body, param):
        success, response = protocol.send_command(self.f, body, param)
        if not success:
            raise ProjectorError(response)
        assert response == 'OK'

    # Power

    def get_power(self):
        param = self.get('POWR')
        return POWER_STATES_REV[param]

    def set_power(self, status, force=False):
        if not force:
            assert status in ('off', 'on')
        self.set('POWR', POWER_STATES[status])

    # Input

    def get_input(self):
        param = self.get('INPT')
        source, number = param
        source = SOURCE_TYPES_REV[source]
        number = int(number)
        return (source, number)

    def set_input(self, source, number):
        source = SOURCE_TYPES[source]
        number = str(number)
        assert number in '123456789'
        self.set('INPT', source + number)

    # A/V mute

    def get_mute(self):
        param = self.get('AVMT')
        return MUTE_STATES_REV[param]

    def set_mute(self, what, state):
        assert what in (MUTE_VIDEO, MUTE_AUDIO, MUTE_VIDEO | MUTE_AUDIO)
        what = str(what)
        assert what in '123'
        state = '1' if state else '0'
        self.set('AVMT', what + state)

    # Errors

    def get_errors(self):
        param = self.get('ERST')
        errors = 'fan lamp temperature cover filter other'.split()
        assert len(param) == len(errors)
        ret = {}
        for key, value in zip(errors, param):
          ret[key] = value
        return ret

    # Lamps

    def get_lamps(self):
        param = self.get('LAMP')
        assert len(param) <= 65
        values = param.split(' ')
        assert len(values) <= 16 and len(values) % 2 == 0

        lamps = []
        for time, state in zip(values[::2], values[1::2]):
            time = int(time)
            state = bool(int(state))
            lamps.append((time, state))

        assert len(lamps) <= 8
        return lamps

    # Input list

    def get_inputs(self):
        param = self.get('INST')
        assert len(param) <= 95

        values = param.split(' ')
        assert len(values) <= 50

        inputs = []
        for value in values:
            source, number = value
            source = SOURCE_TYPES_REV[source]
            assert number in '123456789'
            number = int(number)
            inputs.append((source, number))

        return inputs

    # Projector info

    def get_name(self):
        param = self.get('NAME')
        assert len(param) <= 64
        return param.decode('utf-8')

    def get_manufacturer(self):
        param = self.get('INF1')
        assert len(param) <= 32
        # stupidly, this is not defined as utf-8 in the spec. :(
        return param

    def get_product_name(self):
        param = self.get('INF2')
        assert len(param) <= 32
        # stupidly, this is not defined as utf-8 in the spec. :(
        return param

    def get_other_info(self):
        param = self.get('INFO')
        assert len(param) <= 32
        return param

    # TODO: def get_class(self): self.get('CLSS')
    # once we know that class 2 is, and how to deal with it
