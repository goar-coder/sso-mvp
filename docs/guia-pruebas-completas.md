# 🧪 Guía de Pruebas Manuales Completas - Sistema SSO MVP

> **Objetivo:** Validar manualmente TODA la funcionalidad del sistema SSO, incluyendo autenticación tradicional, portal de selección, permisos, y todas las aplicaciones.

## 📋 **Prerequisitos Generales**

### ✅ **1. Sistema Levantado**
```bash
cd /home/goar/projects/keycloak/sso-mvp

# Verificar todos los contenedores
docker compose ps

# Estado esperado: Todos "Up"
NAME           STATUS                   PORTS
sso-d1         Up X minutes             0.0.0.0:8001->8001/tcp
sso-d2         Up X minutes             0.0.0.0:8002->8002/tcp  
sso-d3         Up X minutes             0.0.0.0:8003->8003/tcp
sso-keycloak   Up X minutes             0.0.0.0:8080->8080/tcp
sso-mysql      Up X minutes (healthy)   0.0.0.0:3306->3306/tcp
```

### ✅ **2. Conexiones Básicas**
```bash
# Verificar APIs responden
curl -s -w "D1: %{http_code}\n" http://localhost:8001/ -o /dev/null
curl -s -w "D2: %{http_code}\n" http://localhost:8002/ -o /dev/null  
curl -s -w "D3: %{http_code}\n" http://localhost:8003/ -o /dev/null
curl -s -w "Keycloak: %{http_code}\n" http://localhost:8080/ -o /dev/null

# Resultado esperado: Todos 200 o 302
```

### ✅ **3. Usuarios de Prueba Creados**
```bash
# Verificar usuarios existen
docker exec sso-d1 python manage.py shell -c "
from django.contrib.auth.models import User
users = ['user_no_access', 'user_d1_only', 'user_d1_d3', 'user_admin']
for username in users:
    user = User.objects.filter(username=username).first()
    if user:
        groups = [g.name for g in user.groups.all()]
        print(f'✓ {username}: {groups}')
    else:
        print(f'❌ {username}: No existe')
"
```

### ✅ **4. Keycloak Admin Accesible**
- URL: http://localhost:8080/admin/
- Usuario: `admin`
- Password: `admin_mvp`

---

## 🚀 **PRUEBA 1: Funcionamiento Básico de Aplicaciones**

### 🎯 **Objetivo:** Verificar que las 3 apps Django funcionan independientemente

### **📱 D1 (Puerto 8001) - App Principal + Portal**

1. **Navegar a:** http://localhost:8001/
2. **Resultado esperado:**
   - ✅ Página carga correctamente
   - ✅ Muestra botón/link de login OIDC
   - ✅ NO está autenticado aún

3. **Navegar a:** http://localhost:8001/admin/
4. **Resultado esperado:**
   - ✅ Django Admin accesible
   - ✅ Usuarios y grupos visibles

### **📱 D2 (Puerto 8002) - Cliente OIDC**

1. **Navegar a:** http://localhost:8002/
2. **Resultado esperado:**
   - ✅ Página carga correctamente  
   - ✅ Muestra botón/link de login OIDC
   - ✅ NO está autenticado aún

### **📱 D3 (Puerto 8003) - Cliente OIDC**

1. **Navegar a:** http://localhost:8003/
2. **Resultado esperado:**
   - ✅ Página carga correctamente
   - ✅ Muestra botón/link de login OIDC  
   - ✅ NO está autenticado aún

---

## 🚀 **PRUEBA 2: Usuario SIN ACCESO (Pantalla No Access)**

### 🎯 **Objetivo:** Verificar que usuarios sin permisos ven la pantalla de "Sin acceso"

### **📝 Usuario de Prueba:**
- **Username:** `user_no_access`
- **Password:** `pass123`
- **Grupos:** Ninguno
- **Acceso esperado:** Ninguna aplicación

### **🔄 Flujo Desde D1:**

1. **Abrir navegador en modo incógnito**
2. **Navegar a:** http://localhost:8001/oidc/authenticate/
3. **Resultado:** Redirección a Keycloak login
4. **Login con:** `user_no_access` / `pass123`
5. **Resultado esperado:**
   - ✅ Login exitoso en Keycloak
   - ✅ Redirección de vuelta a D1
   - ✅ **Pantalla "Sin acceso a ninguna aplicación"**
   - ✅ Usuario logueado pero sin opciones

### **🔄 Flujo Desde D2:**

1. **Nuevo navegador incógnito**
2. **Navegar a:** http://localhost:8002/oidc/authenticate/
3. **Login con:** `user_no_access` / `pass123`
4. **Resultado esperado:**
   - ✅ Login exitoso en Keycloak
   - ✅ Redirección a portal D1
   - ✅ **Pantalla "Sin acceso a ninguna aplicación"**

