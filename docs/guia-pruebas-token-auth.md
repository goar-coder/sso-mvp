# 🧪 Guía de Pruebas Manuales - Autenticación por Token

> **Objetivo:** Validar manualmente que la autenticación por token funciona correctamente en todos los escenarios posibles.

## 📋 **Prerequisitos**

Antes de empezar las pruebas, verifica que tienes:

### ✅ Sistema Levantado
```bash
# Verificar contenedores
docker compose ps

# Estado esperado: Todos "Up"
# sso-d1, sso-d2, sso-d3, sso-keycloak, sso-mysql
```

### ✅ Usuarios de Prueba Existentes
```bash
# Verificar que existen los usuarios
docker exec sso-d1 python manage.py shell -c "
from django.contrib.auth.models import User
users = ['goar', 'user_d1_only', 'user_d1_d3', 'user_admin']
for username in users:
    exists = User.objects.filter(username=username).exists()
    print(f'Usuario {username}: {'✓ Existe' if exists else '❌ No existe'}')
"
```

### ✅ SPI Deployado
```bash
# Verificar JAR en Keycloak
docker exec sso-keycloak ls -la /opt/keycloak/providers/ | grep d1-user-storage

# Debe mostrar: d1-user-storage-spi.jar
```

### ✅ Authentication Flow Configurado
- Acceder: http://localhost:8080/admin/
- Login: admin / admin_mvp
- Ir a: **Authentication** → **Browser flows** 
- Verificar que el flow por defecto es: **browser-token-auth**

---

## 🚀 **Prueba 1: Token Válido Básico**

### 🎯 **Objetivo:** Verificar que un token válido autentica correctamente

### 📝 **Pasos:**

1. **Abrir navegador en modo incógnito/privado**

2. **Navegar a URL con token:**
   ```
   http://localhost:8080/realms/django-realm/protocol/openid-connect/auth?response_type=code&scope=openid+email&client_id=d1-client&redirect_uri=http%3A%2F%2Flocalhost%3A8001%2Foidc%2Fcallback%2F&d1_token=token_goar
   ```

3. **Resultado esperado:**
   - ✅ **NO aparece formulario de login**
   - ✅ **Redirección automática** a D1
   - ✅ **Usuario logueado como 'goar'**
   - ✅ **Portal de selección** (si goar tiene múltiples apps)

### 🔍 **Verificación en Logs:**
```bash
# Ver logs de Keycloak
docker compose logs sso-keycloak | tail -20

# Buscar líneas como:
# "D1TokenAuthenticator: Processing token authentication"
# "D1TokenAuthenticator: Authentication successful for user: goar"
```

---

## 🚀 **Prueba 2: Token Inválido**

### 🎯 **Objetivo:** Verificar fallback a login tradicional

### 📝 **Pasos:**

1. **Abrir navegador en modo incógnito/privado**

2. **Navegar a URL con token inválido:**
   ```
   http://localhost:8080/realms/django-realm/protocol/openid-connect/auth?response_type=code&scope=openid+email&client_id=d1-client&redirect_uri=http%3A%2F%2Flocalhost%3A8001%2Foidc%2Fcallback%2F&d1_token=token_usuario_inexistente
   ```

3. **Resultado esperado:**
   - ✅ **Aparece formulario de login** tradicional
   - ✅ **Mensaje de error** (opcional)
   - ✅ **Posibilidad de login normal** con usuario/password

### 🔍 **Verificación en Logs:**
```bash
docker compose logs sso-keycloak | tail -20

# Buscar líneas como:
# "D1TokenAuthenticator: Token validation failed"
# "D1TokenAuthenticator: Skipping token authentication"
```

---

## 🚀 **Prueba 3: Sin Token (Comportamiento Normal)**

### 🎯 **Objetivo:** Verificar que sin token funciona como antes

### 📝 **Pasos:**

1. **Abrir navegador en modo incógnito/privado**

