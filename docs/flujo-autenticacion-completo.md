# FLUJO COMPLETO DE AUTENTICACIÓN SSO — D1 → KEYCLOAK → D1

## 📋 Descripción General

Cuando un usuario intenta acceder a D1 sin estar autenticado, se dispara una cadena de eventos que involucra:
- **Django D1** (aplicación cliente)
- **Keycloak** (servidor de identidad)
- **Custom SPI Java** (validador de usuarios contra D1)
- **Regreso a Django D1** (portal y selector de apps)

---

## 🔄 PASO 1: Usuario accede a D1 sin autenticación

### Evento
```
Usuario abre: http://localhost:8001/home/
```

### Código en D1 (config/urls.py)
```python
@login_required
def home(request):
    return HttpResponse(f"""
        <h1>D1 — App 1 (fuente de verdad)</h1>
        <p>Usuario: {request.user.username}</p>
        <a href="/oidc/logout/">Cerrar sesión</a>
    """)

urlpatterns = [
    path('home/', home, name='home'),
    path('', home, name='root'),
]
```

### Qué pasa
- El decorador `@login_required` verifica si existe `request.user` autenticado
- Si NO está autenticado → Django lanza redirect al `LOGIN_URL`
- Valor de `LOGIN_URL` en settings.py:
  ```python
  LOGIN_URL = '/oidc/authenticate/'
  ```

### Redirección generada
```
302 Found
Location: http://localhost:8001/oidc/authenticate/?next=/home/
```

**Parámetros enviados:**
- `next=/home/` → URL de retorno después del login exitoso

---

## 🔄 PASO 2: Django redirige a Keycloak (OIDC Authorize)

### Biblioteca: mozilla-django-oidc
La URL `/oidc/authenticate/` es manejada automáticamente por `mozilla_django_oidc.urls`

### Código en config/settings.py (OIDC Configuration)
```python
KC_BASE = 'http://keycloak:8080'
KC_REALM = f'{KC_BASE}/realms/django-realm/protocol/openid-connect'
KC_PUBLIC_BASE = 'http://localhost:8080'
KC_PUBLIC_REALM = f'{KC_PUBLIC_BASE}/realms/django-realm/protocol/openid-connect'

OIDC_RP_CLIENT_ID = 'd1-client'  # ← valor de OIDC_RP_CLIENT_ID env var
OIDC_RP_CLIENT_SECRET = config('OIDC_RP_CLIENT_SECRET')
OIDC_RP_SIGN_ALGO = 'RS256'

OIDC_OP_AUTHORIZATION_ENDPOINT = f'{KC_PUBLIC_REALM}/auth'
OIDC_OP_TOKEN_ENDPOINT = f'{KC_REALM}/token'
OIDC_OP_USER_ENDPOINT = f'{KC_REALM}/userinfo'
OIDC_OP_JWKS_ENDPOINT = f'{KC_REALM}/certs'
OIDC_OP_LOGOUT_ENDPOINT = f'{KC_PUBLIC_REALM}/logout'
```

### Redirección generada automáticamente
```
HTTP 302
Location: http://localhost:8080/realms/django-realm/protocol/openid-connect/auth?
    response_type=code
    &client_id=d1-client
    &redirect_uri=http://localhost:8001/oidc/callback/
    &scope=openid%20profile%20email
    &state=<random_state_value>
    &nonce=<random_nonce>
```

**Parámetros clave:**
- `response_type=code` → Flujo Authorization Code
- `client_id=d1-client` → Identificación de D1
- `redirect_uri=http://localhost:8001/oidc/callback/` → Callback URL
- `scope=openid profile email` → Datos solicitados
- `state=<random>` → CSRF protection
- `nonce=<random>` → Token validation

### Función ejecutada en mozilla_django_oidc
```python
# Internamente llama:
OIDCAuthenticationBackend.get_authorization_code()
  ↓
Construye los parámetros anterior
  ↓
HttpResponseRedirect(auth_endpoint_url)
```

---

## 🔄 PASO 3: Usuario ve el formulario de login de Keycloak

### Evento
El navegador ahora renderiza la UI de login de Keycloak

**URL actual:**
```
http://localhost:8080/realms/django-realm/protocol/openid-connect/auth?...
```

### En Keycloak (realm-export.json)
```json
{
  "realm": "django-realm",
  "enabled": true,
  "sslRequired": "none",
  "registrationAllowed": false,
  "defaultSignatureAlgorithm": "RS256"
}
```

