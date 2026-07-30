# Kubernetes deployment

Runs PetClinic as a single Deployment fronted by one `LoadBalancer`
Service. The app uses its default in-memory H2 database, so there is no
external datastore to deploy — the service is fully self-contained.

## 1. Build and push the image (maintainers)

```bash
./gradlew jib
```

This builds a multi-arch (amd64 + arm64) image and pushes it to
`ghcr.io/appglass/petclinicdemo`, tagged with `version` from
`build.gradle` and `latest`. It requires GHCR credentials
(`docker login ghcr.io` with a PAT that has `write:packages`).

The package is public, so clusters can pull it without any
`imagePullSecret`. If you only want a local image for development,
`./gradlew jibDockerBuild` still works.

## 2. Deploy

```bash
kubectl apply -f k8s/
```

## 3. Access the app

```bash
kubectl get service petclinic
```

Open the `EXTERNAL-IP` shown for the service in a browser.

On a local cluster without a real load balancer, port-forward instead:

```bash
kubectl port-forward service/petclinic 8080:80
```

Then browse to <http://localhost:8080/>.

## Teardown

```bash
kubectl delete -f k8s/
```
