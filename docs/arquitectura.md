MVP simplificado
Perfecto, para una presentación esto es lo correcto. Simplificamos sin perder la arquitectura real.

🏗️ Arquitectura MVP final
┌─────────────────────────────────────────────────────────────┐
│                     docker-compose.yml                      │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                     MySQL                           │   │
│  │  - BD: keycloak_db   (para Keycloak)                │   │
│  │  - BD: d1_db         (para D1)                      │   │
│  │  - BD: d2_db         (para D2)                      │   │
│  │  - BD: d3_db         (para D3)                      │   │
│  │  Una sola instancia, 4 bases de datos               │   │
│  └────────────────────────┬────────────────────────────┘   │
│                           │                                 │
│          ┌────────────────┼─────────────┐                  │
│          │                │             │                   │
│          ▼                ▼             │                   │
│  ┌──────────────┐ ┌──────────────┐     │                   │
│  │   KEYCLOAK   │ │      D1      │     │                   │
│  │   :8080      │ │    :8001     │     │                   │
│  │              │ │              │     │                   │
│  │ Custom SPI ──┼─┼→ /api/       │     │                   │
│  │              │ │  internal/   │     │                   │
│  └──────────────┘ │  auth/       │     │                   │
│                   │              │     │                   │
│                   │ Cliente OIDC │     │                   │
│                   │ Portal router│     │                   │
│                   └──────────────┘     │                   │
│                                        │                   │
│          ┌─────────────────────────────┘                   │
│          │                                                  │
│   ┌──────┴──────┐              ┌──────────────┐            │
│   │     D2      │              │      D3      │            │
│   │   :8002     │              │    :8003     │            │
│   │ Cliente OIDC│              │ Cliente OIDC │            │
│   └─────────────┘              └──────────────┘            │
│                                                             │
│   Red interna Docker: sso-network                          │
└─────────────────────────────────────────────────────────────┘

⚠️ Consideración importante sobre MySQL y Keycloak
Keycloak soporta MySQL pero hay un detalle crítico para el MVP:
Keycloak 24+ (Quarkus) requiere:
  - MySQL 8.0+
  - Driver JDBC incluido en la imagen oficial ✓
  - Variable KC_DB=mysql (no 'mariadb')

El Custom SPI con MySQL no afecta en nada,
el SPI habla HTTP con D1, no toca la DB directamente.

📁 Estructura de archivos del proyecto
sso-mvp/
│
├── docker-compose.yml
├── .env                          # todos los secrets aquí
│
├── keycloak/
│   ├── realm-export.json         # configuración del realm completa
│   └── spi/
│       └── d1-user-storage-spi.jar   # el JAR compilado
│
├── d1/
│   ├── Dockerfile
│   ├── manage.py
│   ├── requirements.txt
│   ├── config/
│   │   ├── settings.py
│   │   └── urls.py
│   ├── internal_auth/            # app Django nueva
│   │   ├── views.py              # endpoints /api/internal/auth/
│   │   ├── urls.py
│   │   └── middleware.py         # validación API key
│   └── portal/                   # app Django nueva
│       ├── views.py              # router post-login
│       ├── urls.py
│       └── templates/
│           └── portal/
│               └── selector.html
│
├── d2/
│   ├── Dockerfile
│   ├── manage.py
│   ├── requirements.txt
│   └── config/
│       ├── settings.py
│       └── urls.py
│
├── d3/
│   ├── Dockerfile
│   ├── manage.py
│   ├── requirements.txt
│   └── config/
│       ├── settings.py
│       └── urls.py
│
└── keycloak-spi/                 # proyecto Java separado
    ├── pom.xml
    └── src/main/java/com/empresa/spi/
        ├── D1UserStorageProvider.java
        ├── D1UserStorageProviderFactory.java
        ├── D1UserAdapter.java
        └── D1ApiClient.java

🐳 docker-compose.yml definitivo
yamlversion: '3.8'