### Usuario ingresa credenciales
```
username: john_doe
password: secure_password_123
```

---

## 🔄 PASO 4: Keycloak valida las credenciales contra D1 (Custom SPI)

### Evento
El usuario hace clic en "Iniciar Sesión" en Keycloak

### Código Java: D1UserStorageProviderFactory (Inicialización)

```java
public class D1UserStorageProviderFactory
        implements UserStorageProviderFactory<D1UserStorageProvider> {

    public static final String PROVIDER_ID = "d1-user-storage";

    @Override
    public D1UserStorageProvider create(KeycloakSession session, ComponentModel model) {
        // Obtiene configuración del proveedor en Keycloak Admin Console
        String d1Url = model.getConfig().getFirst("d1Url");
        // Ejemplo: "http://d1:8001"
        
        String apiKey = model.getConfig().getFirst("apiKey");
        // Ejemplo: "internal-api-key-super-secret-mvp"

        RealmModel realm = session.getContext().getRealm();
        D1ApiClient client = new D1ApiClient(d1Url, apiKey);

        return new D1UserStorageProvider(session, model, client, realm);
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        // Propiedades de configuración en Admin Console:
        // - d1Url
        // - apiKey
    }
}
```

**Configuración esperada en Keycloak Admin:**
```
Provider: "d1-user-storage"
Config:
  d1Url = "http://d1:8001"
  apiKey = "internal-api-key-super-secret-mvp"
```

### Código Java: D1UserStorageProvider (Validación)

```java
public class D1UserStorageProvider implements
        UserStorageProvider,
        UserLookupProvider,
        CredentialInputValidator {

    @Override
    public boolean isValid(RealmModel realm, UserModel user,
                           CredentialInput credentialInput) {
        String password = credentialInput.getChallengeResponse();
        
        // Llama a D1 para verificar credenciales
        D1UserData data = apiClient.verify(user.getUsername(), password);
        
        return data != null;  // ← true si válido, false si no
    }
}
```

**Flujo de validación:**
```
Keycloak auth system
  ↓
¿Existe usuario "john_doe"?
  ↓
D1UserStorageProvider.getUserByUsername("john_doe")
  ↓
D1ApiClient.findByUsername("john_doe")  [HTTP GET]
  ↓
¿Usuario encontrado en D1?
  ↓
Crear D1UserAdapter con datos del usuario
  ↓
¿Contraseña válida?
  ↓
D1UserStorageProvider.isValid()
  ↓
D1ApiClient.verify(username, password)  [HTTP POST]
```

### Código Java: D1ApiClient (Client HTTP)

#### Método 1: findByUsername() — Buscar usuario
```java
public D1UserData findByUsername(String username) {
    HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/internal/auth/user/?username=" + username))
            .header("X-Internal-Api-Key", apiKey)
            .GET()
            .timeout(Duration.ofSeconds(5))
            .build();

    return executeAndParse(request);
}
```

**HTTP Request:**
```
GET http://d1:8001/api/internal/auth/user/?username=john_doe HTTP/1.1
Host: d1:8001
X-Internal-Api-Key: internal-api-key-super-secret-mvp
Accept: application/json
```

#### Método 2: verify() — Validar credenciales
```java
public D1UserData verify(String username, String password) {
    String body = String.format(
        "{\"username\":\"%s\",\"password\":\"%s\"}",
        username.replace("\"", ""),
        password.replace("\"", "")
    );

    HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/internal/auth/verify/"))
            .header("Content-Type", "application/json")
            .header("X-Internal-Api-Key", apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .timeout(Duration.ofSeconds(5))
            .build();

    return executeAndParse(request);
}
```

**HTTP Request:**
```
POST http://d1:8001/api/internal/auth/verify/ HTTP/1.1
Host: d1:8001
Content-Type: application/json
X-Internal-Api-Key: internal-api-key-super-secret-mvp
Content-Length: 52

{"username":"john_doe","password":"secure_password_123"}
```

#### Método parseUser() — Convertir respuesta a objeto
```java
private D1UserData parseUser(JsonNode userNode) {
    if (userNode == null) return null;

    D1UserData data = new D1UserData();
    data.setId(userNode.path("id").asText());
    data.setUsername(userNode.path("username").asText());
    data.setEmail(userNode.path("email").asText());
    data.setFirstName(userNode.path("first_name").asText());
    data.setLastName(userNode.path("last_name").asText());
    data.setActive(userNode.path("is_active").asBoolean(true));

    List<String> roles = new ArrayList<>();
    JsonNode rolesNode = userNode.path("app_roles");
    if (rolesNode.isArray()) {
        rolesNode.forEach(role -> roles.add(role.asText()));
    }
    data.setAppRoles(roles);

    return data;
}
```

