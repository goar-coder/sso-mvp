# 🔐 SSO MVP - Sistema de Single Sign-On con Keycloak

Arquitectura SSO completa con 3 aplicaciones Django, Keycloak, Custom SPI y portal de selección inteligente.

## 🏗️ Arquitectura

```
┌─────────────────────────────────────────────────────────────┐
│                     docker-compose.yml                      │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                     MySQL                           │   │
│  │  - BD: keycloak_db   (para Keycloak)                │   │
│  │  - BD: d1_db         (para D1)                      │   │
│  │  - BD: d2_db         (para D2)                      │   │
│  │  - BD: d3_db         (para D3)                      │   │
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
│                   │ Portal       │     │                   │
│                   │ Post-Login   │     │                   │
│                   └──────────────┘     │                   │
│                                        │                   │
│          ┌─────────────────────────────┘                   │
│          │                                                  │
│   ┌──────┴──────┐              ┌──────────────┐            │
│   │     D2      │              │      D3      │            │
│   │   :8002     │              │    :8003     │            │
│   │ Cliente OIDC│              │ Cliente OIDC │            │
│   └─────────────┘              └──────────────┘            │
└─────────────────────────────────────────────────────────────┘
```

## 🚀 Configuración Rápida

### Prerequisitos

- Docker y Docker Compose
- Git
- Puertos libres: 3306, 8080, 8001, 8002, 8003

### 1. Clonar el Proyecto

```bash
git clone <url-del-repo>
cd sso-mvp
```

### 2. Levantar el Sistema

```bash
# Levantar todos los contenedores
docker compose up -d

# Verificar que todos estén funcionando
docker compose ps
```

**Estado esperado:**
```
NAME           STATUS                   PORTS
sso-d1         Up X minutes             0.0.0.0:8001->8001/tcp
sso-d2         Up X minutes             0.0.0.0:8002->8002/tcp
sso-d3         Up X minutes             0.0.0.0:8003->8003/tcp
sso-keycloak   Up X minutes             0.0.0.0:8080->8080/tcp
sso-mysql      Up X minutes (healthy)   0.0.0.0:3306->3306/tcp
```

### 3. Ejecutar Migraciones de Django

```bash
# Crear tablas en todas las bases de datos Django
docker exec sso-d1 python manage.py migrate
docker exec sso-d2 python manage.py migrate  
docker exec sso-d3 python manage.py migrate
```

### 4. Insertar Datos de Prueba

#### 4.1 Crear Usuarios y Grupos en Django (D1)

```bash
docker exec sso-d1 python manage.py shell -c "
from django.contrib.auth.models import User, Group

# Crear grupos de aplicaciones
groups = {}
for group_name in ['app-d1', 'app-d2', 'app-d3']:
    group, created = Group.objects.get_or_create(name=group_name)
    groups[group_name] = group
    print(f'Grupo: {group_name} {'(creado)' if created else '(existía)'}')

# USUARIO 1: Sin acceso (para probar pantalla de 'no access')
user_no_access, created = User.objects.get_or_create(
    username='user_no_access',
    defaults={
        'email': 'no_access@test.com',
        'first_name': 'No',
        'last_name': 'Access'
    }
)
if created: 
    user_no_access.set_password('pass123')
    user_no_access.save()
print(f'✓ Usuario sin acceso: {user_no_access.username}')

# USUARIO 2: Acceso único a D1 (redirección automática)
user_single, created = User.objects.get_or_create(
    username='user_d1_only',
    defaults={
        'email': 'user_d1@test.com',
        'first_name': 'D1',
        'last_name': 'Only'
    }
)
if created: 
    user_single.set_password('pass123')
    user_single.save()
user_single.groups.clear()
user_single.groups.add(groups['app-d1'])
print(f'✓ Usuario acceso único: {user_single.username} → D1')

# USUARIO 3: Acceso múltiple D1+D3 (pantalla de selección)
user_multi, created = User.objects.get_or_create(
    username='user_d1_d3',
    defaults={
        'email': 'user_multi@test.com',
        'first_name': 'Multi',
        'last_name': 'Access'
    }
)
if created:
    user_multi.set_password('pass123')
    user_multi.save()
user_multi.groups.clear()
user_multi.groups.add(groups['app-d1'], groups['app-d3'])
print(f'✓ Usuario acceso múltiple: {user_multi.username} → D1, D3')

# USUARIO 4: Super admin con acceso completo (selector completo)
user_admin, created = User.objects.get_or_create(
    username='user_admin',
    defaults={
        'email': 'admin@test.com',
        'first_name': 'Super',
        'last_name': 'Admin'
    }
)
if created:
    user_admin.set_password('admin123')
    user_admin.save()
user_admin.groups.clear()
for group in groups.values():
    user_admin.groups.add(group)
print(f'✓ Usuario administrador: {user_admin.username} → D1, D2, D3')

print('\\n🎉 Usuarios de prueba creados exitosamente!')
"
```

