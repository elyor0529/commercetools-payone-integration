#! /bin/bash

set -e

export REPO="sphereio/commercetools-payone-integration"
export DOCKER_TAG=`if [ "$TRAVIS_BRANCH" == "master" -a "$TRAVIS_PULL_REQUEST" = "false" ]; then echo "latest"; else echo ${TRAVIS_BRANCH} | sed -e 's/#//g' -e 's/\\\\/-/g' ; fi`
# where sed -e 's/#//g' -e 's/\\\\/-/g' is: remove [ # ] and replace [ \ -> - ] in the branch name

echo "TRAVIS_BRANCH=$TRAVIS_BRANCH"
echo "DOCKER_TAG                = $DOCKER_TAG"
echo "COMMIT TAG                = $COMMIT"
echo "TRAVIS_BUILD_NUMBER TAG   = $TRAVIS_BUILD_NUMBER"
echo "TRAVIS_TAG                = $TRAVIS_TAG"

echo "Building service jar file including all dependencies."
./gradlew stage

echo "Building Docker image using tag '${REPO}:${COMMIT}'."
docker build -t "${REPO}:${COMMIT}" .

docker login -u="${DOCKER_USERNAME}" -p="${DOCKER_PASSWORD}"

echo "Adding additional tag '${REPO}:${DOCKER_TAG}' to already built Docker image '${REPO}:${COMMIT}'."
docker tag $REPO:$COMMIT $REPO:$DOCKER_TAG
echo "Adding additional tag '${REPO}:travis-${TRAVIS_BUILD_NUMBER}' to already built Docker image '${REPO}:${COMMIT}'."
docker tag $REPO:$COMMIT $REPO:travis-$TRAVIS_BUILD_NUMBER
if [ "$TRAVIS_TAG" ]; then
  echo "Adding additional tag '${REPO}:${TRAVIS_TAG}' to already built Docker image '${REPO}:${COMMIT}'."
  docker tag $REPO:$COMMIT $REPO:${TRAVIS_TAG};
  echo "Adding additional tag '${REPO}:production' to already built Docker image '${REPO}:${COMMIT}'."
  docker tag $REPO:$COMMIT $REPO:production;
fi
echo "Pushing Docker images to repository '${REPO}' (all local tags are pushed)."
docker push $REPO
docker logout
