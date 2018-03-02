#!/bin/bash

#
# import mod-inventory-storage reference data
#

#set -x

type curl >/dev/null 2>&1 || { echo >&2 "$0: curl is required but it's not installed"; exit 1; }

usage() {
   cat << EOF
Usage: ${0##*/} -o OKAPI_URL -t TENANT -a
       -o Okapi_URL (required)
       -t tenant (required)
       -d directory_to_reference_data (optional) Default: '.'
       -a x-okapi-token  (optional) Default: (no auth)
EOF
}


OPTIND=1 

while getopts "h?a:o:t:d:" opt 
do
    case "$opt" in
    h|\?)
        usage
        exit 1
        ;;
    o)  
        okapiUrl=$OPTARG
        ;;
    t)  
        tenant=$OPTARG
        ;;
    d)  
        dataDirs+=("$OPTARG")
        ;;
    a)  
        auth_required=true
        authToken=$OPTARG
        ;;  
    *)  
        usage >&2
        exit 1
        ;;
    esac
done

shift $((OPTIND -1))

okapiUrl=${okapiUrl:-http://localhost:9130}
tenant=${tenant:-demo_tenant}
dataDirs=${dataDirs:-'.'}
modEndpoints_a='material-types loan-types shelf-locations identifier-types contributor-types' 
modEndpoints_b='contributor-name-types instance-types instance-formats classification-types platforms'
modEndpoints="$modEndpoints_a $modEndpoints_b"
method=POST

curlBaseOpts="-s -w '\n' --connect-timeout 10 \
 -H 'Content-type: application/json' \
 -H 'Accept: application/json' \
 -H 'X-Okapi-Tenant: $tenant'"

if [ "$auth_required" = true ]; then
  authOpt="-H 'X-Okapi-Token: $authToken'"
  curlOpts="$curlBaseOpts $authOpt"
else
  curlOpts=$curlBaseOpts
fi

for dir in "${dataDirs[@]}"; 
do
  for endpoint in $modEndpoints 
  do 
    if [ -d "${dir}/${endpoint}" ]; then
      json=$(ls ${dir}/${endpoint}/*.json)
      for j in $json 
      do 
        curl $curlOpts -X $method -d @$j ${okapiUrl}/${endpoint}
      done
    fi
  done
done

