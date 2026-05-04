# Configuración Manual de Keycloak para Autenticación por Token

Este documento describe los pasos necesarios para configurar manualmente Keycloak Admin Console para habilitar la autenticación por token D1.

## Prerrequisitos

- Keycloak funcionando con el realm `django-realm`
- SPI `d1-user-storage-spi.jar` desplegado en `/opt/keycloak/providers/`
- Admin credentials: `admin` / `admin_mvp`

## 1. Acceso a Keycloak Admin Console

1. Navegar a: `http://localhost:8080/admin`
2. **Login:**
   - Username: `admin`
   - Password: `admin_mvp`
3. **Seleccionar realm:** `django-realm` (no Master)

## 2. Crear Clientes OIDC

Los siguientes clientes deben crearse para cada aplicación Django:

### 2.1 Cliente d1-client

1. **Menú lateral:** `Clients`
2. **Click:** `Create client`
3. **Configuración inicial:**
   - **Client type:** `OpenID Connect`
   - **Client ID:** `d1-client`
   - **Click:** `Next`
4. **Capability config:**
   - **Client authentication:** `ON`
   - **Click:** `Next`
5. **Login settings:**
   - **Root URL:** `http://localhost:8001`
   - **Home URL:** `http://localhost:8001`
   - **Valid redirect URIs:** `http://localhost:8001/oidc/callback/*`
   - **Web origins:** `http://localhost:8001`
   - **Click:** `Save`
6. **Configurar Client Secret:**
   - **Ir a pestaña:** `Credentials`
   - **Verificar Client Secret:** `9LH147ogjTBktGVrHo1cccdXKq424dF1`
   - Si no coincide, regenerar o configurar manualmente

### 2.2 Cliente d2-client

1. **Repetir proceso anterior con:**
   - **Client ID:** `d2-client`
   - **Root URL:** `http://localhost:8002`
   - **Home URL:** `http://localhost:8002`
   - **Valid redirect URIs:** `http://localhost:8002/oidc/callback/*`
   - **Web origins:** `http://localhost:8002`
   - **Client Secret:** `OMjvCgixedkHDGIkNmTcrbl2bohQ3lmc`

### 2.3 Cliente d3-client

1. **Repetir proceso anterior con:**
   - **Client ID:** `d3-client`
   - **Root URL:** `http://localhost:8003`
   - **Home URL:** `http://localhost:8003`
   - **Valid redirect URIs:** `http://localhost:8003/oidc/callback/*`
   - **Web origins:** `http://localhost:8003`
   - **Client Secret:** `0tLDiDk8qbd1O6nZT1EYjkm0mwWIlGfX`

## 3. Configurar Authentication Flow

### 3.1 Duplicar Browser Flow

1. **Menú lateral:** `Authentication`
2. **Pestaña:** `Flows`
3. **Localizar flow:** `browser`
4. **En columna Actions, click dropdown:** `Actions` → `Duplicate`
5. **Configurar:**
   - **Alias:** `browser-token-auth`
   - **Description:** `Browser flow with D1 token authentication support`
   - **Click:** `Ok`

### 3.2 Añadir D1 Token Authenticator

1. **Click en el flow:** `browser-token-auth`
2. **Verificar estructura actual:**
   ```
   browser-token-auth
   ├── Cookie (Alternative)
   ├── Kerberos (Disabled)
   ├── Identity Provider Redirector (Alternative)
   └── browser-token-auth forms (Alternative)
   ```
3. **Añadir authenticator:**
   - **Click botón:** `Add step`
   - **Buscar en lista:** `D1 Token Authenticator`
   - **Seleccionar y click:** `Add`

### 3.3 Configurar Requirement y Orden

1. **Configurar D1 Token Authenticator:**
   - **Requirement:** `Alternative` (dropdown al lado derecho)

2. **Ordenar con flechas ↑ ↓ para obtener:**
   ```
   browser-token-auth
   ├── Cookie (Alternative)
   ├── D1 Token Authenticator (Alternative)  ← Segundo lugar
   ├── Kerberos (Disabled)
   ├── Identity Provider Redirector (Alternative)
   └── browser-token-auth forms (Alternative)
   ```

### 3.4 Establecer como Default Browser Flow

**Opción A: Binding Flow (Método Recomendado)**

1. **Click en tres puntos** `⋮` del flow `browser-token-auth`
2. **Seleccionar:** `Bind flow`
3. **Configurar:**
   - **Flow Type:** `Browser`
   - **Click:** `Bind`

**Opción B: Realm Settings (Alternativo)**

1. **Menú lateral:** `Realm Settings`
2. **Pestaña:** `Login`
3. **Browser Flow:** cambiar de `browser` a `browser-token-auth`
4. **Click:** `Save`

## 4. Verificación de la Configuración

### 4.1 Verificar Flow Status

En `Authentication → Flows`, el flow `browser-token-auth` debe aparecer como:
```
browser-token-auth (Used by browser)
```

### 4.2 Verificar Authentication Logic

Con la configuración completada, la lógica de autenticación será:

1. **Cookie** - Si existe sesión activa → login automático
2. **D1 Token Authenticator** - Si existe `d1_token` en URL → autenticación por token
3. **Identity Provider Redirector** - SSO externo
4. **Username/Password Forms** - Formulario tradicional

## 5. Pruebas de Funcionalidad

### 5.1 Autenticación por Token

```bash
# Debe autenticar automáticamente sin formulario de login
curl "http://localhost:8001/?d1_token=token_goar"
```

### 5.2 Autenticación Tradicional

```bash
# Debe mostrar formulario de login
curl "http://localhost:8001/"
```

## 6. Troubleshooting

### 6.1 El authenticator no aparece en la lista

**Verificar:**
- SPI desplegado en `/opt/keycloak/providers/`
- Keycloak reiniciado después del despliegue
- Logs de Keycloak: `KC-SERVICES0047: d1-token-authenticator`

### 6.2 Token no se procesa

**Verificar:**
- Flow configurado como "Used by browser"
- D1 Token Authenticator en posición correcta (segundo lugar)
- Requirement configurado como "Alternative"

### 6.3 Error "Client not found"

**Verificar:**
- Cliente existe con Client ID exacto
- Client Secret coincide con el configurado en .env
- Valid redirect URIs configuradas correctamente

## 7. Configuración de Referencia

### 7.1 Client Secrets (archivo .env)

```dotenv
D1_CLIENT_SECRET=9LH147ogjTBktGVrHo1cccdXKq424dF1
D2_CLIENT_SECRET=OMjvCgixedkHDGIkNmTcrbl2bohQ3lmc
D3_CLIENT_SECRET=0tLDiDk8qbd1O6nZT1EYjkm0mwWIlGfX
```

### 7.2 Flow Final Configurado

```
browser-token-auth (Used by browser)
├── Cookie (Alternative)
├── D1 Token Authenticator (Alternative)
├── Kerberos (Disabled)
├── Identity Provider Redirector (Alternative)
└── browser-token-auth forms (Alternative)
    ├── Username Password Form (Required)
    └── browser-token-auth Browser - Conditional OTP (Conditional)
        ├── Condition - user configured (Required)
        └── OTP Form (Required)
```

## 8. URLs de Prueba

- **Admin Console:** `http://localhost:8080/admin`
- **Autenticación por token:** `http://localhost:8001/?d1_token=token_goar`
- **Autenticación tradicional:** `http://localhost:8001/`

---

**Nota:** Esta configuración habilita autenticación por token como alternativa al método tradicional username/password, manteniendo los mismos privilegios y sesiones unificadas.