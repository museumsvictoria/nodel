from __future__ import with_statement

import argparse
from ConfigParser import (
    NoSectionError,
    SafeConfigParser as ConfigParser
)
from getpass import getpass
from os import path
from socket import socket
import sys

import appdirs

from pjlink import Projector
from pjlink import projector
from pjlink.cliutils import make_command

def cmd_power(p, state=None):
    if state is None:
        print p.get_power()
    else:
        p.set_power(state)

def cmd_input(p, source, number):
    if source is None:
        source, number = p.get_input()
        print source, number
    else:
        p.set_input(source, number)

def cmd_inputs(p):
    for source, number in p.get_inputs():
        print '%s-%s' % (source, number)

def cmd_mute_state(p):
    video, audio = p.get_mute()
    print 'video:', 'muted' if video else 'unmuted'
    print 'audio:', 'muted' if audio else 'unmuted'

def cmd_mute(p, what):
    if what is None:
        return cmd_mute_state(p)
    what = {
        'video': projector.MUTE_VIDEO,
        'audio': projector.MUTE_AUDIO,
        'all': projector.MUTE_VIDEO | projector.MUTE_AUDIO,
    }[what]
    p.set_mute(what, True)

def cmd_unmute(p, what):
    if what is None:
        return cmd_mute_state(p)
    what = {
        'video': projector.MUTE_VIDEO,
        'audio': projector.MUTE_AUDIO,
        'all': projector.MUTE_VIDEO | projector.MUTE_AUDIO,
    }[what]
    p.set_mute(what, False)

def cmd_info(p):
    info = [
        ('Name', p.get_name().encode('utf-8')),
        ('Manufacturer', p.get_manufacturer()),
        ('Product Name', p.get_product_name()),
        ('Other Info', p.get_other_info())
    ]
    for key, value in info:
        print '%s: %s' % (key, value)

def cmd_lamps(p):
    for i, (time, state) in enumerate(p.get_lamps(), 1):
        print 'Lamp %d: %s (%d hours)' % (
            i,
            'on' if state else 'off',
            time,
        )

def cmd_errors(p):
    for what, state in p.get_errors().items():
        print '%s: %s' % (what, state)

def make_parser():
    parser = argparse.ArgumentParser()
    parser.add_argument('-p', '--projector')

    sub = parser.add_subparsers(title='command')

    power = make_command(sub, 'power', cmd_power)
    power.add_argument('state', nargs='?', choices=('on', 'off'))

    inpt = make_command(sub, 'input', cmd_input)
    inpt.add_argument('source', nargs='?', choices=projector.SOURCE_TYPES)
    inpt.add_argument('number', nargs='?', choices='123456789', default='1')

    make_command(sub, 'inputs', cmd_inputs)

    mute = make_command(sub, 'mute', cmd_mute)
    mute.add_argument('what', nargs='?', choices=('video', 'audio', 'all'))

    unmute = make_command(sub, 'unmute', cmd_unmute)
    unmute.add_argument('what', nargs='?', choices=('video', 'audio', 'all'))

    make_command(sub, 'info', cmd_info)
    make_command(sub, 'lamps', cmd_lamps)
    make_command(sub, 'errors', cmd_errors)

    return parser

def resolve_projector(projector):
    password = None

    # host:port
    if projector is not None and ':' in projector:
        host, port = projector.rsplit(':', 1)
        port = int(port)

    # maybe defined in config
    else:
        appdir = appdirs.user_data_dir('pjlink')
        conf_file = path.join(appdir, 'pjlink.conf')

        try:
            config = ConfigParser({'port': '4352', 'password': ''})
            with open(conf_file, 'r') as f:
                config.readfp(f)

            section = projector
            if projector is None:
                section = 'default'

            host = config.get(section, 'host')
            port = config.getint(section, 'port')
            password = config.get(section, 'password') or None

        except (NoSectionError, IOError):
            if projector is None:
                raise KeyError('No default projector defined in %s' % conf_file)

            # no config file, or no projector defined for this host
            # thus, treat the projector as a hostname w/o port
            host = projector
            port = 4352

    return host, port, password

def main():
    parser = make_parser()
    args = parser.parse_args()

    kwargs = dict(args._get_kwargs())
    func = kwargs.pop('__func__')

    projector = kwargs.pop('projector')
    host, port, password = resolve_projector(projector)

    sock = socket()
    sock.connect((host, port))
    f = sock.makefile()

    if password:
        get_password = lambda: password
    else:
        get_password = getpass

    proj = Projector(f)
    rv = proj.authenticate(get_password)
    if rv is False:
        print>>sys.stderr, 'Incorrect password.'
        return

    func(proj, **kwargs)

if __name__ == '__main__':
    main()
