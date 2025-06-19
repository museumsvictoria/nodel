# BUILDING FROM SCRATCH
In short, clone this repository and run `gradlew build`.

The steps below describe usage on both Windows and Linux
* The inline snippets refer to **Windows**
* The section further below shows a complete example for **Linux**

---

## STEP 1: ENSURE PRIMARY DEPENDENCIES ARE PRESENT†
  1. minimum **Java JDK 11**, see production ready distributions from [Amazon Corretto](https://aws.amazon.com/corretto)
      * latest Windows x64 redirected [download link](https://corretto.aws/downloads/latest/amazon-corretto-11-x64-windows-jdk.zip)
      * latest Linux aarch64 redirected [download link](https://corretto.aws/downloads/latest/amazon-corretto-11-aarch64-linux-jdk.tar.gz)
      * latest Linux x64 redirected [download link](https://corretto.aws/downloads/latest/amazon-corretto-11-x64-linux-jdk.tar.gz)
   3. **Git**, see [latest versions](https://git-scm.com/downloads)
      * latest Windows snapshot link - [PortableGit-2.50.0-64-bit.7z.exe](https://github.com/git-for-windows/git/releases/download/v2.50.0.windows.1/PortableGit-2.50.0-64-bit.7z.exe)
      * Linux install `apt-get install git`

† all support "portable" low-impact installation, examples here are extracted to `C:\Apps\jdk11.0.27_6` and `C:\Apps\git\bin` respectively.

## STEP 2: ENSURE DEPENDENCIES ARE ACCESSIBLE
* ensure dependencies above, are on path, e.g. on Windows:
```bat
set PATH=C:\Apps\git\bin;C:\Apps\jdk11.0.27_6\bin;%PATH%
```

## STEP 3: CLONE REPOSITORY
* ensure primary dependencies, above, are on path (see example in STEP 2, above)
* then Clone! e.g. cloning into `c:\temp\nodel-build` directory:
```bat
git clone https://github.com/museumsvictoria/nodel c:\temp\nodel-build
```

## STEP 4: EXECUTE BUILD
* Build! Example, from the `c:\temp\nodel-build` directory:
```bat
cd  c:\Temp\nodel-build
gradlew build
```
* on Windows some Firewall pop-up may need to be acknowledged
* check `nodel-jyhost/build/distributions/standalone` directory for binary release e.g. `nodelhost-dev-2.2.1-rev521.jar`

---

**Full example using a clean Linux environment¹**

```bash
# download and extract Java JDK 11
wget https://corretto.aws/downloads/latest/amazon-corretto-11-aarch64-linux-jdk.tar.gz
tar xf amazon-corretto-11-aarch64-linux-jdk.tar.gz

# Your Java ends up in ~/amazon-corretto-11.0.27.6.1-linux-aarch64

# (git is normally already available)

# adjust PATH to ensure your Java is used first
export PATH=~/amazon-corretto-11.0.27.6.1-linux-aarch64/bin:$PATH

# quickly verify versions of all dependencies

java -version 
# e.g. >> openjdk version "11.0.27" 2025-04-15 LTS
#      >> OpenJDK Runtime Environment Corretto-11.0.27.6.1 (build 11.0.27+6-LTS)
#      >> OpenJDK 64-Bit Server VM Corretto-11.0.27.6.1 (build 11.0.27+6-LTS, mixed mode)

git version
# e.g. >> git version 2.39.5

# clone repo
git clone https://github.com/museumsvictoria/nodel nodel-build

# execute full build
cd ~/nodel-build
./gradlew build
# e.g. >> Starting a Gradle Daemon (subsequent builds will be faster)
#      >> ...
#      >> BUILD SUCCESSFUL in 1m 15s
#      >> 22 actionable tasks: 21 executed, 1 up-to-date

# check output (standalone JAR file)
cd ~/nodel-build/nodel-jyhost/build/distributions/standalone/
ls
# e.g. >> -rw-r--r-- 1 nodel nodel 20214394 May 19 16:03 nodelhost-dev-2.2.1-rev521.jar

# run Nodel (optional)
java -jar ~/nodel-build/nodel-jyhost/build/distributions/standalone/nodelhost-dev-2.2.1-rev521.jar -p 0
# e.g. >> Nodel [Jython] v2.2.1-dev_r521 is running.
#      >>
#      >> Press Enter to initiate a shutdown.
#      >>
#      >>    (web interface available at http://172.17.128.1:8085)
```

**Source update, clean and build re-run**
```
cd ~/nodel-build
git pull

./gradlew clean
# or
git clean -fxd

./gradlew build
```

**Full cleanup**
```bash
rm amazon-corretto-11-aarch64-linux-jdk.tar.gz
rm -fr ~/amazon-corretto-11.0.27.6.1-linux-aarch64
rm -fr ~/nodel-build
```

---
¹ For macOS, adjust to suit.
