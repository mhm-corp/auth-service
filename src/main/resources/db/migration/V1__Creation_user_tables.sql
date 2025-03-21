CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(30) PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    address VARCHAR(100) NOT NULL,
    birth_date DATE,
    phone_number VARCHAR(20) NOT NULL
);