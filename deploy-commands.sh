# Comandos de despliegue para autenticación por token

# 1. Compilar el SPI de Keycloak
cd /home/goar/projects/keycloak/sso-mvp/keycloak-spi
mvn clean package

# 2. Copiar el JAR compilado al directorio de Keycloak
cp target/d1-user-storage-spi-1.0.0.jar ../keycloak/spi/d1-user-storage-spi.jar

# 3. Reiniciar Keycloak para cargar los nuevos cambios
cd /home/goar/projects/keycloak/sso-mvp
docker-compose restart keycloak

# 4. Verificar los logs de Keycloak (opcional)
# docker-compose logs -f keycloak

sleep 20 && echo "Keycloak reiniciado"
