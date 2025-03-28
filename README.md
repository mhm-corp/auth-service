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