2. **Navegar a URL normal (sin d1_token):**
   ```
   http://localhost:8002/oidc/authenticate/
   ```

3. **Resultado esperado:**
   - ✅ **Formulario de login** aparece normalmente
   - ✅ **Login tradicional** funciona
   - ✅ **Flujo SSO** normal

4. **Hacer login con:** `goar` / `pass123`

5. **Verificar redirección:** Portal de selección o app específica

---

## 🚀 **Prueba 4: Token desde Apps Cliente (D2/D3)**

### 🎯 **Objetivo:** Verificar token desde aplicaciones cliente

### 📝 **Pasos - Desde D2:**

1. **Navegar a:**
   ```
   http://localhost:8002/oidc/authenticate/?d1_token=token_goar
   ```

2. **Resultado esperado:**
   - ✅ **Login automático**
   - ✅ **Redirección a portal D1**
   - ✅ **Análisis de permisos**
   - ✅ **Acceso según roles**

### 📝 **Pasos - Desde D3:**

1. **Navegar a:**
   ```
   http://localhost:8003/oidc/authenticate/?d1_token=token_goar
   ```

2. **Resultado esperado:** Igual que D2

---

## 🚀 **Prueba 5: Diferentes Usuarios con Token**

### 🎯 **Objetivo:** Verificar tokens de diferentes usuarios

### 📝 **Escenario A - Usuario con acceso único:**

1. **URL:**
   ```
   http://localhost:8080/realms/django-realm/protocol/openid-connect/auth?response_type=code&scope=openid+email&client_id=d1-client&redirect_uri=http%3A%2F%2Flocalhost%3A8001%2Foidc%2Fcallback%2F&d1_token=token_user_d1_only
   ```

2. **Resultado esperado:**
   - ✅ **Login automático como 'user_d1_only'**
   - ✅ **Redirección directa** a D1 (sin selector)

### 📝 **Escenario B - Usuario con múltiples accesos:**

1. **URL:**
   ```
   http://localhost:8080/realms/django-realm/protocol/openid-connect/auth?response_type=code&scope=openid+email&client_id=d1-client&redirect_uri=http%3A%2F%2Flocalhost%3A8001%2Foidc%2Fcallback%2F&d1_token=token_user_d1_d3
   ```

2. **Resultado esperado:**
   - ✅ **Login automático como 'user_d1_d3'**
   - ✅ **Portal de selección** entre D1 y D3

### 📝 **Escenario C - Usuario admin:**

1. **URL:**
   ```
   http://localhost:8080/realms/django-realm/protocol/openid-connect/auth?response_type=code&scope=openid+email&client_id=d1-client&redirect_uri=http%3A%2F%2Flocalhost%3A8001%2Foidc%2Fcallback%2F&d1_token=token_user_admin
   ```

2. **Resultado esperado:**
   - ✅ **Login automático como 'user_admin'**
   - ✅ **Portal completo** con D1, D2, D3

---

## 🚀 **Prueba 6: Testing con curl**

### 🎯 **Objetivo:** Verificar comportamiento programático

### 📝 **Token Válido:**

```bash
curl -i -X GET "http://localhost:8080/realms/django-realm/protocol/openid-connect/auth?response_type=code&scope=openid+email&client_id=d1-client&redirect_uri=http%3A%2F%2Flocalhost%3A8001%2Foidc%2Fcallback%2F&d1_token=token_goar" --max-redirs 3
```

**Resultado esperado:**
```
HTTP/1.1 302 Found
Location: http://localhost:8001/oidc/callback/?code=...
```

### 📝 **Token Inválido:**

```bash
curl -i -X GET "http://localhost:8080/realms/django-realm/protocol/openid-connect/auth?response_type=code&scope=openid+email&client_id=d1-client&redirect_uri=http%3A%2F%2Flocalhost%3A8001%2Foidc%2Fcallback%2F&d1_token=token_invalid" --max-redirs 0
```