### **🔄 Flujo Desde D3:**

1. **Nuevo navegador incógnito** 
2. **Navegar a:** http://localhost:8003/oidc/authenticate/
3. **Login con:** `user_no_access` / `pass123`
4. **Resultado esperado:** Igual que D2

---

## 🚀 **PRUEBA 3: Usuario CON ACCESO ÚNICO (Redirección Automática)**

### 🎯 **Objetivo:** Usuario con 1 sola app debe ir directo (sin selector)

### **📝 Usuario de Prueba:**
- **Username:** `user_d1_only`
- **Password:** `pass123`
- **Grupos:** `app-d1`
- **Acceso esperado:** Solo D1

### **🔄 Flujo Desde D2 (Usuario va directo a D1):**

1. **Navegador incógnito**
2. **Navegar a:** http://localhost:8002/oidc/authenticate/
3. **Login con:** `user_d1_only` / `pass123`
4. **Resultado esperado:**
   - ✅ Login exitoso
   - ✅ Portal detecta 1 sola app
   - ✅ **Redirección AUTOMÁTICA a D1**
   - ✅ **NO aparece selector**
   - ✅ Usuario dentro de D1 autenticado

### **🔄 Flujo Desde D3 (Usuario va directo a D1):**

1. **Navegador incógnito**
2. **Navegar a:** http://localhost:8003/oidc/authenticate/  
3. **Login con:** `user_d1_only` / `pass123`
4. **Resultado esperado:**
   - ✅ Igual que desde D2
   - ✅ **Redirección automática a D1**

### **🔄 Flujo Desde D1 (Usuario queda en D1):**

1. **Navegador incógnito**
2. **Navegar a:** http://localhost:8001/oidc/authenticate/
3. **Login con:** `user_d1_only` / `pass123`
4. **Resultado esperado:**
   - ✅ Login exitoso
   - ✅ **Usuario dentro de D1** directamente

---

## 🚀 **PRUEBA 4: Usuario CON ACCESO MÚLTIPLE (Portal de Selección)**

### 🎯 **Objetivo:** Usuario con 2+ apps debe ver selector

### **📝 Usuario de Prueba:**
- **Username:** `user_d1_d3` 
- **Password:** `pass123`
- **Grupos:** `app-d1`, `app-d3`
- **Acceso esperado:** D1 y D3

### **🔄 Flujo de Selección:**

1. **Navegador incógnito**
2. **Navegar a:** http://localhost:8002/oidc/authenticate/
3. **Login con:** `user_d1_d3` / `pass123`
4. **Resultado esperado:**
   - ✅ Login exitoso
   - ✅ Redirección a portal D1
   - ✅ **Pantalla de selección aparece**
   - ✅ **Solo 2 opciones:** D1 y D3
   - ✅ **NO aparece D2** (sin permisos)

5. **Hacer clic en "Acceder a D1"**
6. **Resultado esperado:**
   - ✅ Redirección a D1
   - ✅ Usuario autenticado dentro de D1

7. **Volver y hacer clic en "Acceder a D3"**
8. **Resultado esperado:**
   - ✅ Redirección a D3
   - ✅ Usuario autenticado dentro de D3

### **🔄 Verificar Restricciones:**

1. **Intentar acceso directo a D2:** http://localhost:8002/
2. **Resultado esperado:**
   - ✅ **Acceso DENEGADO** o redirección a portal
   - ✅ Usuario NO puede acceder a D2

---

## 🚀 **PRUEBA 5: Usuario ADMIN (Acceso Completo)**

### 🎯 **Objetivo:** Usuario con todos los permisos ve todas las opciones

### **📝 Usuario de Prueba:**
- **Username:** `user_admin`
- **Password:** `admin123`
- **Grupos:** `app-d1`, `app-d2`, `app-d3`
- **Acceso esperado:** Todas las aplicaciones

### **🔄 Flujo Completo:**

1. **Navegador incógnito**
2. **Navegar a:** http://localhost:8001/oidc/authenticate/
3. **Login con:** `user_admin` / `admin123`
4. **Resultado esperado:**
   - ✅ Login exitoso
   - ✅ **Portal de selección completo**
   - ✅ **3 opciones:** D1, D2, D3
   - ✅ Todas clickeables

5. **Probar acceso a D1:** Clic en "Acceder a D1"
   - ✅ Redirección exitosa a D1

6. **Volver y probar D2:** Clic en "Acceder a D2"
   - ✅ Redirección exitosa a D2