#### 4.2 Configurar Keycloak

1. **Acceder al Admin de Keycloak:**
   ```
   URL: http://localhost:8080/admin/
   Usuario: admin
   Password: admin_mvp
   ```

2. **Configurar el Realm (si no está importado):**
   - Ir a "Realm settings" → "General"
   - Verificar que existe el realm `django-realm`

3. **Crear/Verificar Clients OIDC:**

   **Client D1:**
   ```
   Client ID: d1-client
   Client Secret: d1-secret-mvp
   Valid Redirect URIs: http://localhost:8001/oidc/callback/
   Web Origins: http://localhost:8001
   ```

   **Client D2:**
   ```
   Client ID: d2-client  
   Client Secret: d2-secret-mvp
   Valid Redirect URIs: http://localhost:8002/oidc/callback/
   Web Origins: http://localhost:8002
   ```

   **Client D3:**
   ```
   Client ID: d3-client
   Client Secret: d3-secret-mvp
   Valid Redirect URIs: http://localhost:8003/oidc/callback/
   Web Origins: http://localhost:8003
   ```

4. **Configurar Custom User Storage (D1 SPI):**
   - Ir a "User Federation" 
   - Agregar provider "d1-user-storage"
   - Configurar:
     ```
     D1 Base URL: http://d1:8001
     Internal API Key: internal-api-key-super-secret-mvp
     ```

## 🧪 Pruebas del Sistema

### Verificar Conexiones

```bash
# Verificar APIs
curl -s -w "D1: %{http_code}\n" http://localhost:8001/ -o /dev/null
curl -s -w "D2: %{http_code}\n" http://localhost:8002/ -o /dev/null  
curl -s -w "D3: %{http_code}\n" http://localhost:8003/ -o /dev/null
curl -s -w "Keycloak: %{http_code}\n" http://localhost:8080/ -o /dev/null

# Verificar API interna D1
curl -s -H "X-Internal-Api-Key: internal-api-key-super-secret-mvp" \
     -H "Content-Type: application/json" \
     -d '{"username":"user_d1_d3","password":"pass123"}' \
     http://localhost:8001/api/internal/auth/verify/
```

**Resultado esperado:** Status 302 para todos + JSON válido para la API.

### Probar Flujo SSO Completo

#### Escenario 1: Usuario sin acceso
1. Navegar a: `http://localhost:8002/oidc/authenticate/`
2. Login en Keycloak con: `user_no_access` / `pass123`
3. **Resultado esperado:** Pantalla "Sin acceso a ninguna aplicación"

#### Escenario 2: Usuario con acceso único
1. Navegar a: `http://localhost:8002/oidc/authenticate/`
2. Login en Keycloak con: `user_d1_only` / `pass123`
3. **Resultado esperado:** Redirección automática a D1

#### Escenario 3: Usuario con acceso múltiple
1. Navegar a: `http://localhost:8002/oidc/authenticate/`
2. Login en Keycloak con: `user_d1_d3` / `pass123`
3. **Resultado esperado:** Pantalla de selección entre D1 y D3

#### Escenario 4: Usuario administrador
1. Navegar a: `http://localhost:8003/oidc/authenticate/`
2. Login en Keycloak con: `user_admin` / `admin123`
3. **Resultado esperado:** Pantalla de selección entre D1, D2 y D3