---

## 🔄 PASO 5: D1 valida credenciales (Django interno)

### Evento
D1 recibe las peticiones HTTP del SPI de Keycloak

### Ruta 1: GET /api/internal/auth/user/?username=...

#### Código en d1/internal_auth/views.py
```python
@require_http_methods(['GET'])
def get_user(request):
    """
    Keycloak llama aquí para buscar un usuario por username.
    GET /api/internal/auth/user/?username=...
    """
    username = request.GET.get('username', '').strip()

    if not username:
        return JsonResponse({'found': False}, status=400)

    try:
        user = User.objects.get(username=username)
    except User.DoesNotExist:
        return JsonResponse({'found': False})

    return JsonResponse({'found': True, 'user': user_to_dict(user)})
```

#### Middleware: Validación de API Key

En `d1/internal_auth/middleware.py`:
```python
class InternalApiKeyMiddleware:
    def __init__(self, get_response):
        self.get_response = get_response

    def __call__(self, request):
        if request.path.startswith('/api/internal/'):
            api_key = request.headers.get('X-Internal-Api-Key')
            if api_key != settings.INTERNAL_API_KEY:
                return JsonResponse({'error': 'Unauthorized'}, status=401)
        return self.get_response(request)
```

**Verificación:**
1. ¿Header `X-Internal-Api-Key` presente?
2. ¿Coincide con `INTERNAL_API_KEY` en settings?
   - Valor esperado: `internal-api-key-super-secret-mvp`
3. Si NO → responder 401 Unauthorized
4. Si SÍ → continuar con GET_USER

#### Función helper: user_to_dict()

```python
def user_to_dict(user):
    return {
        'id': str(user.pk),
        'username': user.username,
        'email': user.email,
        'first_name': user.first_name,
        'last_name': user.last_name,
        'is_active': user.is_active,
        'app_roles': get_app_roles(user),
    }

def get_app_roles(user):
    """Convierte los grupos de Django en roles de app."""
    group_names = user.groups.values_list('name', flat=True)
    roles = []
    for group, role in settings.GROUP_TO_APP_ROLE.items():
        if group in group_names:
            roles.append(role)
    return roles
```

**GROUP_TO_APP_ROLE en settings.py:**
```python
GROUP_TO_APP_ROLE = {
    'app-d1': 'd1-access',
    'app-d2': 'd2-access',
    'app-d3': 'd3-access',
}
```

#### HTTP Response
```json
{
  "found": true,
  "user": {
    "id": "42",
    "username": "john_doe",
    "email": "john@empresa.com",
    "first_name": "John",
    "last_name": "Doe",
    "is_active": true,
    "app_roles": ["d1-access", "d2-access"]
  }
}
```

### Ruta 2: POST /api/internal/auth/verify/

#### Código en d1/internal_auth/views.py
```python
@csrf_exempt
@require_http_methods(['POST'])
def verify_user(request):
    """
    Keycloak llama aquí para verificar credenciales.
    POST /api/internal/auth/verify/
    Body: { "username": "...", "password": "..." }
    """
    try:
        body = json.loads(request.body)
        username = body.get('username', '').strip()
        password = body.get('password', '')
    except (json.JSONDecodeError, AttributeError):
        return JsonResponse({'valid': False}, status=400)

    if not username or not password:
        return JsonResponse({'valid': False})

    # ← Autentica contra Django usando ModelBackend
    user = authenticate(request, username=username, password=password)

    if user is None or not user.is_active:
        return JsonResponse({'valid': False})

    return JsonResponse({
        'valid': True,
        'user': user_to_dict(user),
    })
```

**Flujo de autenticación:**
```
POST {"username": "john_doe", "password": "secure_password_123"}
  ↓
Parse JSON body
  ↓
Validar que username y password no estén vacíos
  ↓
django.contrib.auth.authenticate(request, username=..., password=...)
  ↓
ModelBackend valida contra User table en BD
  ↓
Si válido: user object
Si inválido: None
  ↓
¿user is not None AND user.is_active?
  ↓
Response: {'valid': true, 'user': {...}}
         o {'valid': false}
```

