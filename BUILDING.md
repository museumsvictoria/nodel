# Building from scratch
* **gradle** is the primary build tool for the main Java project (runs on **Java**)


The steps below describe usage on both Windows and Linux
* The example refers to **Windows**
* The section further below shows complete example for **Linux**

---

## STEP 1: ENSURE PRIMARY DEPENDENCIES ARE PRESENT
  1. **Java JDK 11+**, see [latest versions](https://adoptopenjdk.net/releases.html)
      * latest Windows [OpenJDK11U-jdk_x64_windows_hotspot_11.0.15_10.zip](https://github.com/adoptium/temurin11-binaries/releases/download/jdk-11.0.15%2B10/OpenJDK11U-jdk_x64_windows_hotspot_11.0.15_10.zip)
      * latest Linux [OpenJDK11U-jdk_x64_linux_hotspot_11.0.15_10.tar.gz](https://github.com/adoptium/temurin11-binaries/releases/download/jdk-11.0.15%2B10/OpenJDK11U-jdk_x64_linux_hotspot_11.0.15_10.tar.gz)
   3. **Git**, see [latest versions](https://git-scm.com/download)
      * latest Windows [PortableGit-2.26.0-64-bit.7z.exe](https://github.com/git-for-windows/git/releases/download/v2.26.0.windows.1/PortableGit-2.26.0-64-bit.7z.exe)
      * Linux install `apt-get install git`

 * all support "portable" installation, examples here are extracted to `C:\Apps`

## STEP 2: ENSURE DEPENDENCIES ARE 'READY'
* ensure dependencies, above, are on path, e.g. on Windows:
```bat
set PATH=%PATH%;C:\Apps\git\bin;C:\Apps\jdk-11.0.15+10\bin
```

## STEP 3: ENSURE REPOSITORY IS CLONED
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
* check `nodel-jyhost/build/distributions/standalone` directory for binary release e.g. `nodelhost-dev-2.2.1-rev407.jar`

---

**Full example using clean Linux environment¹**

```bash
# download and extract Java
wget https://github.com/adoptium/temurin11-binaries/releases/download/jdk-11.0.15%2B10/OpenJDK11U-jdk_x64_linux_hotspot_11.0.15_10.tar.gz
tar xf OpenJDK11U-jdk_x64_linux_hotspot_11.0.15_10.tar.gz
# ends up in ./jdk-11.0.15+10

# (git is normally already available)

# adjust PATH
export PATH=$PATH:~/jdk-11.0.15+10/bin/

# quickly verify versions of all dependencies

java -version 
# e.g. >> openjdk version "11.0.15"

git version
# e.g. >> git version 2.25.1

# clone repo
git clone https://github.com/museumsvictoria/nodel nodel-build

# execute full build
cd ~/nodel-build
./gradlew build

# check output (standalone JAR file)
ls ~/nodel-build/nodel-jyhost/build/distributions/standalone/

# run Nodel (optional)
java -jar ~/nodel-build/nodel-jyhost/build/distributions/standalone/nodelhost-dev-2.2.1-rev407.jar -p 0
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
rm -fr ~/jdk-11.0.15+10
rm -fr ~/nodel-build
```

---
¹ For macOS, adjust to suit.