## 🗂️ Estructura del Proyecto

```
sso-mvp/
├── docker-compose.yml          # Orquestación de contenedores
├── .env                        # Variables de entorno
├── mysql/init.sql             # Inicialización de bases de datos
├── keycloak/
│   ├── realm-export.json      # Configuración del realm
│   └── spi/                   # Custom SPI compilado
│       └── d1-user-storage-spi.jar
├── keycloak-spi/              # Código fuente del SPI Java
│   ├── pom.xml
│   └── src/main/java/com/empresa/spi/
├── d1/                        # Django D1 (fuente de verdad + portal)
│   ├── internal_auth/         # API interna para Keycloak
│   ├── portal/                # Portal post-login
│   └── config/settings.py     # Configuración OIDC
├── d2/                        # Django D2 (cliente OIDC)
└── d3/                        # Django D3 (cliente OIDC)
```

## 🔧 Configuraciones Importantes

### Variables de Entorno (.env)

```bash
# MySQL
MYSQL_ROOT_PASSWORD=root_secret_mvp
MYSQL_USER=sso_user
MYSQL_PASSWORD=sso_password_mvp

# Keycloak admin
KC_ADMIN_USER=admin
KC_ADMIN_PASSWORD=admin_mvp

# API key interna D1 ↔ Keycloak SPI
INTERNAL_API_KEY=internal-api-key-super-secret-mvp

# Client secrets OIDC
D1_CLIENT_SECRET=d1-secret-mvp
D2_CLIENT_SECRET=d2-secret-mvp
D3_CLIENT_SECRET=d3-secret-mvp
```

### Puertos

- **3306**: MySQL
- **8080**: Keycloak
- **8001**: Django D1 (fuente de verdad + portal)
- **8002**: Django D2 (cliente OIDC)
- **8003**: Django D3 (cliente OIDC)

## 🐛 Troubleshooting

### Contenedores no arrancan

```bash
# Ver logs
docker compose logs -f

# Reiniciar servicios específicos
docker compose restart d1 d2 d3
```

### Base de datos no conecta

```bash
# Verificar salud de MySQL
docker compose ps mysql

# Ejecutar migraciones manualmente
docker exec sso-d1 python manage.py migrate
```

### Keycloak no carga SPI

```bash
# Verificar JAR
docker exec sso-keycloak ls -la /opt/keycloak/providers/

# Recompilar SPI
cd keycloak-spi && mvn clean package
cp target/d1-user-storage-spi-1.0.0.jar ../keycloak/spi/d1-user-storage-spi.jar
```

### Custom SPI no conecta con D1

```bash
# Probar API interna manualmente
curl -H "X-Internal-Api-Key: internal-api-key-super-secret-mvp" \
     -H "Content-Type: application/json" \
     -d '{"username":"testuser","password":"testpass"}' \
     http://localhost:8001/api/internal/auth/verify/
```

## ⚡ Comandos Útiles

```bash
# Limpiar todo y empezar de cero
docker compose down -v
docker compose up -d --build

# Logs en tiempo real
docker compose logs -f

# Ejecutar shell en contenedores
docker exec -it sso-d1 python manage.py shell
docker exec -it sso-d1 bash

# Reiniciar servicios específicos
docker compose restart d1 d2 d3 keycloak
```

## 🎯 Flujo SSO Completo

```
1. Usuario accede → D2/D3 (:8002/:8003)
2. Redirección → Keycloak (:8080)
3. Login → Keycloak valida credenciales
4. Custom SPI → Consulta API interna D1
5. D1 API → Retorna usuario + roles
6. Keycloak → Autentica y redirije
7. Portal D1 → Analiza roles del usuario:
   ├─ Sin roles → Pantalla "no access"
   ├─ 1 rol → Redirección automática
   └─ 2+ roles → Pantalla de selección
8. Usuario → Accede a la aplicación elegida
```

---

**🚀 ¡Sistema SSO completamente funcional con portal inteligente!**