#### HTTP Response (Éxito)
```json
{
  "valid": true,
  "user": {
    "id": "42",
    "username": "john_doe",
    "email": "john@empresa.com",
    "first_name": "John",
    "last_name": "Doe",
    "is_active": true,
    "app_roles": ["d1-access", "d2-access"]
  }
}
```

#### HTTP Response (Fallo)
```json
{
  "valid": false
}
```

---

## 🔄 PASO 6: Keycloak crea el JWT y redirige a D1

### Evento
La validación fue exitosa. Keycloak prepara el token de respuesta.

### Flujo de generación de token

```java
// En D1UserAdapter
@Override
public Stream<RoleModel> getRealmRoleMappingsStream() {
    List<String> roles = data.getAppRoles();  // ["d1-access", "d2-access"]
    if (roles == null) return Stream.empty();

    return roles.stream()
            .map(roleName -> realm.getRole(roleName))
            .filter(role -> role != null);
}
```

**Token JWT (estructura):**
```
Header:
{
  "alg": "RS256",
  "typ": "JWT",
  "kid": "<key_id>"
}

Payload:
{
  "jti": "...",
  "exp": 1715500800,
  "iat": 1715414400,
  "iss": "http://localhost:8080/realms/django-realm",
  "aud": "d1-client",
  "sub": "42",
  "typ": "Bearer",
  "preferred_username": "john_doe",
  "name": "John Doe",
  "email": "john@empresa.com",
  "realm_access": {
    "roles": ["d1-access", "d2-access", "default-roles-django-realm"]
  },
  "scope": "openid profile email"
}

Signature: <signed_with_keycloak_private_key>
```

### HTTP Redirección de Keycloak a D1

```
HTTP 302 Found
Location: http://localhost:8001/oidc/callback/?
    code=<authorization_code>
    &state=<same_state_as_before>
```

**Parámetros:**
- `code=<authorization_code>` → Código para obtener token
- `state=<state>` → Validación CSRF (debe coincidir)

---

## 🔄 PASO 7: D1 intercambia código por token

### Evento
D1 recibe el callback de Keycloak con el authorization code

### Código en mozilla_django_oidc (automático)

```python
# Internamente ejecuta:
OIDCAuthenticationBackend.authentication_callback(request)
  ↓
exchange_tokens(code)
  ↓
POST http://keycloak:8080/realms/django-realm/protocol/openid-connect/token
  {
    "grant_type": "authorization_code",
    "code": "<authorization_code>",
    "client_id": "d1-client",
    "client_secret": "<D1_CLIENT_SECRET>"
  }
```

**HTTP POST Request:**
```
POST http://keycloak:8080/realms/django-realm/protocol/openid-connect/token
Host: keycloak:8080
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code
&code=<authorization_code>
&client_id=d1-client
&client_secret=<D1_CLIENT_SECRET>
```

### HTTP Response de Keycloak

```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6I...",
  "expires_in": 300,
  "refresh_expires_in": 1800,
  "refresh_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6I...",
  "token_type": "Bearer",
  "id_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6I...",
  "not-before-policy": 0,
  "session_state": "...",
  "scope": "openid profile email"
}
```

---

## 🔄 PASO 8: D1 valida el JWT y crea sesión de usuario

### Código en mozilla_django_oidc

```python
OIDCAuthenticationBackend.get_userinfo(access_token)
  ↓
GET http://keycloak:8080/realms/django-realm/protocol/openid-connect/userinfo
  Authorization: Bearer <access_token>
  ↓
Response: {usuario + claims}
  ↓
OIDCAuthenticationBackend.authenticate(request, **kwargs)
  ↓
get_or_create_user(claims)
  ↓
get_user_id(claims)
  ↓
create_user(claims)  [si no existe]
  ↓
update_user_claims(user, claims)
  ↓
django.contrib.auth.login(request, user)  [Crear sesión Django]
```

### Callback personalizado: oidc_callback_with_roles()

En `config/settings.py`:
```python
def oidc_callback_with_roles(strategy, details, backend, user, *args, **kwargs):
    """Callback para guardar roles de Keycloak en la sesión."""
    request = strategy.request
    if hasattr(request, 'session') and 'access_token' in kwargs.get('response', {}):
        from django.conf import settings
        roles = []
        for group_name in user.groups.values_list('name', flat=True):
            if group_name in settings.GROUP_TO_APP_ROLE:
                roles.append(settings.GROUP_TO_APP_ROLE[group_name])
        request.session['oidc_app_roles'] = roles

OIDC_RP_CALLBACK_FUNC = 'config.settings.oidc_callback_with_roles'
```

