About
=====

[Nodel](http://nodel.io) is a digital media control system for museums and galleries.

Nodel is typically used to control digital media devices in galleries, museums, corporate meeting rooms and digital signage.


Technologies
============

* Java
* Python (using Jython)
 
Quick start
===========
* download a release
* `java -jar nodelhost-2.0.7-rc1.jar`
* drop some [recipes] (https://github.com/museumvictoria/nodel-recipes) into `nodes` folder
* check http://localhost:8085

Building and releases
=====================
* Latest releases can be found in [github releases] (https://github.com/museumvictoria/nodel/releases)
* To build from scratch, clone repository and use [Gradle] (http://www.gradle.org/):
  * `gradle :nodel-jyhost-java:build` 
  * Check `nodel-jyhost-java\build\distributions\standalone` directory

Notes
=====
* ensure Java 7 or higher is installed (use `java -version`)
* for service / daemon use, see [wiki pages] (https://github.com/museumvictoria/nodel/wiki)
* check `bootstrap` files for startup config

Licenses
========
* Platform - [Mozilla Public License, version 2.0](http://www.mozilla.org/MPL/2.0)
* Recipes - [MIT License](http://opensource.org/licenses/MIT)


Credits
=======

* [Museum Victoria](http://museumvictoria.com.au)
* [Lumicom](http://lumicom.com.au)
