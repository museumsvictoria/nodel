import getpass
import string

def prompt(name, default=None):
    """
    Grab user input from command line.

    :param name: prompt text
    :param default: default value if no input provided.
    """

    prompt = name + (default and ' [%s]' % default or '')
    prompt += name.endswith('?') and ' ' or ': '
    while True:
        rv = raw_input(prompt)
        if rv:
            return rv
        if default is not None:
            return default

def prompt_pass(name, default=None):
    """
    Grabs hidden (password) input from command line.

    :param name: prompt text
    :param default: default value if no input provided.
    """

    prompt = name + (default and ' [%s]' % default or '')
    prompt += name.endswith('?') and ' ' or ': '
    while True:
        rv = getpass.getpass(prompt)
        if rv:
            return rv
        if default is not None:
            return default

def prompt_bool(name, default=False, yes_choices=None, no_choices=None):
    """
    Grabs user input from command line and converts to boolean
    value.

    :param name: prompt text
    :param default: default value if no input provided.
    :param yes_choices: default 'y', 'yes', '1', 'on', 'true', 't'
    :param no_choices: default 'n', 'no', '0', 'off', 'false', 'f'
    """

    yes_choices = yes_choices or ('y', 'yes', '1', 'on', 'true', 't')
    no_choices = no_choices or ('n', 'no', '0', 'off', 'false', 'f')

    while True:
        rv = prompt(name, default and yes_choices[0] or no_choices[0])
        if not rv:
            return default
        if rv.lower() in yes_choices:
            return True
        elif rv.lower() in no_choices:
            return False


def prompt_choices(
    name, choices,
    default=None,
    resolve=string.lower, no_choice=('none',)
):
    """
    Grabs user input from command line from set of provided choices.

    :param name: prompt text
    :param choices: list or tuple of available choices. Choices may be
                    single strings or (key, value) tuples.
    :param default: default value if no input provided.
    :param no_choice: acceptable list of strings for "null choice"
    """

    _choices = []
    options = []

    for choice in choices:
        if isinstance(choice, basestring):
            options.append(choice)
        else:
            options.append("%s [%s]" % (choice[1], choice[0]))
            choice = choice[0]
        _choices.append(choice)

    while True:
        rv = prompt(name + '? - (%s)' % ', '.join(options), default)
        if not rv:
            return default
        rv = resolve(rv)
        if rv in no_choice:
            return None
        if rv in _choices:
            return rv

def make_command(group, name, function):
    parser = group.add_parser(name, help=function.__doc__)
    parser.set_defaults(
        __func__=function,
    )
    return parser

def make_command_group(parent_group, name):
    parser = parent_group.add_parser(name)
    sub = parser.add_subparsers(title='subcommands')
    return sub

