#!/usr/bin/env bash

tenant=${1:-demo_tenant}
item_storage_address=http://localhost:9130/item-storage/items
instance_storage_address=http://localhost:9130/instance-storage/instances
holdings_storage_address=http://localhost:9130/holdings-storage/holdings

for f in ./instances/*.json; do
    curl -w '\n' -X POST -D - \
         -H "Content-type: application/json" \
         -H "X-Okapi-Tenant: ${tenant}" \
         -d @$f \
         "${instance_storage_address}"
done

for f in ./holdingsrecords/*.json; do
    curl -w '\n' -X POST -D - \
         -H "Content-type: application/json" \
         -H "X-Okapi-Tenant: ${tenant}" \
         -d @$f \
         "${holdings_storage_address}"
done

for f in ./items/*.json; do
    curl -w '\n' -X POST -D - \
         -H "Content-type: application/json" \
         -H "X-Okapi-Tenant: ${tenant}" \
         -d @$f \
         "${item_storage_address}"
done