**Resultado en sesión Django:**
```python
request.session = {
    '_auth_user_id': '42',
    '_auth_user_backend': 'mozilla_django_oidc.auth.OIDCAuthenticationBackend',
    '_auth_user_hash': '...',
    'oidc_app_roles': ['d1-access', 'd2-access'],
    'access_token': 'eyJhbGciOiJSUzI1NiIs...',
    'refresh_token': 'eyJhbGciOiJSUzI1NiIs...',
}
```

### Redirección a LOGIN_REDIRECT_URL

En `config/settings.py`:
```python
LOGIN_REDIRECT_URL = '/post-login/'
```

```
HTTP 302 Found
Location: http://localhost:8001/post-login/
```

---

## 🔄 PASO 9: D1 renderiza el portal — Selección de aplicación

### Evento
Usuario es redirigido a `/post-login/` (ahora autenticado)

### Código en d1/portal/views.py

```python
@login_required
def post_login(request):
    # Leer roles del token OIDC guardados en sesión (PASO 8)
    app_roles = request.session.get('oidc_app_roles', [])

    # Mapear roles a URLs
    accessible = {
        role: url
        for role, url in APP_URLS.items()
        if role in app_roles
    }

    # Si no tiene acceso a nada
    if len(accessible) == 0:
        return render(request, 'portal/no_access.html')

    # Si tiene acceso a 1 app → redireccionar directamente
    if len(accessible) == 1:
        return redirect(list(accessible.values())[0])

    # Si tiene acceso a múltiples apps → mostrar selector
    return render(request, 'portal/selector.html', {'apps': accessible})
```

**APP_URLS en views.py:**
```python
APP_URLS = {
    'd1-access': 'http://localhost:8001/home/',
    'd2-access': 'http://localhost:8002/home/',
    'd3-access': 'http://localhost:8003/home/',
}
```

### Ejemplo de ejecución

**Caso 1: Usuario con acceso a D1 y D2**
```
request.session['oidc_app_roles'] = ['d1-access', 'd2-access']

accessible = {
    'd1-access': 'http://localhost:8001/home/',
    'd2-access': 'http://localhost:8002/home/',
}

len(accessible) == 2
  ↓
render('portal/selector.html', {'apps': accessible})
```

**Caso 2: Usuario con acceso solo a D1**
```
request.session['oidc_app_roles'] = ['d1-access']

accessible = {
    'd1-access': 'http://localhost:8001/home/',
}

len(accessible) == 1
  ↓
redirect('http://localhost:8001/home/')
```

**Caso 3: Usuario sin acceso a ninguna app**
```
request.session['oidc_app_roles'] = []

accessible = {}

len(accessible) == 0
  ↓
render('portal/no_access.html')
```

### HTML Template: selector.html

En `d1/templates/portal/selector.html`:
```html
<!DOCTYPE html>
<html lang="es">
<head><meta charset="UTF-8"><title>Selecciona aplicación</title></head>
<body>
  <h2>Bienvenido, {{ request.user.username }}</h2>
  <p>¿A qué aplicación quieres acceder?</p>
  <ul>
    {% for role, url in apps.items %}
      <li><a href="{{ url }}">{{ role }}</a></li>
    {% endfor %}
  </ul>
  <hr>
  <a href="/oidc/logout/">Cerrar sesión</a>
</body>
</html>
```

**HTML renderizado:**
```html
<h2>Bienvenido, john_doe</h2>
<p>¿A qué aplicación quieres acceder?</p>
<ul>
  <li><a href="http://localhost:8001/home/">d1-access</a></li>
  <li><a href="http://localhost:8002/home/">d2-access</a></li>
</ul>
<hr>
<a href="/oidc/logout/">Cerrar sesión</a>
```

---

## 🔄 PASO 10: Usuario accede a la aplicación destino

### Evento
Usuario hace clic en un enlace de aplicación (ej: D2)

### URL visitada
```
http://localhost:8002/home/
```

### En D2 (aplicación cliente OIDC)

D2 tiene la misma configuración que D1:
- Mismo `OIDC_RP_CLIENT_ID = 'd2-client'`
- Mismo servidor Keycloak
- Mismo Custom SPI

