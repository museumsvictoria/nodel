# How to establish SSH connection with Nodel

[Nodel](http://nodel.io) supports SSH (Secure Shell) connection to devices.

With using SSH connection, you can easily communicate with devices and send command to them.

## API

API usage

```
ssh_con = SSH(
        mode='shell',
        dest='192.168.0.10:22',
        knownHosts=None,
        username='USERNAME',
        password='PASSWORD',
        reverseForwardingParams=None,
        connected=callback_on_connected,
        executed=callback_on_executed,
        disconnected=callback_on_disconnected,
        timeout=callback_on_command_timeout,
        shellConsoleOut=callback_on_shell_consoleout,
        sendDelimiters='\n',
        receiveDelimiters='\r\n',
        enableEcho=True
)
```

## Parameters

The parameters in details.

- **mode**: 'shell' or 'exec'. 
  [what is the difference?](https://stackoverflow.com/questions/6770206/what-is-the-difference-between-the-shell-channel-and-the-exec-channel-in-jsc) 
  In most cases (especially for automation), using 'exec' mode is much simpler than 'shell' mode, because you don't have to care about console out from the server.
  In 'exec' mode, you can make sure that you receive an entire response message when sending a command.
  In contrast, in 'shell' mode a single response message may be split in many parts. Please remember that 'shell' mode is for interaction with user on screen.
  *(Note: 'exec' mode may not be supported depending on SSH server implementation of devices)*
  
- **dest**: IP address with port

- **knownHosts**: KnownHosts for the server. NOT IN USE.

- **username**: username to log in the server

- **password**: password to log in the server

- **reverseForwardingParams**: parameters to establish reverse SSH port forwarding. 
  [What is reverse SSH port forwarding?](https://www.ssh.com/ssh/tunneling/example) 
    ```
    reverse_forwarding_params = {
      'bind_address': None,
      'rport': 80,
      'host': 'localhost',
      'lport': 80
    }
    ```

- **connected**: callback to be called when SSH connection successfully established with the server.
    ```
    def ssh_connected():
        console.info('[ssh_connected]')
    ```

- **executed**: callback to be called when a command successfully sent to the server. 
  To check response from server, please use shellConsoleOut callback.
    ```
    def ssh_executed(cmd):
        console.info('[cmd] %s' % cmd)
    ```

- **disconnected**: callback to be called when SSH connection with the server closed.
    ```
    def ssh_disconnected():
        console.info('[ssh_disconnected]')
    ```

- **timeout**: callback to be called when the command timeout.
    ```
    def cmd_timeout():
        console.info('[cmd_timeout]')  
    ```

- **shellConsoleOut**: callback to be called when the client receives message from the server.
    ```
    def ssh_shellOut(message):
        console.info(message)
    ```
  
- **sendDelimiters**: In 'shell' mode (only), this parameter will be appended to command string. Default value is `\n`.

- **receiveDelimiters**: In 'shell' mode (only), with setting this parameter you can split a received message. 
  When Nodel finds that the message from input stream ends with the delimiter, it will call shellConsoleOut callback as well as response handler (if set).

- **enableEcho**: flag to enable / disable ECHO which is sent by the server. 
  [Technical specification](https://tools.ietf.org/html/rfc4254) 
  *(Note: this parameter may not work depending on SSH server implementation of devices)*

## Example

In 'exec' mode, send a command to the server and receive response.

```
def ssh_connected():
  console.info('[ssh_connected]')
  
def ssh_disconnected():
  console.info('[ssh_disconnected]')  

def ssh_executed(cmd):
  console.info('[cmd] %s' % cmd)

def ssh_shellOut(message):
  console.info('[shellout] %s' % message)

ssh_con = SSH(
        mode='exec',
        dest='192.168.0.10:22',
        username='USERNAME',
        password='PASSWORD',
        connected=ssh_connected,
        executed=ssh_executed,
        disconnected=ssh_disconnected,
        shellConsoleOut=ssh_shellOut
)

ssh_con.send('ps -ef')

or

ssh_con.send('ps -ef', lambda response: console.info(response))
```

In 'shell' mode, send a command to the server and receive response.

```
def ssh_connected():
  console.info('[ssh_connected]')
  
def ssh_disconnected():
  console.info('[ssh_disconnected]')  

def ssh_executed(cmd):
  console.info('[cmd] %s' % cmd)

def ssh_shellOut(message):
  console.info('[shellout] %s' % message)
  
ssh_con = SSH(
        mode='shell',
        dest='192.168.0.10:22',
        username='USERNAME',
        password='PASSWORD',
        connected=ssh_connected,
        executed=ssh_executed,
        disconnected=ssh_disconnected,
        shellConsoleOut=ssh_shellOut,
        # receiveDelimiters='\r\n',
)

ssh_con.send('ps -ef')

or

# CAUTION 
# In the case, response which comes from Nodel may be a part of an entire message you are expecting.
# To overcome this issue, you can set the parameter 'receiveDelimiters' with care.
ssh_con.send('ps -ef', lambda response: console.info(response)) 
```