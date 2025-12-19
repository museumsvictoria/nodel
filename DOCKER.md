<div align="center">

  <img src="http://nodel.io/media/1066/logo-nodel.png" alt="Nodel logo" height="110">

  <h3>Nodel, on Docker.</h3>

  <p>
    <a href="https://github.com/museumsvictoria/nodel/pkgs/container/nodel">
      <img src="https://img.shields.io/badge/registry-ghcr.io-blue" alt="Container Registry">
    </a>
  </p>

</div>

##### Run Nodel anywhere Docker runs.

The official container image packages everything needed to get started in seconds.

###### Why Docker?

* No Java installation required on the host
* Consistent environment across all deployments
* Easy updates with a single pull command
* Isolated from host system dependencies

-------------

Quick Start
===========

The fastest way to get Nodel running is with the install script:

```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/museumsvictoria/nodel/master/install.sh)"
```

This creates a persistent container named `nodel` on port 8085. Once running, open http://localhost:8085 in your browser.

> **Tip:** Customise with `PORT`, `NAME`, or `IMAGE` env vars before the command.

Running Nodel
=============

Pull the image:

```bash
docker pull ghcr.io/museumsvictoria/nodel:latest
```

Interactive (Ctrl+C to stop):

```bash
docker run --rm -it -p 8085:8085 ghcr.io/museumsvictoria/nodel
```

Detached with auto-restart:

```bash
docker run -d --name nodel -p 8085:8085 --restart unless-stopped \
  ghcr.io/museumsvictoria/nodel
```

Pass Nodel arguments after the image name:

```bash
docker run --rm -it -p 8086:8086 ghcr.io/museumsvictoria/nodel -p 8086
```

Development
===========

Build and run using Docker Compose:

```bash
docker compose up --build -d
```

> **Tip:** Use a `docker-compose.override.yml` to swap in a local image or tweak settings without modifying the base file.

Data Persistence
================

Node configurations are stored inside the container by default. Named containers retain data across restarts, but it's lost if the container is removed (`docker rm`). Mount host directories as volumes to keep data independent of the container lifecycle.

Mount your nodes folder:

```bash
docker run -d --name nodel -p 8085:8085 \
  -v ./nodes:/app/nodes \
  ghcr.io/museumsvictoria/nodel
```

Or mount multiple directories:

```bash
docker run -d --name nodel -p 8085:8085 \
  -v ./nodes:/app/nodes \
  -v ./recipes:/app/recipes \
  ghcr.io/museumsvictoria/nodel
```

Configuration
=============

### JVM options

Tune Java memory settings with the `JAVA_OPTS` environment variable:

```bash
docker run -d --name nodel -p 8085:8085 \
  -e JAVA_OPTS="-XX:MaxRAMPercentage=50.0 -Xms256m" \
  ghcr.io/museumsvictoria/nodel
```

Default JVM options: `-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0`

### Health check

The image includes a built-in health check that polls `/REST/` every 30 seconds. It automatically detects the HTTP port from `.lastHTTPPort` if present.

Notes
=====

* Trigger an image build manually: `gh workflow run docker-publish.yml --ref <branch> -f push_image=true`
* Images are available for both `amd64` and `arm64` architectures
* For service/daemon setup on the host, see the [wiki pages](https://github.com/museumsvictoria/nodel/wiki)
* Drop [recipes](https://github.com/museumsvictoria/nodel-recipes) into the nodes folder to get started