7. **Volver y probar D3:** Clic en "Acceder a D3"
   - ✅ Redirección exitosa a D3

---

## 🚀 **PRUEBA 6: API Interna D1 <-> Keycloak SPI**

### 🎯 **Objetivo:** Verificar que la API interna funciona (Custom SPI)

### **📝 Test de Usuario Válido:**

```bash
curl -X POST http://localhost:8001/api/internal/auth/verify/ \
  -H "Content-Type: application/json" \
  -H "X-Internal-Api-Key: internal-api-key-super-secret-mvp" \
  -d '{"username": "user_admin", "password": "admin123"}'
```

**Resultado esperado:**
```json
{
    "success": true,
    "user": {
        "id": X,
        "username": "user_admin",
        "email": "admin@test.com",
        "first_name": "Super",
        "last_name": "Admin"
    },
    "groups": ["app-d1", "app-d2", "app-d3"]
}
```

### **📝 Test de Usuario Inválido:**

```bash
curl -X POST http://localhost:8001/api/internal/auth/verify/ \
  -H "Content-Type: application/json" \
  -H "X-Internal-Api-Key: internal-api-key-super-secret-mvp" \
  -d '{"username": "inexistente", "password": "wrong"}'
```

**Resultado esperado:**
```json
{
    "success": false,
    "error": "Invalid credentials"
}
```

### **📝 Test Sin API Key:**

```bash
curl -X POST http://localhost:8001/api/internal/auth/verify/ \
  -H "Content-Type: application/json" \
  -d '{"username": "user_admin", "password": "admin123"}'
```

**Resultado esperado:**
```
HTTP 403 Forbidden
```

---

## 🚀 **PRUEBA 7: Persistencia de Sesión (SSO Real)**

### 🎯 **Objetivo:** Una vez logueado, acceder a otras apps sin volver a hacer login

### **🔄 Flujo de SSO:**

1. **Navegador normal (NO incógnito)**

2. **Login inicial desde D2:**
   - Navegar: http://localhost:8002/oidc/authenticate/
   - Login: `user_admin` / `admin123`
   - Seleccionar: D2
   - **Usuario dentro de D2**

3. **Acceso a D1 (misma sesión):**
   - **Nueva pestaña:** http://localhost:8001/oidc/authenticate/
   - **Resultado esperado:** 
     - ✅ **NO pide login** (ya autenticado)
     - ✅ Portal de selección aparece directamente
     - ✅ Selección de D1 funciona

4. **Acceso a D3 (misma sesión):**
   - **Nueva pestaña:** http://localhost:8003/oidc/authenticate/
   - **Resultado esperado:**
     - ✅ **NO pide login** (ya autenticado) 
     - ✅ Portal de selección aparece directamente
     - ✅ Selección de D3 funciona

### **🔄 Test de Logout:**

5. **Logout desde Keycloak:**
   - Navegar: http://localhost:8080/realms/django-realm/protocol/openid-connect/logout
   - **Resultado esperado:** Sesión cerrada

6. **Intentar acceso nuevamente:**
   - Navegar: http://localhost:8001/oidc/authenticate/
   - **Resultado esperado:**
     - ✅ **Pide login** de nuevo
     - ✅ Ciclo completo de autenticación

---

## 🚀 **PRUEBA 8: Custom SPI en Keycloak Admin**

### 🎯 **Objetivo:** Verificar que el SPI está correctamente configurado

### **📝 Verificación en Admin Console:**

1. **Acceder:** http://localhost:8080/admin/
2. **Login:** admin / admin_mvp
3. **Ir a:** User Federation
4. **Resultado esperado:**
   - ✅ Provider "d1-user-storage" existe
   - ✅ Estado "Enabled"
   - ✅ Configuración:
     ```
     D1 Base URL: http://d1:8001
     Internal API Key: internal-api-key-super-secret-mvp
     ```

### **📝 Test User Sync:**

1. **En User Federation → d1-user-storage**
2. **Clic en "Sync all users"**
3. **Resultado esperado:**
   - ✅ Proceso completa sin errores
   - ✅ Mensaje de sync exitoso

---

## 🚀 **PRUEBA 9: Validación de Client Secrets**

### 🎯 **Objetivo:** Verificar que los clients OIDC tienen los secrets correctos

### **📝 Verificar Client Secrets:**

1. **Acceder:** http://localhost:8080/admin/
2. **Ir a:** Clients

3. **Client: d1-client**
   - **Secret esperado:** `9LH147ogjTBktGVrHo1cccdXKq424dF1`
   - ✅ Configuración OIDC correcta

4. **Client: d2-client**  
   - **Secret esperado:** `OMjvCgixedkHDGIkNmTcrbl2bohQ3lmc`
   - ✅ Configuración OIDC correcta

