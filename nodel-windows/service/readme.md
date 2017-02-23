For Windows service installation, please use

* `install_as_service_x64.bat` for 64-bit Java
* `install_as_service_x86.bat` for 32-bit Java

Note: to check for 64-bit Java, use:
```
C:\>java -version
java version "1.8.0_121"
Java(TM) SE Runtime Environment (build 1.8.0_121-b13)
Java HotSpot(TM) 64-Bit Server VM (build 25.121-b13, mixed mode)
```

Same, but on 32-bit Java (notice no mention of 64-bit)
```
C:\>java -version
java version "1.8.0_121"
Java(TM) SE Runtime Environment (build 1.8.0_121-b13)
Java HotSpot(TM) Client VM (build 25.121-b13, mixed mode, sharing)
```

### Links
Apache commons daemon reference - http://commons.apache.org/proper/commons-daemon/procrun.html