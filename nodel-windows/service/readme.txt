Installation instructions
=========================

1. Make sure the file "C:\MyXYZnodel\nodel.jar" exists (or change to a custom entry-point when performing step 3.) 

2. rename the `nodelsvc.exe` & `nodelsvcw.exe` (see note below) executable files into suitable short and space-less names by changing the common prefix, e.g. `MyXYZNodelsvc.exe` & `MyXYZNodelsvcw.exe` respectively.

3. Register the service using the following command:

   For 64-bit systems
   ------------------
   
> MyXYZnodelsvc.exe //IS --DisplayName "MyXYZ Nodel" --Startup auto --StartMode jvm --StartClass org.nodel.nodelhost.Service --StartMethod start --StopMode jvm --StopClass org.nodel.nodelhost.Service --StopMethod stop --Classpath C:\MyXYZnodel\nodel.jar --JvmOptions=-XX:MaxPermSize=1024


   For 32-bit systems (exactly the same as above without the last argument)
   ------------------------------------------------------------------------

> MyXYZnodelsvc.exe //IS --DisplayName "MyXYZ Nodel" --Startup auto --StartMode jvm --StartClass org.nodel.nodelhost.Service --StartMethod start --StopMode jvm --StopClass org.nodel.nodelhost.Service --StopMethod stop --Classpath C:\MyXYZnodel\nodel.jar 

4. Start the newly created service, "MyXYZ Nodel" using the Windows Service manager.

Links:
Apache commons daemon reference - http://commons.apache.org/proper/commons-daemon/procrun.html