**Flujo:**
```
Usuario llega a http://localhost:8002/home/
  ↓
@login_required verifica autenticación en D2
  ↓
¿D2 tiene sesión de usuario?
  ↓
NO (es un navegador con sesión de D1, no de D2)
  ↓
Redirige a /oidc/authenticate/
  ↓
Keycloak ve que el navegador YA está autenticado
  (mediante sesión de Keycloak)
  ↓
Usa token existente (Single Sign-On)
  ↓
Redirige de vuelta a D2 con nuevo authorization_code
  ↓
D2 intercambia código por token
  ↓
D2 crea sesión de usuario local
  ↓
Renderiza /home/
```

**Resultado:**
```html
<h1>D2 — App 2</h1>
<p>Usuario: john_doe</p>
<a href="/oidc/logout/">Cerrar sesión</a>
```

---

## 📊 Diagrama de secuencia completo

```
Usuario                Django D1        Keycloak          D1 (API)
  │                      │                  │                │
  ├─ GET /home/ ────────>│                  │                │
  │                      │                  │                │
  │                      ├─ No autenticado  │                │
  │                      ├─ Redirige ─────>│ (PASO 2)        │
  │<─ 302 /auth ─────────┤                  │                │
  │                      │                  │                │
  ├─ Browser va a Keycloak               │                │
  │                      │<─────────────────┤ (PASO 3)       │
  │                      │  Formulario      │                │
  │                      │                  │                │
  ├─ Ingresa credenciales                  │                │
  │                      │<─────────────────┤ (PASO 4)       │
  │                      │                  │                │
  │                      │                  ├─ SPI ─────────>│
  │                      │                  │  GET /api/     │
  │                      │                  │  internal/     │
  │                      │                  │  auth/user/    │
  │                      │                  │<────────────────┤
  │                      │                  │  {found: true}  │
  │                      │                  │                │
  │                      │                  ├─ SPI ─────────>│
  │                      │                  │  POST /api/    │
  │                      │                  │  internal/     │
  │                      │                  │  auth/verify/  │
  │                      │                  │<────────────────┤
  │                      │                  │ {valid: true}   │
  │                      │                  │                │
  │                      │<─────────────────┤ JWT + code      │
  │<─ 302 /oidc/callback ┤                  │ (PASO 6)        │
  │                      │                  │                │
  │                      ├─ Code exchange ──>│ (PASO 7)       │
  │                      │<─ Access token ────┤               │
  │                      │                  │                │
  │                      ├─ Login local      │                │
  │                      ├─ Sesión Django    │                │
  │                      │                  │                │
  │<─ 302 /post-login ───┤                  │                │
  │                      │                  │                │
  │ (PASO 9)             │                  │                │
  ├─ GET /post-login/ ──>│                  │                │
  │                      │                  │                │
  │<─ Selector HTML ─────┤                  │                │
  │  (d1-access, d2...)  │                  │                │
  │                      │                  │                │
  ├─ Click d2-access ────>│                  │                │
  │<─ 302 /d2/home/ ─────┤                  │                │
  │                      │                  │                │
  │ (Browser va a D2, repite proceso PASO 4-8 en D2)
  │
  ├─ GET /home/ D2 ─────>└─>Django D2
  │                         (Redirige a Keycloak SSO)
  │
  │                         Keycloak reconoce sesión
  │                         (No pide credenciales)
  │<─ Home D2 renderizado─┘
```

---

## 🔐 Resumen de flujo de autenticación

### 1️⃣ Inicio
```
Usuario sin sesión en D1 → GET /home/
```

### 2️⃣ Redirección a Keycloak
```
@login_required → redirect /oidc/authenticate/
→ /auth endpoint de Keycloak
```

### 3️⃣ Ingresa credenciales
```
Usuario en formulario de Keycloak
```

### 4️⃣ Validación contra D1
```
Keycloak SPI (Java)
  → HTTP GET/POST a D1 /api/internal/auth/
  → Middleware valida API Key
  → Django authenticate() valida credenciales
  → Retorna roles del usuario
```

### 5️⃣ Generación de JWT
```
Keycloak crea token con:
  - claims del usuario (sub, email, name, etc)
  - roles del usuario (d1-access, d2-access)
  - firma RS256
```

### 6️⃣ Redirección a D1 con code
```
/oidc/callback/?code=...&state=...
```

### 7️⃣ Intercambio de código por token
```
D1 POST a Keycloak /token
Recibe access_token, refresh_token, id_token
```

### 8️⃣ Creación de sesión Django
```
django.contrib.auth.login()
request.session['_auth_user_id'] = user.pk
request.session['oidc_app_roles'] = [...]
```

