#!/usr/bin/env bash

deployment_descriptor=${1:-target/DeploymentDescriptor-environment.json}
tenant_id=${2:-demo_tenant}
okapi_proxy_address=${3:-http://localhost:9130}
module_id=${4:-mod-inventory-storage-5.1.1-SNAPSHOT}

./okapi-registration/managed-deployment/register.sh \
  ${module_id} \
  ${okapi_proxy_address} \
  ${tenant_id} \
  ${deployment_descriptor}

