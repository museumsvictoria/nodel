![nodellogo](http://nodel.io/media/1066/logo-nodel.png)

About
=====

[Nodel](http://nodel.io) is a digital media control system for museums and galleries.

Nodel is typically used for the control, monitoring and scheduling of digital media devices. With a design focused on galleries, museums, corporate meeting rooms and digital signage.

An active instance of Nodel can manage any number of integrate media devices, or assistant tools (schedulers, monitors, clusters) to form a coherent and manageable group of *nodes*.

These nodes are discoverable from other active instances of Nodel on the network, by which a series of user specified *bindings* allows the seamless propagation of remote actions and events between a group of nodes.

The nodes are written utilising a Python (Jython) API, examples of which are available on the [recipes](https://github.com/museumsvictoria/nodel-recipes) repository.

There's also a fully featured web interface for configuration of nodes, including an in-line editor to encourage most activities to take place within the platform.

![activityroomexample](http://nodel.io/media/1065/activityroomnodel_cut.png)


Technologies
============

* **Java** core
* **Python** (Jython) API to integrate systems (called nodes)
* **Zeroconf** networking manages decentralised network communication
* **Web** user interface to manage integrated nodes

Quick start
===========
* **download a [release](https://github.com/museumsvictoria/nodel/releases)**
* open a console
* `java -jar nodelhost-2.1.1-rc1.jar`
* drop some [recipes](https://github.com/museumsvictoria/nodel-recipes) into `nodes` folder
* check http://localhost:8085

Building and releases
=====================
* Latest releases can be found in [github releases](https://github.com/museumvictoria/nodel/releases)
* To build from scratch, clone repository and use [Gradle](http://www.gradle.org/):
  * `gradle :nodel-jyhost-java:build`
  * Check `nodel-jyhost-java\build\distributions\standalone` directory

Notes
=====
* ensure Java 7 or higher is installed (use `java -version`)
* for service / daemon use, see [wiki pages](https://github.com/museumvictoria/nodel/wiki)
* check `bootstrap` files for startup config

Licenses
========
* Platform - [Mozilla Public License, version 2.0](http://www.mozilla.org/MPL/2.0)
* Recipes - [MIT License](http://opensource.org/licenses/MIT)


Credits
=======

* [Museums Victoria](http://museumvictoria.com.au)
* [Automatic](http://automatic.com.au)
