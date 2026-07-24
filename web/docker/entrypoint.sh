#!/bin/bash



# if you are using windows, you may need to convert the file to unix format
# you can use the Ubuntu terminal to convert this file to unix format
# otherwise, you may get the error after running the docker container

# sudo apt-get install dos2unix
# dos2unix entrypoint.sh


set -e

export NEXT_PUBLIC_DEPLOY_ENV=${DEPLOY_ENV}
export HOSTNAME=0.0.0.0

echo "NEXT_PUBLIC_DEPLOY_ENV: ${NEXT_PUBLIC_DEPLOY_ENV}"
echo "HOSTNAME: ${HOSTNAME}"
echo "NODE_ENV: ${NODE_ENV}"

pm2 start ./pm2.json --no-daemon