### 9️⃣ Portal post-login
```
GET /post-login/
Leer request.session['oidc_app_roles']
Si 1 app → redirect automático
Si múltiples → renderizar selector
```

### 🔟 Acceso a D2
```
Usuario navega a D2
D2 redirige a Keycloak
Keycloak ve sesión existente
Devuelve token sin pedir credenciales (SSO)
D2 crea su propia sesión Django
```

---

## 📝 Parámetros y headers clave

### Headers HTTP
```
X-Internal-Api-Key: internal-api-key-super-secret-mvp
  [Middleware validación D1 API interna]

Authorization: Bearer <access_token>
  [Validación de JWT en endpoints protegidos]

Content-Type: application/json
  [POST requests a D1 API]
```

### Parámetros OIDC
```
response_type=code           [Authorization Code Flow]
client_id=d1-client          [Identificación de D1]
redirect_uri=...callback/    [Dónde vuelve Keycloak]
scope=openid profile email   [Claims solicitados]
state=<random>               [CSRF protection]
nonce=<random>               [Token validation]
```

### Parámetros en Django
```
next=/home/             [URL post-login]
username=john_doe       [Identificador de usuario]
password=...            [Credencial validada por Django]
```

### Parámetros en Keycloak SPI
```
d1Url=http://d1:8001    [Base URL para HTTP requests]
apiKey=internal-...     [API Key para headers]
```

---

## 🎯 Funciones principales invocadas

### Django D1
```
1. config.settings.oidc_callback_with_roles()
   └─ Guarda roles en sesión

2. internal_auth.views.verify_user()
   └─ POST /api/internal/auth/verify/
   └─ Autentica credenciales

3. internal_auth.views.get_user()
   └─ GET /api/internal/auth/user/
   └─ Busca usuario por username

4. internal_auth.middleware.InternalApiKeyMiddleware.__call__()
   └─ Valida header X-Internal-Api-Key

5. portal.views.post_login()
   └─ Renderiza selector de apps

6. config.urls.home()
   └─ Homepage con @login_required
```

### Keycloak Java SPI
```
1. D1UserStorageProviderFactory.create()
   └─ Inicializa el provider con config

2. D1UserStorageProvider.getUserByUsername()
   └─ Busca usuario en D1 (read-only)

3. D1UserStorageProvider.isValid()
   └─ Valida contraseña contra D1

4. D1ApiClient.findByUsername()
   └─ HTTP GET a /api/internal/auth/user/

5. D1ApiClient.verify()
   └─ HTTP POST a /api/internal/auth/verify/

6. D1UserAdapter.getRealmRoleMappingsStream()
   └─ Mapea roles D1 → roles Keycloak

7. D1ApiClient.parseUser()
   └─ Convierte JSON → D1UserData
```

### mozilla-django-oidc
```
1. OIDCAuthenticationBackend.authenticate()
   └─ Punto de entrada principal

2. OIDCAuthenticationBackend.get_authorization_code()
   └─ Redirige a Keycloak /auth

3. OIDCAuthenticationBackend.exchange_tokens()
   └─ POST a Keycloak /token

4. OIDCAuthenticationBackend.get_userinfo()
   └─ GET a Keycloak /userinfo

5. OIDCAuthenticationBackend.get_or_create_user()
   └─ User.objects.get_or_create()

6. django.contrib.auth.login()
   └─ Crea sesión Django
```

---

## ✅ Validaciones en cada paso

| Paso | Validación | Fuente | Falla si... |
|------|-----------|--------|-----------|
| 2 | CORS + estado | Keycloak | navegador ≠ http://localhost:8080 |
| 4a | API Key | D1 middleware | `X-Internal-Api-Key` ≠ valor esperado |
| 4b | Credenciales | Django auth | `User.objects.get(username=...)?` ó `check_password()?` |
| 4c | Usuario activo | D1 models | `user.is_active == False` |
| 5 | JWT válido | Keycloak/D1 | firma RS256 no verifica ó token expirado |
| 7 | State coincide | OIDC lib | state en callback ≠ state inicial |
| 8 | Nonce coincide | OIDC lib | nonce no matchea id_token |
| 9 | Sesión válida | Django | `_auth_user_id` no existe en sesión |
| 10 | Roles en sesión | D1 portal | `oidc_app_roles` vacío |

---

## 🚀 Flujo resumido en pseudocódigo