5. **Client: d3-client**
   - **Secret esperado:** `0tLDiDk8qbd1O6nZT1EYjkm0mwWIlGfX` 
   - ✅ Configuración OIDC correcta

---

## 🚀 **PRUEBA 10: Logs y Debugging**

### 🎯 **Objetivo:** Verificar que los logs muestran información útil

### **📝 Logs Durante Login:**

```bash
# En una terminal, seguir logs mientras haces login
docker compose logs -f sso-keycloak sso-d1

# Luego hacer login desde navegador
# Buscar en los logs:
```

**Keycloak logs esperados:**
```
INFO [org.keycloak.authentication] User 'user_admin' authenticated
```

**D1 logs esperados:**
```
INFO "POST /api/internal/auth/verify/ HTTP/1.1" 200
INFO "POST /oidc/callback/ HTTP/1.1" 302  
INFO "GET /portal/selector/ HTTP/1.1" 200
```

---

## 🔍 **Troubleshooting por Síntomas**

### ❌ **"Aplicación no carga (500/404)"**

```bash
# Verificar contenedor
docker compose ps [servicio]

# Ver logs específicos
docker compose logs [servicio]

# Verificar conexión directa
curl -I http://localhost:[puerto]/
```

### ❌ **"Login exitoso pero sin portal de selección"**

**Verificar:**
1. Usuario tiene grupos asignados
2. D1 recibe correctamente el callback OIDC
3. Logs de D1 durante callback

```bash
# Verificar grupos del usuario
docker exec sso-d1 python manage.py shell -c "
from django.contrib.auth.models import User
user = User.objects.get(username='user_admin')
print([g.name for g in user.groups.all()])
"
```

### ❌ **"Portal muestra apps incorrectas"**

**Verificar grupos:**
```bash
# Ver todos los grupos
docker exec sso-d1 python manage.py shell -c "
from django.contrib.auth.models import Group
for g in Group.objects.all():
    print(f'{g.name}: {[u.username for u in g.user_set.all()]}')
"
```

### ❌ **"Custom SPI no funciona"**

```bash
# Verificar JAR deployado
docker exec sso-keycloak ls -la /opt/keycloak/providers/

# Logs de Keycloak durante login
docker compose logs sso-keycloak | grep -i "d1.*storage"

# Test API directamente 
curl -X POST http://localhost:8001/api/internal/auth/verify/ \
  -H "Content-Type: application/json" \
  -H "X-Internal-Api-Key: internal-api-key-super-secret-mvp" \
  -d '{"username":"user_admin","password":"admin123"}'
```

---

## ✅ **Checklist Final Completo**

### **🔧 Infraestructura:**
- [ ] Todos los contenedores UP y healthy
- [ ] Todas las aplicaciones responden (8001, 8002, 8003, 8080)
- [ ] Bases de datos conectadas
- [ ] Migraciones Django aplicadas

### **🔐 Autenticación Básica:**
- [ ] Keycloak Admin Console accesible
- [ ] Custom SPI deployado y configurado
- [ ] API interna D1 responde correctamente
- [ ] Client Secrets configurados

### **👥 Usuarios y Permisos:**
- [ ] Usuario sin acceso → Pantalla "No access"
- [ ] Usuario con 1 app → Redirección automática  
- [ ] Usuario con 2+ apps → Portal de selección
- [ ] Usuario admin → Acceso completo a todas las apps

### **🔄 Flujo SSO:**
- [ ] Login funciona desde cualquier app
- [ ] Redirección post-login correcta
- [ ] Portal de selección muestra opciones correctas
- [ ] Acceso a apps seleccionadas funciona
- [ ] Persistencia de sesión entre apps

### **🚀 Funcionalidades Avanzadas:**
- [ ] SSO real (login una vez, acceso a todas)
- [ ] Logout global funciona
- [ ] Custom SPI valida usuarios correctamente
- [ ] API keys requeridas y validadas

### **🐛 Debugging:**
- [ ] Logs informativos y útiles
- [ ] Errores se muestran apropiadamente
- [ ] Fallbacks funcionan cuando algo falla

---

## 🎉 **Resultado Final Esperado**

Al completar todas las pruebas exitosamente, tendrás:

1. ✅ **Sistema SSO completamente funcional**
2. ✅ **3 aplicaciones Django integradas**  
3. ✅ **Portal de selección inteligente**
4. ✅ **4 tipos de usuarios con diferentes permisos**
5. ✅ **Custom SPI funcionando**
6. ✅ **Autenticación tradicional + token**
7. ✅ **Logs útiles para debugging**

**🚀 ¡Sistema SSO MVP completamente operativo y validado!**