services:

  mysql:
    image: mysql:8.0
    container_name: sso-mysql
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      MYSQL_USER: ${MYSQL_USER}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD}
    volumes:
      - mysql-data:/var/lib/mysql
      - ./mysql/init.sql:/docker-entrypoint-initdb.d/init.sql
    ports:
      - "3306:3306"                 # expuesto solo para debug en MVP
    networks:
      - sso-network
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost",
             "-u", "root", "-p${MYSQL_ROOT_PASSWORD}"]
      interval: 10s
      timeout: 5s
      retries: 10

  keycloak:
    image: quay.io/keycloak/keycloak:24.0
    container_name: sso-keycloak
    command: start-dev --import-realm
    environment:
      KC_DB: mysql
      KC_DB_URL: jdbc:mysql://mysql:3306/keycloak_db
      KC_DB_USERNAME: ${MYSQL_USER}
      KC_DB_PASSWORD: ${MYSQL_PASSWORD}
      KEYCLOAK_ADMIN: ${KC_ADMIN_USER}
      KEYCLOAK_ADMIN_PASSWORD: ${KC_ADMIN_PASSWORD}
      KC_HTTP_PORT: 8080
    volumes:
      - ./keycloak/realm-export.json:/opt/keycloak/data/import/realm-export.json
      - ./keycloak/spi/d1-user-storage-spi.jar:/opt/keycloak/providers/d1-user-storage-spi.jar
    ports:
      - "8080:8080"
    depends_on:
      mysql:
        condition: service_healthy
    networks:
      - sso-network

  d1:
    build: ./d1
    container_name: sso-d1
    environment:
      DB_NAME: d1_db
      DB_USER: ${MYSQL_USER}
      DB_PASSWORD: ${MYSQL_PASSWORD}
      DB_HOST: mysql
      DB_PORT: 3306
      INTERNAL_API_KEY: ${INTERNAL_API_KEY}
      OIDC_RP_CLIENT_ID: d1-client
      OIDC_RP_CLIENT_SECRET: ${D1_CLIENT_SECRET}
      KEYCLOAK_URL: http://keycloak:8080
    ports:
      - "8001:8001"
    depends_on:
      mysql:
        condition: service_healthy
    networks:
      - sso-network

  d2:
    build: ./d2
    container_name: sso-d2
    environment:
      DB_NAME: d2_db
      DB_USER: ${MYSQL_USER}
      DB_PASSWORD: ${MYSQL_PASSWORD}
      DB_HOST: mysql
      DB_PORT: 3306
      OIDC_RP_CLIENT_ID: d2-client
      OIDC_RP_CLIENT_SECRET: ${D2_CLIENT_SECRET}
      KEYCLOAK_URL: http://keycloak:8080
    ports:
      - "8002:8002"
    depends_on:
      mysql:
        condition: service_healthy
    networks:
      - sso-network

  d3:
    build: ./d3
    container_name: sso-d3
    environment:
      DB_NAME: d3_db
      DB_USER: ${MYSQL_USER}
      DB_PASSWORD: ${MYSQL_PASSWORD}
      DB_HOST: mysql
      DB_PORT: 3306
      OIDC_RP_CLIENT_ID: d3-client
      OIDC_RP_CLIENT_SECRET: ${D3_CLIENT_SECRET}
      KEYCLOAK_URL: http://keycloak:8080
    ports:
      - "8003:8003"
    depends_on:
      mysql:
        condition: service_healthy
    networks:
      - sso-network

volumes:
  mysql-data:

networks:
  sso-network:
    driver: bridge

🗄️ mysql/init.sql
sql-- Se ejecuta una sola vez al crear el contenedor MySQL

CREATE DATABASE IF NOT EXISTS keycloak_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS d1_db       CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS d2_db       CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS d3_db       CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- El usuario ya existe (creado por MYSQL_USER), solo damos permisos
GRANT ALL PRIVILEGES ON keycloak_db.* TO '${MYSQL_USER}'@'%';
GRANT ALL PRIVILEGES ON d1_db.*       TO '${MYSQL_USER}'@'%';
GRANT ALL PRIVILEGES ON d2_db.*       TO '${MYSQL_USER}'@'%';
GRANT ALL PRIVILEGES ON d3_db.*       TO '${MYSQL_USER}'@'%';
FLUSH PRIVILEGES;

🔑 .env
bash# MySQL
MYSQL_ROOT_PASSWORD=root_secret_mvp
MYSQL_USER=sso_user
MYSQL_PASSWORD=sso_password_mvp

# Keycloak admin
KC_ADMIN_USER=admin
KC_ADMIN_PASSWORD=admin_mvp

# API key interna D1 ↔ Keycloak SPI
INTERNAL_API_KEY=internal-api-key-super-secret-mvp

# Client secrets (se generan al crear los clients en Keycloak)
D1_CLIENT_SECRET=d1-secret-mvp
D2_CLIENT_SECRET=d2-secret-mvp
D3_CLIENT_SECRET=d3-secret-mvp

📋 Orden de implementación confirmado
FASE 1 — docker-compose + MySQL + Keycloak arrancando       
FASE 2 — Endpoint interno de D1 (/api/internal/auth/)
FASE 3 — Custom SPI Java (keycloak-spi/) ← estamos aquí con posibles errores
FASE 4 — OIDC en D2
FASE 5 — OIDC en D3
FASE 6 — OIDC en D1 + /admin/ independiente
FASE 7 — Portal post-login (router + selector)
FASE 8 — Prueba end-to-end completa