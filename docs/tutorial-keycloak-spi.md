# 🔧 Tutorial: Cómo Crear un Custom SPI en Keycloak

## 📋 Índice

1. [¿Qué es un SPI de Keycloak?](#qué-es-un-spi-de-keycloak)
2. [Preparación del Entorno](#preparación-del-entorno)
3. [Estructura del Proyecto](#estructura-del-proyecto)
4. [Clase 1: Modelo de Datos](#clase-1-modelo-de-datos)
5. [Clase 2: Cliente HTTP](#clase-2-cliente-http)
6. [Clase 3: Adaptador de Usuario](#clase-3-adaptador-de-usuario)
7. [Clase 4: Proveedor Principal](#clase-4-proveedor-principal)
8. [Clase 5: Factory del Proveedor](#clase-5-factory-del-proveedor)
9. [Configuración Maven](#configuración-maven)
10. [Compilación e Instalación](#compilación-e-instalación)
11. [Configuración en Keycloak](#configuración-en-keycloak)
12. [Troubleshooting](#troubleshooting)

---

## ¿Qué es un SPI de Keycloak?

**SPI** significa **Service Provider Interface**. Es como un "plugin" que permite extender las funcionalidades de Keycloak.

### ¿Para qué sirve nuestro SPI?

En nuestro proyecto, el SPI permite que Keycloak **valide usuarios contra una API externa** (Django D1) en lugar de usar su propia base de datos.

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│   Usuario   │───▶│  Keycloak   │───▶│ Django D1   │
│ (login)     │    │ (Custom SPI)│    │ (API REST)  │
└─────────────┘    └─────────────┘    └─────────────┘
                          │                   │
                          │◀──── Valida ─────┘
                          │      usuario
                          ▼
                   ┌─────────────┐
                   │ Aplicación  │
                   │ (D2/D3)     │
                   └─────────────┘
```

### ¿Qué hace exactamente?

1. **Usuario** intenta hacer login en Keycloak
2. **Keycloak** llama a nuestro SPI
3. **Nuestro SPI** hace una llamada HTTP a Django D1
4. **Django D1** valida usuario y contraseña
5. **Django D1** devuelve datos del usuario + roles
6. **Nuestro SPI** convierte la respuesta a formato Keycloak
7. **Keycloak** autentica al usuario

---

## Preparación del Entorno

### Prerequisitos

```bash
# Java 17+
java -version

# Maven 3.6+
mvn -version

# Keycloak 24+ running
curl http://localhost:8080
```

### Dependencias Maven Necesarias

Nuestro SPI necesita estas librerías:

```xml
<!-- APIs de Keycloak (scope provided = ya están en Keycloak) -->
<dependency>
    <groupId>org.keycloak</groupId>
    <artifactId>keycloak-server-spi</artifactId>
    <version>24.0.0</version>
    <scope>provided</scope>
</dependency>

<!-- Para procesar JSON de la API externa -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>2.17.0</version>
</dependency>
```

---

## Estructura del Proyecto

Nuestro SPI tiene esta estructura:

```
keycloak-spi/
├── pom.xml                           # Configuración Maven
└── src/main/java/com/empresa/spi/
    ├── D1UserData.java               # 📊 Modelo de datos
    ├── D1ApiClient.java              # 🌐 Cliente HTTP
    ├── D1UserAdapter.java            # 🔄 Adaptador Keycloak
    ├── D1UserStorageProvider.java    # 🏢 Lógica principal
    └── D1UserStorageProviderFactory.java # 🏭 Factory
└── src/main/resources/META-INF/services/
    └── org.keycloak.storage.UserStorageProviderFactory
```

**¿Por qué tantas clases?** Cada una tiene una responsabilidad específica:

- **D1UserData**: Almacena información del usuario
- **D1ApiClient**: Se comunica con la API externa
- **D1UserAdapter**: "Traduce" datos al formato de Keycloak
- **D1UserStorageProvider**: Lógica de autenticación
- **D1UserStorageProviderFactory**: Crea instancias del proveedor

---

## Clase 1: Modelo de Datos

### D1UserData.java

Esta clase es un **POJO** (Plain Old Java Object) que almacena los datos del usuario.

```java
package com.empresa.spi;

import java.util.List;

public class D1UserData {
    // Variables privadas (encapsulación)
    private String id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private boolean active;
    private List<String> appRoles;

    // Getters y Setters (métodos para acceder a las variables)
    
    public String getId()                  { return id; }
    public void setId(String id)           { this.id = id; }

    public String getUsername()            { return username; }
    public void setUsername(String u)      { this.username = u; }

    public String getEmail()               { return email; }
    public void setEmail(String e)         { this.email = e; }

    public String getFirstName()           { return firstName; }
    public void setFirstName(String f)     { this.firstName = f; }

    public String getLastName()            { return lastName; }
    public void setLastName(String l)      { this.lastName = l; }

    public boolean isActive()              { return active; }
    public void setActive(boolean a)       { this.active = a; }

    public List<String> getAppRoles()      { return appRoles; }
    public void setAppRoles(List<String> r){ this.appRoles = r; }
}
```

### Explicación para Principiantes

**¿Qué es un POJO?**
- Un objeto Java simple que solo almacena datos
- Tiene variables privadas y métodos públicos para acceder a ellas
- Es como una "caja" donde guardamos información del usuario

**¿Por qué getters y setters?**
- **Encapsulación**: Las variables son `private` (no se pueden tocar directamente)
- **Control**: Los métodos `get/set` nos permiten controlar cómo se acceden
- **Convención Java**: Es el estándar en Java

**Mapeo con JSON de la API:**
```json
{
  "id": "123",
  "username": "juan",
  "email": "juan@test.com", 
  "first_name": "Juan",      ← firstName
  "last_name": "Pérez",      ← lastName
  "is_active": true,         ← active
  "app_roles": ["d1-access"] ← appRoles
}
```

---

## Clase 2: Cliente HTTP

### D1ApiClient.java

Esta clase se encarga de **comunicarse con la API externa** de Django.

```java
package com.empresa.spi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class D1ApiClient {
    
    // Variables de instancia
    private final String baseUrl;    // URL de la API (ej: http://d1:8001)
    private final String apiKey;     // Clave para autenticar con la API
    private final HttpClient httpClient;    // Cliente HTTP de Java 11+
    private final ObjectMapper mapper;      // Para procesar JSON
    
    // Constructor: se ejecuta al crear una nueva instancia
    public D1ApiClient(String baseUrl, String apiKey) {
        this.baseUrl  = baseUrl;
        this.apiKey   = apiKey;
        
        // Configurar cliente HTTP con timeout de 5 segundos
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
                
        // Jackson para convertir JSON ↔ Java Objects
        this.mapper = new ObjectMapper();
    }
    
    /**
     * Verifica usuario y contraseña contra la API de D1
     * 
     * @param username Nombre del usuario
     * @param password Contraseña del usuario  
     * @return D1UserData si es válido, null si no
     */
    public D1UserData verify(String username, String password) {
        // 1. Crear JSON con los datos a enviar
        String body = String.format(
            "{\"username\":\"%s\",\"password\":\"%s\"}",
            username.replace("\"", ""),  // Prevenir inyección
            password.replace("\"", "")
        );
        
        // 2. Crear petición HTTP POST
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/internal/auth/verify/"))
                .header("Content-Type", "application/json")
                .header("X-Internal-Api-Key", apiKey)  // Autenticación
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(5))
                .build();
        
        // 3. Ejecutar petición y procesar respuesta
        return executeAndParse(request);
    }
    
    /**
     * Busca un usuario por username
     */
    public D1UserData findByUsername(String username) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/internal/auth/user/?username=" + username))
                .header("X-Internal-Api-Key", apiKey)
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();
        
        return executeAndParse(request);
    }
    
    /**
     * Método privado que ejecuta la petición HTTP y procesa el JSON
     */
    private D1UserData executeAndParse(HttpRequest request) {
        try {
            // 1. Enviar petición HTTP
            HttpResponse<String> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofString()
            );
            
            // 2. Parsear JSON de la respuesta
            JsonNode root = mapper.readTree(response.body());
            
            // 3. Procesar según el tipo de endpoint
            
            // Endpoint /verify/ devuelve: {"valid": true, "user": {...}}
            if (root.has("valid")) {
                if (!root.get("valid").asBoolean()) return null;
                return parseUser(root.get("user"));
            }
            
            // Endpoint /user/ devuelve: {"found": true, "user": {...}}
            if (root.has("found")) {
                if (!root.get("found").asBoolean()) return null;
                return parseUser(root.get("user"));
            }
            
            return null;
            
        } catch (IOException | InterruptedException e) {
            // Si hay error de red, devolver null (usuario no válido)
            return null;
        }
    }
    
    /**
     * Convierte un JsonNode a D1UserData
     */
    private D1UserData parseUser(JsonNode userNode) {
        if (userNode == null) return null;
        
        // Crear objeto y llenar campos
        D1UserData data = new D1UserData();
        data.setId(userNode.path("id").asText());
        data.setUsername(userNode.path("username").asText());
        data.setEmail(userNode.path("email").asText());
        data.setFirstName(userNode.path("first_name").asText());
        data.setLastName(userNode.path("last_name").asText());
        data.setActive(userNode.path("is_active").asBoolean(true));
        
        // Procesar array de roles
        List<String> roles = new ArrayList<>();
        JsonNode rolesNode = userNode.path("app_roles");
        if (rolesNode.isArray()) {
            rolesNode.forEach(r -> roles.add(r.asText()));
        }
        data.setAppRoles(roles);
        
        return data;
    }
}
```

### Explicación Detallada

**¿Qué es HttpClient?**
- Es la forma moderna en Java (11+) de hacer peticiones HTTP
- Reemplaza a librerías como Apache HttpClient
- Soporta timeouts, headers personalizados, etc.

**¿Cómo funciona Jackson?**
```java
// JSON → Java Object
JsonNode root = mapper.readTree(jsonString);
String username = root.get("username").asText();

// Java Object → JSON  
String json = mapper.writeValueAsString(objeto);
```

**¿Por qué usar .path() en lugar de .get()?**
```java
// .get() lanza excepción si la clave no existe
String name = root.get("name").asText();  // ❌ Puede fallar

// .path() devuelve un nodo vacío si no existe  
String name = root.path("name").asText(); // ✅ Más seguro
```

**¿Qué pasa si la API no responde?**
- IOException: Problema de red
- InterruptedException: Timeout o cancelación
- Nuestro método devuelve `null` = usuario no válido

---

## Clase 3: Adaptador de Usuario

### D1UserAdapter.java

Esta clase "adapta" nuestros datos al formato que espera Keycloak.

```java
package com.empresa.spi;

import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.SubjectCredentialManager;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.adapter.AbstractUserAdapter;

import java.util.List;
import java.util.stream.Stream;

public class D1UserAdapter extends AbstractUserAdapter {
    
    // Datos del usuario de nuestra API
    private final D1UserData data;
    private final ComponentModel componentModel;
    
    public D1UserAdapter(KeycloakSession session, RealmModel realm,
                         ComponentModel model, D1UserData data) {
        super(session, realm, model);  // Llamar al constructor padre
        this.data = data;
        this.componentModel = model;
    }
    
    /**
     * ID único del usuario en Keycloak
     * Debe ser único en todo el sistema
     */
    @Override
    public String getId() {
        return StorageId.keycloakId(componentModel, data.getId());
    }
    
    // Métodos que Keycloak usa para obtener datos del usuario
    @Override public String getUsername()  { return data.getUsername(); }
    @Override public String getEmail()     { return data.getEmail(); }
    @Override public String getFirstName() { return data.getFirstName(); }
    @Override public String getLastName()  { return data.getLastName(); }
    @Override public boolean isEnabled()   { return data.isActive(); }
    
    /**
     * Convierte nuestros "app_roles" a roles de Keycloak
     */
    @Override
    public Stream<RoleModel> getRealmRoleMappingsStream() {
        List<String> roles = data.getAppRoles();
        if (roles == null) return Stream.empty();
        
        return roles.stream()
                .map(roleName -> realm.getRole(roleName))  // Buscar rol en Keycloak
                .filter(role -> role != null);            // Filtrar roles que existan
    }
    
    @Override
    public Stream<RoleModel> getRoleMappingsStream() {
        return getRealmRoleMappingsStream();
    }
    
    /**
     * No soportamos cambio de credenciales para usuarios externos
     */
    @Override
    public SubjectCredentialManager credentialManager() {
        throw new UnsupportedOperationException("Not supported for external users");
    }
    
    // Métodos de escritura: no hacen nada (usuarios read-only)
    @Override public void setUsername(String s)  {}
    @Override public void setEmail(String s)     {}
    @Override public void setFirstName(String s) {}
    @Override public void setLastName(String s)  {}
}
```

### Explicación del Patrón Adapter

**¿Qué es el patrón Adapter?**

Es como un "traductor" entre dos sistemas que hablan diferente:

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│   Django    │───▶│   Adapter   │───▶│  Keycloak   │
│   D1 API    │    │ (Traductor) │    │   System    │
└─────────────┘    └─────────────┘    └─────────────┘

Django dice:           Keycloak espera:
"first_name"    ──▶    getFirstName()
"is_active"     ──▶    isEnabled()  
"app_roles"     ──▶    getRoleMappingsStream()
```

**¿Qué es AbstractUserAdapter?**
- Es una clase base de Keycloak
- Implementa métodos comunes
- Nosotros solo sobrescribimos lo que necesitamos

**¿Qué es Stream<RoleModel>?**
- Java 8+ usa Streams para procesar colecciones
- Es como un "pipeline" de transformaciones
```java
roles.stream()                    // ["d1-access", "d3-access"]
     .map(name -> realm.getRole(name))  // [RoleModel1, RoleModel2]
     .filter(role -> role != null)     // [RoleModel1, RoleModel2] (filtrar nulls)
```

---

## Clase 4: Proveedor Principal  

### D1UserStorageProvider.java

Esta es la clase **más importante**. Implementa la lógica de autenticación.

```java
package com.empresa.spi;

import org.keycloak.component.ComponentModel;
import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.CredentialInputValidator;
import org.keycloak.models.*;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.user.UserLookupProvider;

public class D1UserStorageProvider implements
        UserStorageProvider,          // Interfaz base
        UserLookupProvider,           // Para buscar usuarios
        CredentialInputValidator {    // Para validar contraseñas

    // Dependencias inyectadas
    private final KeycloakSession session;
    private final ComponentModel  model;
    private final D1ApiClient     apiClient;
    private final RealmModel      realm;

    public D1UserStorageProvider(KeycloakSession session, ComponentModel model,
                                  D1ApiClient apiClient, RealmModel realm) {
        this.session   = session;
        this.model     = model;
        this.apiClient = apiClient;
        this.realm     = realm;
    }

    // ── UserLookupProvider ────────────────────────────────────────────

    /**
     * Keycloak llama este método cuando necesita buscar un usuario por username
     */
    @Override
    public UserModel getUserByUsername(RealmModel realm, String username) {
        // 1. Llamar a la API externa para buscar el usuario
        D1UserData data = apiClient.findByUsername(username);
        
        // 2. Si no existe, devolver null
        if (data == null) return null;
        
        // 3. Crear adaptador y devolverlo
        return new D1UserAdapter(session, realm, model, data);
    }

    /**
     * Buscar usuario por ID interno de Keycloak
     */
    @Override
    public UserModel getUserById(RealmModel realm, String id) {
        // El ID de Keycloak incluye info del storage provider
        // StorageId.externalId() extrae nuestro ID original
        String username = StorageId.externalId(id);
        return getUserByUsername(realm, username);
    }

    /**
     * Buscar por email (no implementado en nuestro MVP)
     */
    @Override
    public UserModel getUserByEmail(RealmModel realm, String email) {
        return null; // No implementado
    }

    // ── CredentialInputValidator ──────────────────────────────────────

    /**
     * ¿Qué tipos de credencial soportamos?
     */
    @Override
    public boolean supportsCredentialType(String credentialType) {
        return PasswordCredentialModel.TYPE.equals(credentialType);
    }

    /**
     * ¿El usuario tiene configurado este tipo de credencial?
     */
    @Override
    public boolean isConfiguredFor(RealmModel realm, UserModel user, String credentialType) {
        return supportsCredentialType(credentialType);
    }

    /**
     * MÉTODO CLAVE: Validar credenciales del usuario
     * Este es el que hace la "magia" de autenticación
     */
    @Override
    public boolean isValid(RealmModel realm, UserModel user,
                           CredentialInput credentialInput) {
        
        // 1. Obtener la contraseña del input
        String password = credentialInput.getChallengeResponse();
        
        // 2. Llamar a la API externa para verificar usuario+contraseña
        D1UserData data = apiClient.verify(user.getUsername(), password);
        
        // 3. Si la API devuelve datos, las credenciales son válidas
        return data != null;
    }

    // ── UserStorageProvider ───────────────────────────────────────────

    /**
     * Cleanup al cerrar el proveedor
     */
    @Override
    public void close() {
        // No hay recursos que liberar
    }
}
```

### Explicación de las Interfaces

**¿Por qué tantas interfaces?**

Keycloak usa el patrón de **composición de interfaces**:

```java
// Cada interfaz tiene un propósito específico
UserStorageProvider      ← Interfaz base
UserLookupProvider       ← Buscar usuarios  
CredentialInputValidator ← Validar contraseñas
UserRegistrationProvider ← Registrar usuarios (opcional)
```

**¿Cómo funciona el flujo de login?**

```
1. Usuario ingresa: username="juan", password="123"

2. Keycloak llama: getUserByUsername("juan")
   └─ apiClient.findByUsername("juan") 
   └─ Retorna: D1UserAdapter o null

3. Keycloak llama: isValid(user, password="123")
   └─ apiClient.verify("juan", "123")
   └─ Retorna: true o false

4. Si ambos son exitosos → Login exitoso
```

**¿Qué es CredentialInput?**
- Abstrae diferentes tipos de credenciales
- `getChallengeResponse()` obtiene la contraseña
- Podría ser contraseña, OTP, certificado, etc.

---

## Clase 5: Factory del Proveedor

### D1UserStorageProviderFactory.java

Esta clase **crea instancias** de nuestro proveedor.

```java
package com.empresa.spi;

import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.storage.UserStorageProviderFactory;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.List;

public class D1UserStorageProviderFactory
        implements UserStorageProviderFactory<D1UserStorageProvider> {

    // ID único de nuestro proveedor en Keycloak
    public static final String PROVIDER_ID = "d1-user-storage";

    /**
     * Identificador único del proveedor
     */
    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    /**
     * MÉTODO CLAVE: Crear instancia del proveedor
     * Keycloak llama este método cada vez que necesita usar nuestro SPI
     */
    @Override
    public D1UserStorageProvider create(KeycloakSession session, ComponentModel model) {
        // 1. Leer configuración del modelo
        String d1Url = model.getConfig().getFirst("d1Url");
        String apiKey = model.getConfig().getFirst("apiKey");

        // 2. Obtener el realm actual
        RealmModel realm = session.getContext().getRealm();
        
        // 3. Crear cliente API
        D1ApiClient client = new D1ApiClient(d1Url, apiKey);

        // 4. Crear y retornar proveedor
        return new D1UserStorageProvider(session, model, client, realm);
    }

    /**
     * Definir campos de configuración que aparecen en la UI de Keycloak
     */
    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        
        // Campo: URL de D1
        ProviderConfigProperty urlProp = new ProviderConfigProperty();
        urlProp.setName("d1Url");                    // ID interno
        urlProp.setLabel("D1 Base URL");             // Etiqueta en UI
        urlProp.setType(ProviderConfigProperty.STRING_TYPE);
        urlProp.setHelpText("URL base de D1, ej: http://d1:8001");

        // Campo: API Key  
        ProviderConfigProperty keyProp = new ProviderConfigProperty();
        keyProp.setName("apiKey");                   // ID interno
        keyProp.setLabel("Internal API Key");        // Etiqueta en UI
        keyProp.setType(ProviderConfigProperty.PASSWORD); // Tipo password (oculto)
        keyProp.setHelpText("Valor de X-Internal-Api-Key");

        return List.of(urlProp, keyProp);
    }

    /**
     * Descripción que aparece en la UI de Keycloak
     */
    @Override
    public String getHelpText() {
        return "Autentica usuarios contra la API interna de D1";
    }
}
```

### ¿Por qué necesitamos una Factory?

**Patrón Factory en acción:**

```
Keycloak dice: "Necesito un proveedor de usuarios"
     ↓
Factory.create() se ejecuta:
  1. Lee configuración (URL, API Key)
  2. Crea D1ApiClient  
  3. Crea D1UserStorageProvider
  4. Lo retorna a Keycloak
     ↓
Keycloak usa el proveedor para autenticar usuarios
```

**¿Qué es ComponentModel?**
- Almacena la configuración del proveedor
- `model.getConfig().getFirst("d1Url")` obtiene configuración
- Es como un Map<String, String> de settings

**¿Por qué ProviderConfigProperty?**
- Define los campos de configuración en la UI
- **STRING_TYPE**: Campo de texto normal
- **PASSWORD**: Campo oculto con asteriscos
- **BOOLEAN_TYPE**: Checkbox
- **LIST_TYPE**: Lista desplegable

---

## Configuración Maven

### pom.xml Completo

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <!-- Información del proyecto -->
    <groupId>com.empresa</groupId>
    <artifactId>d1-user-storage-spi</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <!-- Propiedades -->
    <properties>
        <java.version>17</java.version>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <keycloak.version>24.0.0</keycloak.version>
    </properties>

    <dependencies>
        <!-- APIs básicas de Keycloak -->
        <dependency>
            <groupId>org.keycloak</groupId>
            <artifactId>keycloak-server-spi</artifactId>
            <version>${keycloak.version}</version>
            <scope>provided</scope>  <!-- Ya está en Keycloak -->
        </dependency>
        
        <!-- APIs privadas de Keycloak -->
        <dependency>
            <groupId>org.keycloak</groupId>
            <artifactId>keycloak-server-spi-private</artifactId>
            <version>${keycloak.version}</version>
            <scope>provided</scope>
        </dependency>
        
        <!-- Servicios de Keycloak -->
        <dependency>
            <groupId>org.keycloak</groupId>
            <artifactId>keycloak-services</artifactId>
            <version>${keycloak.version}</version>
            <scope>provided</scope>
        </dependency>
        
        <!-- Jackson para procesar JSON -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>2.17.0</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- Plugin para crear JAR -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-jar-plugin</artifactId>
                <version>3.3.0</version>
                <configuration>
                    <archive>
                        <manifestEntries>
                            <!-- Dependencias que Keycloak debe cargar -->
                            <Dependencies>org.keycloak.keycloak-server-spi,org.keycloak.keycloak-server-spi-private</Dependencies>
                        </manifestEntries>
                    </archive>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### Explicación del POM

**¿Qué es scope="provided"?**
```xml
<scope>provided</scope>  
```
- La librería se usa para compilar
- **NO** se incluye en el JAR final  
- Se asume que ya está disponible en runtime (Keycloak)

**¿Por qué Jackson no tiene scope="provided"?**
- Jackson NO viene incluido en Keycloak por defecto
- Se incluye en nuestro JAR
- Es una dependencia externa que necesitamos

**¿Qué hace `<Dependencies>` en MANIFEST.MF?**
- Le dice a Keycloak qué módulos cargar
- Sin esto, Keycloak no encuentra las clases de SPI
- Es específico del classloader de Keycloak

---

## Archivo de Registro del SPI

### META-INF/services/org.keycloak.storage.UserStorageProviderFactory

```
com.empresa.spi.D1UserStorageProviderFactory
```

### ¿Para qué sirve este archivo?

**Java Service Loader Pattern:**
1. Java busca archivos en `META-INF/services/`
2. El nombre del archivo es la **interfaz**
3. El contenido es la **implementación**
4. Java automáticamente registra la implementación

**Sin este archivo:**
- Keycloak no encuentra nuestro SPI
- No aparece en la lista de proveedores
- Error: "Provider not found"

**Con este archivo:**
- Keycloak automáticamente registra nuestro SPI
- Aparece en User Federation
- Se puede configurar desde la UI

---

## Compilación e Instalación

### Paso 1: Compilar

```bash
# Ir al directorio del SPI
cd keycloak-spi/

# Compilar y generar JAR
mvn clean package

# Resultado: target/d1-user-storage-spi-1.0.0.jar
```

### Paso 2: Copiar a Keycloak

```bash
# Copiar JAR a directorio de providers
cp target/d1-user-storage-spi-1.0.0.jar /path/to/keycloak/providers/

# En nuestro proyecto Docker:
cp target/d1-user-storage-spi-1.0.0.jar ../keycloak/spi/d1-user-storage-spi.jar
```

### Paso 3: Reiniciar Keycloak

```bash
# Docker Compose
docker compose restart keycloak

# Standalone
bin/kc.sh start-dev
```

### Paso 4: Verificar Instalación

```bash
# Ver logs de Keycloak
docker compose logs keycloak | grep -i spi

# Buscar nuestro proveedor
# Debería aparecer: "d1-user-storage" provider registered
```

---

## Configuración en Keycloak

### Paso 1: Acceder al Admin

```
URL: http://localhost:8080/admin/
Usuario: admin  
Password: admin_mvp
```

### Paso 2: Ir a User Federation

1. Seleccionar realm: `django-realm`
2. Sidebar → `User Federation`
3. Click `Add provider`
4. Seleccionar `d1-user-storage`

### Paso 3: Configurar el Proveedor

```
Display Name: D1 User Storage
Priority: 0

Settings:
  D1 Base URL: http://d1:8001  
  Internal API Key: internal-api-key-super-secret-mvp

Cache:
  Cache Policy: DEFAULT
```

### Paso 4: Probar

1. Click `Save`
2. Click `Test connection` (si está disponible)  
3. Ir a `Users` → `Add user`
4. Username: `user_d1_d3`
5. Save → `Credentials` tab
6. Set password: `pass123`
7. Login con esas credenciales

---

## Troubleshooting

### Error: "Provider not found"

**Problema:** Keycloak no encuentra nuestro SPI

**Verificaciones:**
```bash
# 1. JAR está en el lugar correcto?
ls -la /opt/keycloak/providers/d1-user-storage-spi.jar

# 2. Archivo de servicio existe?
jar tf d1-user-storage-spi.jar | grep META-INF/services

# 3. Contenido del archivo es correcto?
jar xf d1-user-storage-spi.jar
cat META-INF/services/org.keycloak.storage.UserStorageProviderFactory
```

**Solución:**
- Verificar que el archivo `META-INF/services/...` existe
- Verificar que el contenido es exactamente: `com.empresa.spi.D1UserStorageProviderFactory`

### Error: "ClassNotFoundException"

**Problema:** Keycloak no puede cargar las clases

**Verificaciones:**
```bash
# Ver clases en el JAR
jar tf d1-user-storage-spi.jar | grep .class

# Ver MANIFEST.MF
jar xf d1-user-storage-spi.jar
cat META-INF/MANIFEST.MF
```

**Solución:**
- Verificar que `<Dependencies>` está en MANIFEST.MF
- Verificar que las clases están compiladas correctamente

### Error: "Connection refused"

**Problema:** El SPI no puede conectar con la API de D1

**Verificaciones:**
```bash
# Probar conectividad desde Keycloak
docker exec sso-keycloak curl http://d1:8001/api/internal/auth/verify/ \
  -H "X-Internal-Api-Key: internal-api-key-super-secret-mvp" \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"test"}'
```

**Soluciones:**
- Verificar que D1 está corriendo
- Verificar configuración de red Docker
- Verificar URL en configuración del proveedor
- Verificar API Key

### Error: "Authentication failed"

**Problema:** Las credenciales no se validan correctamente

**Debug:**
```bash
# Ver logs detallados de Keycloak
docker compose logs -f keycloak

# Probar API manualmente
curl -H "X-Internal-Api-Key: internal-api-key-super-secret-mvp" \
     -H "Content-Type: application/json" \
     -d '{"username":"user_d1_d3","password":"pass123"}' \
     http://localhost:8001/api/internal/auth/verify/
```

**Verificaciones:**
- Usuario existe en Django D1
- Contraseña es correcta
- API Key es correcta
- Endpoint está funcionando

---

## Mejoras Avanzadas

### Logging Personalizado

```java
import org.jboss.logging.Logger;

public class D1UserStorageProvider {
    private static final Logger logger = Logger.getLogger(D1UserStorageProvider.class);
    
    @Override
    public boolean isValid(RealmModel realm, UserModel user, CredentialInput credentialInput) {
        logger.infof("Validating credentials for user: %s", user.getUsername());
        
        String password = credentialInput.getChallengeResponse();
        D1UserData data = apiClient.verify(user.getUsername(), password);
        
        if (data != null) {
            logger.infof("Authentication successful for user: %s", user.getUsername());
            return true;
        } else {
            logger.warnf("Authentication failed for user: %s", user.getUsername());
            return false;
        }
    }
}
```

### Cache de Usuarios

```java
@Override
public UserModel getUserByUsername(RealmModel realm, String username) {
    // Buscar en cache primero
    UserModel cached = session.userCache().getCachedUser(realm, username);
    if (cached != null) {
        logger.debugf("User found in cache: %s", username);
        return cached;
    }
    
    // Si no está en cache, buscar en API
    D1UserData data = apiClient.findByUsername(username);
    if (data == null) return null;
    
    UserModel user = new D1UserAdapter(session, realm, model, data);
    
    // Guardar en cache
    session.userCache().evict(realm, user);
    
    return user;
}
```

### Configuración Avanzada

```java
@Override
public List<ProviderConfigProperty> getConfigProperties() {
    List<ProviderConfigProperty> props = new ArrayList<>();
    
    // URL de D1
    ProviderConfigProperty urlProp = new ProviderConfigProperty();
    urlProp.setName("d1Url");
    urlProp.setLabel("D1 Base URL");
    urlProp.setType(ProviderConfigProperty.STRING_TYPE);
    urlProp.setDefaultValue("http://d1:8001");
    urlProp.setHelpText("URL base de la API de D1");
    props.add(urlProp);
    
    // API Key
    ProviderConfigProperty keyProp = new ProviderConfigProperty();
    keyProp.setName("apiKey");
    keyProp.setLabel("Internal API Key");
    keyProp.setType(ProviderConfigProperty.PASSWORD);
    keyProp.setHelpText("Clave para autenticar con la API de D1");
    props.add(keyProp);
    
    // Timeout
    ProviderConfigProperty timeoutProp = new ProviderConfigProperty();
    timeoutProp.setName("timeout");
    timeoutProp.setLabel("Timeout (seconds)");
    timeoutProp.setType(ProviderConfigProperty.STRING_TYPE);
    timeoutProp.setDefaultValue("5");
    timeoutProp.setHelpText("Timeout para llamadas HTTP");
    props.add(timeoutProp);
    
    return props;
}
```

---

## Recursos y Referencias

### Documentación Oficial

- [Keycloak SPI Guide](https://www.keycloak.org/docs/latest/server_development/)
- [User Storage SPI](https://www.keycloak.org/docs/latest/server_development/#_user-storage-spi)
- [Maven Dependencies](https://mvnrepository.com/artifact/org.keycloak)

### Ejemplos Adicionales

- [Keycloak GitHub Examples](https://github.com/keycloak/keycloak/tree/main/examples)
- [User Storage Examples](https://github.com/keycloak/keycloak/tree/main/examples/providers)

### Herramientas Útiles

```bash
# Ver contenido de JAR
jar tf archivo.jar

# Extraer JAR 
jar xf archivo.jar

# Ver logs de Keycloak en tiempo real
docker compose logs -f keycloak

# Debug HTTP requests
curl -v -H "Header: value" http://api-url
```

---

## Conclusión

Has aprendido a crear un **Custom SPI completo** para Keycloak:

✅ **Estructura del proyecto** con 5 clases principales  
✅ **Comunicación HTTP** con APIs externas  
✅ **Adaptación de datos** entre sistemas  
✅ **Configuración Maven** y dependencias  
✅ **Instalación y configuración** en Keycloak  
✅ **Debugging y troubleshooting**

### Próximos Pasos

1. **Personalizar** el SPI para tus necesidades específicas
2. **Agregar logging** detallado para debugging  
3. **Implementar cache** para mejor performance
4. **Agregar tests** unitarios con JUnit
5. **Documentar** tu SPI para el equipo

¡Ahora tienes el conocimiento para crear SPIs avanzados en Keycloak! 🚀