**Resultado esperado:**
```
HTTP/1.1 200 OK
Content-Type: text/html
# Contiene formulario de login
```

---

## 🚀 **Prueba 7: Verificar API Endpoint Directamente**

### 🎯 **Objetivo:** Validar que el endpoint D1 funciona

### 📝 **Token Válido:**

```bash
curl -X POST http://localhost:8001/api/internal/auth/verify-token/ \
  -H "Content-Type: application/json" \
  -H "X-Internal-Api-Key: internal-api-key-super-secret-mvp" \
  -d '{"token": "token_goar"}'
```

**Resultado esperado:**
```json
{
    "success": true,
    "user": {
        "id": 1,
        "username": "goar",
        "email": "goar@example.com",
        "first_name": "Goar",
        "last_name": "User"
    },
    "groups": ["app-d1", "app-d2", "app-d3"]
}
```

### 📝 **Token Inválido:**

```bash
curl -X POST http://localhost:8001/api/internal/auth/verify-token/ \
  -H "Content-Type: application/json" \
  -H "X-Internal-Api-Key: internal-api-key-super-secret-mvp" \
  -d '{"token": "token_invalid"}'
```

**Resultado esperado:**
```json
{
    "success": false,
    "error": "Invalid token format"
}
```

---

## 🔍 **Troubleshooting Durante las Pruebas**

### ❌ **Error: "D1TokenAuthenticator no ejecuta"**

**Verificar:**
```bash
# 1. SPI deployado
docker exec sso-keycloak ls -la /opt/keycloak/providers/

# 2. Authentication Flow activo
# Acceder Admin Console → Authentication → Flows
# Verificar Default Browser Flow = "browser-token-auth"

# 3. Restart Keycloak si es necesario
docker compose restart sso-keycloak
```

### ❌ **Error: "Token válido no autentica"**

**Debug paso a paso:**
```bash
# 1. Verificar API D1
curl -X POST http://localhost:8001/api/internal/auth/verify-token/ \
  -H "Content-Type: application/json" \
  -H "X-Internal-Api-Key: internal-api-key-super-secret-mvp" \
  -d '{"token": "token_goar"}'

# 2. Ver logs Keycloak
docker compose logs sso-keycloak | grep D1TokenAuthenticator

# 3. Ver logs D1
docker compose logs sso-d1 | grep verify-token
```

### ❌ **Error: "Redirección infinita"**

**Verificar configuración OIDC:**
- Client ID correcto
- Redirect URI exacta
- Client Secret actualizado

---

## ✅ **Checklist Final de Validación**

### **Funcionalidad Básica:**
- [ ] Token válido autentica correctamente
- [ ] Token inválido hace fallback a login normal
- [ ] Sin token funciona como antes
- [ ] Logs muestran actividad del authenticator

### **Integración con Apps:**
- [ ] Token funciona desde D2
- [ ] Token funciona desde D3
- [ ] Portal de selección funciona igual
- [ ] Permisos se respetan igual

### **Usuarios Diferentes:**
- [ ] Token de usuario con 1 app → redirección directa
- [ ] Token de usuario con múltiples apps → selector
- [ ] Token de admin → selector completo
- [ ] Token de usuario inexistente → fallback

### **Seguridad:**
- [ ] API key requerida para endpoint interno
- [ ] Token mal formateado rechazado
- [ ] Token de usuario inexistente rechazado
- [ ] No bypass de autorizaciones

---

## 🎉 **Resultado Esperado Final**

Al completar todas las pruebas, deberías tener:

1. ✅ **Token authentication** funcionando perfectamente
2. ✅ **Coexistencia** con autenticación tradicional 
3. ✅ **Mismos permisos y flujos** para ambos métodos
4. ✅ **Logs detallados** para debugging
5. ✅ **Fallbacks apropiados** en caso de errores

**🚀 ¡Sistema de autenticación dual completamente operativo!**