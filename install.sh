#!/bin/bash
#
# Nodel Docker Installer
#
# Usage:
#   /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/museumsvictoria/nodel/master/install.sh)"
#
# With options:
#   PORT=8086 NAME=nodel2 IMAGE=ghcr.io/scroix/nodel:latest \
#     /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/museumsvictoria/nodel/master/install.sh)"
#
set -e

# Defaults (override with environment variables)
PORT=${PORT:-8085}
NAME=${NAME:-"nodel"}
IMAGE=${IMAGE:-"ghcr.io/museumsvictoria/nodel:latest"}

# Colours (if terminal supports them)
if [ -t 1 ]; then
  RED='\033[0;31m'
  GREEN='\033[0;32m'
  YELLOW='\033[0;33m'
  BLUE='\033[0;34m'
  NC='\033[0m' # No Colour
else
  RED=''
  GREEN=''
  YELLOW=''
  BLUE=''
  NC=''
fi

log() {
  echo -e "${BLUE}[nodel]${NC} $1"
}

error() {
  echo -e "${RED}[error]${NC} $1" >&2
}

success() {
  echo -e "${GREEN}[success]${NC} $1"
}

warn() {
  echo -e "${YELLOW}[warn]${NC} $1"
}

# Check Docker is installed
if ! command -v docker &> /dev/null; then
  error "Docker is not installed"
  echo ""
  echo "Please install Docker first:"
  echo "  https://docs.docker.com/get-docker/"
  exit 1
fi

# Check Docker daemon is running
if ! docker info &> /dev/null; then
  error "Docker daemon is not running"
  echo ""
  echo "Please start Docker and try again"
  exit 1
fi

# Check if container already exists
if docker container inspect "$NAME" &> /dev/null; then
  warn "Container '$NAME' already exists"
  echo ""
  echo "To reinstall, first remove the existing container:"
  echo "  docker rm -f $NAME"
  exit 1
fi

# Check if port is in use by another container
if docker ps --format '{{.Ports}}' | grep -q ":$PORT->"; then
  warn "Port $PORT appears to be in use by another container"
  exit 1
fi

log "Pulling Nodel image..."
docker pull "$IMAGE"

log "Starting Nodel..."
docker run -d \
  --name "$NAME" \
  --restart unless-stopped \
  -p "$PORT:$PORT" \
  "$IMAGE" -p "$PORT" > /dev/null

echo ""
success "Nodel is running!"
echo ""
echo "  Web UI:    http://localhost:$PORT"
echo ""
echo "Commands:"
echo "  docker logs $NAME       # View logs"
echo "  docker stop $NAME       # Stop Nodel"
echo "  docker start $NAME      # Start Nodel"
echo "  docker rm -f $NAME      # Remove container"
