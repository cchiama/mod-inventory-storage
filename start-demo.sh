#!/usr/bin/env bash

kbport=${1:-9401}
catalogueport=${2:-9402}

./start-registered.sh ${kbport} ${catalogueport}

cd demo/ui

npm install

./node_modules/.bin/webpack-dev-server --host 0.0.0.0



