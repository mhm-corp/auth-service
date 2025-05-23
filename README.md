## Authentication and Authorization Microservice - auth-service

This microservice is responsible for:

- User management
- Authentication (JWT)
- Authorization (RBAC)
- Role and permission management


## Preconditions

Before uploading the project for the first time, the following preconditions must be met:

1. Connect to the PostgreSQL database and execute the following script: `CREATE DATABASE bank_auth;`
2. Verify that the **bank_auth** database exists: `SELECT datname FROM pg_database WHERE datname = 'bank_auth';`


## Documentation

- The API documentation can be accessed at the following URL: http://localhost:8081/swagger-ui/index.html
- Documentation in JSON format: http://localhost:8081/v3/api-docs

## Build the image and publish it to a local registry
Reminder: That the property `spring.profiles.active=docker` must be set in **application.properties** file 

1. In the console, navigate to the directory where the Dockerfile is located. 
2. Build the image (Verify the project version in the build.gradle file.): `docker build -t auth-service:1.0.0 .`
3. Verify the image: `docker images auth-service`
4. Push the image to a local registry: `docker run -d -p 5000:5000 --name local-registry registry:2`
5. Tag the image: `docker tag auth-service:1.0.0 localhost:5000/auth-service:1.0.0`
6. Upload the image to the local registry: `docker push localhost:5000/auth-service:1.0.0`
7. Verify that the image is in the local registry: `curl http://localhost:5000/v2/_catalog`


### If the image already exists in the local registry (Locate where docker-compose is located)
1. View the containers using the image: `docker ps -a --filter "ancestor=localhost:5000/auth-service:1.0.0"`
2. Stop the container(s) (if running): `docker stop <container_id>`
3. Remove the container(s): `docker rm <container_id>`
4. View the image: `docker images localhost:5000/auth-service`
5. Delete the image: `docker rmi localhost:5000/auth-service:1.0.0`
6. Verify that the image has been removed from the local registry: `curl http://localhost:5000/v2/_catalog`
