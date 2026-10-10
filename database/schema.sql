CREATE DATABASE IF NOT EXISTS smart_carpool CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE smart_carpool;

CREATE TABLE IF NOT EXISTS users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  email VARCHAR(160) NOT NULL UNIQUE,
  password VARCHAR(255) NOT NULL,
  phone VARCHAR(24),
  role VARCHAR(16) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME NOT NULL
);
CREATE TABLE IF NOT EXISTS vehicles (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_id BIGINT NOT NULL,
  make_model VARCHAR(80) NOT NULL,
  registration_number VARCHAR(24) NOT NULL UNIQUE,
  seat_capacity INT NOT NULL,
  color VARCHAR(255),
  CONSTRAINT fk_vehicle_owner FOREIGN KEY (owner_id) REFERENCES users(id)
);
CREATE TABLE IF NOT EXISTS rides (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  driver_id BIGINT NOT NULL,
  vehicle_id BIGINT NOT NULL,
  source VARCHAR(120) NOT NULL,
  destination VARCHAR(120) NOT NULL,
  travel_date DATE NOT NULL,
  departure_time TIME NOT NULL,
  total_seats INT NOT NULL,
  available_seats INT NOT NULL,
  estimated_cost DECIMAL(10,2) NOT NULL,
  notes VARCHAR(500),
  status VARCHAR(16) NOT NULL,
  INDEX idx_ride_schedule (travel_date, departure_time),
  CONSTRAINT fk_ride_driver FOREIGN KEY (driver_id) REFERENCES users(id),
  CONSTRAINT fk_ride_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles(id)
);
CREATE TABLE IF NOT EXISTS ride_requests (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  ride_id BIGINT NOT NULL,
  passenger_id BIGINT NOT NULL,
  message VARCHAR(300),
  status VARCHAR(16) NOT NULL,
  created_at DATETIME NOT NULL,
  UNIQUE KEY uq_request_ride_passenger (ride_id, passenger_id),
  CONSTRAINT fk_request_ride FOREIGN KEY (ride_id) REFERENCES rides(id),
  CONSTRAINT fk_request_passenger FOREIGN KEY (passenger_id) REFERENCES users(id)
);
CREATE TABLE IF NOT EXISTS bookings (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  request_id BIGINT NOT NULL UNIQUE,
  status VARCHAR(16) NOT NULL,
  confirmed_at DATETIME NOT NULL,
  CONSTRAINT fk_booking_request FOREIGN KEY (request_id) REFERENCES ride_requests(id)
);
CREATE TABLE IF NOT EXISTS payments (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  booking_id BIGINT NOT NULL UNIQUE,
  amount DECIMAL(10,2) NOT NULL,
  status VARCHAR(16) NOT NULL,
  created_at DATETIME NOT NULL,
  CONSTRAINT fk_payment_booking FOREIGN KEY (booking_id) REFERENCES bookings(id)
);
CREATE TABLE IF NOT EXISTS ratings (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  booking_id BIGINT NOT NULL,
  passenger_id BIGINT NOT NULL,
  driver_id BIGINT NOT NULL,
  stars INT NOT NULL,
  review VARCHAR(1000),
  created_at DATETIME NOT NULL,
  UNIQUE KEY uq_rating_booking_passenger (booking_id, passenger_id),
  CONSTRAINT fk_rating_booking FOREIGN KEY (booking_id) REFERENCES bookings(id),
  CONSTRAINT fk_rating_passenger FOREIGN KEY (passenger_id) REFERENCES users(id),
  CONSTRAINT fk_rating_driver FOREIGN KEY (driver_id) REFERENCES users(id)
);
CREATE TABLE IF NOT EXISTS complaints (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  ride_id BIGINT,
  description VARCHAR(1200) NOT NULL,
  status VARCHAR(16) NOT NULL,
  created_at DATETIME NOT NULL,
  CONSTRAINT fk_complaint_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_complaint_ride FOREIGN KEY (ride_id) REFERENCES rides(id)
);
CREATE TABLE IF NOT EXISTS notifications (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  message VARCHAR(300) NOT NULL,
  is_read BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME NOT NULL,
  CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES users(id)
);