```python
# PASO 1-2: Usuario accede a D1 sin autenticación
def home(request):
    if not request.user.is_authenticated:
        return redirect('/oidc/authenticate/?next=/home/')
    return render('home.html')

# PASO 3: Usuario ingresa credenciales en Keycloak

# PASO 4: Keycloak llama al SPI para validar
# (Java)
provider = D1UserStorageProvider(...)
user = provider.getUserByUsername(realm, "john_doe")
    # ↓ HTTP GET a D1 /api/internal/auth/user/?username=john_doe
    # ↓ Retorna D1UserData

valid = provider.isValid(realm, user, password)
    # ↓ HTTP POST a D1 /api/internal/auth/verify/
    # ↓ Valida contra Django User.objects.get(username=...)
    # ↓ Verifica password con check_password()
    # ↓ Retorna True/False

# PASO 5: D1 responde al SPI
def verify_user(request):
    user = authenticate(username=username, password=password)
    return {'valid': user is not None, 'user': user_to_dict(user)}

# PASO 6: Keycloak crea JWT
jwt_token = create_token(
    sub="42",
    username="john_doe",
    email="john@empresa.com",
    roles=["d1-access", "d2-access"],  # ← del SPI
    aud="d1-client",
    exp=time.time() + 300,
    iat=time.time(),
)
return redirect('/oidc/callback/?code=...&state=...')

# PASO 7-8: D1 intercambia código por token y crea sesión
def oidc_callback(request):
    code = request.GET['code']
    access_token = exchange_code_for_token(code)
        # ↓ POST a Keycloak /token
    
    user_info = get_user_info(access_token)
        # ↓ GET a Keycloak /userinfo
    
    user = get_or_create_user(user_info)
        # ↓ User.objects.get_or_create(username=...)
    
    request.session['oidc_app_roles'] = user_info['app_roles']
    request.session['access_token'] = access_token
    login(request, user)
    
    return redirect('/post-login/')

# PASO 9: D1 renderiza portal
def post_login(request):
    app_roles = request.session.get('oidc_app_roles', [])
    
    accessible = {
        role: url for role, url in APP_URLS.items()
        if role in app_roles
    }
    
    if len(accessible) == 0:
        return render('no_access.html')
    elif len(accessible) == 1:
        return redirect(list(accessible.values())[0])
    else:
        return render('selector.html', {'apps': accessible})

# PASO 10: Usuario accede a D2 (SSO)
# Browser va a http://localhost:8002/home/
# D2 redirige a Keycloak
# Keycloak ve sesión existente → no pide credenciales
# Devuelve nuevo code
# D2 intercambia por su propio token
# Mismo usuario, múltiples sesiones locales
```

---

## 🔄 Ciclo de vida de la sesión

### Sesión Keycloak
```
Creada:  Cuando usuario pasa /auth
Válida:  300+ segundos (configurable)
Renovada: Via refresh_token
Destruida: /logout endpoint
```

### Sesión Django D1
```
Creada:  En /oidc/callback/ via login()
Válida:  SESSION_COOKIE_AGE (default 2 semanas)
Almacena: _auth_user_id, oidc_app_roles, access_token
Destruida: /oidc/logout/
```

### Sesión Django D2
```
Independiente de D1
Creada:  Cuando usuario accede desde selector
Válida:  SESSION_COOKIE_AGE
Almacena: Misma estructura que D1
Destruida: /oidc/logout/ en D2
```

### Relación entre sesiones
```
┌─────────────────────────┐
│  Sesión Browser Keycloak│ ← Una sola para todas las apps
└─────────────────────────┘
           │
    ┌──────┼──────┐
    │      │      │
    ▼      ▼      ▼
┌───────┐┌───────┐┌───────┐
│  D1   ││  D2   ││  D3   │
│Cookie ││Cookie ││Cookie │
└───────┘└───────┘└───────┘
[Independientes pero vinculadas por Keycloak]
```

---

## 🎓 Conclusión

El flujo completo es:

1. **Sin autenticación** → Keycloak
2. **Keycloak** → Valida contra D1 (SPI HTTP)
3. **D1 API** → Autentica usuario
4. **Keycloak** → Crea JWT + roles
5. **D1** → Intercambia código + crea sesión local
6. **Portal D1** → Selector de apps (SSO)
7. **D2+** → Reutiliza sesión Keycloak (sin volver a validar)

Cada paso envía parámetros específicos, invoca funciones determinadas, y valida datos antes de proceder al siguiente.