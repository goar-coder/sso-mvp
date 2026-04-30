CREATE DATABASE IF NOT EXISTS keycloak_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS d1_db       CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS d2_db       CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS d3_db       CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- El usuario se crea automáticamente por MYSQL_USER
-- Otorgamos permisos usando la variable del entorno
GRANT ALL PRIVILEGES ON keycloak_db.* TO '${MYSQL_USER}'@'%';
GRANT ALL PRIVILEGES ON d1_db.*       TO '${MYSQL_USER}'@'%';
GRANT ALL PRIVILEGES ON d2_db.*       TO '${MYSQL_USER}'@'%';
GRANT ALL PRIVILEGES ON d3_db.*       TO '${MYSQL_USER}'@'%';
FLUSH PRIVILEGES;